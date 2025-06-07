import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.nan_app.NanMode
import com.example.nan_app.NanSessionHelper
import com.example.nan_app.PublishSettings
import com.example.nan_app.SettingsScreen


@RequiresApi(Build.VERSION_CODES.O)
fun StartPublishing(
    context: Context,
    wifiAwareManager: WifiAwareManager,
    onSessionReady: (WifiAwareSession) -> Unit,
    onPublishStarted: () -> Unit,
    onPublishFailed: () -> Unit,
) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
        onPublishFailed()
        return
    }

    wifiAwareManager.attach(@RequiresApi(Build.VERSION_CODES.O)
    object : AttachCallback() {
        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
        override fun onAttached(session: WifiAwareSession) {

            onSessionReady(session)

            val publishConfig = PublishConfig.Builder()
                .setServiceName(PublishSettings.serviceName)
                .setServiceSpecificInfo(PublishSettings.serviceInfo.toByteArray())
                .setPublishType(PublishSettings.publishType)
                .build()

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            session.publish(publishConfig, object : DiscoverySessionCallback() {
                override fun onPublishStarted(session: PublishDiscoverySession) {
                    onPublishStarted()
                }

                override fun onSessionTerminated() {
                    // Handle session termination if needed
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    Log.d("NAN", "Received Message")
                }

                override fun onMessageSendSucceeded(messageId: Int) {
                    Log.d("NAN", "Message send succeeded with id $messageId")
                }

                override fun onMessageSendFailed(messageId: Int) {
                    Log.d("NAN", "Message send failed with id $messageId")
                }
            }, null)
        }

        override fun onAttachFailed() {
            onPublishFailed()
        }
    }, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PublishScreen(
    wifiAwareManager: WifiAwareManager,
    onBack: () -> Unit
) {
    var ownMac by remember { mutableStateOf("Getting MAC...") }
    var showSettings by remember { mutableStateOf(false) }
    var PublishSession by remember { mutableStateOf<WifiAwareSession?>(null) }

    val context = LocalContext.current

    val nanSessionHelper = remember {
        NanSessionHelper(
            context,
            wifiAwareManager,
            onMacAvailable = { mac -> ownMac = mac },
            onSessionReady = { /* ignore if already handled elsewhere */ },
            onFailed = { ownMac = "Failed to get MAC" }
        )
    }

    LaunchedEffect(Unit) {
        nanSessionHelper.attach()
    }

    DisposableEffect(Unit) {
        onDispose {
            nanSessionHelper.close()
        }
    }

    if (showSettings) {
        SettingsScreen(
            mode = NanMode.PUBLISH,
            onBack = { showSettings = false }
        )
    } else {
        BackHandler {
            PublishSession?.close()
            PublishSession = null
            onBack() }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Publish") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your NAN MAC Address:")
                Text(ownMac, style = MaterialTheme.typography.bodyLarge)

                Spacer(modifier = Modifier.height(24.dp))

                // 🔘 Button to start publishing
                Button(
                    onClick = {
                        // Trigger your publish logic from here
                        Log.d("NAN", "Starting publish with: ${PublishSettings.serviceName}")
                        StartPublishing(
                            context = context,
                            wifiAwareManager = wifiAwareManager,
                            onSessionReady = { session ->
                                Log.d("NAN", "Session ready")
                                PublishSession = session
                            },
                            onPublishStarted = {
                                Log.d("NAN", "Publish started")
                            },
                            onPublishFailed = {
                                Log.e("NAN", "Publish failed")
                            }
                        )

                    },
                ) {
                    Text("Start Publishing")
                }
            }
        }
    }
}



