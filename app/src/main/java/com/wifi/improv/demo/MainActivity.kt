package com.wifi.improv.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.improv.wifi.*
import com.wifi.improv.ImprovDevice
import com.wifi.improv.demo.ui.theme.ImprovTheme

class MainActivity : ComponentActivity() {

    private lateinit var improv: ImprovManager
    private val viewModel: ImprovViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        improv = ImprovManager(applicationContext, viewModel)
        setContent {
            ImprovMain(
                viewModel = viewModel,
                findDevices = { improv.findDevices() },
                stopScan = { improv.stopScan() },
                connect = { improv.connectToDevice(it) },
                sendWifi = { ssid, pass -> improv.sendWifi(ssid, pass) }
            )
        }
    }
}

@Composable
fun ImprovMain(
    viewModel: ImprovViewModel,
    findDevices: ()->Unit,
    stopScan: ()->Unit,
    connect: (ImprovDevice) -> Unit,
    sendWifi: (String, String)->Unit
){
    val improvScreenState by viewModel.improvState.observeAsState()
    ImprovTheme {
        // A surface container using the 'background' color from the theme
        Surface(color = MaterialTheme.colors.background) {
            Column(modifier = Modifier.fillMaxHeight()) {
                Row {
                    Button(onClick = findDevices) {
                        Text("Scan for Devices")
                    }
                    Button(onClick = stopScan) {
                        Text("Stop Scanning")
                    }
                }
                Divider()
                ImprovDeviceList(
                    devices = improvScreenState?.devices ?: setOf(),
                    connect = connect
                )
                Divider()
                ImprovStatus(
                    name = improvScreenState?.name,
                    address = improvScreenState?.address,
                    deviceState = improvScreenState?.deviceState,
                    errorState = improvScreenState?.errorState
                )
                Divider()
                ImprovWifiEntry(sendWifi = sendWifi)
            }
        }
    }
}


@Composable
fun ImprovStatus(
    name: String?,
    address: String?,
    deviceState: String?,
    errorState: String?
){
    Column {
        Text(text = "Connected Device:")
        Text(text = "Name: $name")
        Text(text = "Address: $address")
        Text(text = "Device State: $deviceState")
        Text(text = "Error State: $errorState")
    }
}

@Composable
fun ImprovDeviceList(
    devices: Set<ImprovDevice>,
    connect: (ImprovDevice) -> Unit
){
    for(device in devices){
        Button(onClick = { connect(device) }) {
            Text("${device.name}: ${device.address}")
        }
        Divider()
    }
}

@Composable
fun ImprovWifiEntry(
    sendWifi: (String, String)->Unit
){
    var ssid by remember { mutableStateOf("I am IRON LAN") }
    var pass by remember { mutableStateOf("OuiOuiBarry") }

    TextField(
        value = ssid,
        onValueChange = { ssid = it },
        label = { Text("SSID") }
    )
    Divider()
    TextField(
        value = pass,
        onValueChange = { pass = it },
        label = { Text("Password") }
    )
    Button(onClick = { sendWifi(ssid, pass) }) {
        Text(text = "Submit Wifi")
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val viewModel = ImprovViewModel()
    ImprovMain(viewModel = viewModel , {},{}, {}, {_,_->})
}