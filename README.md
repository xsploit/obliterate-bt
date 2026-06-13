<p align="center">
  <b>☠ OBLITERATE BT</b>
</p>
<p align="center">
  <i>Android Bluetooth/WiFi Attack Toolkit — 27 BLE Modes, Network Spoofing, GPS Wardriving, ESP32 Control</i>
</p>
<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-brightgreen" alt="Platform">
  <img src="https://img.shields.io/badge/root-not%20required-blue" alt="Root">
  <img src="https://img.shields.io/badge/ESP32-Ghost%20ESP%20Bridge-purple" alt="ESP32">
  <img src="https://img.shields.io/badge/license-MIT-red" alt="License">
</p>

---

## ⚠️ Disclaimer

**This project is for educational and security research purposes only.**

OBLITERATE is a proof-of-concept wireless security testing tool. Use it only on devices and networks you own or have explicit written permission to test. Unauthorized use against devices you do not own may violate local, state, and federal laws.

**You are solely responsible for your actions.** The developers assume no liability for misuse, damage, or legal consequences. Check your local laws and regulations before using any feature of this tool.

---

## Features

### 📶 27 BLE Attack Modes
Swift Pair, Google Fast Pair (5 variants), Apple Continuity (5 variants including iOS 17 crash), Samsung Buds/Watch, LoveSpouse, AirSense CPAP spoof, AirDrop, Handoff, Tethering, Nearby Share, Eddystone-UID/URL, iBeacon, Find My Network, Exposure Notification, Aggressive 200ms cycle, Settings Flood

### 📡 Phone-Native Network Attacks
| Attack | Description |
|--------|-------------|
| WiFi Probe Flood | Forces phone to blast probe requests for 18+ troll SSIDs |
| mDNS Spoofer | Fake AirPlay/Chromecast/printers flooding LAN |
| SSDP Spoofer | Fake TVs/routers/cameras on Windows Network |
| Hotspot Honeypot | Open "Free_WiFi_No_Pass" hotspot, logs MAC/vendor of curious devices |
| WiFi Direct Spam | P2P discovery + invite flooding |
| Network Enumeration | NetBIOS / SMB share / Printer scanner with subnet sweep |

### 🔍 Bluetooth Reconnaissance
| Tool | Description |
|------|-------------|
| BT Scanner | Classic + BLE discovery with device class identification |
| BT Inspector | SDP service discovery — shows all UUIDs with human-readable names |
| RFCOMM Scanner | Probes channels 1-30 on any BT device, finds open services |
| Distance Estimator | RSSI tracking with proximity brackets (touching → far) |
| BT Name Turbo | 100ms name cycling through 18+ troll names |

### 📍 GPS Wardriving
- Maps every BT/BLE device with exact GPS coordinates
- Path tracking with speed/accuracy
- Smart dedup (only logs new sightings >20m apart)
- **Export GeoJSON** — open in geojson.io or Google My Maps
- **Export GPX** — open in Google Earth

### 📡 ESP32 Control (Ghost ESP Bridge)
- HTTP API bridge to Ghost ESP firmware
- Full command set: WiFi scan, deauth, beacon spam, BLE spam, evil portal, packet capture
- Auto-polling log viewer

### 🔬 Byte Fuzzer
13 protocol presets for systematic BLE payload discovery:
Apple Continuity types, Swift Pair, Fast Pair, Samsung, LoveSpouse

---

## Build (Termux, no PC needed)

```bash
# Requirements
pkg install aapt2 openjdk-17 dx apksigner zipalign

# Clone
git clone https://github.com/xsploit/obliterate-bt
cd obliterate-bt

# Build + install
bash build.sh
cp build/outputs/obliterate-bt.apk ~/storage/downloads/
# Install via file manager or: pm install ~/storage/downloads/obliterate-bt.apk
```

**No Android Studio, no Gradle, no PC.** Pure aapt2 + javac + dx pipeline.

---

## ESP32 Setup (optional)

1. Flash [Ghost ESP](https://ghostesp.net) to your ESP32-S3/C3/C6
2. Connect phone to `GhostNet` WiFi (password: `GhostNet`)
3. Open OBLITERATE → 📡 ESP32 CONTROL PANEL
4. Phone becomes the ESP32's screen and controller

---

## Project Structure

```
obliterate-bt/
├── app/src/main/java/com/obliterate/btspam/
│   ├── MainActivity.java          — BLE arsenal + launcher (27 modes + fuzzer)
│   ├── BtToolsActivity.java       — Scanner, inspector, distance, RFCOMM
│   ├── NetworkActivity.java       — Probe flood, mDNS, SSDP, hotspot, LAN scan
│   ├── GpsWardriveActivity.java   — GPS-tagged device mapping
│   └── EspControlActivity.java    — Ghost ESP HTTP bridge
├── build.sh                        — APK builder (aapt2 + javac + dx)
└── ATTACK_REFERENCE.md             — BLE payload reference
```

---

## License

MIT — do whatever you want, at your own risk.
