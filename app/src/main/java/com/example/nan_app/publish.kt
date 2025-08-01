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
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkInfo
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.nan_app.NanMode
import com.example.nan_app.NanSessionHelper
import com.example.nan_app.PublishSettings
import com.example.nan_app.SettingsScreen
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

lateinit var publishSession: PublishDiscoverySession
private const val TAG = "Publish"
private var socket: Socket? = null
private var peerIp: String? = null
var serverSocket: ServerSocket? = null
var clientSocket: Socket?= null

private fun sendMessageToPeer(
    message: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (clientSocket == null) {
        onError("❌ No connection. Cannot send message.")
        return
    }

    if (message.isBlank()) return

    Thread {
        try {
            val outputStream = clientSocket!!.getOutputStream()
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
@RequiresApi(Build.VERSION_CODES.O)
fun StartPublishing(
    context: Context,
    wifiAwareManager: WifiAwareManager,
    onSessionReady: (WifiAwareSession) -> Unit,
    onPublishStarted: () -> Unit,
    onPublishFailed: () -> Unit,
    messages: SnapshotStateList<String>
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
                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onPublishStarted(session: PublishDiscoverySession) {
                    publishSession = session

                }

                override fun onSessionTerminated() {
                    // Handle session termination if needed
                }

                @RequiresApi(Build.VERSION_CODES.Q)
                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {

                    val received = message.toString(Charsets.UTF_8)
                    Log.d("NAN", "Received Message $received")
                    registerNetworkCallback( context, publishSession, peerHandle, messages)
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

@RequiresApi(Build.VERSION_CODES.Q)
private fun registerNetworkCallback(
    context: Context,
    session: PublishDiscoverySession,
    peerHandle: PeerHandle,
    messages: SnapshotStateList<String>
) {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val networkSpecifier = WifiAwareNetworkSpecifier.Builder(session, peerHandle)
        .build()

    val networkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
        .setNetworkSpecifier(networkSpecifier)
        .build()

    connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "✅ Data path network available")
            Toast.makeText(context, "Data path established", Toast.LENGTH_SHORT).show()
            handleNetworkConnection(network, messages) // e.g., bind sockets here


        }

        override fun onUnavailable() {
            Log.e(TAG, "❌ Data path request received but failed — network unavailable")
            Toast.makeText(context, "Data path not established", Toast.LENGTH_SHORT).show()
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "⚠️ Data path network lost")
            try {
                socket?.close()
                Log.d("NAN", "Socket closed")

                // Clean session explicitly if needed
                publishSession.close()
                Log.d("NAN", "Discovery session closed")

                connectivityManager.unregisterNetworkCallback(this)
                Log.d("NAN", "Network callback unregistered")
            } catch (e: Exception) {
                Log.e("NAN", "Cleanup error: ${e.message}")
            }
        }
    })

    Log.d(TAG, "📡 Publisher registered to accept data path requests")
}

private fun handleNetworkConnection(network: Network, messages: SnapshotStateList<String>) {
    // WiFi Aware creates a direct network connection between devices
    // Use a predefined port or service discovery mechanism


    val port = 8888 // Use a predefined port both sides know about

    Log.d("SUB", "Client connected from subscriber")
    try {
        serverSocket = ServerSocket()
        serverSocket?.bind(InetSocketAddress(port))
        Log.d("SUB", "Server listening on WiFi Aware network, port: $port")
        clientSocket = serverSocket?.accept()
        Log.d("NAN","Trying to listen")
        val inputStream = clientSocket?.getInputStream()
        val reader = BufferedReader(InputStreamReader(inputStream))

        while (true) {
            val line = reader.readLine() ?: break
            Log.d("NAN", "Received: $line")

            Handler(Looper.getMainLooper()).post {
                messages.add("Peer: $line")
            }
        }
    } catch (e: Exception) {
        Log.e("NAN", "Error reading message: ${e.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PublishScreen(
    wifiAwareManager: WifiAwareManager,
    onBack: () -> Unit
) {
    var ownMac by remember { mutableStateOf<ByteArray?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var PublishSession by remember { mutableStateOf<WifiAwareSession?>(null) }
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<String>() }
    var messageText by remember { mutableStateOf("") }


    val nanSessionHelper = remember {
        NanSessionHelper(
            context,
            wifiAwareManager,
            onMacAvailable = { macBytes -> ownMac = macBytes },
            onSessionReady = { },
            onFailed = {  }

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
                Text(ownMac?.joinToString(":") { "%02X".format(it) } ?: "Getting MAC...", style = MaterialTheme.typography.bodyLarge)

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
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
                            },
                            messages
                        )

                    },
                ) {
                    Text("Start Publishing")
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    items(messages) { message ->
                        Text(
                            text = message,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
                    },) {
                        Text("Send")
                    }
                }
            }
        }
    }
}



