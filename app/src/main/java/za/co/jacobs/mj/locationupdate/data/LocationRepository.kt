package za.co.jacobs.mj.locationupdate.data

/**
 * Provides a single, reliable location fix using only the AOSP location stack.
 */
interface LocationRepository {

    /**
     * Requests one fresh location fix, trying the available providers in order of accuracy
     * (GPS, then network, then passive) and falling back to the best last-known location.
     *
     * @param timeoutMs how long to wait for each provider to deliver a fresh fix.
     */
    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): LocationResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
