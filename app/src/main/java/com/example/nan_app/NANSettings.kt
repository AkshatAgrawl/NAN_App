package com.example.nan_app

import android.net.wifi.aware.PublishConfig
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Your existing publish settings object
object PublishSettings {
    var serviceName: String = "com.example.test"
    var serviceInfo: String = "Test_Info"
    var publishType: Int = PublishConfig.PUBLISH_TYPE_UNSOLICITED
}

// You can add subscriber settings similarly here if needed
object SubscribeSettings {
    var filter: String = "Test_Filter"
    var serviceName: String = "com.example.test"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    mode: NanMode,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings - ${mode.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (mode) {
                NanMode.PUBLISH -> PublishSettingsContent(onBack)
                NanMode.SUBSCRIBE -> SubscribeSettingsContent(onBack)
            }
        }
    }
}

@Composable
fun PublishSettingsContent(onBack: () -> Unit) {
    var serviceName by remember { mutableStateOf(PublishSettings.serviceName) }
    var serviceInfo by remember { mutableStateOf(PublishSettings.serviceInfo) }
    var selectedType by remember { mutableStateOf(PublishSettings.publishType) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Publish Settings", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Service Name Input
        OutlinedTextField(
            value = serviceName,
            onValueChange = { serviceName = it },
            label = { Text("Service Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Service Info Input
        OutlinedTextField(
            value = serviceInfo,
            onValueChange = { serviceInfo = it },
            label = { Text("Service Info") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Publish Type Selector
        Text("Publish Type", fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selectedType == PublishConfig.PUBLISH_TYPE_UNSOLICITED,
                onClick = { selectedType = PublishConfig.PUBLISH_TYPE_UNSOLICITED }
            )
            Text("Unsolicited", modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)

            Spacer(modifier = Modifier.width(16.dp))

            RadioButton(
                selected = selectedType == PublishConfig.PUBLISH_TYPE_SOLICITED,
                onClick = { selectedType = PublishConfig.PUBLISH_TYPE_SOLICITED }
            )
            Text("Solicited", modifier = Modifier.padding(start = 4.dp), fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                PublishSettings.serviceName = serviceName
                PublishSettings.serviceInfo = serviceInfo
                PublishSettings.publishType = selectedType
                onBack()
            },

            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Save")
        }
    }
}

@Composable
fun SubscribeSettingsContent(onBack: () -> Unit) {
    var filter by remember { mutableStateOf(SubscribeSettings.filter) }
    var serviceName by remember { mutableStateOf(SubscribeSettings.serviceName) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Subscribe Settings", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Service Name Input
        OutlinedTextField(
            value = serviceName,
            onValueChange = { serviceName = it },
            label = { Text("Service Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Input
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filter") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                SubscribeSettings.serviceName = serviceName
                SubscribeSettings.filter = filter
                onBack()
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Save")
        }
    }
}


