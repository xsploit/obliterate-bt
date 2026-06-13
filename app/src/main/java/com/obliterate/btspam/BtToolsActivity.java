package com.obliterate.btspam;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.text.method.ScrollingMovementMethod;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;

/**
 * OBLITERATE BT Tools — Scanner, Inspector, Distance Estimator, RFCOMM Scanner.
 * All Bluetooth recon + tools in one page.
 */
public class BtToolsActivity extends Activity {

    // ── Bluetooth ──────────────────────────────
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner leScanner;
    private BleScanCb activeBleScanCb;
    private final ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private ArrayAdapter<String> deviceListAdapter;
    private final Map<String, Short> deviceRssiMap = Collections.synchronizedMap(new HashMap<String, Short>());

    // ── Distance Estimator ─────────────────────
    private BluetoothDevice trackTarget = null;
    private volatile boolean isTracking = false;
    private final ArrayList<Integer> rssiHistory = new ArrayList<>();
    private int rssiMin = 0, rssiMax = -100, rssiAvg = 0;

    // ── RFCOMM Scanner ─────────────────────────
    private volatile boolean isRfcommScanning = false;

    // ── BT Name Turbo ──────────────────────────
    private volatile boolean isBtNameTurbo = false;

    // ── UI ─────────────────────────────────────
    private TextView statusText, rssiText, logText;
    private ListView deviceListView;
    private Button btnScan, btnInspect, btnTrack, btnStopTrack, btnRfcomm, btnNameTurbo, btnExport, btnBack;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) {
            btAdapter = bm.getAdapter();
            leScanner = btAdapter != null ? btAdapter.getBluetoothLeScanner() : null;
        }
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OB:BTTools");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // BT receiver
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_UUID);
        registerReceiver(mBtReceiver, f);

        buildUI();
        log("╔══════════════════════════════════╗");
        log("║  OBLITERATE BT TOOLS            ║");
        log("║  Scanner · Inspector · Distance  ║");
        log("║  RFCOMM · Name Turbo · Export   ║");
        log("╚══════════════════════════════════╝");
        log("  Scan for devices to begin.");
    }

    @Override
    protected void onDestroy() {
        isTracking = false; isRfcommScanning = false; isBtNameTurbo = false;
        trackTarget = null;
        if (btAdapter != null && btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        if (leScanner != null && activeBleScanCb != null) try { leScanner.stopScan(activeBleScanCb); } catch (Exception e) {}
        mainHandler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(mBtReceiver); } catch (Exception e) {}
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    // ═══════════════════════════════════════════
    //  UI
    // ═══════════════════════════════════════════

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0A);
        root.setPadding(12, 48, 12, 20);

        root.addView(mkLabel("🔍  BT TOOLS", 0xFFFF2222, 22));
        root.addView(mkLabel("[ scanner · inspector · distance · rfcomm ]", 0xFF888888, 10));

        // Status
        statusText = mkLabel("⚫ IDLE", 0xFF00FF41, 13);
        statusText.setPadding(0, 10, 0, 4);
        root.addView(statusText);

        // RSSI display
        rssiText = mkLabel("", 0xFF888888, 11);
        rssiText.setPadding(0, 2, 0, 6);
        root.addView(rssiText);

        // Buttons row 1
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        btnScan = mkBtnWide("🔍 SCAN");
        btnScan.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startScan(); }});
        r1.addView(btnScan);
        btnInspect = mkBtnWide("🔬 INSPECT");
        btnInspect.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { inspectDevice(); }});
        r1.addView(btnInspect);
        btnTrack = mkBtnWide("📏 TRACK RSSI");
        btnTrack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startTracking(); }});
        r1.addView(btnTrack);
        root.addView(r1);

        // Buttons row 2
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        btnStopTrack = mkBtnWide("🛑 STOP TRACK");
        btnStopTrack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { stopTracking(); }});
        r2.addView(btnStopTrack);
        btnRfcomm = mkBtnWide("📡 RFCOMM SCAN");
        btnRfcomm.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startRfcommScan(); }});
        r2.addView(btnRfcomm);
        btnNameTurbo = mkBtnWide("📛 NAME TURBO");
        btnNameTurbo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!btReady()) return;
            if (isBtNameTurbo) { isBtNameTurbo = false; resetBtn(btnNameTurbo, "📛 NAME TURBO"); log("🛑 Name turbo stopped"); updateStatus("IDLE"); releaseLock(); }
            else { isBtNameTurbo = true; setBtnOn(btnNameTurbo, "📛 TURBO ON"); log("⚡ BT Name Turbo — 100ms cycling"); updateStatus("NAME TURBO"); acquireLock(); new Thread(new NameTurboRunner()).start(); }
        }});
        r2.addView(btnNameTurbo);
        root.addView(r2);

        // Buttons row 3
        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        btnExport = mkBtnWide("📋 EXPORT JSON");
        btnExport.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { exportScans(); }});
        r3.addView(btnExport);
        btnBack = mkBtnWide("← BACK");
        btnBack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); }});
        r3.addView(btnBack);
        root.addView(r3);

        // Device list
        root.addView(mkLabel("DEVICES:", 0xFF888888, 9));
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        deviceListView = new ListView(this);
        deviceListView.setAdapter(deviceListAdapter);
        deviceListView.setBackgroundColor(0xFF111111);
        deviceListView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(100)));
        root.addView(deviceListView);

        // Log
        root.addView(mkLabel("TERMINAL:", 0xFF888888, 9));
        logText = new TextView(this);
        logText.setTextColor(0xFF00FF41);
        logText.setTextSize(10);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setBackgroundColor(0xFF0D0D0D);
        logText.setPadding(6, 6, 6, 6);
        logText.setMovementMethod(new ScrollingMovementMethod());
        logText.setHorizontallyScrolling(true);
        logText.setTextIsSelectable(true);
        logText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(160)));
        root.addView(logText);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0A0A0A);
        sv.addView(root);
        sv.setFillViewport(true);
        setContentView(sv);
    }

    // ═══════════════════════════════════════════
    //  SCANNING
    // ═══════════════════════════════════════════

    private void startScan() {
        if (!btReady()) return;
        discoveredDevices.clear(); deviceListAdapter.clear(); deviceRssiMap.clear();
        log("🔍 Scanning BT + BLE — 15s...");
        updateStatus("SCANNING");
        if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        btAdapter.startDiscovery();

        if (leScanner != null) {
            if (activeBleScanCb != null) try { leScanner.stopScan(activeBleScanCb); } catch (Exception e) {}
            activeBleScanCb = new BleScanCb();
            BleScanCb cb = activeBleScanCb;
            try {
                leScanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb);
                mainHandler.postDelayed(new Runnable() { public void run() {
                    try { leScanner.stopScan(cb); } catch (Exception e) {}
                }}, 15000);
            } catch (SecurityException e) {}
        }

        mainHandler.postDelayed(new Runnable() { public void run() {
            btAdapter.cancelDiscovery();
            updateStatus("DONE — " + discoveredDevices.size() + " devices");
        }}, 15000);
    }

    // ═══════════════════════════════════════════
    //  BT DEVICE INSPECTOR (SDP)
    // ═══════════════════════════════════════════

    private void inspectDevice() {
        final ArrayList<BluetoothDevice> devs;
        synchronized (discoveredDevices) { devs = new ArrayList<>(discoveredDevices); }
        if (devs.isEmpty()) { log("✕ Scan first"); return; }
        final String[] names = new String[devs.size()];
        for (int i = 0; i < devs.size(); i++) {
            BluetoothDevice d = devs.get(i);
            BluetoothClass c = d.getBluetoothClass();
            String type = c != null ? getDeviceTypeName(c.getMajorDeviceClass()) : "?";
            names[i] = (i+1) + ". " + getDeviceName(d) + "  " + type;
        }
        new AlertDialog.Builder(this).setTitle("🔬 Select Device to Inspect")
            .setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    BluetoothDevice d = devs.get(which);
                    inspectTarget = d;
                    log("🔬 Inspecting: " + getDeviceName(d));
                    updateStatus("INSPECTING...");
                    try {
                        d.fetchUuidsWithSdp();
                    } catch (SecurityException e) { log("✕ Permission denied"); }
                }
            }).setNegativeButton("Cancel", null).show();
    }

    private BluetoothDevice inspectTarget = null;
    
    private void showInspectResult(BluetoothDevice d, Parcelable[] uuids) {
        BluetoothClass btClass = d.getBluetoothClass();
        StringBuilder sb = new StringBuilder();
        sb.append("\n═══ DEVICE INSPECTOR ═══\n");
        sb.append("  Name:      ").append(getDeviceName(d)).append("\n");
        sb.append("  Address:   ").append(d.getAddress()).append("\n");
        sb.append("  Bond:      ").append(d.getBondState() == BluetoothDevice.BOND_BONDED ? "PAIRED" : "not paired").append("\n");
        if (btClass != null) {
            sb.append("  Class:     0x").append(Integer.toHexString(btClass.getDeviceClass())).append("\n");
            sb.append("  Type:      ").append(getDeviceTypeName(btClass.getMajorDeviceClass())).append("\n");
            sb.append("  Sub-type:  ").append(getDeviceSubType(btClass)).append("\n");
        }
        sb.append("  Services:\n");
        if (uuids != null && uuids.length > 0) {
            for (Parcelable p : uuids) {
                String uid = p.toString().toUpperCase();
                sb.append("    • ").append(uid).append("  ").append(getUuidName(uid)).append("\n");
            }
        } else { sb.append("    (no SDP services exposed)\n"); }
        log(sb.toString());
        updateStatus("INSPECT DONE");
        inspectTarget = null;
    }

    // ═══════════════════════════════════════════
    //  DISTANCE ESTIMATOR
    // ═══════════════════════════════════════════

    private void startTracking() {
        final ArrayList<BluetoothDevice> devs;
        synchronized (discoveredDevices) { devs = new ArrayList<>(discoveredDevices); }
        if (devs.isEmpty()) { log("✕ Scan first"); return; }
        final String[] names = new String[devs.size()];
        for (int i = 0; i < devs.size(); i++) {
            BluetoothDevice d = devs.get(i);
            Short rssi = deviceRssiMap.get(d.getAddress());
            names[i] = (i+1) + ". " + getDeviceName(d) + "  " + (rssi != null ? rssi + "dBm" : "");
        }
        new AlertDialog.Builder(this).setTitle("📏 Select Device to Track")
            .setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    trackTarget = devs.get(which);
                    isTracking = true;
                    rssiHistory.clear(); rssiMin = 0; rssiMax = -100;
                    log("📏 Tracking RSSI: " + getDeviceName(trackTarget));
                    updateStatus("TRACKING");
                    acquireLock();
                    setBtnOn(btnTrack, "📏 TRACKING");
                    startContinuousDiscovery();
                }
            }).setNegativeButton("Cancel", null).show();
    }

    private void startContinuousDiscovery() {
        if (!isTracking || trackTarget == null) return;
        if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        btAdapter.startDiscovery();
        // Re-trigger every 12s (Android max discovery duration)
        mainHandler.postDelayed(new Runnable() { public void run() {
            if (isTracking) startContinuousDiscovery();
        }}, 12000);
    }

    private void updateRssi(BluetoothDevice d, short rssi) {
        if (!isTracking || trackTarget == null) return;
        if (!d.getAddress().equals(trackTarget.getAddress())) return;
        
        rssiHistory.add((int)rssi);
        if (rssiHistory.size() > 100) rssiHistory.remove(0);
        
        // Calc stats
        int sum = 0; rssiMin = 0; rssiMax = -100;
        for (int r : rssiHistory) {
            sum += r;
            if (r > rssiMin) rssiMin = r;
            if (r < rssiMax) rssiMax = r;
        }
        rssiAvg = sum / rssiHistory.size();
        
        // Distance bracket
        String dist;
        if (rssi > -45) dist = "📳 TOUCHING (< 0.5m)";
        else if (rssi > -55) dist = "📍 VERY CLOSE (0.5-1m)";
        else if (rssi > -65) dist = "📍 CLOSE (1-3m)";
        else if (rssi > -75) dist = "📍 MEDIUM (3-10m)";
        else if (rssi > -85) dist = "📍 FAR (10-20m)";
        else if (rssi > -95) dist = "📍 VERY FAR (20-50m)";
        else dist = "🌐 OUT OF RANGE (> 50m)";
        
        final String display = "📡 RSSI: " + rssi + "dBm  " + dist + "  [" + rssiHistory.size() + "samples]";
        final String detail = "  " + rssi + "dBm  " + dist;
        mainHandler.post(new Runnable() { public void run() {
            rssiText.setText(display);
            updateStatus(dist);
            if (rssiHistory.size() % 5 == 0) log(detail);
        }});
    }

    private void stopTracking() {
        isTracking = false; trackTarget = null;
        btAdapter.cancelDiscovery();
        resetBtn(btnTrack, "📏 TRACK RSSI");
        rssiText.setText("");
        updateStatus("IDLE");
        releaseLock();
        log("📏 Tracking stopped — " + rssiHistory.size() + " samples");
        if (rssiHistory.size() > 0) {
            log("  Min: " + rssiMax + "dBm  Max: " + rssiMin + "dBm  Avg: " + rssiAvg + "dBm");
        }
    }

    // ═══════════════════════════════════════════
    //  RFCOMM CHANNEL SCANNER
    // ═══════════════════════════════════════════

    private void startRfcommScan() {
        final ArrayList<BluetoothDevice> devs;
        synchronized (discoveredDevices) { devs = new ArrayList<>(discoveredDevices); }
        if (devs.isEmpty()) { log("✕ Scan first"); return; }
        final String[] names = new String[devs.size()];
        for (int i = 0; i < devs.size(); i++) names[i] = (i+1) + ". " + getDeviceName(devs.get(i));
        new AlertDialog.Builder(this).setTitle("📡 Select Device for RFCOMM Scan")
            .setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    final BluetoothDevice d = devs.get(which);
                    isRfcommScanning = true;
                    log("📡 Scanning RFCOMM channels on " + getDeviceName(d) + "...");
                    updateStatus("RFCOMM SCAN");
                    acquireLock();
                    new Thread(new Runnable() { public void run() {
                        for (int ch = 1; ch <= 30 && isRfcommScanning; ch++) {
                            final int channel = ch;
                            try {
                                java.lang.reflect.Method m = d.getClass().getMethod("createRfcommSocket", int.class);
                                BluetoothSocket s = (BluetoothSocket) m.invoke(d, channel);
                                s.connect();
                                s.close();
                                final String service = getRfcommServiceName(channel);
                                mainHandler.post(new Runnable() { public void run() {
                                    log("  ✓ Channel " + channel + " OPEN — " + service);
                                }});
                            } catch (Exception e) {
                                // Channel closed or error — only log every 5 failures to reduce noise
                            }
                            try { Thread.sleep(300); } catch (InterruptedException e) { break; }
                        }
                        isRfcommScanning = false;
                        mainHandler.post(new Runnable() { public void run() {
                            log("📡 RFCOMM scan complete (channels 1-30)");
                            updateStatus("RFCOMM DONE");
                            releaseLock();
                        }});
                    }}).start();
                }
            }).setNegativeButton("Cancel", null).show();
    }

    // ═══════════════════════════════════════════
    //  BT NAME TURBO
    // ═══════════════════════════════════════════

    private class NameTurboRunner implements Runnable {
        public void run() {
            final String[] trollNames = {
                "FBI Surveillance Van", "NSA Listening Post", "Police Drone #42",
                "⚠ Security Breach", "Camera Remote", "Microphone Active",
                "Free WiFi No Pass", "Mom iPhone", "Hacked Device",
                "Windows Alert", "Virus Detected", "iCloud Locked",
                "CIA Field Office", "Interpol Mobile", "Karen AirPods Pro",
                "Dad Search History", "Bathroom Cam #3", "You Are Watched",
            };
            int count = 0;
            while (isBtNameTurbo) {
                String name = trollNames[count % trollNames.length] + " " + (count % 99);
                try { btAdapter.setName(name); } catch (Exception e) {}
                count++;
                if (count % 20 == 0) {
                    final int c = count;
                    mainHandler.post(new Runnable() { public void run() {
                        updateStatus("TURBO #" + c);
                    }});
                }
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
            final int changed = count;
            mainHandler.post(new Runnable() { public void run() {
                log("📛 Name turbo stopped — " + changed + " changes");
                updateStatus("IDLE");
            }});
            releaseLock();
        }
    }

    // ═══════════════════════════════════════════
    //  EXPORT
    // ═══════════════════════════════════════════

    private void exportScans() {
        final ArrayList<BluetoothDevice> devs;
        synchronized (discoveredDevices) { devs = new ArrayList<>(discoveredDevices); }
        if (devs.isEmpty()) { log("✕ No devices"); return; }
        new Thread(new Runnable() { public void run() {
            try {
                File f = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "obliterate_bt_scan.json");
                FileWriter fw = new FileWriter(f);
                fw.write("{\n  \"timestamp\": \"" + new Date() + "\",\n");
                fw.write("  \"count\": " + devs.size() + ",\n  \"devices\": [\n");
                for (int i = 0; i < devs.size(); i++) {
                    BluetoothDevice d = devs.get(i);
                    Short r = deviceRssiMap.get(d.getAddress());
                    fw.write("    {\"name\":\"" + getDeviceName(d).replace("\"","'") + "\",");
                    fw.write("\"addr\":\"" + d.getAddress() + "\",");
                    fw.write("\"rssi\":" + (r != null ? r : 0) + "}");
                    if (i < devs.size() - 1) fw.write(",");
                    fw.write("\n");
                }
                fw.write("  ]\n}\n"); fw.close();
                final String p = f.getAbsolutePath();
                mainHandler.post(new Runnable() { public void run() {
                    log("📋 Exported → " + p);
                }});
            } catch (Exception e) {
                final String m = e.getMessage();
                mainHandler.post(new Runnable() { public void run() { log("✕ " + m); }});
            }
        }}).start();
        log("📋 Exporting " + devs.size() + " devices...");
    }

    // ═══════════════════════════════════════════
    //  BROADCAST RECEIVER
    // ═══════════════════════════════════════════

    private BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            String a = i.getAction(); if (a == null) return;
            if (BluetoothDevice.ACTION_FOUND.equals(a)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short)0);
                if (d != null) {
                    final BluetoothDevice fd = d; final short fr = rssi;
                    mainHandler.post(new Runnable() { public void run() {
                        if (!hasDevice(fd)) addDevice(fd, fr);
                        if (isTracking && trackTarget != null && fd.getAddress().equals(trackTarget.getAddress()))
                            updateRssi(fd, fr);
                    }});
                }
            } else if (BluetoothDevice.ACTION_UUID.equals(a)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null && inspectTarget != null && d.getAddress().equals(inspectTarget.getAddress())) {
                    final BluetoothDevice fd = d;
                    final Parcelable[] uuids = i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    mainHandler.post(new Runnable() { public void run() {
                        showInspectResult(fd, uuids);
                    }});
                    inspectTarget = null;
                }
            }
        }
    };

    // ═══════════════════════════════════════════
    //  BLE SCAN CALLBACK
    // ═══════════════════════════════════════════

    private class BleScanCb extends ScanCallback {
        public void onScanResult(int type, ScanResult r) {
            BluetoothDevice d = r.getDevice();
            if (d != null) {
                final BluetoothDevice fd = d; final short fr = (short)r.getRssi();
                mainHandler.post(new Runnable() { public void run() {
                    if (!hasDevice(fd)) addDevice(fd, fr);
                    // Log BLE advertisement detail
                    ScanRecord rec = r.getScanRecord();
                    if (rec != null && rec.getManufacturerSpecificData() != null) {
                        android.util.SparseArray<byte[]> mfr = rec.getManufacturerSpecificData();
                        for (int j = 0; j < mfr.size(); j++) {
                            StringBuilder hex = new StringBuilder();
                            for (byte b : mfr.get(mfr.keyAt(j))) hex.append(String.format("%02X", b & 0xFF));
                            log("  📻 " + getDeviceName(fd) + " mfr=0x" + Integer.toHexString(mfr.keyAt(j)) + " data=" + hex);
                        }
                    }
                }});
            }
        }
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private boolean btReady() {
        if (btAdapter == null) { log("✕ No BT"); return false; }
        if (!btAdapter.isEnabled()) { log("✕ BT off"); startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1); return false; }
        return true;
    }

    private boolean hasDevice(BluetoothDevice d) {
        for (BluetoothDevice x : discoveredDevices) if (x.getAddress().equals(d.getAddress())) return true;
        return false;
    }

    private void addDevice(BluetoothDevice d, short rssi) {
        synchronized (discoveredDevices) { discoveredDevices.add(d); }
        deviceRssiMap.put(d.getAddress(), rssi);
        String name = getDeviceName(d);
        BluetoothClass bc = d.getBluetoothClass();
        String type = bc != null ? " [" + getDeviceTypeName(bc.getMajorDeviceClass()) + "]" : "";
        deviceListAdapter.add(name + "  " + rssi + "dBm" + type);
        log("  📱 " + name + "  " + rssi + "dBm" + type);
    }

    private String getDeviceName(BluetoothDevice d) {
        String n = d.getName(); return (n != null && !n.isEmpty()) ? n : d.getAddress();
    }

    private String getDeviceTypeName(int major) {
        switch (major) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "Audio";
            case BluetoothClass.Device.Major.COMPUTER: return "Computer";
            case BluetoothClass.Device.Major.PHONE: return "Phone";
            case BluetoothClass.Device.Major.WEARABLE: return "Wearable";
            case BluetoothClass.Device.Major.HEALTH: return "Health";
            case BluetoothClass.Device.Major.TOY: return "Toy";
            case BluetoothClass.Device.Major.PERIPHERAL: return "Peripheral";
            case BluetoothClass.Device.Major.NETWORKING: return "Network";
            case BluetoothClass.Device.Major.IMAGING: return "Imaging";
            default: return "Device";
        }
    }

    private String getDeviceSubType(BluetoothClass bc) {
        int dc = bc.getDeviceClass();
        if (bc.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            if (dc == 0x0404) return "Headset"; if (dc == 0x0408) return "Speaker";
            if (dc == 0x0414) return "Car Audio"; if (dc == 0x043C) return "Gaming";
        }
        if (bc.getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE) {
            if (dc == 0x0208) return "Smartphone"; if (dc == 0x0200) return "Cell";
        }
        if (bc.getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER) {
            if (dc == 0x0108) return "Laptop"; if (dc == 0x0100) return "Desktop";
        }
        if (bc.getMajorDeviceClass() == BluetoothClass.Device.Major.WEARABLE) {
            if (dc == 0x0704) return "Watch";
        }
        return "0x" + Integer.toHexString(dc);
    }

    private String getUuidName(String uuid) {
        if (uuid.contains("1101")) return "→ Serial Port (SPP)";
        if (uuid.contains("1103")) return "→ Dial-Up";
        if (uuid.contains("1105")) return "→ OBEX Push";
        if (uuid.contains("1106")) return "→ OBEX File Transfer";
        if (uuid.contains("1108")) return "→ Headset (HSP)";
        if (uuid.contains("110A")) return "→ Audio Source (A2DP)";
        if (uuid.contains("110B")) return "→ Audio Sink";
        if (uuid.contains("110C")) return "→ AVRCP Remote";
        if (uuid.contains("1112")) return "→ Headset AG (HFP)";
        if (uuid.contains("1116")) return "→ PAN Tether";
        if (uuid.contains("111E")) return "→ Hands-Free";
        if (uuid.contains("1124")) return "→ HID Keyboard/Mouse";
        if (uuid.contains("1130")) return "→ MAP (SMS/MMS)";
        if (uuid.contains("1132")) return "→ PBAP (Phone Book)";
        if (uuid.contains("1200")) return "→ PnP Info";
        if (uuid.contains("180A")) return "→ Device Info";
        return "";
    }

    private String getRfcommServiceName(int channel) {
        switch (channel) {
            case 1: return "SPP (Serial)";
            case 2: return "SPP / DUN";
            case 3: return "SPP";
            case 4: return "HSP Headset";
            case 5: return "HFP Hands-Free";
            case 6: return "HSP/HFP";
            case 7: return "OBEX Push";
            case 8: return "OBEX File Transfer";
            case 9: return "OBEX Sync";
            case 10: return "A2DP Audio";
            case 11: return "A2DP Control";
            case 12: return "AVRCP";
            case 13: return "AVRCP";
            case 14: return "PBAP";
            case 15: return "MAP";
            case 16: return "HID";
            case 17: return "HID";
            case 18: return "HID";
            case 19: return "HID";
            default: return "Unknown";
        }
    }

    private void updateStatus(String s) {
        mainHandler.post(new Runnable() { public void run() { statusText.setText("⚡ " + s); }});
    }

    private void log(final String msg) {
        mainHandler.post(new Runnable() { public void run() {
            if (logText != null) {
                logText.append(msg + "\n");
                int scroll = logText.getLayout() != null ?
                    logText.getLayout().getLineTop(logText.getLineCount()) - logText.getHeight() : 0;
                if (scroll > 0) logText.scrollTo(0, scroll);
            }
        }});
    }

    private void acquireLock() {
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
    }

    private void releaseLock() {
        if (wakeLock != null && wakeLock.isHeld() && !isTracking && !isRfcommScanning && !isBtNameTurbo)
            wakeLock.release();
    }

    private void setBtnOn(Button b, String t) { b.setText(t); b.setTextColor(0xFF00FF41); }
    private void resetBtn(Button b, String t) { b.setText(t); b.setTextColor(0xFFFF2222); }

    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density); }

    private TextView mkLabel(String txt, int color, int size) {
        TextView tv = new TextView(this);
        tv.setText(txt); tv.setTextColor(color); tv.setTextSize(size);
        tv.setTypeface(Typeface.MONOSPACE); tv.setPadding(0, 6, 0, 2);
        return tv;
    }

    private Button mkBtnWide(String txt) {
        Button b = new Button(this);
        b.setText(txt); b.setTextColor(0xFFFF2222); b.setBackgroundColor(0xFF1A1A1A);
        b.setTypeface(Typeface.MONOSPACE); b.setTextSize(10); b.setAllCaps(true);
        b.setPadding(6, 14, 6, 14); b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(3, 5, 3, 5); b.setLayoutParams(p);
        return b;
    }
}
