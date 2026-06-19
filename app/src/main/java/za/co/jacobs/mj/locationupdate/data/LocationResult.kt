package za.co.jacobs.mj.locationupdate.data

/**
 * A single location fix obtained from the AOSP [android.location.LocationManager]
 * (no Google Play Services).
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double,
    val bearing: Float,
    val speed: Float,
    val provider: String,
    val satellitesInFix: Int? = null,
    val placeName: String? = null,
)

/**
 * Outcome of a single-shot location request. Every failure mode is modelled explicitly so
 * the UI can react to it (rather than silently showing 0,0).
 */
sealed interface LocationResult {
    data class Success(val data: LocationData) : LocationResult
    data class Error(val reason: Failure) : LocationResult

    enum class Failure {
        /** The location permission was not granted. */
        PERMISSION_DENIED,

        /** No usable location provider is enabled (GPS / network off). */
        PROVIDERS_DISABLED,

        /** A provider was enabled but produced no fix within the timeout. */
        TIMEOUT,

        /** No fix could be produced and there was no usable last-known location. */
        NO_FIX,
    }
}
