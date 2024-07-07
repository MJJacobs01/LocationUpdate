package za.co.jacobs.mj.locationupdate

import android.Manifest
import android.annotation.*
import android.content.*
import android.location.*
import android.os.*
import android.util.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.activity.result.contract.*
import androidx.annotation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import za.co.jacobs.mj.locationupdate.ui.theme.*
import java.text.*

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            
            val lat = remember { mutableDoubleStateOf(0.0) }
            val lng = remember { mutableDoubleStateOf(0.0) }
            val accuracy = remember { mutableFloatStateOf(0f) }
            val altitude = remember { mutableDoubleStateOf(0.0) }
            val bearing = remember { mutableFloatStateOf(0f) }
            val speed = remember { mutableFloatStateOf(0f) }
            val bundle = remember { mutableStateOf(Bundle()) }
            val placeName = remember { mutableStateOf("") }
            
            val decimalFormat = DecimalFormat("0.0000")
            
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    coroutineScope.launch {
                        val location = gpsProvider(context = context)
                        location?.let {
                            lat.doubleValue = it.latitude
                            lng.doubleValue = it.longitude
                            accuracy.floatValue = it.accuracy
                            altitude.doubleValue = it.altitude
                            bearing.floatValue = it.bearing
                            speed.floatValue = it.speed
                            it.extras?.let { bundle ->
                                bundle.putBundle("extras", bundle)
                            }
                        }
                        
                        Geocoder(context)
                            .getFromLocation(
                                lat.doubleValue,
                                lng.doubleValue,
                                1
                            ) { addresses ->
                                if (addresses.isNotEmpty()) {
                                    //  Address is not empty and address can be accessed
                                    placeName.value = addresses[0].toString()
                                } else {
                                    //  Address is empty
                                    placeName.value = "Address is empty"
                                }
                            }
                        
                        //  Todo - Geocoder can only be called once there is internet access for the app
//                        placeName.value = Geocoder(context)
//                            .getFromLocation(
//                                lat.doubleValue,
//                                lng.doubleValue,
//                                1
//                            ).toString()
                    }
                }
            }
            
            LocationUpdateTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "The coordinates for the current location is:")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Latitude : ${decimalFormat.format(lat.doubleValue)}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Longitude : ${decimalFormat.format(lng.doubleValue)}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Accuracy : ${accuracy.floatValue}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Altitude : ${altitude.doubleValue}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Bearing : ${bearing.floatValue}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Speed : ${speed.floatValue}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Satellites : ${
                                bundle.value.toString()
//                                    .getBundle("extras")?.getInt("satellites")
                            }"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This is the name ${placeName.value}"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        ) {
                            Text(text = "Request current location")
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
suspend fun gpsProvider(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val locationRequest = LocationRequest.Builder(1000L)
        .setDurationMillis(Long.MAX_VALUE)
        .setMaxUpdates(1)
        .build()
    locationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER,
        locationRequest,
        context.mainExecutor
    ) {
        /** No-Op */
    }
    
    val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val numberOfSatellites = status.satelliteCount
            for (i in 0 until numberOfSatellites) {
                if (status.getCn0DbHz(i) > 0.5) {
                    Log.e(
                        "Satellite information",
                        "This is number $i from $numberOfSatellites seen by the device.\n" +
                                "ID: ${status.getSvid(i)}\n" +
                                "Signal Strength: ${status.getCn0DbHz(i)}\n" +
                                "Used in fix: ${status.usedInFix(i)}\n" +
                                "azimuth: ${status.getAzimuthDegrees(i)}\n" +
                                "constellationType: ${status.getConstellationType(i)}\n" +
                                "elevation: ${status.getElevationDegrees(i)}\n" +
                                "almanacData: ${status.hasAlmanacData(i)}\n" +
                                "carrierFrequencyHz: ${status.hasCarrierFrequencyHz(i)}\n" +
                                "basebandCn0DbHz: ${status.hasBasebandCn0DbHz(i)}\n" +
                                "ephemeris: ${status.hasEphemerisData(i)}\n" +
                                "contents: ${status.describeContents()}\n"
                    )
                }
            }
            
        }
    }
    locationManager.registerGnssStatusCallback(context.mainExecutor, gnssCallback)
    delay(5000L)
    return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
}

@SuppressLint("MissingPermission")
fun networkProvider(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
}

@SuppressLint("MissingPermission")
fun passiveProvider(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
}

//  Do not want to use as it crashes
// available for API >= 31
@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
fun fusedProvider(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
}
