package za.co.jacobs.mj.locationupdate.ui

import za.co.jacobs.mj.locationupdate.data.LocationData

/** UI state for the location screen. */
sealed interface LocationUiState {
    /** Nothing requested yet. */
    data object Idle : LocationUiState

    /** A fix is being acquired. */
    data object Loading : LocationUiState

    /** A fix was obtained. */
    data class Success(val data: LocationData) : LocationUiState

    /** The request failed; [message] is user-facing. [permissionDenied] drives the settings CTA. */
    data class Error(
        val message: String,
        val permissionDenied: Boolean = false,
    ) : LocationUiState
}
