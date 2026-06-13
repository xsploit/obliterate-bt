<div align="center">

# OBLITERATE BT

Android wireless testing workbench for Bluetooth, BLE, Wi-Fi/LAN discovery,
GPS-tagged observations, and ESP HTTP control.

<p>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84">
  <img alt="Language" src="https://img.shields.io/badge/language-Java-007396">
  <img alt="Custom build" src="https://img.shields.io/badge/custom_build-local_check_passed-2ea44f">
  <img alt="Gradle" src="https://img.shields.io/badge/gradle-currently_failing-c2410c">
  <img alt="License" src="https://img.shields.io/badge/license-not_declared-lightgrey">
</p>

</div>

## What This Is

OBLITERATE BT is an experimental Android app written against Android SDK APIs.
The current working build path is the custom Termux shell build in
`build.sh`.

This README is intentionally conservative: it lists features that are present in
the codebase and calls out build/runtime limits that still matter.

Implemented does not mean exhaustively tested. Some modules are built into the
APK and some have been manually checked, but the full feature set has not been
validated across every Android version, phone model, target device, or ESP
firmware build.

## Implemented Feature Map

<table>
  <thead>
    <tr>
      <th>Area</th>
      <th>Implemented in</th>
      <th>What is present in code</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Main panel</td>
      <td><code>MainActivity.java</code></td>
      <td>Launcher UI, Bluetooth scan/export controls, BLE advertisement modes, BLE fuzzer controls, Wi-Fi Direct controls, and shortcuts into the secondary modules.</td>
    </tr>
    <tr>
      <td>BLE modes</td>
      <td><code>MainActivity.java</code></td>
      <td>A mode spinner backed by the local <code>bleModes</code> array, plus advertising callbacks and stop paths.</td>
    </tr>
    <tr>
      <td>Bluetooth tools</td>
      <td><code>BtToolsActivity.java</code></td>
      <td>Classic/BLE discovery, device inspection through SDP UUID fetches, RSSI tracking, RFCOMM channel probing, name cycling controls, and scan export.</td>
    </tr>
    <tr>
      <td>Network tools</td>
      <td><code>NetworkActivity.java</code></td>
      <td>Wi-Fi Direct discovery/connection controls, LAN-related scan buttons, NetBIOS scan, SMB scan, printer scan, scan-all sequencing, mDNS/SSDP controls, hotspot controls, and probe controls.</td>
    </tr>
    <tr>
      <td>GPS wardrive</td>
      <td><code>GpsWardriveActivity.java</code></td>
      <td>GPS-tagged Bluetooth/BLE observations, device mapping, GeoJSON export, and GPX track export.</td>
    </tr>
    <tr>
      <td>ESP bridge</td>
      <td><code>EspControlActivity.java</code></td>
      <td>Plain HTTP command UI, preset command buttons, custom command input, and log polling for an ESP-style device at <code>192.168.4.1</code>.</td>
    </tr>
  </tbody>
</table>

## Tested Vs Implemented

<table>
  <thead>
    <tr>
      <th>Status</th>
      <th>What belongs here</th>
      <th>Current notes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Build-checked</td>
      <td>Custom APK build path</td>
      <td><code>bash build.sh</code> completed in this workspace and produced a signed APK.</td>
    </tr>
    <tr>
      <td>Manually reported working</td>
      <td>Selected BLE modes</td>
      <td><code>Swift Pair (Windows)</code> and some Apple BLE popup modes have been reported working in local manual testing.</td>
    </tr>
    <tr>
      <td>Implemented, not completely verified</td>
      <td>Remaining BLE modes, network tools, GPS exports, Bluetooth tools, and ESP bridge controls</td>
      <td>The code and UI paths exist, but they should be treated as needing device-by-device testing.</td>
    </tr>
    <tr>
      <td>Known not clean yet</td>
      <td>Gradle build path</td>
      <td>The custom build works; Gradle still needs cleanup before it can be described as working.</td>
    </tr>
  </tbody>
</table>

## Build Status

<table>
  <thead>
    <tr>
      <th>Build path</th>
      <th>Status in this workspace</th>
      <th>Notes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>bash build.sh</code></td>
      <td>Working in local checks</td>
      <td>Uses <code>aapt2</code>, <code>javac</code>, <code>dx</code>, <code>zipalign</code>, and <code>apksigner</code>. Output APK is <code>build/outputs/obliterate-bt.apk</code>.</td>
    </tr>
    <tr>
      <td><code>gradle --offline :app:assembleDebug</code></td>
      <td>Currently failing locally</td>
      <td>The SDK platform is present, including <code>android-35/source.properties</code> with <code>AndroidVersion.ApiLevel=35</code> and an <code>android-35/android.jar</code>. The failure appears to be Gradle-side, not a missing SDK platform.</td>
    </tr>
  </tbody>
