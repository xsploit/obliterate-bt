package com.obliterate.btspam;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.util.Random;

final class ObNet {
    private ObNet() {}

    static boolean wifiConnected(WifiManager wifiManager) {
        try {
            WifiInfo info = wifiManager != null ? wifiManager.getConnectionInfo() : null;
            return info != null && info.getNetworkId() != -1;
        } catch (Exception e) {
            return false;
        }
    }

    static String localIp(WifiManager wifiManager) {
        try {
            int ip = wifiManager.getConnectionInfo().getIpAddress();
            return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
        } catch (Exception e) {
            return "192.168.1.80";
        }
    }

    static String subnetPrefix(WifiManager wifiManager) {
        String ip = localIp(wifiManager);
        int dot = ip.lastIndexOf('.');
        return dot > 0 ? ip.substring(0, dot) : "192.168.1";
    }

    static String macVendor(String mac) {
        if (mac == null) return null;
        String normalized = mac.toUpperCase();
        if (normalized.startsWith("00:1A") || normalized.startsWith("00:25") || normalized.startsWith("F0:DB")) return "Apple";
        if (normalized.startsWith("F4:0F") || normalized.startsWith("D0:57") || normalized.startsWith("E0:98") || normalized.startsWith("C4:65")) return "Samsung";
        if (normalized.startsWith("08:00") || normalized.startsWith("00:E0")) return "Intel";
        if (normalized.startsWith("F0:18") || normalized.startsWith("18:FE") || normalized.startsWith("24:0A") || normalized.startsWith("FC:F5")) return "Espressif";
        if (normalized.startsWith("3C:5A") || normalized.startsWith("48:4F") || normalized.startsWith("70:B3") || normalized.startsWith("E4:5F")) return "Google";
        if (normalized.startsWith("B8:27")) return "Raspberry Pi";
        if (normalized.startsWith("28:6C") || normalized.startsWith("C8:3A")) return "TP-Link";
        if (normalized.startsWith("04:92") || normalized.startsWith("08:BE")) return "Xiaomi";
        if (normalized.startsWith("0C:2E") || normalized.startsWith("2C:F0")) return "Huawei";
        if (normalized.startsWith("D8:1B") || normalized.startsWith("8C:BB")) return "Sony";
        if (normalized.startsWith("B4:8A") || normalized.startsWith("10:D0")) return "LG";
        if (normalized.startsWith("7C:DB") || normalized.startsWith("00:17")) return "Amazon";
        if (normalized.startsWith("AC:37")) return "Roku";
        if (normalized.startsWith("34:29")) return "Ring";
        if (normalized.startsWith("40:5B")) return "Nest";
        if (normalized.startsWith("00:96")) return "Wyze";
        return null;
    }

    static String resolveHostname(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String hostname = addr.getCanonicalHostName();
            if (!hostname.equals(ip)) return "(" + hostname + ")";
        } catch (Exception e) {}
        return null;
    }

    static byte[] buildMdnsPacket(String hostname, String ipAddr, String[] services, Random rng) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            writeU16(baos, rng.nextInt(0xFFFF));
            writeU16(baos, 0x8400);
            writeU16(baos, 0);
            writeU16(baos, 1 + services.length);
            writeU16(baos, 0);
            writeU16(baos, 0);

            byte[] hostEnc = encodeDnsName(hostname);
            baos.write(hostEnc, 0, hostEnc.length);
            writeU16(baos, 1);
            writeU16(baos, 1);
            writeU32(baos, 120);
            writeU16(baos, 4);
            for (String p : ipAddr.split("\\.")) baos.write(Integer.parseInt(p));

            for (String service : services) {
                byte[] serviceEnc = encodeDnsName(service);
                baos.write(serviceEnc, 0, serviceEnc.length);
                writeU16(baos, 12);
                writeU16(baos, 1);
                writeU32(baos, 120);
                byte[] targetEnc = encodeDnsName(hostname);
                writeU16(baos, targetEnc.length);
                baos.write(targetEnc, 0, targetEnc.length);
            }
        } catch (Exception e) {}
        return baos.toByteArray();
    }

    static String buildSsdpNotify(String usn, String nt, String server, String location) {
        Random rng = new Random();
        return "NOTIFY * HTTP/1.1\r\n"
            + "HOST: 239.255.255.250:1900\r\n"
            + "CACHE-CONTROL: max-age=1800\r\n"
            + "LOCATION: " + location + "\r\n"
            + "NT: " + nt + "\r\n"
            + "NTS: ssdp:alive\r\n"
            + "SERVER: " + server + "\r\n"
            + "USN: " + usn + "\r\n"
            + "BOOTID.UPNP.ORG: " + rng.nextInt(9999) + "\r\n"
            + "CONFIGID.UPNP.ORG: " + rng.nextInt(99999) + "\r\n"
            + "\r\n";
    }

    private static byte[] encodeDnsName(String name) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            for (String part : name.split("\\.")) {
                byte[] bytes = part.getBytes();
                baos.write(bytes.length);
                baos.write(bytes, 0, bytes.length);
            }
            baos.write(0);
        } catch (Exception e) {}
        return baos.toByteArray();
    }

    private static void writeU16(ByteArrayOutputStream baos, int value) {
        baos.write((value >> 8) & 0xFF);
        baos.write(value & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream baos, long value) {
        baos.write((int)((value >> 24) & 0xFF));
        baos.write((int)((value >> 16) & 0xFF));
        baos.write((int)((value >> 8) & 0xFF));
        baos.write((int)(value & 0xFF));
    }
}
