# Flipper Zero BLE Spam Attack Payloads — Research Notes

## Swift Pair (Windows)
- Manufacturer ID: 0x0006 (Microsoft)
- Triggers Windows 10/11 "Swift Pair" notification popup
- Flipper payload: BLE advertisement with Microsoft vendor data
- Known payload: `060001 0920...` (Microsoft Beacon)
- Windows shows: "Tap to connect to [DEVICE_NAME]"

## Google Fast Pair (Android / ChromeOS)
- Service UUID: 0xFE2C (Google Fast Pair Service)
- Triggers "Set up nearby device" popup on Android
- Known payload: 16-bit service UUID 0xFE2C in advertisement
- Android shows: "[DEVICE_NAME] is nearby. Tap to set up."

## iOS / Apple Continuity
- Uses Apple manufacturer ID + specific proximity payloads
- Triggers "AirPods nearby", "Apple TV", etc popups
- AirPods use specific pattern with iBeacon-like data
- Known patterns: manufacturer data 0x004C (Apple) + sub-type bytes

## LoveSpouse
- Adult toy device with open BLE advertisements
- Triggers "Pair with LoveSpouse?" on nearby phones
- Known manufacturer ID: various Chinese BLE vendors
- Simple connectable BLE peripheral advertisement

## Rapid Name Cycling
- Changes BLE device name every 100-500ms
- Overwhelms BLE scanner apps on Android/iOS
- Can crash poorly-coded BLE stacks
- No special payload needed — just name cycling

## Samsung Easy Pair (Galaxy Buds)
- Manufacturer ID: 0x0075 (Samsung)
- Triggers Samsung Easy Pair popup on Galaxy devices
- Shows: "Galaxy Buds detected nearby"

## Key Implementation Notes
- Use AdvertiseData.addManufacturerData(manufacturerId, payload)
- Use AdvertiseData.addServiceUuid(ParcelUuid) for service UUIDs
- Set connectable=false for popup spam, connectable=true for pairing spam
- Cycle names via multiple startAdvertising/stopAdvertising calls

## References
- https://github.com/flipperdevices/flipperzero-firmware
- https://www.bitdefender.com/en-gb/blog/hotforsecurity/flipper-zero-bluetooth-spam-attack-capabilities-expand-to-android-and-windows
