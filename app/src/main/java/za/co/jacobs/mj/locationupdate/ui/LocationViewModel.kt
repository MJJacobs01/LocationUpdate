package za.co.jacobs.mj.locationupdate.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.co.jacobs.mj.locationupdate.data.LocationManagerRepository
import za.co.jacobs.mj.locationupdate.data.LocationRepository
import za.co.jacobs.mj.locationupdate.data.LocationResult

class LocationViewModel(
    private val repository: LocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    fun fetchLocation() {
        _uiState.value = LocationUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getCurrentLocation()) {
                is LocationResult.Success -> LocationUiState.Success(result.data)
                is LocationResult.Error -> result.toUiState()
            }
        }
    }

    /** Called when the user denies the runtime permission outright (no system request issued). */
    fun onPermissionDenied() {
        _uiState.value = LocationUiState.Error(
            message = "Location permission is required to show your position.",
            permissionDenied = true,
        )
    }

    private fun LocationResult.Error.toUiState(): LocationUiState.Error = when (reason) {
        LocationResult.Failure.PERMISSION_DENIED -> LocationUiState.Error(
            message = "Location permission is required to show your position.",
            permissionDenied = true,
        )

        LocationResult.Failure.PROVIDERS_DISABLED -> LocationUiState.Error(
            message = "Location services are turned off. Enable them in system settings and try again.",
        )

        LocationResult.Failure.TIMEOUT -> LocationUiState.Error(
            message = "Timed out waiting for a location fix. Move to an area with a clearer sky and try again.",
        )

        LocationResult.Failure.NO_FIX -> LocationUiState.Error(
            message = "Couldn't determine your location. Please try again.",
        )
    }

    /** Lightweight manual DI — wires the AOSP-backed repository without pulling in Hilt. */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LocationViewModel(
                LocationManagerRepository(context.applicationContext),
            ) as T
        }
    }
}
