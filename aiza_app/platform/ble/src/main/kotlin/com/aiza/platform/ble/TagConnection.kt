package com.aiza.platform.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class LinkState { DISCONNECTED, CONNECTING, READY }

data class DeviceInfo(val model: String?, val serial: String?, val firmware: String?)

/**
 * GATT client for one bonded tag (SRS §4.3): ring (FR-RNG-01), find mode (FR-HC-01), battery (FR-STA-02),
 * button events (FR-RNG-02), link-loss level and connection RSSI (FR-SEP). Keeps reconnecting until [close].
 *
 * Android allows one GATT operation at a time per connection, so every operation goes through [op]: a mutex
 * plus a deferred completed by the matching callback, with a timeout.
 * Needs BLUETOOTH_CONNECT; callers check it before creating connections.
 */
@SuppressLint("MissingPermission")
class TagConnection(
    private val context: Context,
    val address: String,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    val state: StateFlow<LinkState> = _state
    private val _battery = MutableStateFlow<Int?>(null)
    val battery: StateFlow<Int?> = _battery
    private val _info = MutableStateFlow<DeviceInfo?>(null)
    val info: StateFlow<DeviceInfo?> = _info
    private val _buttons = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 8)
    val buttonEvents: SharedFlow<ButtonEvent> = _buttons

    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val mutex = Mutex()
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var pending: CompletableDeferred<Any?>? = null
    @Volatile private var closed = false
    private var setupJob: Job? = null
    private var lastButtonCounter = -1

    fun connect() {
        if (closed || gatt != null) return
        val device: BluetoothDevice = adapter.getRemoteDevice(address)
        _state.value = LinkState.CONNECTING
        // autoConnect: the stack connects whenever the tag is in range, without our scanning.
        gatt = device.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun close() {
        closed = true
        setupJob?.cancel()
        gatt?.let { it.disconnect(); it.close() }
        gatt = null
        pending?.complete(null)
        _state.value = LinkState.DISCONNECTED
    }

    /** Waits up to [timeoutMs] for the link to be ready. */
    suspend fun awaitReady(timeoutMs: Long = 8_000): Boolean {
        connect()
        return withTimeoutOrNull(timeoutMs) { state.first { it == LinkState.READY } } != null
    }

    suspend fun ring(on: Boolean): Boolean =
        write(AizaGatt.IMMEDIATE_ALERT_SERVICE, AizaGatt.ALERT_LEVEL, byteArrayOf(if (on) AizaGatt.ALERT_HIGH else AizaGatt.ALERT_NONE), withResponse = false)

    suspend fun setFindMode(on: Boolean, timeoutSeconds: Int = 300): Boolean =
        write(AizaGatt.AIZA_SERVICE, AizaGatt.FIND_MODE_CONTROL, AizaGatt.findMode(on, timeoutSeconds), withResponse = true)

    suspend fun setLinkLossLevel(level: Int): Boolean =
        write(AizaGatt.LINK_LOSS_SERVICE, AizaGatt.ALERT_LEVEL, byteArrayOf(level.toByte()), withResponse = true)

    suspend fun readRssi(): Int? {
        if (_state.value != LinkState.READY) return null
        return op { it.readRemoteRssi() } as? Int
    }

    // ------------------------------------------------------------------ operations

    private suspend fun op(timeoutMs: Long = 5_000, start: (BluetoothGatt) -> Boolean): Any? = mutex.withLock {
        val g = gatt ?: return null
        val d = CompletableDeferred<Any?>()
        pending = d
        if (!start(g)) { pending = null; return null }
        val r = withTimeoutOrNull(timeoutMs) { d.await() }
        pending = null
        r
    }

    private fun chr(service: UUID, uuid: UUID): BluetoothGattCharacteristic? = gatt?.getService(service)?.getCharacteristic(uuid)

    private suspend fun write(service: UUID, uuid: UUID, value: ByteArray, withResponse: Boolean): Boolean {
        if (_state.value != LinkState.READY) return false
        val c = chr(service, uuid) ?: return false
        val type = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return op { g ->
            if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, value, type) == BluetoothGatt.GATT_SUCCESS
            else @Suppress("DEPRECATION") run { c.writeType = type; c.value = value; g.writeCharacteristic(c) }
        } == true
    }

    private suspend fun read(service: UUID, uuid: UUID): ByteArray? {
        val c = chr(service, uuid) ?: return null
        return op { it.readCharacteristic(c) } as? ByteArray
    }

    private suspend fun enableNotify(service: UUID, uuid: UUID): Boolean {
        val c = chr(service, uuid) ?: return false
        val g = gatt ?: return false
        if (!g.setCharacteristicNotification(c, true)) return false
        val d = c.getDescriptor(AizaGatt.CCCD) ?: return false
        val v = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return op { gg ->
            if (Build.VERSION.SDK_INT >= 33) gg.writeDescriptor(d, v) == BluetoothGatt.GATT_SUCCESS
            else @Suppress("DEPRECATION") run { d.value = v; gg.writeDescriptor(d) }
        } == true
    }

    /** After service discovery: subscribe, then read battery and device info. */
    private fun setUp() {
        setupJob?.cancel()
        setupJob = scope.launch {
            enableNotify(AizaGatt.AIZA_SERVICE, AizaGatt.BUTTON_EVENT)
            enableNotify(AizaGatt.BATTERY_SERVICE, AizaGatt.BATTERY_LEVEL)
            read(AizaGatt.BATTERY_SERVICE, AizaGatt.BATTERY_LEVEL)?.firstOrNull()?.let { _battery.value = it.toInt() and 0xFF }
            fun str(b: ByteArray?) = b?.toString(Charsets.UTF_8)?.trim()?.ifEmpty { null }
            _info.value = DeviceInfo(
                model = str(read(AizaGatt.DEVICE_INFO_SERVICE, AizaGatt.MODEL_NUMBER)),
                serial = str(read(AizaGatt.DEVICE_INFO_SERVICE, AizaGatt.SERIAL_NUMBER)),
                firmware = str(read(AizaGatt.DEVICE_INFO_SERVICE, AizaGatt.FIRMWARE_REVISION)),
            )
            _state.value = LinkState.READY
        }
    }

    private fun onValue(uuid: UUID, value: ByteArray) {
        when (uuid) {
            AizaGatt.BUTTON_EVENT -> AizaGatt.buttonEvent(value)?.let { e ->
                if (e.counter != lastButtonCounter) { lastButtonCounter = e.counter; _buttons.tryEmit(e) }
            }
            AizaGatt.BATTERY_LEVEL -> value.firstOrNull()?.let { _battery.value = it.toInt() and 0xFF }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "$address state $newState status $status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = LinkState.CONNECTING
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setupJob?.cancel()
                pending?.complete(null)
                _state.value = LinkState.DISCONNECTED
                // A fresh autoConnect object reconnects more reliably than reusing one after an error.
                g.close()
                if (gatt === g) gatt = null
                if (!closed) scope.launch { delay(1_000); connect() }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) setUp() else g.disconnect()
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            pending?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }

        @Deprecated("Android 12")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) pending?.complete(if (status == BluetoothGatt.GATT_SUCCESS) c.value else null)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pending?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            pending?.complete(if (status == BluetoothGatt.GATT_SUCCESS) rssi else null)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = onValue(c.uuid, value)

        @Deprecated("Android 12")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) onValue(c.uuid, c.value)
        }
    }

    private companion object { const val TAG = "AizaTag" }
}
