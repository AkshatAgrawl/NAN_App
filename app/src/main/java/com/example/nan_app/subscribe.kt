package com.example.nan_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkInfo
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.Socket
import android.os.Handler
import android.widget.Toast
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.BufferedReader
import java.io.InputStreamReader

var subscribeSession: SubscribeDiscoverySession? = null
var subscribestarted = false
private var socket: Socket? = null
private var peerIp: String? = null

private fun sendMessageToPeer(
    message: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (socket == null) {
        onError("❌ No connection. Cannot send message.")
        return
    }

    if (message.isBlank()) return

    Thread {
        try {
            val outputStream = socket!!.getOutputStream()
            outputStream.write((message + "\n").toByteArray())
            outputStream.flush()

            Log.d("NAN", "✅ Message sent: $message")
            Handler(Looper.getMainLooper()).post {
                onSuccess()
            }
        } catch (e: Exception) {
            Log.e("NAN", "❌ Failed to send message: ${e.message}")
            Handler(Looper.getMainLooper()).post {
                onError("❌ Failed to send message: ${e.message}")
            }
        }
    }.start()
}


@RequiresApi(Build.VERSION_CODES.Q)
fun requestDataPath(
    context: Context,
    subscribeDiscoverySession: SubscribeDiscoverySession,
    peerHandle: PeerHandle,
    ownMac: ByteArray?,
    messages: SnapshotStateList<String>
) {
    val networkSpecifier =
        WifiAwareNetworkSpecifier.Builder(subscribeDiscoverySession, peerHandle)
            .build()

    val networkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
        .setNetworkSpecifier(networkSpecifier)
        .build()

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    Log.d("NAN", "Sending data path request")

    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("NAN", "Data path established to peer")
            Toast.makeText(context, "Data path established", Toast.LENGTH_SHORT).show()
            connectivityManager.bindProcessToNetwork(network)
        }


        override fun onUnavailable() {
            Log.e("NAN", "Failed to establish data path")
            try {
                connectivityManager.unregisterNetworkCallback(this)
            } catch (e: Exception) {
                Log.w("NAN", "Callback may not have been registered")
            }
            connectivityManager.bindProcessToNetwork(null)
            Toast.makeText(context, "Data path not established", Toast.LENGTH_SHORT).show()
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            Log.d("NAN", "Capabilities changed")

            val awareInfo = capabilities.transportInfo as? WifiAwareNetworkInfo
            peerIp = awareInfo?.peerIpv6Addr?.hostAddress

            if (peerIp == null) {
                Log.e("NAN", "Failed to get peer IP from transportInfo")
                return
            }

            Log.d("NAN", "Got peer IP: $peerIp")


            Thread {
                try {
                    socket = network.socketFactory.createSocket(peerIp, 8888)
                    val inputStream = socket?.getInputStream()
                    val reader = BufferedReader(InputStreamReader(inputStream))

                    while (true) {
                        val line = reader.readLine() ?: break
                        Log.d("NAN", "Received: $line")

                        Handler(Looper.getMainLooper()).post {
                            messages.add("Peer: $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NAN", "❌ Error reading message: ${e.message}")
                }
            }.start()
        }
        override fun onLost(network: Network) {
            Log.w("NAN", "Data path lost")
            try {
                socket?.close()
                Log.d("NAN", "Socket closed")

                subscribeDiscoverySession.close()
                Log.d("NAN", "Discovery session closed")

                connectivityManager.unregisterNetworkCallback(this)
                Log.d("NAN", "Network callback unregistered")
            } catch (e: Exception) {
                Log.e("NAN", "Cleanup error: ${e.message}")
            }
        }
    }
    connectivityManager.requestNetwork(networkRequest, networkCallback, 10000)
}

