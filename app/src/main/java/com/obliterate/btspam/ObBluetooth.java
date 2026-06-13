package com.obliterate.btspam;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

final class ObBluetooth {
    private ObBluetooth() {}

    static String deviceName(BluetoothDevice device) {
        String name = device.getName();
        return (name != null && !name.isEmpty()) ? name : device.getAddress();
    }

    static String deviceTypeName(int majorClass) {
        switch (majorClass) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "🎵 Audio/Video";
            case BluetoothClass.Device.Major.COMPUTER: return "💻 Computer";
            case BluetoothClass.Device.Major.HEALTH: return "🏥 Health";
            case BluetoothClass.Device.Major.IMAGING: return "📷 Imaging";
            case BluetoothClass.Device.Major.MISC: return "📦 Misc";
            case BluetoothClass.Device.Major.NETWORKING: return "🌐 Network";
            case BluetoothClass.Device.Major.PERIPHERAL: return "🖱 Peripheral";
            case BluetoothClass.Device.Major.PHONE: return "📱 Phone";
            case BluetoothClass.Device.Major.TOY: return "🎮 Toy";
            case BluetoothClass.Device.Major.WEARABLE: return "⌚ Wearable";
            case BluetoothClass.Device.Major.UNCATEGORIZED: return "❓ Unknown";
            default: return "❓ Unknown (" + majorClass + ")";
        }
    }

    static String deviceSubType(BluetoothClass btClass) {
        int deviceClass = btClass.getDeviceClass();
        if (btClass.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            switch (deviceClass) {
                case 0x0404: return "Headset";
                case 0x0408: return "Loudspeaker";
                case 0x0414: return "Car Audio";
                case 0x0418: return "Set-Top Box";
                case 0x041C: return "VCR";
                case 0x0420: return "Video Camera";
                case 0x0424: return "Camcorder";
                case 0x0428: return "Video Monitor";
                case 0x043C: return "Video Gaming";
                default: return "Audio (0x" + Integer.toHexString(deviceClass) + ")";
            }
        }
        if (btClass.getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER) {
            switch (deviceClass) {
                case 0x0100: return "Desktop";
                case 0x0104: return "Server";
                case 0x0108: return "Laptop";
                case 0x010C: return "Handheld";
                case 0x0110: return "Palm";
                case 0x0114: return "Wearable";
                default: return "Computer (0x" + Integer.toHexString(deviceClass) + ")";
            }
        }
        if (btClass.getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE) {
            switch (deviceClass) {
                case 0x0200: return "Cellular";
                case 0x0204: return "Cordless";
                case 0x0208: return "Smartphone";
                case 0x020C: return "Modem";
                default: return "Phone (0x" + Integer.toHexString(deviceClass) + ")";
            }
        }
        if (btClass.getMajorDeviceClass() == BluetoothClass.Device.Major.WEARABLE) {
            switch (deviceClass) {
                case 0x0704: return "Watch";
                case 0x0708: return "Glasses";
                default: return "Wearable (0x" + Integer.toHexString(deviceClass) + ")";
            }
        }
        return "0x" + Integer.toHexString(deviceClass);
    }

    static String uuidName(String uuid) {
        if (uuid.contains("1101")) return "→ Serial Port (SPP)";
        if (uuid.contains("1103")) return "→ Dial-Up Networking";
        if (uuid.contains("1105")) return "→ OBEX Object Push";
        if (uuid.contains("1106")) return "→ OBEX File Transfer";
        if (uuid.contains("1108")) return "→ Headset (HSP)";
        if (uuid.contains("110A")) return "→ Audio Source (A2DP)";
        if (uuid.contains("110B")) return "→ Audio Sink (A2DP)";
        if (uuid.contains("110C")) return "→ AVRCP Remote";
        if (uuid.contains("110E")) return "→ AVRCP Target";
        if (uuid.contains("1112")) return "→ Headset AG (HFP)";
        if (uuid.contains("1116")) return "→ NAP (PAN tether)";
        if (uuid.contains("111E")) return "→ Hands-Free (HFP)";
        if (uuid.contains("111F")) return "→ Hands-Free Audio";
        if (uuid.contains("1124")) return "→ HID Keyboard/Mouse";
        if (uuid.contains("1130")) return "→ MAP (SMS/MMS)";
        if (uuid.contains("1131")) return "→ MAP Client";
        if (uuid.contains("1132")) return "→ PBAP (Phone Book)";
        if (uuid.contains("1134")) return "→ PBAP Client";
        if (uuid.contains("1200")) return "→ PnP Information";
        if (uuid.contains("180A")) return "→ Device Info";
        if (uuid.contains("FE2C")) return "→ Google Fast Pair";
        if (uuid.contains("FD56")) return "→ AirSense CPAP";
        if (uuid.contains("0000") && uuid.length() > 8) return "→ Custom service";
        return "";
    }
}
