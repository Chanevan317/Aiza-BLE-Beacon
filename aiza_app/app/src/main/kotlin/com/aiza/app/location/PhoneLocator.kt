package com.aiza.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class PhoneFix(val lat: Double, val lon: Double, val accuracyM: Float?, val place: String?)

/**
 * The phone's own location for "last seen" (FR-STA-03), from Android's LocationManager (no Play services, no key).
 * Uses a recent cached fix when there is one, so frequent calls stay cheap on battery.
 */
interface Locator {
    fun allowed(): Boolean
    /** [precise] = GPS-quality fix (used when a tag is lost); otherwise a cheaper one. */
    suspend fun current(precise: Boolean): PhoneFix?
}

class PhoneLocator(private val context: Context) : Locator {
    private val manager = context.getSystemService(LocationManager::class.java)

    override fun allowed(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    override suspend fun current(precise: Boolean): PhoneFix? {
        if (!allowed() || !manager.isLocationEnabled) return null
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { manager.hasProvider(it) && manager.isProviderEnabled(it) } ?: return null
        // Reuse a recent fix only if it is fresh and already good enough.
        val maxAgeMs = if (precise) 15_000L else 60_000L
        val maxAccuracyM = if (precise) 25f else 100f
        val cached = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.takeIf {
            (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 < maxAgeMs &&
                it.hasAccuracy() && it.accuracy <= maxAccuracyM
        }
        val loc = cached ?: withTimeoutOrNull(if (precise) 25_000 else 15_000) { fresh(provider, precise) }
            ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull() // better an older fix than none
            ?: return null
        return PhoneFix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null, placeName(loc))
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(provider: String, precise: Boolean): Location? = suspendCancellableCoroutine { cont ->
        val cancel = CancellationSignal()
        cont.invokeOnCancellation { cancel.cancel() }
        val quality = if (precise) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(0).setQuality(quality).build()
        runCatching {
            manager.getCurrentLocation(provider, request, cancel, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    /** Street-level name like "12, 5th Cross, Indiranagar". Uses the phone's geocoder; null if it has none or no network. */
    private suspend fun placeName(loc: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context)
        val address = if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(list: MutableList<android.location.Address>) { if (cont.isActive) cont.resume(list.firstOrNull()) }
                        override fun onError(message: String?) { if (cont.isActive) cont.resume(null) }
                    })
                }
            }
        } else withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION") runCatching { geocoder.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull() }.getOrNull()
        }
        return address?.run {
            val street = listOfNotNull(subThoroughfare, thoroughfare).joinToString(" ").ifBlank { null }
            listOfNotNull(street, subLocality ?: locality).distinct().joinToString(", ").ifBlank { null }
                ?: getAddressLine(0)?.split(",")?.take(2)?.joinToString(",")?.trim()
        }
    }
}
