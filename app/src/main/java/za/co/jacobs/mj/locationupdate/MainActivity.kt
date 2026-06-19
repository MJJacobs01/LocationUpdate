package za.co.jacobs.mj.locationupdate

import android.Manifest
import android.annotation.*
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.activity.result.contract.*
import androidx.annotation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import za.co.jacobs.mj.locationupdate.ui.theme.*
import java.text.*
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationUpdateTheme {
                LocationScreen()
            }
        }
    }
}

@Composable
fun LocationScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val lat = remember { mutableDoubleStateOf(0.0) }
    val lng = remember { mutableDoubleStateOf(0.0) }
    val accuracy = remember { mutableFloatStateOf(0f) }
    val altitude = remember { mutableDoubleStateOf(0.0) }
    val bearing = remember { mutableFloatStateOf(0f) }
    val speed = remember { mutableFloatStateOf(0f) }
    val placeName = remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }
    var showGpsDisabled by remember { mutableStateOf(false) }
    
    val decimalFormat = remember { DecimalFormat("0.0000") }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (LocationUtils.isGpsEnabled(context)) {
                coroutineScope.launch {
                    isLoading = true
                    val location = getBestLocation(context)
                    updateLocationState(location, lat, lng, accuracy, altitude, bearing, speed)
                    location?.let {
                        updatePlaceName(context, it) { name -> placeName.value = name }
                    } ?: run {
                        placeName.value = "Failed to get location fix"
                    }
                    isLoading = false
                }
            } else {
                showGpsDisabled = true
            }
        }
    }

    if (showRationale) {
        RationaleDialog(
            onDismiss = { showRationale = false },
            onConfirm = {
                showRationale = false
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        )
    }

    if (showGpsDisabled) {
        GpsDisabledDialog(
            onDismiss = { showGpsDisabled = false },
            onConfirm = {
                showGpsDisabled = false
                LocationUtils.openLocationSettings(context)
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current Location Information",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))

            LocationDataRow("Latitude", decimalFormat.format(lat.doubleValue))
            LocationDataRow("Longitude", decimalFormat.format(lng.doubleValue))
            LocationDataRow("Accuracy", "${accuracy.floatValue} m")
            LocationDataRow("Altitude", "${altitude.doubleValue} m")
            LocationDataRow("Bearing", "${bearing.floatValue}°")
            LocationDataRow("Speed", "${speed.floatValue} m/s")

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Address: ${placeName.value}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Searching for satellites...")
            } else {
                Button(
                    onClick = {
                        handleLocationRequest(
                            context,
                            onGrant = {
                                coroutineScope.launch {
                                    isLoading = true
                                    val location = getBestLocation(context)
                                    updateLocationState(location, lat, lng, accuracy, altitude, bearing, speed)
                                    location?.let {
                                        updatePlaceName(context, it) { name -> placeName.value = name }
                                    } ?: run {
                                        placeName.value = "Failed to get location fix"
                                    }
                                    isLoading = false
                                }
                            },
                            onRationale = { showRationale = true },
                            onGpsDisabled = { showGpsDisabled = true },
                            onPermissionRequest = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                        )
                    }
                ) {
                    Text(text = "Request current location")
                }
            }
        }
    }
}

private fun updateLocationState(
    location: Location?,
    lat: MutableDoubleState,
    lng: MutableDoubleState,
    accuracy: MutableFloatState,
    altitude: MutableDoubleState,
    bearing: MutableFloatState,
    speed: MutableFloatState
) {
    location?.let {
        lat.doubleValue = it.latitude
        lng.doubleValue = it.longitude
        accuracy.floatValue = it.accuracy
        altitude.doubleValue = it.altitude
        bearing.floatValue = it.bearing
        speed.floatValue = it.speed
    }
}

private fun handleLocationRequest(
    context: Context,
    onGrant: () -> Unit,
    onRationale: () -> Unit,
    onGpsDisabled: () -> Unit,
    onPermissionRequest: () -> Unit
) {
    when {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED -> {
            if (LocationUtils.isGpsEnabled(context)) {
                onGrant()
            } else {
                onGpsDisabled()
            }
        }
        ActivityCompat.shouldShowRequestPermissionRationale(
            context as Activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) -> {
            onRationale()
        }
        else -> {
            onPermissionRequest()
        }
    }
}

@Composable
fun RationaleDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Location Permission") },
        text = { Text("This app needs location access to show you your current coordinates and place name.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Grant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Deny")
            }
        }
    )
}

@Composable
fun GpsDisabledDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GPS Disabled") },
        text = { Text("GPS is required for high accuracy location updates. Please enable it in settings.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LocationDataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", fontWeight = FontWeight.Bold)
        Text(text = value)
    }
}

private fun updatePlaceName(context: Context, location: Location, onResult: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Geocoder(context).getFromLocation(
            location.latitude,
            location.longitude,
            1
        ) { addresses ->
            if (addresses.isNotEmpty()) {
                onResult(addresses[0].getAddressLine(0) ?: addresses[0].toString())
            } else {
                onResult("No address found")
            }
        }
    } else {
        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                onResult(addresses[0].getAddressLine(0) ?: addresses[0].toString())
            } else {
                onResult("No address found")
            }
        } catch (e: Exception) {
            onResult("Geocoder error: ${e.message}")
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun getBestLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    return withTimeoutOrNull(30000L) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (ignored: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
            
            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
        }
    } ?: run {
        // Fallback to last known if timeout occurs
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }
}
