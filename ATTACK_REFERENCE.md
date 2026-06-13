#!/data/data/com.termux/files/usr/bin/python3
"""
OBLITERATE — All BLE Attack Payloads Reference
Based on Flipper Xtreme + simondankelmann BLE Spam research

CURRENTLY IN OB APK (15 modes):
  Mode 0  - 🪟 Swift Pair (Windows)
  Mode 1  - 🤖 Fast Pair Devices
  Mode 2  - 🤖 Fast Pair Debug
  Mode 3  - 🤖 Fast Pair NonProd  
  Mode 4  - 🤖 Fast Pair Phone Setup
  Mode 5  - 🍎 Apple New Device Popup
  Mode 6  - 🍎 Apple Action Modal
  Mode 7  - 🍎 Apple iOS 17 CRASH
  Mode 8  - 🍎 Apple New AirTag
  Mode 9  - 🍎 Apple Not Your Device
  Mode 10 - ⭐ Samsung Buds
  Mode 11 - ⭐ Samsung Watch
  Mode 12 - 💗 LoveSpouse Play
  Mode 13 - 💗 LoveSpouse Stop
  Mode 14 - 😴 AirSense CPAP Spoof

NEW MODES TO ADD:
  Mode 15 - 📛 BT Settings Flood (rapid name cycling)
  Mode 16 - 🔫 Aggressive Cycle (200ms per mode instead of 2s)
  Mode 17 - 📡 WiFi Probe Flood (from phone - Android WiFi API)
  Mode 18 - 🎯 AirTag Tracker Spoof (iPhone "tracking" alert)

BLUETOOTH EXPLOITS FOUND (research only - not in app):
  Linux:
    CVE-2026-45835 - L2CAP NULL pointer (crash)
    CVE-2026-46056 - Use-after-free pairing (RCE potential)
    CVE-2026-46140 - MediaTek BT buffer overflow
    CVE-2026-31512 - L2CAP buffer overflow
    CVE-2026-46186 - virtio_bt driver crash
  
  Apple:
    CVE-2025-36911 - WhisperPair (patched)
    CVE-2026-20700 - Zero-day RCE (patched iOS 26)
    CVE-2026-20606 - UIKit vulnerability (patched iOS 26.3)
    Unpatched: BLE popup spam (protocol, can't patch)
    Unpatched: Action Modal spam (HomePod/AppleTV triggers)

ESP32 ATTACKS (when board arrives):
  WiFi Deauth Flood - kick devices off networks
  WiFi Beacon Spam - flood fake SSIDs  
  BLE Spam - same as OB app but standalone
  Evil Portal - captive portal phishing
  Probe Sniffing - track nearby devices
"""

# ── BT Settings Flood payload generator ──

TROLL_NAMES = [
    # Government / Authority
    "FBI Surveillance Van",
    "NSA Listening Post 7",
    "CIA Field Office",
    "Homeland Security Drone",
    "Police Surveillance #42",
    "Interpol Mobile Unit",
    "Secret Service Detail",
    "DEA Monitoring Station",
    
    # Corporate / Tech
    "Apple Internal Test Device",
    "Google Data Collection",
    "Microsoft Security Audit",
    "Amazon Delivery Drone",
    "Meta Ad Tracker v2",
    "Tesla Telemetry Unit",
    "SpaceX Starlink Terminal",
    
    # Creepy / Personal
    "Your Location: Tracked",
    "Microphone Active",
    "Camera Remote Access",
    "This Device Is Infected",
    "Your Battery Is Mining Crypto",
    "⚠️ Security Breach Detected",
    "You Are Being Watched",
    "Someone Is Following You",
    
    # Funny / Troll
    "Mom's Hidden Vibrator",
    "Karen's AirPods Pro",
    "Free WiFi (No Password)",
    "I Can See Your Screen",
    "Your Dad's Search History",
    "Bathroom Cam #3",
    "Ceiling Microphone Active",
    "Toilet Paper Inventory Low",
    
    # Tech Support Scam
    "Windows Support Alert",
    "Virus Detected Call Now",
    "iCloud Storage Full",
    "Your Account Is Locked",
    "Payment Method Expired",
    "Update Required Now",
    "Critical Security Update",
    "Your PC Has 847 Viruses",
]

def generate_settings_flood(count=50):
    """Generate a list of troll names for BT Settings Flood."""
    import random
    names = []
    for i in range(count):
        base = random.choice(TROLL_NAMES)
        suffix = random.choice(["", f" #{random.randint(1,99)}", f" ({random.randint(1000,9999)})"])
        names.append(f"{base}{suffix}")
    return names

if __name__ == "__main__":
    print("OBLITERATE — Attack Payload Reference")
    print("====================================")
    print()
    print("Troll name examples for Settings Flood:")
    for name in generate_settings_flood(10):
        print(f"  • {name}")
    print()
    print(f"Total troll names in database: {len(TROLL_NAMES)}")
    print(f"Generated Settings Flood list: 50 names")