@RequiresPermission(Manifest.permission.ACCESS_WIFI_STATE)
@RequiresApi(Build.VERSION_CODES.O)
fun StartSubscribing(
    context: Context,
    wifiAwareManager: WifiAwareManager,
    onSessionReady: (WifiAwareSession) -> Unit,
    onSubscribeStarted: (SubscribeDiscoverySession) -> Unit,
    onSubscribeFailed: () -> Unit,
    onServiceDiscovered: (PeerHandle, String) -> Unit,
    onMessageSendSucceeded: (messageId : Int) -> Unit ,
    ownMac: ByteArray?,
) {
    if (!wifiAwareManager.isAvailable) {
        Log.e("NAN", "Wi-Fi Aware not available")
        onSubscribeFailed()
        return
    }

    wifiAwareManager.attach(object : AttachCallback() {
        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
        override fun onAttached(session: WifiAwareSession) {
            val wifiAwareSession = session
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
                    subscribeSession = session
                    Log.d("NAN", "Subscribe started")
                    onSubscribeStarted(session)
                }

                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onServiceDiscovered(
                    peerHandle: PeerHandle,
                    serviceSpecificInfo: ByteArray,
                    matchFilter: List<ByteArray>,
                ) {
                    if (subscribestarted == false) {
                        subscribestarted = true
                    val info = String(serviceSpecificInfo)
                        onServiceDiscovered(peerHandle, info)
                    Log.d("NAN", "Discovered service: $info")

                        val message = "request_ndp"
                        val messageId = 0 // Can be anything, just used to track status

                        try {
                            subscribeSession?.sendMessage(
                                peerHandle,
                                messageId,
                                message.toByteArray()
                            )
                            Log.d("NAN", "Sent message to peer: $message")
                        } catch (e: Exception) {
                            Log.e("NAN", "Failed to send message: ${e.message}")
                        }
                }
                }
                override fun onMessageSendSucceeded(messageId: Int) {
                    Log.d("NAN", "Follow-up message sent successfully. Now requesting data path.")

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
    var isServiceDiscovered by remember { mutableStateOf(false) }
    var ownMac by remember { mutableStateOf<ByteArray?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var subscribeSession by remember { mutableStateOf<WifiAwareSession?>(null) }
    var discoveredPeerHandle by remember { mutableStateOf<PeerHandle?>(null) }
    val context = LocalContext.current
    var subscribeDiscoverySession by remember { mutableStateOf<SubscribeDiscoverySession?>(null) }
    val messages = remember { mutableStateListOf<String>() }
    var messageText by remember { mutableStateOf("") }
    val nanSessionHelper = remember {
        NanSessionHelper(
            context,
            wifiAwareManager,
            onMacAvailable = { macBytes -> ownMac = macBytes },
            onSessionReady = { /* ignore if already handled elsewhere */ },
            onFailed = {}
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
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                        verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Scrollable content above the input bar
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally                ) {
                    Text("Your NAN MAC Address:")
                    Text(
                        ownMac?.joinToString(":") { "%02X".format(it) } ?: "Getting MAC...",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(onClick = {
                        Log.d("NAN", "Start Subscribing with:")
                        Log.d("NAN", "Service Name: ${SubscribeSettings.serviceName}")
                        subscribestarted = false
                        StartSubscribing(
                            context = context,
                            wifiAwareManager = wifiAwareManager,
                            onSessionReady = { session -> subscribeSession = session },
                            onSubscribeFailed = { Log.e("NAN", "Subscribe failed") },
                            onSubscribeStarted = { session -> subscribeDiscoverySession = session },
                            onServiceDiscovered = { peer, info ->
                                Log.i("NAN", "Discovered peer with info: $info")
                                CoroutineScope(Dispatchers.Main).launch {
                                    isServiceDiscovered = true
                                    discoveredPeerHandle = peer
                                }
                            },
                            onMessageSendSucceeded = { messageId ->
                                Log.i("NAN", "Message sent successfully: $messageId")
                            },
                            ownMac = ownMac
                        )
                    }) {
                        Text("Start Subscribing")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            discoveredPeerHandle?.let { peer ->
                                subscribeDiscoverySession?.let { session ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        requestDataPath(context, session, peer, ownMac, messages)
                                    }
                                }
                            }
                        },
                        enabled = isServiceDiscovered
                    ) {
                        Text("Request Data Path")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        for (message in messages) {
                            Text(
                                text = message,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                // Fixed input row at bottom
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message") },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        val messageToSend = messageText.trim()
                        sendMessageToPeer(
                            message = messageToSend,
                            onSuccess = {
                                messages.add("You: ${messageToSend}")
                                messageText = ""
                            },
                            onError = { errorMsg ->
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }) {
                        Text("Send")
                    }
                }
            }


        }
        LaunchedEffect(Unit) {
            Log.d("NAN", "Wi-Fi Aware available: ${wifiAwareManager?.isAvailable}")
        }
    }
}
