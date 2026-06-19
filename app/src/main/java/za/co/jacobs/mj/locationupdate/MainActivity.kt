package za.co.jacobs.mj.locationupdate

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import za.co.jacobs.mj.locationupdate.data.LocationData
import za.co.jacobs.mj.locationupdate.ui.LocationUiState
import za.co.jacobs.mj.locationupdate.ui.LocationViewModel
import za.co.jacobs.mj.locationupdate.ui.theme.LocationUpdateTheme
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationUpdateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LocationScreen()
                }
            }
        }
    }
}

@Composable
private fun LocationScreen(
    viewModel: LocationViewModel = viewModel(
        factory = LocationViewModel.Factory(LocalContext.current),
    ),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) viewModel.fetchLocation() else viewModel.onPermissionDenied()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val state = uiState) {
            LocationUiState.Idle -> Text(
                text = "Tap below to fetch your current location.",
                textAlign = TextAlign.Center,
            )

            LocationUiState.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(text = "Acquiring a location fix…")
            }

            is LocationUiState.Success -> LocationDetails(state.data)

            is LocationUiState.Error -> ErrorContent(
                message = state.message,
                showSettings = state.permissionDenied,
                onOpenSettings = { context.openAppSettings() },
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            enabled = uiState != LocationUiState.Loading,
            onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        ) {
            Text(text = "Request current location")
        }
    }
}

@Composable
private fun LocationDetails(data: LocationData) {
    val df = DecimalFormat("0.0000")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Current location",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            DetailRow("Latitude", df.format(data.latitude))
            DetailRow("Longitude", df.format(data.longitude))
            DetailRow("Accuracy", "${data.accuracy} m")
            DetailRow("Altitude", "${data.altitude} m")
            DetailRow("Bearing", "${data.bearing}°")
            DetailRow("Speed", "${data.speed} m/s")
            DetailRow("Provider", data.provider)
            DetailRow("Satellites in fix", data.satellitesInFix?.toString() ?: "—")
            DetailRow("Place", data.placeName ?: "—")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(text = "$label: $value")
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ErrorContent(
    message: String,
    showSettings: Boolean,
    onOpenSettings: () -> Unit,
) {
    Text(text = message, textAlign = TextAlign.Center)
    if (showSettings) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Text(text = "Open settings")
        }
    }
}

private fun android.content.Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

@Preview(showBackground = true)
@Composable
private fun LocationDetailsPreview() {
    LocationUpdateTheme {
        LocationDetails(
            LocationData(
                latitude = -26.2041,
                longitude = 28.0473,
                accuracy = 4.0f,
                altitude = 1680.0,
                bearing = 92.0f,
                speed = 0.0f,
                provider = "gps",
                satellitesInFix = 9,
                placeName = "Johannesburg, South Africa",
            ),
        )
    }
}
