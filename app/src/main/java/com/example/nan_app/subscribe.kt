package com.example.nan_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
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
import androidx.core.app.ActivityCompat

@RequiresApi(Build.VERSION_CODES.O)
fun StartSubscribing(
    context: Context,
    wifiAwareManager: WifiAwareManager,
    onSessionReady: (android.net.wifi.aware.WifiAwareSession) -> Unit,
    onSubscribeStarted: () -> Unit,
    onSubscribeFailed: () -> Unit,
    onServiceDiscovered: (peerHandle: android.net.wifi.aware.PeerHandle, info: String) -> Unit,
) {
    if (!wifiAwareManager.isAvailable) {
        Log.e("NAN", "Wi-Fi Aware not available")
        onSubscribeFailed()
        return
    }

    wifiAwareManager.attach(object : android.net.wifi.aware.AttachCallback() {
        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
        override fun onAttached(session: WifiAwareSession) {
            onSessionReady(session)

            val subscribeConfig = SubscribeConfig.Builder()
                .setServiceName(SubscribeSettings.serviceName)
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
            session.subscribe(subscribeConfig, object : DiscoverySessionCallback() {
                override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                    Log.d("NAN", "Subscribe started")
                    onSubscribeStarted()
                }

                override fun onServiceDiscovered(
                    peerHandle: PeerHandle,
                    serviceSpecificInfo: ByteArray,
                    matchFilter: List<ByteArray>
                ) {
                    val info = String(serviceSpecificInfo)
                    Log.d("NAN", "Discovered service: $info")
                    onServiceDiscovered(peerHandle, info)
                }

                override fun onSessionTerminated() {
                    Log.w("NAN", "Subscribe session terminated")
                }
            }, null)
        }

        override fun onAttachFailed() {
            Log.e("NAN", "Attach failed")
            onSubscribeFailed()
        }
    }, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SubscribeScreen(
    wifiAwareManager: WifiAwareManager,
    onBack: () -> Unit
) {
    var ownMac by remember { mutableStateOf("Getting MAC...") }
    var showSettings by remember { mutableStateOf(false) }
    var subscribeSession by remember { mutableStateOf<WifiAwareSession?>(null) }
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
        BackHandler {
            showSettings = false
        }
        SettingsScreen(
            mode = NanMode.SUBSCRIBE,
            onBack = { showSettings = false }
        )
    } else {
        val context = LocalContext.current

        BackHandler {
            subscribeSession?.close()
            subscribeSession = null
            onBack() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Subscribe") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your NAN MAC Address:")
                Text(ownMac, style = MaterialTheme.typography.bodyLarge)

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = {
                    // Log subscriber settings here (replace with your settings object)
                    Log.d("NAN", "Start Subscribing with:")
                    Log.d("NAN", "Service Name: ${SubscribeSettings.serviceName}")
                    StartSubscribing(
                        context = context,
                        wifiAwareManager = wifiAwareManager,
                        onSessionReady = { session ->
                            Log.d("NAN", "Session ready")
                            subscribeSession = session
                        },
                        onSubscribeStarted = { Log.d("NAN", "Subscribe session started") },
                        onSubscribeFailed = { Log.e("NAN", "Subscribe failed") },
                        onServiceDiscovered = { peer, info ->
                            Log.i("NAN", "Discovered peer with info: $info")
                            // Optionally trigger UI updates or message sending
                        }
                    )

                    // TODO: Add subscribe logic here using wifiAwareManager
                }) {
                    Text("Start Subscribing")
                }
            }
        }

        LaunchedEffect(Unit) {
            Log.d("NAN", "Wi-Fi Aware available: ${wifiAwareManager?.isAvailable}")
        }
    }
}
