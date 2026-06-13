package com.obliterate.btspam;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.*;
import android.os.*;
import android.text.method.ScrollingMovementMethod;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * OBLITERATE Network Attacks — Probe Flood, mDNS, SSDP, Hotspot, LAN Scan, Net Enum.
 * All WiFi/network phone-native attacks in one page.
 */
public class NetworkActivity extends Activity {

    // ── WiFi ──────────────────────────────────
    private WifiManager wifiManager;
    private WifiManager.MulticastLock multicastLock;
    private volatile boolean isProbeFlood = false, isMdnsSpoof = false, isSsdpSpoof = false, isHoneypot = false;
    private volatile int probeFloodNetId = -1;

    // ── WiFi Direct ────────────────────────────
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiChannel;
    private volatile boolean isWifiSpam = false;
    private final ArrayList<WifiP2pDevice> wifiPeers = new ArrayList<>();

    // ── Net Enum ───────────────────────────────
    private volatile boolean stopNetScan = false;
    private volatile boolean stopNetbiosScan = false;
    private volatile boolean stopSmbScan = false;
    private volatile boolean stopPrinterScan = false;
    private volatile String capturedSubnetPrefix = null;

    // ── UI ─────────────────────────────────────
    private TextView statusText, logText;
    private EditText spoofNameInput, subnetInput;
    private Button btnProbeFlood, btnMdnsSpoof, btnSsdpSpoof, btnHoneypot, btnWifiSpam;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("OB:Multicast");
            multicastLock.setReferenceCounted(false);
        }
        initWifiDirect();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OB:NetAtk");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildUI();
        log("╔══════════════════════════════════╗");
        log("║  OBLITERATE NETWORK ATTACKS     ║");
        log("║  Probe · mDNS · SSDP · Hotspot  ║");
        log("║  LAN Scan · Net Enum · WiFi Dir ║");
        log("╚══════════════════════════════════╝");
        log("  Network attacks armed.");
    }

    @Override
    protected void onDestroy() {
        stopAll();
        mainHandler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(mWifiReceiver); } catch (Exception e) {}
        super.onDestroy();
    }

    // ═══════════════════════════════════════════
    //  WIFI DIRECT INIT
    // ═══════════════════════════════════════════

    private void initWifiDirect() {
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) return;
        wifiChannel = wifiP2pManager.initialize(this, getMainLooper(), null);
        IntentFilter f = new IntentFilter();
        f.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        registerReceiver(mWifiReceiver, f);
    }

    // ═══════════════════════════════════════════
    //  UI
    // ═══════════════════════════════════════════

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0A);
        root.setPadding(12, 48, 12, 20);

        root.addView(mkLabel("🌐  NETWORK ATTACKS", 0xFFFF2222, 22));
        root.addView(mkLabel("[ probe flood · mdns · ssdp · hotspot · lan · net enum ]", 0xFF888888, 9));

        statusText = mkLabel("⚫ READY", 0xFF00FF41, 13);
        statusText.setPadding(0, 10, 0, 4);
        root.addView(statusText);

        // Spoof name
        root.addView(mkLabel("SPOOF DEVICE NAME:", 0xFFFFCC00, 9));
        spoofNameInput = mkEdit("📱 iPhone 15 Pro");
        root.addView(spoofNameInput);

        // Row 1: Probe Flood + BT Turbo placeholder
        LinearLayout r1 = new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL);
        btnProbeFlood = mkBtnWide("📡 PROBE FLOOD");
        btnProbeFlood.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleProbeFlood(); }});
        r1.addView(btnProbeFlood);
        btnMdnsSpoof = mkBtnWide("🌐 mDNS SPOOF");
        btnMdnsSpoof.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleMdnsSpoof(); }});
        r1.addView(btnMdnsSpoof);
        root.addView(r1);

        // Row 2: SSDP + Hotspot
        LinearLayout r2 = new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL);
        btnSsdpSpoof = mkBtnWide("📺 SSDP SPOOF");
        btnSsdpSpoof.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleSsdpSpoof(); }});
        r2.addView(btnSsdpSpoof);
        btnHoneypot = mkBtnWide("📶 HONEYPOT");
        btnHoneypot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleHoneypot(); }});
        r2.addView(btnHoneypot);
        root.addView(r2);

        // Row 3: WiFi Direct Spam
        LinearLayout r3 = new LinearLayout(this); r3.setOrientation(LinearLayout.HORIZONTAL);
        btnWifiSpam = mkBtnWide("📡 WIFI DIRECT SPAM");
        btnWifiSpam.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleWifiSpam(); }});
        r3.addView(btnWifiSpam);
        root.addView(r3);

        // Row 4: LAN Scan + Net Enum
        root.addView(mkLabel("NETWORK RECON:", 0xFFFFCC00, 9));
        LinearLayout r4 = new LinearLayout(this); r4.setOrientation(LinearLayout.HORIZONTAL);
        r4.addView(mkActionBtn("🖧 LAN SCAN", new Runnable() { public void run() { scanLan(); }}));
        r4.addView(mkActionBtn("💻 NETBIOS", new Runnable() { public void run() { scanNetbios(); }}));
        r4.addView(mkActionBtn("📁 SMB", new Runnable() { public void run() { scanSmb(); }}));
        root.addView(r4);

        // Row 5: Printers + Scan All
        LinearLayout r5 = new LinearLayout(this); r5.setOrientation(LinearLayout.HORIZONTAL);
        r5.addView(mkActionBtn("🖨 PRINTERS", new Runnable() { public void run() { scanPrinters(); }}));
        r5.addView(mkActionBtn("🔍 SCAN ALL", new Runnable() { public void run() { scanAll(); }}));
        root.addView(r5);

        // Subnet input
        subnetInput = mkEdit("192.168.1");
        subnetInput.setHint("Subnet prefix");
        subnetInput.setTextSize(11);
        root.addView(subnetInput);

        // Row 6: Stop All + Back
        LinearLayout r6 = new LinearLayout(this); r6.setOrientation(LinearLayout.HORIZONTAL);
        Button btnStop = mkBtnWide("🛑 STOP ALL");
        btnStop.setBackgroundColor(0xFF8B0000);
        btnStop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { stopAll(); }});
        r6.addView(btnStop);
        Button btnBack = mkBtnWide("← BACK");
        btnBack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); }});
        r6.addView(btnBack);
        root.addView(r6);

        // Log
        root.addView(mkLabel("TERMINAL:", 0xFF888888, 9));
        logText = new TextView(this);
        logText.setTextColor(0xFF00FF41); logText.setTextSize(10); logText.setTypeface(Typeface.MONOSPACE);
        logText.setBackgroundColor(0xFF0D0D0D); logText.setPadding(6, 6, 6, 6);
        logText.setMovementMethod(new ScrollingMovementMethod());
        logText.setHorizontallyScrolling(true); logText.setTextIsSelectable(true);
        logText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180)));
        root.addView(logText);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0A0A0A); sv.addView(root); sv.setFillViewport(true);
        setContentView(sv);
    }

    private Button mkActionBtn(String label, final Runnable action) {
        Button b = mkBtnWide(label);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            action.run();
        }});
        return b;
    }

    private void toggleProbeFlood() {
        if (isProbeFlood) {
            isProbeFlood = false;
            resetBtn(btnProbeFlood, "📡 PROBE FLOOD");
            if (probeFloodNetId >= 0) {
                try { wifiManager.removeNetwork(probeFloodNetId); probeFloodNetId = -1; } catch (Exception e) {}
            }
            log("🛑 Probe cycle stopped");
            updateStatus("READY");
            releaseLock();
            return;
        }
        if (wifiManager == null) { log("✕ No WiFi manager"); return; }
        isProbeFlood = true;
        setBtnOn(btnProbeFlood, "📡 PROBE ON");
        log("⚡ WiFi probe cycle started — Android may throttle or block configured-network probes");
        updateStatus("PROBE FLOOD");
        acquireLock();
        new Thread(new ProbeFloodRunner()).start();
    }

    private void toggleMdnsSpoof() {
        if (isMdnsSpoof) {
            isMdnsSpoof = false;
            resetBtn(btnMdnsSpoof, "🌐 mDNS SPOOF");
            log("🛑 mDNS spoofer stopped");
            updateStatus("READY");
            releaseLock();
            return;
        }
        if (!wifiConnected()) { log("✕ Connect to WiFi first"); return; }
        isMdnsSpoof = true;
        setBtnOn(btnMdnsSpoof, "🌐 mDNS ON");
        log("🌐 mDNS sender started — multicast announcements will be attempted on this LAN");
        updateStatus("mDNS SPOOF");
        acquireLock();
        new Thread(new MdnsSpooferRunner()).start();
    }

    private void toggleSsdpSpoof() {
        if (isSsdpSpoof) {
            isSsdpSpoof = false;
            resetBtn(btnSsdpSpoof, "📺 SSDP SPOOF");
            log("🛑 SSDP spoofer stopped");
            updateStatus("READY");
            releaseLock();
            return;
        }
        if (!wifiConnected()) { log("✕ Connect to WiFi first"); return; }
        isSsdpSpoof = true;
        setBtnOn(btnSsdpSpoof, "📺 SSDP ON");
        log("📺 SSDP sender started — multicast NOTIFY packets will be attempted on this LAN");
        updateStatus("SSDP SPOOF");
        acquireLock();
        new Thread(new SsdpSpooferRunner()).start();
    }

    private void toggleHoneypot() {
        if (isHoneypot) {
            isHoneypot = false;
            resetBtn(btnHoneypot, "📶 HONEYPOT");
            log("🛑 Hotspot stopped");
            updateStatus("READY");
            releaseLock();
            return;
        }
        if (wifiManager == null) { log("✕ No WiFi manager"); return; }
        isHoneypot = true;
        setBtnOn(btnHoneypot, "📶 HOTSPOT ON");
        log("📶 Hotspot monitor started — AP control depends on Android/OEM support");
        updateStatus("HONEYPOT");
        acquireLock();
        new Thread(new HotspotHoneypotRunner()).start();
    }

    private void toggleWifiSpam() {
        if (isWifiSpam) {
            isWifiSpam = false;
            resetBtn(btnWifiSpam, "📡 WIFI DIRECT SPAM");
            if (wifiP2pManager != null && wifiChannel != null) {
                try { wifiP2pManager.stopPeerDiscovery(wifiChannel, null); } catch (Exception e) {}
            }
            log("🛑 WiFi Direct spam stopped");
            updateStatus("READY");
            releaseLock();
            return;
        }
        if (wifiP2pManager == null || wifiChannel == null) { log("✕ No WiFi Direct"); return; }
        isWifiSpam = true;
        setBtnOn(btnWifiSpam, "📡 WIFI ON");
        log("⚡ WiFi Direct cycle started — peer discovery/invites depend on nearby devices");
        updateStatus("WIFI DIRECT");
        acquireLock();
        startWifiSpam();
    }

    // ═══════════════════════════════════════════
    //  WIFI PROBE FLOOD
    // ═══════════════════════════════════════════

    private class ProbeFloodRunner implements Runnable {
        public void run() {
            log("  [Probe] runner start, isProbeFlood=" + isProbeFlood);
            final String[] trollSSIDs = {
                "FBI_Surveillance_Van", "NSA_Listening_Post_7", "CIA_Field_Office",
                "Police_Drone_42", "Homeland_Security_Drone", "Secret_Service_Detail",
                "Interpol_Mobile_Unit", "DEA_Monitoring", "Military_Police_Air",
                "Apple_Internal_Test", "Google_Data_Collection", "Microsoft_Security_Audit",
                "Amazon_Delivery_Drone", "Meta_Ad_Tracker", "Tesla_Telemetry",
                "Your_Location_Tracked", "Microphone_Active", "Camera_Remote_Access",
                "This_Device_Infected", "You_Are_Watched", "Security_Breach",
                "FBI_Van_Outside", "Police_Stakeout", "Drone_Overhead",
                "Free_WiFi_No_Pass", "HACKED_NETWORK", "STARBUCKS_SECURE",
                "iPhone_15_Pro_Max", "Samsung_S25_Ultra", "Pixel_10_Pro",
                "Mom_iPhone", "Dad_Galaxy", "Karen_AirPods",
            };
            int idx = 0; int count = 0;
            boolean loggedProbeError = false;
            while (isProbeFlood) {
                final String ssid = trollSSIDs[idx % trollSSIDs.length] + "_" + (idx % 999); idx++;
                try {
                    if (probeFloodNetId >= 0) { wifiManager.removeNetwork(probeFloodNetId); probeFloodNetId = -1; }
                    WifiConfiguration wc = new WifiConfiguration();
                    wc.SSID = "\"" + ssid + "\"";
                    wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    probeFloodNetId = wifiManager.addNetwork(wc);
                    if (probeFloodNetId >= 0) { wifiManager.enableNetwork(probeFloodNetId, false); wifiManager.saveConfiguration(); }
                } catch (Exception e) {
                    if (!loggedProbeError) {
                        loggedProbeError = true;
                        final String err = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                        mainHandler.post(new Runnable() { public void run() { log("✕ Probe cycle error: " + err); }});
                    }
                }
                try { wifiManager.startScan(); } catch (Exception e) {}
                count++;
                if (count % 10 == 0) {
                    final int c = count; final String s = ssid;
                    mainHandler.post(new Runnable() { public void run() {
                        log("  📡 Probe #" + c + ": \"" + s + "\"");
                        updateStatus("PROBE: " + s.substring(0, Math.min(14, s.length())));
                    }});
                }
                try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            }
            if (probeFloodNetId >= 0) { try { wifiManager.removeNetwork(probeFloodNetId); probeFloodNetId = -1; } catch (Exception e) {} }
            final int sent = count;
            mainHandler.post(new Runnable() { public void run() { log("📡 Probe cycle stopped — " + sent + " scan requests"); updateStatus("READY"); }});
            releaseLock();
        }
    }

    // ═══════════════════════════════════════════
    //  mDNS SPOOFER
    // ═══════════════════════════════════════════

    private class MdnsSpooferRunner implements Runnable {
        public void run() {
            log("  [mDNS] runner start, isMdnsSpoof=" + isMdnsSpoof);
            if (multicastLock != null) try { multicastLock.acquire(); } catch (Exception e) {}
            final String[][] devs = {
                {"FBI-Surveillance-Van-3.local","192.168.1.50","_airplay._tcp.local:_raop._tcp.local"},
                {"NSA-Listening-Post-7.local","192.168.1.51","_printer._tcp.local:_http._tcp.local:_scanner._tcp.local"},
                {"Samsung-Fridge-5G.local","192.168.1.52","_googlecast._tcp.local:_spotify-connect._tcp.local"},
                {"NOT-A-BOMB.local","192.168.1.53","_airport._tcp.local:_tftp._tcp.local"},
                {"Karens-CCTV-CAM-4.local","192.168.1.54","_rtsp._tcp.local:_http._tcp.local"},
                {"You-Are-Watched.local","192.168.1.55","_airplay._tcp.local:_raop._tcp.local:_googlecast._tcp.local"},
                {"Skynet-Dev-Node.local","192.168.1.56","_ssh._tcp.local:_telnet._tcp.local:_ftp._tcp.local"},
                {"HR-Disciplinary.local","192.168.1.57","_smb._tcp.local:_afpovertcp._tcp.local"},
                {"FREE-OnlyFans-WiFi.local","192.168.1.58","_airplay._tcp.local:_googlecast._tcp.local"},
                {"Mom-Vibrator-Pro.local","192.168.1.59","_airplay._tcp.local:_spotify-connect._tcp.local"},
            };
            Random rng = new Random(); MulticastSocket sock = null; int burst = 0;
            try {
                sock = new MulticastSocket(); sock.setTimeToLive(255);
                InetAddress group = InetAddress.getByName("224.0.0.251");
                String lip = getLocalIpStr();
                while (isMdnsSpoof) { burst++;
                    int pick = 2 + rng.nextInt(3);
                    ArrayList<Integer> idxs = new ArrayList<>();
                    while (idxs.size() < pick) { int r = rng.nextInt(devs.length); if (!idxs.contains(r)) idxs.add(r); }
                    for (int idx : idxs) {
                        String[] dev = devs[idx];
                        byte[] packet = buildMdnsPacket(dev[0], dev[1], dev[2].split(":"), rng);
                        DatagramPacket dp = new DatagramPacket(packet, packet.length, group, 5353);
                        for (int s = 0; s < 3; s++) { sock.send(dp); Thread.sleep(80); }
                        final String name = dev[0]; final int b = burst;
                        mainHandler.post(new Runnable() { public void run() { log("  🌐 [" + b + "] " + name); }});
                    }
                    final int b = burst;
                    mainHandler.post(new Runnable() { public void run() { updateStatus("mDNS #" + b); }});
                    Thread.sleep(10000);
                }
            } catch (Exception e) {
                final String err = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                mainHandler.post(new Runnable() { public void run() { log("✕ mDNS: " + err); }});
            }
            finally { if (sock != null) sock.close(); }
            if (multicastLock != null) try { multicastLock.release(); } catch (Exception e) {}
            final int b = burst;
            mainHandler.post(new Runnable() { public void run() { log("🌐 mDNS stopped — " + b + " bursts"); updateStatus("READY"); }});
            releaseLock();
        }
    }

    // ═══════════════════════════════════════════
    //  SSDP SPOOFER
    // ═══════════════════════════════════════════

    private class SsdpSpooferRunner implements Runnable {
        public void run() {
            log("  [SSDP] runner start, isSsdpSpoof=" + isSsdpSpoof);
            if (multicastLock != null) try { multicastLock.acquire(); } catch (Exception e) {}
            final String[][] devs = {
                {"uuid:FBI-Surveillance-Van-3","upnp:rootdevice","Linux/2.6 UPnP/1.0 FBI-SVR/1.0","http://192.168.1.100:5000/root.xml","FBI Surveillance Van"},
                {"uuid:Samsung-Fridge-5G","urn:schemas-upnp-org:device:InternetGatewayDevice:1","Samsung/1.0 UPnP/1.0","http://192.168.1.100:8080/desc.xml","Samsung Fridge 5G"},
                {"uuid:NSA-Media-Server","urn:schemas-upnp-org:device:MediaRenderer:1","NSA-PR/1.0 UPnP/1.0","http://192.168.1.100:9000/device.xml","NSA Media Server"},
                {"uuid:HR-Disciplinary","urn:schemas-upnp-org:device:Basic:1","Windows-NT/10.0 UPnP/1.0","http://192.168.1.100:5357/root.xml","HR Disciplinary Files"},
                {"uuid:Karens-CCTV-4","urn:schemas-upnp-org:device:DigitalSecurityCamera:1","Hikvision/5.5 UPnP/1.0","http://192.168.1.100:554/desc.xml","Karen CCTV #4"},
                {"uuid:INFECTED-Printer","urn:schemas-upnp-org:device:Printer:1","HP-LaserJet/1.0 UPnP/1.0","http://192.168.1.100:631/root.xml","INFECTED Printer"},
                {"uuid:OnlyFans-Router","urn:schemas-upnp-org:device:WANConnectionDevice:1","Router/1.0 UPnP/1.0","http://192.168.1.100:80/device.xml","OnlyFans WiFi Router"},
                {"uuid:Skynet-Dev-Node","urn:schemas-upnp-org:device:MediaServer:1","Skynet/3.0 UPnP/1.0","http://192.168.1.100:8200/root.xml","Skynet Dev Node"},
                {"uuid:Mom-Vibrator-Pro","urn:schemas-upnp-org:device:BinaryLight:1","Lovense/2.0 UPnP/1.0","http://192.168.1.100:9999/device.xml","Mom Vibrator Pro"},
                {"uuid:You-Are-Watched","urn:schemas-upnp-org:device:MediaRenderer:1","Panopticon/1.0 UPnP/1.0","http://192.168.1.100:9090/root.xml","You Are Watched"},
            };
            Random rng = new Random(); MulticastSocket sock = null; int burst = 0;
            try {
                sock = new MulticastSocket(); sock.setTimeToLive(4);
                InetAddress group = InetAddress.getByName("239.255.255.250");
                String lip = getLocalIpStr();
                while (isSsdpSpoof) { burst++;
                    int pick = 3 + rng.nextInt(3);
                    ArrayList<Integer> idxs = new ArrayList<>();
                    while (idxs.size() < pick) { int r = rng.nextInt(devs.length); if (!idxs.contains(r)) idxs.add(r); }
                    for (int idx : idxs) {
                        String[] dev = devs[idx];
                        String msg = ObNet.buildSsdpNotify(dev[0], dev[1], dev[2], dev[3].replace("192.168.1.100", lip));
                        byte[] data = msg.getBytes();
                        DatagramPacket dp = new DatagramPacket(data, data.length, group, 1900);
                        for (int s = 0; s < 2; s++) { sock.send(dp); Thread.sleep(80); }
                        final String name = dev[4]; final int b = burst;
                        mainHandler.post(new Runnable() { public void run() { log("  📺 [" + b + "] " + name); }});
                    }
                    final int b = burst;
                    mainHandler.post(new Runnable() { public void run() { updateStatus("SSDP #" + b); }});
                    Thread.sleep(30000);
                }
                for (String[] dev : devs) {
                    String bye = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: " + dev[1] + "\r\nNTS: ssdp:byebye\r\nUSN: " + dev[0] + "\r\n\r\n";
                    sock.send(new DatagramPacket(bye.getBytes(), bye.length(), group, 1900));
                }
            } catch (Exception e) {
                final String err = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                mainHandler.post(new Runnable() { public void run() { log("✕ SSDP: " + err); }});
            }
            finally { if (sock != null) sock.close(); }
            if (multicastLock != null) try { multicastLock.release(); } catch (Exception e) {}
            final int b = burst;
            mainHandler.post(new Runnable() { public void run() { log("📺 SSDP stopped — " + b + " bursts"); updateStatus("READY"); }});
            releaseLock();
        }
    }

    // ═══════════════════════════════════════════
    //  WIFI HONEYPOT
    // ═══════════════════════════════════════════

    private class HotspotHoneypotRunner implements Runnable {
        public void run() {
            try {
                java.lang.reflect.Method m = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
                WifiConfiguration cfg = new WifiConfiguration();
                cfg.SSID = "\"Free_WiFi_No_Pass\"";
                cfg.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                m.invoke(wifiManager, cfg, true);
                mainHandler.post(new Runnable() { public void run() {
                    log("📶 Hotspot ON: \"Free_WiFi_No_Pass\"");
                    log("  Monitoring for curious devices...");
                }});
                int prev = 0;
                while (isHoneypot) {
                    Thread.sleep(5000);
                    BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
                    br.readLine();
                    String line; final ArrayList<String> clients = new ArrayList<>();
                    while ((line = br.readLine()) != null) {
                        String[] p = line.trim().split("\\s+");
                        if (p.length >= 4) {
                            String mac = p[3];
                            if (!mac.equals("00:00:00:00:00:00") && mac.contains(":")) {
                                String v = getMacVendor(mac);
                                clients.add(p[0] + " " + mac + (v != null ? " [" + v + "]" : ""));
                            }
                        }
                    }
                    br.close();
                    if (clients.size() != prev) { prev = clients.size();
                        final int c = clients.size();
                        mainHandler.post(new Runnable() { public void run() {
                            log("  📶 Clients: " + c);
                            for (String cl : clients) log("    " + cl);
                            updateStatus("HONEYPOT: " + c + " clients");
                        }});
                    }
                }
                m.invoke(wifiManager, cfg, false);
            } catch (Exception e) {
                isHoneypot = false;
                final String msg = e.getMessage();
                mainHandler.post(new Runnable() { public void run() {
                    log("✕ Hotspot: " + (msg != null ? msg : "API blocked"));
                    log("  Honeypot requires system-app privileges (setWifiApEnabled).");
                    log("  Won't work on stock Android without root/system signature.");
                    if (btnHoneypot != null) resetBtn(btnHoneypot, "📶 HONEYPOT");
                    updateStatus("READY");
                }});
            }
            releaseLock();
        }
    }

    // ═══════════════════════════════════════════
    //  WIFI DIRECT SPAM
    // ═══════════════════════════════════════════

    private void startWifiSpam() {
        if (wifiP2pManager == null || wifiChannel == null) { log("✕ No WiFi Direct"); isWifiSpam = false; return; }
        try {
            wifiP2pManager.discoverPeers(wifiChannel, new WifiP2pManager.ActionListener() {
                public void onSuccess() { log("  ✓ WiFi Direct discovery started"); }
                public void onFailure(int code) { log("  ⚠ WiFi discovery failed: " + code); }
            });
        } catch (Exception e) { log("  ✕ WiFi: " + e.getMessage()); }
        if (isWifiSpam) {
            mainHandler.postDelayed(new Runnable() { public void run() { if (isWifiSpam) startWifiSpam(); }}, 10000);
        }
    }

    private BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(a) && isWifiSpam && wifiP2pManager != null && wifiChannel != null) {
                try {
                    wifiP2pManager.requestPeers(wifiChannel, new WifiP2pManager.PeerListListener() {
                        public void onPeersAvailable(WifiP2pDeviceList peers) {
                            wifiPeers.clear(); wifiPeers.addAll(peers.getDeviceList());
                            log("  📡 WiFi peers: " + wifiPeers.size());
                            for (WifiP2pDevice p : wifiPeers) {
                                log("     " + (p.deviceName != null ? p.deviceName : p.deviceAddress));
                            }
                            sendWifiInvites();
                        }
                    });
                } catch (Exception e) {}
            }
        }
    };

    private void sendWifiInvites() {
        if (!isWifiSpam || wifiPeers.isEmpty()) return;
        final String spoofName = spoofNameInput != null ? spoofNameInput.getText().toString() : "";
        for (final WifiP2pDevice peer : wifiPeers) {
            if (!isWifiSpam) break;
            final String peerName = peer.deviceName != null ? peer.deviceName : peer.deviceAddress;
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = peer.deviceAddress;
            try {
                java.lang.reflect.Field f = config.getClass().getField("groupOwnerIntent");
                f.setInt(config, 0);
            } catch (Exception e) {}
            try {
                wifiP2pManager.connect(wifiChannel, config, new WifiP2pManager.ActionListener() {
                    public void onSuccess() {
                        log("  📩 Invite sent → " + peerName + " (showing as: \"" + spoofName + "\")");
                    }
                    public void onFailure(int code) {
                        log("  ✕ Invite failed → " + peerName + " (code: " + code + ")");
                    }
                });
            } catch (Exception e) {
                log("  ✕ WiFi connect error: " + e.getMessage());
            }
        }
    }

    // ═══════════════════════════════════════════
    //  NETWORK ENUMERATION
    // ═══════════════════════════════════════════

    private void scanLan() {
        log("🖧 Scanning LAN via ARP..."); updateStatus("LAN SCAN");
        new Thread(new Runnable() { public void run() {
            try {
                BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
                String line; int count = 0; br.readLine();
                final StringBuilder sb = new StringBuilder("🖧 LAN DEVICES:\n");
                while ((line = br.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length >= 4) {
                        String mac = p[3];
                        if (!mac.equals("00:00:00:00:00:00") && mac.contains(":")) {
                            count++;
                            String v = getMacVendor(mac);
                            sb.append("  ").append(p[0]).append("  ").append(mac);
                            if (v != null) sb.append("  [").append(v).append("]");
                            sb.append("\n");
                        }
                    }
                }
                br.close();
                sb.append("  Total: ").append(count).append(" devices\n");
                final String result = sb.toString();
                mainHandler.post(new Runnable() { public void run() { log(result); updateStatus("READY"); }});
            } catch (Exception e) { mainHandler.post(new Runnable() { public void run() { log("✕ " + e.getMessage()); updateStatus("READY"); }}); }
        }}).start();
    }

    private void scanNetbios() {
        log("🔍 NetBIOS scan..."); updateStatus("NETBIOS");
        capturedSubnetPrefix = captureSubnetPrefixForScan();
        final String prefix = capturedSubnetPrefix;
        new Thread(new Runnable() { public void run() {
            stopNetbiosScan = false; stopNetScan = false;
            for (int i = 1; i <= 254 && !stopNetbiosScan && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                DatagramSocket ds = null;
                try {
                    ds = new DatagramSocket(); ds.setSoTimeout(400);
                    byte[] nb = new byte[50];
                    nb[0]=(byte)0x82; nb[1]=0x28; nb[5]=1; nb[45]=0x20; nb[46]=0x43; nb[47]=0x4B;
                    DatagramPacket dp = new DatagramPacket(nb, 50, InetAddress.getByName(ip), 137);
                    ds.send(dp); ds.receive(dp);
                    if(dp.getLength()>0){
                        final String d = new String(dp.getData(),0,dp.getLength(),"ASCII").replaceAll("[^\\x20-\\x7E]",".");
                        mainHandler.post(new Runnable(){public void run(){log("  💻 "+ip+" "+d.substring(0,Math.min(40,d.length())));}});
                    }
                } catch (Exception e) {}
                finally { if (ds != null) ds.close(); }
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ NetBIOS done"); updateStatus("READY");}});
        }}).start();
    }

    private void scanSmb() {
        log("📁 SMB scan..."); updateStatus("SMB");
        capturedSubnetPrefix = captureSubnetPrefixForScan();
        final String prefix = capturedSubnetPrefix;
        new Thread(new Runnable() { public void run() {
            stopSmbScan = false; stopNetScan = false;
            for (int i = 1; i <= 254 && !stopSmbScan && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                Socket s = null;
                try {
                    s = new Socket(); s.connect(new InetSocketAddress(ip,445),600);
                    byte[] smb={0x00,0x00,0x00,(byte)0xA4,(byte)0xFF,0x53,0x4D,0x42,0x72,0x00,0x00,0x00,0x00,0x18,0x53,(byte)0xC8,0x00,0x26,(byte)0xFE,(byte)0xFE,(byte)0xFE,(byte)0xFE,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
                    OutputStream os = s.getOutputStream();
                    os.write(smb); os.flush();
                    byte[] resp = new byte[512]; int len = s.getInputStream().read(resp);
                    if(len>0){ String d=""; for(int j=40;j<len;j++){if(resp[j]>=32&&resp[j]<127)d+=(char)resp[j];else if(resp[j]==0)d+=" ";}
                        final String r = "  📁 "+ip+" "+d.trim();
                        mainHandler.post(new Runnable(){public void run(){log(r);}});
                    }
                } catch (Exception e) {}
                finally { if (s != null) try { s.close(); } catch (Exception ex) {} }
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ SMB done"); updateStatus("READY");}});
        }}).start();
    }

    private void scanPrinters() {
        log("🖨 Printer scan..."); updateStatus("PRINTERS");
        capturedSubnetPrefix = captureSubnetPrefixForScan();
        final String prefix = capturedSubnetPrefix;
        new Thread(new Runnable() { public void run() {
            stopPrinterScan = false; stopNetScan = false;
            int[] ports={9100,515,631,80,443,8080};
            for (int i = 1; i <= 254 && !stopPrinterScan && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                for (int p = 0; p < ports.length; p++) {
                    Socket s = null;
                    try {
                        s = new Socket(); s.connect(new InetSocketAddress(ip,ports[p]),500);
                        final String r = "  🖨 "+ip+":"+ports[p];
                        mainHandler.post(new Runnable(){public void run(){log(r);}});
                    } catch (Exception e) {}
                    finally { if (s != null) try { s.close(); } catch (Exception ex) {} }
                }
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ Printers done"); updateStatus("READY");}});
        }}).start();
    }

    private void scanAll() {
        log("🔍 Full network enum..."); stopNetScan = false; stopNetbiosScan = false; stopSmbScan = false; stopPrinterScan = false;
        capturedSubnetPrefix = captureSubnetPrefixForScan();
        scanNetbios();
        new Thread(new Runnable(){public void run(){
            try{Thread.sleep(3000);}catch(Exception e){}
            if (!stopNetScan && !stopSmbScan) scanSmb();
            try{Thread.sleep(3000);}catch(Exception e){}
            if (!stopNetScan && !stopPrinterScan) scanPrinters();
        }}).start();
    }

    private String getSubnetPrefix() {
        if (capturedSubnetPrefix != null) return capturedSubnetPrefix;
        return readSubnetPrefixFromUi();
    }

    private String captureSubnetPrefixForScan() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            capturedSubnetPrefix = readSubnetPrefixFromUi();
        } else if (capturedSubnetPrefix == null) {
            capturedSubnetPrefix = getLocalSubnetPrefix();
        }
        return capturedSubnetPrefix;
    }

    private String readSubnetPrefixFromUi() {
        try { if (subnetInput != null && subnetInput.getText().length() > 0 && subnetInput.getText().toString().matches("\\d+\\.\\d+\\.\\d+")) return subnetInput.getText().toString().trim(); } catch (Exception e) {}
        return getLocalSubnetPrefix();
    }

    private String getLocalSubnetPrefix() {
        return ObNet.subnetPrefix(wifiManager);
    }

    // ═══════════════════════════════════════════
    //  mDNS PACKET BUILDER
    // ═══════════════════════════════════════════

    private byte[] buildMdnsPacket(String host, String ip, String[] svcs, Random rng) {
        return ObNet.buildMdnsPacket(host, ip, svcs, rng);
    }

    // ═══════════════════════════════════════════
    //  STOP ALL
    // ═══════════════════════════════════════════

    private void stopAll() {
        isProbeFlood = false; isMdnsSpoof = false; isSsdpSpoof = false; isHoneypot = false; isWifiSpam = false;
        stopNetScan = true; stopNetbiosScan = true; stopSmbScan = true; stopPrinterScan = true;
        if (wifiP2pManager != null && wifiChannel != null) try { wifiP2pManager.stopPeerDiscovery(wifiChannel, null); } catch (Exception e) {}
        if (probeFloodNetId >= 0) { try { wifiManager.removeNetwork(probeFloodNetId); probeFloodNetId = -1; } catch (Exception e) {} }
        if (multicastLock != null) try { multicastLock.release(); } catch (Exception e) {}
        if (btnProbeFlood != null) resetBtn(btnProbeFlood, "📡 PROBE FLOOD");
        if (btnMdnsSpoof != null) resetBtn(btnMdnsSpoof, "🌐 mDNS SPOOF");
        if (btnSsdpSpoof != null) resetBtn(btnSsdpSpoof, "📺 SSDP SPOOF");
        if (btnHoneypot != null) resetBtn(btnHoneypot, "📶 HONEYPOT");
        if (btnWifiSpam != null) resetBtn(btnWifiSpam, "📡 WIFI DIRECT SPAM");
        releaseLock();
        updateStatus("READY");
        log("🛑 ALL STOPPED");
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private boolean wifiConnected() {
        return ObNet.wifiConnected(wifiManager);
    }

    private String getLocalIpStr() {
        return ObNet.localIp(wifiManager);
    }

    private String getMacVendor(String mac) {
        return ObNet.macVendor(mac);
    }

    private void updateStatus(String s) {
        mainHandler.post(new Runnable() { public void run() { statusText.setText("⚡ " + s); }});
    }

    private void log(final String m) {
        mainHandler.post(new Runnable() { public void run() {
            if (logText != null) {
                logText.append(m + "\n");
                int scroll = logText.getLayout() != null
                    ? logText.getLayout().getLineTop(logText.getLineCount()) - logText.getHeight() : 0;
                if (scroll > 0) logText.scrollTo(0, scroll);
            }
        }});
    }

    private void acquireLock() {
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
    }

    private void releaseLock() {
        if (!isProbeFlood && !isMdnsSpoof && !isSsdpSpoof && !isHoneypot && !isWifiSpam) {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void setBtnOn(Button b, String t) { ObUi.setButtonOn(b, t); }
    private void resetBtn(Button b, String t) { ObUi.resetButton(b, t); }

    private int dp(int px) { return ObUi.dp(this, px); }

    private TextView mkLabel(String t, int c, int s) {
        return ObUi.tightLabel(this, t, c, s);
    }

    private EditText mkEdit(String t) {
        return ObUi.edit(this, t);
    }

    private Button mkBtnWide(String t) {
        return ObUi.weightedWideButton(this, t);
    }
}
