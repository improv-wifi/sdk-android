package com.wifi.improv.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wifi.improv.DeviceState
import com.wifi.improv.ImprovDevice
import com.wifi.improv.ImprovManager
import com.wifi.improv.demo.ui.theme.ImprovTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var improv: ImprovManager
    private val viewModel: ImprovViewModel by viewModels()

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.all { it.value }) {
            Log.i(TAG, "Got permission!")
            improv.findDevices()
        }
    }

    private fun findDevices() {
        // make sure we have permissions!
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (requiredPermissions.any { permission ->
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_DENIED
            }) {
            requestPermission.launch(requiredPermissions)
        } else {
            improv.findDevices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        improv = ImprovManager(applicationContext, viewModel)
        setContent {
            val improvScreenState by viewModel.improvState.observeAsState()
            ImprovMain(
                screenState = improvScreenState,
                findDevices = { findDevices() },
                stopScan = { improv.stopScan() },
                connect = { improv.connectToDevice(it) },
                sendWifi = { ssid, pass -> improv.sendWifi(ssid, pass) }
            )
        }
    }
}

@Composable
fun ImprovMain(
    screenState: ImprovScreenState?,
    findDevices: () -> Unit,
    stopScan: () -> Unit,
    connect: (ImprovDevice) -> Unit,
    sendWifi: (String, String) -> Unit
) {
    ImprovTheme {
        Surface(color = MaterialTheme.colors.background) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(5.dp)
            ) {
                Row(Modifier.align(CenterHorizontally)) {
                    Button(onClick = findDevices, enabled = !(screenState?.scanning ?: false)) {
                        Text("Scan for Devices")
                    }
                    Button(onClick = stopScan, enabled = screenState?.scanning ?: false) {
                        Text("Stop Scanning")
                    }
                }
                Spacer(Modifier.padding(5.dp))
                ImprovDeviceList(
                    devices = screenState?.devices ?: listOf(),
                    connect = connect
                )
                Spacer(Modifier.padding(5.dp))
                if (screenState?.btConnected == true) {
                    ImprovStatus(
                        name = screenState.name,
                        address = screenState.address,
                        deviceState = screenState.deviceState,
                        errorState = screenState.errorState,
                        rpcResult = screenState.rpcResult
                    )
                    Spacer(Modifier.padding(5.dp))
                }
                if (screenState?.deviceState == DeviceState.AUTHORIZED.toString()) {
                    ImprovWifiEntry(sendWifi = sendWifi)
                }
            }
        }
    }
}


@Composable
fun ImprovStatus(
    name: String?,
    address: String?,
    deviceState: String?,
    errorState: String?,
    rpcResult: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Connected Device:",
                style = MaterialTheme.typography.h6
            )
            Text(text = "Name: $name")
            Text(text = "Address: $address")
            Text(text = "Device State: $deviceState")
            Text(text = "Error State: $errorState")
            Text(text = "RPC Result: $rpcResult")
        }
    }
}

@Composable
fun ImprovDeviceList(
    devices: List<ImprovDevice>,
    connect: (ImprovDevice) -> Unit
) {
    if (devices.isEmpty()) {
        Text(text = "No devices found yet.")
    }
    LazyColumn {
        items(devices) {
            Button(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .padding(10.dp),
                onClick = { connect(it) }
            ) {
                Column {
                    Text(it.name ?: it.address)
                    Text(
                        text = it.address,
                        style = MaterialTheme.typography.caption
                    )
                }
            }

        }
    }
}

@Composable
fun ImprovWifiEntry(
    sendWifi: (String, String) -> Unit
) {
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Enter Wifi Information:",
                style = MaterialTheme.typography.h6
            )
            Spacer(Modifier.padding(5.dp))
            TextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("SSID") }
            )
            Spacer(Modifier.padding(5.dp))
            TextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Password") }
            )
            Spacer(Modifier.padding(5.dp))
            Button(onClick = { sendWifi(ssid, pass) }) {
                Text(text = "Submit Wifi")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val improvScreenState = ImprovScreenState(
        false,
        listOf(ImprovDevice("Test Device", "12:34:56:78")),
        "demoName",
        "12:34:56:78",
        true,
        DeviceState.AUTHORIZED.toString(),
        "errorState",
        listOf()
    )
    ImprovMain(screenState = improvScreenState, {}, {}, {}, { _, _ -> })
}