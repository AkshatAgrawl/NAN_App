package com.example.nan_app

import PublishScreen
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.IdentityChangedListener
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.nan_app.ui.theme.Nan_appTheme

enum class NanMode {
    PUBLISH,
    SUBSCRIBE
}

private val REQUIRED_PERMISSIONS = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}

@Composable
fun ChoosePublishOrSubscribe(
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedOption == "publish",
                    onClick = { onOptionSelected("publish") }
                )
                Spacer(Modifier.width(8.dp))
                Text("Publish", fontSize = 24.sp)
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedOption == "subscribe",
                    onClick = { onOptionSelected("subscribe") }
                )
                Spacer(Modifier.width(8.dp))
                Text("Subscribe", fontSize = 24.sp)
            }
        }

        Button(
            onClick = onSubmit,
            enabled = selectedOption != null,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Submit", fontSize = 20.sp)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SimpleChoiceScreen() {
    var currentPage by remember { mutableStateOf("choose") }
    var selectedOption by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val wifiAwareManager = remember {
        context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager
    }
    when (currentPage) {
        "choose" -> ChoosePublishOrSubscribe(
            selectedOption = selectedOption,
            onOptionSelected = { selectedOption = it },
            onSubmit = {
                if (selectedOption != null) {
                    Log.d("MyApp", "Selected option: $selectedOption")
                    currentPage = selectedOption!!
                }
            }
        )
        "publish" -> PublishScreen(
            wifiAwareManager = wifiAwareManager,
            onBack = {
                currentPage = "choose"
                selectedOption = null
            }

        )
        "subscribe" -> SubscribeScreen(
            wifiAwareManager = wifiAwareManager,
            onBack = {
                currentPage = "choose"
                selectedOption = null
            }
        )
    }
}
class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register the permission launcher ONCE during onCreate
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.all { it.value }
            if (granted) {
                Toast.makeText(this, "All permissions granted.", Toast.LENGTH_SHORT).show()
                setContent {
                    SimpleChoiceScreen()
                }
            } else {
                Toast.makeText(this, "Permissions not granted. Retrying...", Toast.LENGTH_SHORT).show()
                // Only request again for the denied ones
                val deniedPermissions = permissions.filterValues { !it }.keys.toTypedArray()
                if (deniedPermissions.isNotEmpty()) {
                    permissionLauncher.launch(deniedPermissions)
                }
            }
        }

        // Check + launch for missing permissions
        requestPermissionsIfNeeded()
    }
    override fun onStart() {
        super.onStart()
        checkWifiEnabled()
    }
    private fun showWifiOffDialog() {
        if (!this.isFinishing) {
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Wi-Fi Disabled")
                    .setMessage("Wi-Fi is off. Please enable it to continue.")
                    .setCancelable(false)
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun checkWifiEnabled() {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Wi-Fi is off. Please enable it.", Toast.LENGTH_LONG).show()
            showWifiOffDialog()
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestPermissionsIfNeeded() {
        val requiredPermissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        val permissionsToAsk = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToAsk.isEmpty()) {
            // Already granted
            setContent {
                SimpleChoiceScreen()
            }
        } else {
            // Ask only those not granted
            permissionLauncher.launch(permissionsToAsk.toTypedArray())
        }
    }
}

class NanSessionHelper(
    private val context: Context,
    private val wifiAwareManager: WifiAwareManager,
    private val onMacAvailable: (String) -> Unit,
    private val onSessionReady: (WifiAwareSession) -> Unit,
    private val onFailed: () -> Unit
) {
    private var wifiAwareSession: WifiAwareSession? = null

    fun attach() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // Permissions check - you must handle requesting these before calling attach
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onFailed()
            return
        }

        wifiAwareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                wifiAwareSession = session
                onSessionReady(session)
            }

            override fun onAttachFailed() {
                onFailed()
            }
        }, object : IdentityChangedListener() {
            override fun onIdentityChanged(mac: ByteArray?) {
                mac?.let {
                    val macString = it.joinToString(":") { byte -> "%02X".format(byte) }
                    onMacAvailable(macString)
                }
            }
        }, null)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun close() {
        wifiAwareSession?.close()
        wifiAwareSession = null
    }
}