package com.aiza.data.tags

import kotlinx.serialization.Serializable

/**
 * Coarse battery level. The tag estimates charge from LiPo voltage, which is only good to roughly
 * ±10–20 %, so the app never shows an exact percentage (FR-STA-02 still warns below 20 %).
 */
enum class BatteryLevel(val label: String) {
    FULL("Full"), GOOD("Good"), LOW("Low"), CRITICAL("Critical");

    companion object {
        fun from(percent: Int?): BatteryLevel? = when {
            percent == null -> null
            percent >= 70 -> FULL
            percent >= 20 -> GOOD
            percent >= 10 -> LOW
            else -> CRITICAL
        }
    }
}

/** What the tag is attached to; the UI maps each value to an icon (FR-SET-02). */
@Serializable
enum class TagIcon { KEYS, WALLET, BAG, BACKPACK, LUGGAGE, CAR, PET, BIKE, HEADPHONES, LAPTOP, REMOTE, TAG }

/** A paired tag (FR-SET-02, FR-STA-01..03). */
@Serializable
data class Tag(
    /** BLE identity address of the bonded tag; stable key for this tag. */
    val address: String,
    val name: String,
    val icon: TagIcon,
    /** Short ID printed on / reported by the tag, e.g. "AZ-4F2A-91C3". */
    val serial: String,
    val firmwareVersion: String?,
    val batteryPercent: Int?,
    /** Last received signal strength in dBm, if heard recently. */
    val rssiDbm: Int?,
    val lastSeenEpochMs: Long?,
    val lastSeenPlace: String?,
    val lastSeenLat: Double?,
    val lastSeenLon: Double?,
    val leftBehindAlert: Boolean,
    val pairedEpochMs: Long,
    /** A short press on the tag's button rings this phone (FR-RNG-02). Off = presses are ignored. */
    val buttonRingsPhone: Boolean = true,
) {
    fun isNearby(nowMs: Long): Boolean = lastSeenEpochMs != null && nowMs - lastSeenEpochMs < NEARBY_WINDOW_MS
    val batteryLow: Boolean get() = batteryPercent != null && batteryPercent < LOW_BATTERY_PERCENT
    val batteryLevel: BatteryLevel? get() = BatteryLevel.from(batteryPercent)

    companion object {
        const val NEARBY_WINDOW_MS = 60_000L
        const val LOW_BATTERY_PERCENT = 20 // FR-STA-02
    }
}
