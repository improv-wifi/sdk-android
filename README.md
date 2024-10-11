# Android SDK for Improv Wi-Fi
## Installation
Recent releases of the library are published on Maven Central, and each release of the library includes the AAR on the [GitHub release](https://github.com/improv-wifi/sdk-android/releases).

```groovy
dependencies {
    implementation "org.openhomefoundation.improv-wifi:sdk-android:<latest version>"
}
```

## Usage
This library is for dealing with the complexities of the Bluetooth connection not creating any UI. You as the developer are responsible for creating the UI/UX. A sample [Compose](https://developer.android.com/jetpack/compose) UI is provided in the demo application.

 - Review which [permissions are needed](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions) for your app. By default, the library only adds the Bluetooth permissions to the manifest and no location permissions. You will want to add the location permission and/or declare that your app doesn't use Bluetooth to derive physical location.

 - A simplified typical flow looks like:
   ```mermaid
   flowchart TB
    A("Check app permissions and Bluetooth status")-- OK --> B["`ImprovManager::findDevices`"]
    B ---|onDeviceFound|C["ImprovManager::connectToDevice"]
    B --> D["ImprovManager::stopScan"]
    C ---|onConnectionStateChange true, onStateChange AUTHORIZED|E["ImprovManager::sendWifi"]
    E ---|onStateChange PROVISIONED, onRpcResult has url|F("Redirect to device in browser")
   ```

[![Improv Wi-Fi - A project from the Open Home Foundation](https://www.openhomefoundation.org/badges/ohf-library.png)](https://www.openhomefoundation.org/)