</table>

Gradle context:

- `app/build.gradle` declares `compileSdk 35` and `targetSdk 35`.
- `android-sdk/platforms/android-35/source.properties` exists and reports
  `AndroidVersion.ApiLevel=35`.
- `android-sdk/platforms/android-35/android.jar` exists.
- The repository has Gradle wrapper metadata for Gradle `8.4`, but no checked-in
  `gradlew` script was found locally.
- The available system Gradle `9.5.1` failed during project evaluation.
- Current working assumption: the remaining Gradle issue is environment,
  daemon/cache, or SDK index related. The custom shell build remains the
  reliable build path right now.

Last recorded custom-build check in this workspace:

```text
./build.sh completed without errors
213 classes compiled
APK size: 92K
APK signing verified with v1/v2/v3 schemes
v3.1/v4/SourceStamp signatures were not present
Java 8/deprecated API warnings were emitted
```

## Version And SDK Reality

There are currently two build configurations in the repository:

<table>
  <thead>
    <tr>
      <th>Source</th>
      <th>Version</th>
      <th>Target SDK</th>
      <th>Comment</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>AndroidManifest.xml</code> and <code>build.sh</code></td>
      <td><code>4.4</code> / code <code>4</code></td>
      <td><code>28</code></td>
      <td>This is the custom shell build path.</td>
    </tr>
    <tr>
      <td><code>app/build.gradle</code></td>
      <td><code>2.0</code> / code <code>1</code></td>
      <td><code>35</code></td>
      <td>This metadata is not currently aligned with the custom build.</td>
    </tr>
  </tbody>
</table>

That mismatch matters. Android permission behavior can change depending on the
target SDK and install path.

## Runtime Requirements

The manifest declares permissions for:

- Bluetooth legacy APIs.
- Bluetooth scan, connect, and advertise APIs.
- Wi-Fi state/change access.
- Location access required by Android Bluetooth/Wi-Fi scanning behavior.
- Internet access for network and ESP HTTP features.
- Wake lock usage for long-running operations.

Hardware support is marked as optional for Bluetooth and Wi-Fi Direct, so the
app can install on more devices, but individual modules still depend on the
actual phone/tablet hardware and Android vendor behavior.

## ESP Bridge

`EspControlActivity` targets an ESP-style HTTP API at:

```text
http://192.168.4.1
```

The UI text assumes:

```text
SSID: GhostNet
Password: GhostNet
```

The manifest currently enables cleartext traffic with
`android:usesCleartextTraffic="true"`. That matches the plain HTTP bridge, but
it also means this is not an HTTPS-secured control channel.

## Install Notes

From Termux, after a custom build that completes without errors:

```sh
bash build.sh
cp build/outputs/obliterate-bt.apk ~/storage/downloads/
```

Install with Android's package installer, or use `pm install` if your device
setup allows it.

## Limits And Caveats

- Runtime behavior depends on Android version, target SDK, permissions granted,
  chipset support, vendor Bluetooth/Wi-Fi behavior, and local radio conditions.
- The custom shell build is the known-good path in this workspace; Gradle still
  needs cleanup before it can be treated as a reliable build path. The installed
  SDK platform itself appears to be present.
- Build metadata is split between the manifest/build script and Gradle.
- Export behavior can vary across Android storage models.
- Some features use legacy Android APIs and reflective Bluetooth calls, so
  behavior can vary across devices.
- The ESP bridge uses plain HTTP on a local AP-style address.

## Responsible Use

Use this only on devices, networks, and radio environments you own or are
explicitly authorized to test. The code does not guarantee legal, safe, or
reliable behavior in every environment.

## Project Layout

```text
bluetooth-spammer/
|-- app/src/main/AndroidManifest.xml
|-- app/src/main/java/com/obliterate/btspam/
|   |-- MainActivity.java
|   |-- BtToolsActivity.java
|   |-- NetworkActivity.java
|   |-- GpsWardriveActivity.java
|   `-- EspControlActivity.java
|-- app/build.gradle
|-- build.gradle
|-- settings.gradle
|-- build.sh
|-- ATTACK_REFERENCE.md
|-- RESEARCH.md
`-- README.md
```

## Related Notes

- `ATTACK_REFERENCE.md`: local BLE payload notes and references.
- `RESEARCH.md`: local research notes.
- `downloads/fixreport.html`: bug review report outside this repository.

## License

No license file is currently present in this repository. Do not assume any
license terms until a real `LICENSE` file is added.
