package za.co.jacobs.mj.locationupdate.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * [LocationRepository] backed purely by the AOSP [LocationManager] — no Google Play Services.
 *
 * Reliability comes from: requesting a *fresh* fix (not a stale last-known one), trying
 * providers in order of accuracy, version-gating to the best single-shot API available, a
 * per-provider timeout, and a last-known fallback only as a last resort.
 */
class LocationManagerRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocationRepository {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** Providers we try, most accurate first. */
    private val providerPriority = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )

    override suspend fun getCurrentLocation(timeoutMs: Long): LocationResult {
        if (!hasLocationPermission()) {
            return LocationResult.Error(LocationResult.Failure.PERMISSION_DENIED)
        }

        val enabledProviders = providerPriority.filter { provider ->
            locationManager.allProviders.contains(provider) &&
                locationManager.isProviderEnabled(provider)
        }
        if (enabledProviders.isEmpty()) {
            return LocationResult.Error(LocationResult.Failure.PROVIDERS_DISABLED)
        }

        // Track satellites used in the fix for the duration of the request (GPS only).
        val satelliteTracker = SatelliteTracker().also { it.start() }
        try {
            for (provider in enabledProviders) {
                val fix = withTimeoutOrNull(timeoutMs) { awaitFix(provider) }
                if (fix != null) {
                    return success(fix, satelliteTracker.usedInFix())
                }
            }

            // No fresh fix in time — fall back to the freshest last-known location.
            val lastKnown = enabledProviders
                .mapNotNull { runCatchingLastKnown(it) }
                .maxByOrNull { it.time }
            return if (lastKnown != null) {
                success(lastKnown, satelliteTracker.usedInFix())
            } else {
                LocationResult.Error(LocationResult.Failure.NO_FIX)
            }
        } finally {
            satelliteTracker.stop()
        }
    }

    private suspend fun success(location: Location, satellites: Int?): LocationResult {
        return LocationResult.Success(
            LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                altitude = location.altitude,
                bearing = location.bearing,
                speed = location.speed,
                provider = location.provider ?: "unknown",
                satellitesInFix = satellites,
                placeName = geocode(location.latitude, location.longitude),
            )
        )
    }

    /** Suspends until [provider] delivers one fresh fix, using the best API for this SDK level. */
    @SuppressLint("MissingPermission") // permission verified in getCurrentLocation()
    private suspend fun awaitFix(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                locationManager.getCurrentLocation(
                    provider,
                    signal,
                    appContext.mainExecutor,
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    // Required no-op overrides for older API levels.
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                    @Deprecated("Deprecated in API 29")
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                }
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        }

    @SuppressLint("MissingPermission") // permission verified in getCurrentLocation()
    private fun runCatchingLastKnown(provider: String): Location? =
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()

    /** Reverse-geocodes coordinates to a human-readable place; null when offline/unavailable. */
    private suspend fun geocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    val name = addresses.firstOrNull()?.let { it.getAddressLine(0) ?: it.locality }
                    if (continuation.isActive) continuation.resume(name)
                }
            }
        } else {
            withContext(ioDispatcher) {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)
                        ?.firstOrNull()
                        ?.let { it.getAddressLine(0) ?: it.locality }
                }.getOrNull() // IOException when offline / no geocoder backend
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /**
     * Registers a [GnssStatus.Callback] for the lifetime of a request and exposes the number of
     * satellites used in the most recent fix. GNSS status is only available from API 24.
     */
    private inner class SatelliteTracker {
        private val count = AtomicInteger(-1)
        private var callback: GnssStatus.Callback? = null

        @SuppressLint("MissingPermission") // permission verified in getCurrentLocation()
        fun start() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            val cb = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    var used = 0
                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) used++
                    }
                    count.set(used)
                }
            }
            callback = cb
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.registerGnssStatusCallback(appContext.mainExecutor, cb)
            } else {
                locationManager.registerGnssStatusCallback(cb, Handler(Looper.getMainLooper()))
            }
        }

        fun stop() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            callback?.let { locationManager.unregisterGnssStatusCallback(it) }
            callback = null
        }

        fun usedInFix(): Int? = count.get().takeIf { it >= 0 }
    }
}
