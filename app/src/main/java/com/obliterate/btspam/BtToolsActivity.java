package com.obliterate.btspam;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
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

    private static final int REQUEST_STORAGE = 4;

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
    private String originalBtName = null;

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
        restoreAdapterName();
        if (btAdapter != null && btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        if (leScanner != null && activeBleScanCb != null) try { leScanner.stopScan(activeBleScanCb); } catch (Exception e) {}
        mainHandler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(mBtReceiver); } catch (Exception e) {}
        if (activeDialog != null) try { activeDialog.dismiss(); } catch (Exception e) {}
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
            else {
                originalBtName = getAdapterName();
                isBtNameTurbo = true;
                setBtnOn(btnNameTurbo, "📛 TURBO ON");
                log("⚡ BT Name Turbo — 100ms cycling");
                updateStatus("NAME TURBO");
                acquireLock();
                new Thread(new NameTurboRunner()).start();
            }
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
        try {
            if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
            if (!btAdapter.startDiscovery()) log("  ⚠ Classic discovery request returned false");
        } catch (SecurityException e) {
            log("  ✕ Classic discovery blocked by Bluetooth permission");
        } catch (Exception e) {
            log("  ✕ Classic discovery failed: " + safeMsg(e));
        }

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
            } catch (SecurityException e) {
                log("  ✕ BLE scan blocked by Bluetooth/location permission");
            } catch (Exception e) {
                log("  ✕ BLE scan failed: " + safeMsg(e));
            }
        }

        mainHandler.postDelayed(new Runnable() { public void run() {
            try { btAdapter.cancelDiscovery(); } catch (Exception e) {}
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
        if (activeDialog != null) try { activeDialog.dismiss(); } catch (Exception e) {}
        activeDialog = new AlertDialog.Builder(this).setTitle("🔬 Select Device to Inspect")
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
    private AlertDialog activeDialog;
    private Runnable pendingStorageAction;
    
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
        if (activeDialog != null) try { activeDialog.dismiss(); } catch (Exception e) {}
        activeDialog = new AlertDialog.Builder(this).setTitle("📏 Select Device to Track")
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
        int sum = 0; rssiMin = Integer.MAX_VALUE; rssiMax = Integer.MIN_VALUE;
        for (int r : rssiHistory) {
            sum += r;
            if (r < rssiMin) rssiMin = r;
            if (r > rssiMax) rssiMax = r;
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
        if (btAdapter != null && btAdapter.isDiscovering()) try { btAdapter.cancelDiscovery(); } catch (Exception e) {}
        resetBtn(btnTrack, "📏 TRACK RSSI");
        rssiText.setText("");
        updateStatus("IDLE");
        releaseLock();
        log("📏 Tracking stopped — " + rssiHistory.size() + " samples");
        if (rssiHistory.size() > 0) {
            log("  Min: " + rssiMin + "dBm  Max: " + rssiMax + "dBm  Avg: " + rssiAvg + "dBm");
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
        if (activeDialog != null) try { activeDialog.dismiss(); } catch (Exception e) {}
        activeDialog = new AlertDialog.Builder(this).setTitle("📡 Select Device for RFCOMM Scan")
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
                            BluetoothSocket s = null;
                            try {
                                java.lang.reflect.Method m = d.getClass().getMethod("createRfcommSocket", int.class);
                                s = (BluetoothSocket) m.invoke(d, channel);
                                if (connectWithTimeout(s, 2000)) {
                                    final String service = getRfcommServiceName(channel);
                                    mainHandler.post(new Runnable() { public void run() {
                                        log("  ✓ Channel " + channel + " OPEN — " + service);
                                    }});
                                }
                            } catch (Exception e) {
                                // Channel closed or error — only log every 5 failures to reduce noise
                            } finally {
                                if (s != null) try { s.close(); } catch (Exception ex) {}
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
            restoreAdapterName();
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

    private boolean hasStoragePermission() {
        return ObStorage.hasLegacyWritePermission(this);
    }

    private void requestStoragePermission(Runnable onGranted) {
        pendingStorageAction = onGranted;
        ObStorage.requestLegacyWritePermission(this, REQUEST_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_STORAGE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            log("✓ Storage permission granted");
            if (pendingStorageAction != null) {
                pendingStorageAction.run();
                pendingStorageAction = null;
            }
        }
    }

    private void exportScans() {
        if (!hasStoragePermission()) {
            log("📁 Requesting storage permission for export...");
            requestStoragePermission(new Runnable() { public void run() { exportScans(); }});
            return;
        }
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
                            byte[] data = mfr.get(mfr.keyAt(j));
                            if (data != null) {
                                StringBuilder hex = new StringBuilder();
                                for (byte b : data) hex.append(String.format("%02X", b & 0xFF));
                                log("  📻 " + getDeviceName(fd) + " mfr=0x" + Integer.toHexString(mfr.keyAt(j)) + " data=" + hex);
                            }
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
        return ObBluetooth.deviceName(d);
    }

    private String getAdapterName() {
        try { return btAdapter != null ? btAdapter.getName() : null; } catch (Exception e) { return null; }
    }

    private void restoreAdapterName() {
        if (btAdapter == null || originalBtName == null || originalBtName.length() == 0) return;
        try { btAdapter.setName(originalBtName); } catch (Exception e) {}
        originalBtName = null;
    }

    private String safeMsg(Exception e) {
        return e != null && e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private String getDeviceTypeName(int major) {
        return ObBluetooth.deviceTypeName(major);
    }

    private String getDeviceSubType(BluetoothClass bc) {
        return ObBluetooth.deviceSubType(bc);
    }

    private String getUuidName(String uuid) {
        return ObBluetooth.uuidName(uuid);
    }

    // Connect on a separate thread with a hard timeout. Closing the socket from
    // another thread usually unblocks a stuck BluetoothSocket.connect() call.
    private boolean connectWithTimeout(final BluetoothSocket socket, final long timeoutMs) {
        final boolean[] connected = {false};
        Thread t = new Thread(new Runnable() { public void run() {
            try { socket.connect(); connected[0] = true; } catch (Exception e) {}
        }});
        t.start();
        try { t.join(timeoutMs); } catch (InterruptedException e) { t.interrupt(); return false; }
        if (!connected[0]) {
            try { socket.close(); } catch (Exception e) {}
            try { t.join(500); } catch (Exception e) {}
        }
        return connected[0];
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
        if (!isTracking && !isRfcommScanning && !isBtNameTurbo && wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
    }

    private void setBtnOn(Button b, String t) { ObUi.setButtonOn(b, t); }
    private void resetBtn(Button b, String t) { ObUi.resetButton(b, t); }

    private int dp(int px) { return ObUi.dp(this, px); }

    private TextView mkLabel(String txt, int color, int size) {
        return ObUi.tightLabel(this, txt, color, size);
    }

    private Button mkBtnWide(String txt) {
        return ObUi.weightedWideButton(this, txt);
    }
}
