package com.obliterate.btspam;

import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.*;
import android.net.wifi.*;
import android.os.*;
import android.text.method.ScrollingMovementMethod;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * OBLITERATE GPS Wardriving — WiFi + BT/BLE mapping with GPS coordinates.
 * Walk or drive around, every network/device gets tagged with lat/lng.
 * Export as GeoJSON for Google Maps / GIS tools.
 */
public class GpsWardriveActivity extends Activity {

    private static final int REQUEST_STORAGE = 4;
    private static final int REQUEST_LOCATION = 5;

    // ── GPS ───────────────────────────────────
    private LocationManager locationManager;
    private Location lastLocation;
    private double lat = 0, lng = 0;
    private float accuracy = 0, speed = 0;
    private int satCount = 0;

    // ── Bluetooth ──────────────────────────────
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner leScanner;
    private BleScanCb activeBleScanCb;
    private final ArrayList<MappedDevice> mappedDevices = new ArrayList<>();
    private final HashMap<String, MappedDevice> deviceMap = new HashMap<>();
    private volatile boolean isScanning = false;
    private int scanCycle = 0;

    // ── WiFi ────────────────────────────────────
    private WifiManager wifiManager;

    // ── Track Logging ──────────────────────────
    private final ArrayList<TrackPoint> trackPoints = new ArrayList<>();
    private volatile boolean isTracking = false;

    // ── UI ─────────────────────────────────────
    private TextView statusText, gpsText, statsText, logText;
    private Button btnStart, btnStop, btnExportGeo, btnExportTrack, btnBack;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private Runnable pendingStorageAction;
    private volatile boolean pendingStartAfterLocationGrant = false;
    private volatile boolean waitingForFixLogged = false;

    // ── Data classes ───────────────────────────
    private static class MappedDevice {
        String name, address;
        double lat, lng;
        int rssi;
        long timestamp;
        int deviceType; // 0=classic, 1=ble, 2=dual, 3=wifi
        int frequency;
        String details;
    }

    private static class TrackPoint {
        double lat, lng;
        float speed, accuracy;
        long timestamp;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) { btAdapter = bm.getAdapter(); leScanner = btAdapter != null ? btAdapter.getBluetoothLeScanner() : null; }
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) { log("⚠ No location manager"); }
        if (wifiManager == null) { log("⚠ No WiFi manager"); }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OB:GPS");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildUI();
        
        // Register scan receivers
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mScanReceiver, f);
        
        log("╔══════════════════════════════════╗");
        log("║  OBLITERATE GPS WARDRIVING      ║");
        log("║  Map WiFi + BT/BLE with GPS     ║");
        log("╚══════════════════════════════════╝");
        updateGpsDisplay();
    }

    @Override
    protected void onDestroy() {
        stopAll();
        mainHandler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(mScanReceiver); } catch (Exception e) {}
        if (locationManager != null) try { locationManager.removeUpdates(locationListener); } catch (Exception e) {}
        super.onDestroy();
    }

    // ═══════════════════════════════════════════
    //  GPS LISTENER
    // ═══════════════════════════════════════════

    private LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location loc) {
            boolean firstFix = lastLocation == null;
            lastLocation = loc;
            lat = loc.getLatitude(); lng = loc.getLongitude();
            accuracy = loc.getAccuracy(); speed = loc.getSpeed() * 3.6f; // m/s to km/h
            if (loc.getExtras() != null) satCount = loc.getExtras().getInt("satellites", 0);
            if (firstFix) {
                waitingForFixLogged = false;
                log(String.format("✓ Location fix %.6f, %.6f ±%.0fm", lat, lng, accuracy));
            }
            if (isTracking) {
                TrackPoint tp = new TrackPoint();
                tp.lat = lat; tp.lng = lng; tp.speed = speed; tp.accuracy = accuracy;
                tp.timestamp = System.currentTimeMillis();
                synchronized (trackPoints) { trackPoints.add(tp); }
            }
            mainHandler.post(new Runnable() { public void run() { updateGpsDisplay(); }});
        }
        public void onStatusChanged(String p, int s, Bundle e) {}
        public void onProviderEnabled(String p) { log("✓ GPS: " + p + " enabled"); }
        public void onProviderDisabled(String p) { log("⚠ GPS: " + p + " disabled"); }
    };

    private void updateGpsDisplay() {
        String fix = lastLocation != null ? "🟢 FIX" : "🔴 NO FIX";
        gpsText.setText(String.format("%s  %.6f, %.6f  ±%.0fm  %.0fkm/h  %dsats",
            fix, lat, lng, accuracy, speed, satCount));
    }

    // ═══════════════════════════════════════════
    //  UI
    // ═══════════════════════════════════════════

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0A);
        root.setPadding(12, 48, 12, 20);

        root.addView(mkLabel("📍  GPS WARDRIVING", 0xFFFF2222, 22));
        root.addView(mkLabel("[ map wifi + bt/ble with gps coordinates ]", 0xFF888888, 9));

        // Status
        statusText = mkLabel("⚫ IDLE", 0xFF00FF41, 13);
        statusText.setPadding(0, 10, 0, 2);
        root.addView(statusText);

        // GPS display
        gpsText = mkLabel("🔴 NO FIX  ---, ---  --  --km/h", 0xFFFFCC00, 12);
        gpsText.setPadding(0, 2, 0, 8);
        root.addView(gpsText);

        // Stats
        statsText = mkLabel("WiFi: 0  BT/BLE: 0  Track: 0pts", 0xFF888888, 11);
        statsText.setPadding(0, 2, 0, 8);
        root.addView(statsText);

        // Row 1: Start / Stop
        LinearLayout r1 = new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL);
        btnStart = mkBtnWide("▶ START SCAN+GPS");
        btnStart.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startAll(); }});
        r1.addView(btnStart);
        btnStop = mkBtnWide("⏹ STOP ALL");
        btnStop.setBackgroundColor(0xFF8B0000);
        btnStop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { stopAll(); }});
        r1.addView(btnStop);
        root.addView(r1);

        // Row 2: Track toggle
        LinearLayout r2 = new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL);
        Button btnTrackToggle = mkBtnWide("🛤 TRACK PATH");
        btnTrackToggle.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (isTracking) { isTracking = false; resetBtn(btnTrackToggle, "🛤 TRACK PATH"); log("🛤 Path tracking stopped — " + trackPoints.size() + " points"); }
            else { isTracking = true; setBtnOn(btnTrackToggle, "🛤 TRACKING ON"); log("🛤 Path tracking started"); }
        }});
        r2.addView(btnTrackToggle);
        r2.addView(mkActionBtn("📊 CLEAR ALL", "clearAll"));
        root.addView(r2);

        // Row 3: Export + Back
        LinearLayout r3 = new LinearLayout(this); r3.setOrientation(LinearLayout.HORIZONTAL);
        btnExportGeo = mkBtnWide("📍 EXPORT GEOJSON");
        btnExportGeo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { exportGeoJson(); }});
        r3.addView(btnExportGeo);
        btnExportTrack = mkBtnWide("🛤 EXPORT GPX");
        btnExportTrack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { exportGpx(); }});
        r3.addView(btnExportTrack);
        btnBack = mkBtnWide("← BACK");
        btnBack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); }});
        r3.addView(btnBack);
        root.addView(r3);

        // Log
        root.addView(mkLabel("DISCOVERIES / SCAN STATUS:", 0xFF888888, 9));
        logText = new TextView(this);
        logText.setTextColor(0xFF00FF41); logText.setTextSize(10); logText.setTypeface(Typeface.MONOSPACE);
        logText.setBackgroundColor(0xFF0D0D0D); logText.setPadding(6, 6, 6, 6);
        logText.setMovementMethod(new ScrollingMovementMethod());
        logText.setHorizontallyScrolling(true); logText.setTextIsSelectable(true);
        logText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)));
        root.addView(logText);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0A0A0A); sv.addView(root); sv.setFillViewport(true);
        setContentView(sv);
    }

    private Button mkActionBtn(String label, final String action) {
        Button b = mkBtnWide(label);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try {
                java.lang.reflect.Method m = GpsWardriveActivity.class.getDeclaredMethod(action);
                m.setAccessible(true);
                m.invoke(GpsWardriveActivity.this);
            } catch (Exception e) { log("✕ " + e.getMessage()); }
        }});
        return b;
    }

    // ═══════════════════════════════════════════
    //  START / STOP
    // ═══════════════════════════════════════════

    private void startAll() {
        // Start GPS
        if (locationManager == null) { log("✕ No location manager"); return; }
        if (!hasLocationPermission()) {
            pendingStartAfterLocationGrant = true;
            log("📍 Requesting location permission for wardriving...");
            requestLocationPermission();
            return;
        }
        try {
            seedLastKnownLocation();
            int providers = 0;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
                providers++;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, locationListener);
                providers++;
            }
            if (providers == 0) {
                log("✕ No location provider enabled. Turn on GPS/location first.");
                return;
            }
        } catch (SecurityException e) { log("✕ GPS permission denied"); return; }
        
        // Start WiFi + BT/BLE scan. BT can be unavailable; WiFi wardriving should still run.
        isScanning = true;
        scanCycle = 0;
        setBtnOn(btnStart, "▶ SCANNING");
        log("📍 Wardriving started — GPS + WiFi + BT/BLE scan cycle active");
        if (lastLocation == null) log("⏳ Waiting for location fix; devices are mapped after first fix");
        updateStatus("WARDIVING");
        acquireLock();
        
        startScanCycle();
    }

    private void startScanCycle() {
        if (!isScanning) return;
        scanCycle++;
        boolean btClassic = false;
        boolean ble = false;
        boolean wifi = false;

        if (btAdapter != null && btAdapter.isEnabled()) {
            try {
                if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
                btClassic = btAdapter.startDiscovery();
            } catch (SecurityException e) {
                log("  ⚠ BT classic scan blocked by permission");
            } catch (Exception e) {
                log("  ⚠ BT classic scan failed: " + safeMsg(e));
            }
        } else {
            log("  ⚠ Bluetooth off/unavailable; WiFi wardrive still running");
        }

        if (btAdapter != null && btAdapter.isEnabled() && leScanner != null) {
            if (activeBleScanCb != null) {
                try { leScanner.stopScan(activeBleScanCb); } catch (Exception e) {}
            }
            activeBleScanCb = new BleScanCb();
            BleScanCb cb = activeBleScanCb;
            try {
                leScanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb);
                ble = true;
            } catch (SecurityException e) {
                log("  ⚠ BLE scan blocked by permission");
            } catch (Exception e) {
                log("  ⚠ BLE scan failed: " + safeMsg(e));
            }
        }

        if (wifiManager != null) {
            try {
                wifi = wifiManager.startScan();
                if (!wifi) log("  ⚠ WiFi scan request returned false");
            } catch (SecurityException e) {
                log("  ⚠ WiFi scan blocked by location permission");
            } catch (Exception e) {
                log("  ⚠ WiFi scan failed: " + safeMsg(e));
            }
        }

        log("  🔁 Scan cycle #" + scanCycle + " wifi=" + wifi + " bt=" + btClassic + " ble=" + ble);

        // Re-scan every 15 seconds
        mainHandler.postDelayed(new Runnable() { public void run() {
            if (isScanning) startScanCycle();
        }}, 15000);
    }

    private void stopAll() {
        isScanning = false; isTracking = false;
        if (btAdapter != null && btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        if (leScanner != null && activeBleScanCb != null) try { leScanner.stopScan(activeBleScanCb); } catch (Exception e) {}
        try { locationManager.removeUpdates(locationListener); } catch (Exception e) {}
        resetBtn(btnStart, "▶ START SCAN+GPS");
        releaseLock();
        updateStatus("IDLE");
        log("📍 Wardriving stopped — " + mappedDevices.size() + " mapped entries, " + trackPoints.size() + " track points");
    }

    private void clearAll() {
        synchronized (mappedDevices) { mappedDevices.clear(); deviceMap.clear(); }
        synchronized (trackPoints) { trackPoints.clear(); }
        updateStats();
        log("📊 Cleared all data");
    }

    // ═══════════════════════════════════════════
    //  DEVICE LOGGING
    // ═══════════════════════════════════════════

    private void logDevice(BluetoothDevice d, short rssi, int devType) {
        if (d == null) return;
        logMappedEntry(ObBluetooth.deviceName(d), d.getAddress(), rssi, devType, 0, "");
    }

    private void logWifiAp(android.net.wifi.ScanResult ap) {
        if (ap == null) return;
        String ssid = ap.SSID != null && ap.SSID.length() > 0 ? ap.SSID : "<hidden>";
        String details = wifiBand(ap.frequency) + " ch" + wifiChannel(ap.frequency) + " " + sanitize(ap.capabilities);
        logMappedEntry(ssid, ap.BSSID, ap.level, 3, ap.frequency, details);
    }

    private void logMappedEntry(String name, String address, int rssi, int devType, int frequency, String details) {
        if (!isScanning) return;
        if (lastLocation == null) {
            if (!waitingForFixLogged) {
                waitingForFixLogged = true;
                log("⏳ Signal seen but no location fix yet; not mapped");
            }
            return;
        }
        if (address == null || address.length() == 0) return;

        MappedDevice existing;
        synchronized (mappedDevices) { existing = deviceMap.get(address); }
        
        // Only log if new or position changed significantly
        if (existing != null) {
            double dist = distance(lat, lng, existing.lat, existing.lng);
            if (dist < 20) { // within 20m — update RSSI only
                existing.rssi = rssi;
                existing.timestamp = System.currentTimeMillis();
                existing.frequency = frequency;
                existing.details = details;
                return;
            }
            // Remove old, add new sighting at new location
            synchronized (mappedDevices) { mappedDevices.remove(existing); }
        }

        MappedDevice md = new MappedDevice();
        md.address = address;
        md.name = name != null && name.length() > 0 ? name : "<unknown>";
        md.lat = lat; md.lng = lng;
        md.rssi = rssi;
        md.timestamp = System.currentTimeMillis();
        md.deviceType = devType;
        md.frequency = frequency;
        md.details = details != null ? details : "";
        
        synchronized (mappedDevices) {
            mappedDevices.add(md);
            deviceMap.put(address, md);
        }
        
        final String entry = String.format("  📍 %s %s  %.5f,%.5f  %ddBm  %s  %s",
            typeLabel(md.deviceType), md.name, md.lat, md.lng, md.rssi,
            new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(md.timestamp)), md.details);
        mainHandler.post(new Runnable() { public void run() {
            log(entry);
            updateStats();
        }});
    }

    private void updateStats() {
        final int wifiCount; final int btCount; final int track;
        int wifi = 0; int bt = 0;
        synchronized (mappedDevices) {
            for (MappedDevice md : mappedDevices) {
                if (md.deviceType == 3) wifi++;
                else bt++;
            }
        }
        wifiCount = wifi; btCount = bt;
        synchronized (trackPoints) { track = trackPoints.size(); }
        mainHandler.post(new Runnable() { public void run() {
            statsText.setText(String.format("WiFi: %d  BT/BLE: %d  Track: %dpts", wifiCount, btCount, track));
        }});
    }

    // ═══════════════════════════════════════════
    //  SCAN RECEIVERS
    // ═══════════════════════════════════════════

    private BroadcastReceiver mScanReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            String a = i.getAction(); if (a == null) return;
            if (BluetoothDevice.ACTION_FOUND.equals(a)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short)0);
                if (d != null) {
                    final BluetoothDevice fd = d; final short fr = rssi;
                    mainHandler.post(new Runnable() { public void run() {
                        logDevice(fd, fr, 0); // classic
                    }});
                }
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(a)) {
                handleWifiScanResults();
            }
        }
    };

    private void handleWifiScanResults() {
        if (!isScanning || wifiManager == null) return;
        try {
            List<android.net.wifi.ScanResult> results = wifiManager.getScanResults();
            if (results == null) results = Collections.emptyList();
            final int count = results.size();
            for (android.net.wifi.ScanResult ap : results) logWifiAp(ap);
            mainHandler.post(new Runnable() { public void run() {
                log("  📶 WiFi scan results: " + count + " APs");
                updateStats();
            }});
        } catch (SecurityException e) {
            log("  ⚠ WiFi results blocked by location permission");
        } catch (Exception e) {
            log("  ⚠ WiFi results failed: " + safeMsg(e));
        }
    }

    // ═══════════════════════════════════════════
    //  BLE SCAN CALLBACK
    // ═══════════════════════════════════════════

    private class BleScanCb extends ScanCallback {
        public void onScanResult(int type, android.bluetooth.le.ScanResult r) {
            BluetoothDevice d = r.getDevice();
            if (d != null) {
                final BluetoothDevice fd = d; final short fr = (short)r.getRssi();
                mainHandler.post(new Runnable() { public void run() {
                    logDevice(fd, fr, 1); // BLE
                }});
            }
        }
    }

    // ═══════════════════════════════════════════
    //  EXPORT — GeoJSON
    // ═══════════════════════════════════════════

    private boolean hasStoragePermission() {
        return ObStorage.hasLegacyWritePermission(this);
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT < 23) return;
        requestPermissions(new String[] {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        }, REQUEST_LOCATION);
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
        } else if (requestCode == REQUEST_LOCATION) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            }
            if (granted) {
                log("✓ Location permission granted");
                if (pendingStartAfterLocationGrant) {
                    pendingStartAfterLocationGrant = false;
                    startAll();
                }
            } else {
                pendingStartAfterLocationGrant = false;
                log("✕ Location permission denied; wardriving cannot map coordinates");
            }
        }
    }

    private void exportGeoJson() {
        if (!hasStoragePermission()) {
            log("📁 Requesting storage permission for export...");
            requestStoragePermission(new Runnable() { public void run() { exportGeoJson(); }});
            return;
        }
        if (mappedDevices.isEmpty()) { log("✕ No devices to export"); return; }
        new Thread(new Runnable() { public void run() {
            try {
                File f = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "obliterate_wardrive_" + System.currentTimeMillis() + ".geojson");
                FileWriter fw = new FileWriter(f);
                fw.write("{\n  \"type\": \"FeatureCollection\",\n");
                fw.write("  \"features\": [\n");
                
                ArrayList<MappedDevice> devs;
                synchronized (mappedDevices) { devs = new ArrayList<>(mappedDevices); }
                
                for (int i = 0; i < devs.size(); i++) {
                    MappedDevice md = devs.get(i);
                    fw.write("    {\n");
                    fw.write("      \"type\": \"Feature\",\n");
                    fw.write("      \"geometry\": {\n");
                    fw.write("        \"type\": \"Point\",\n");
                    fw.write(String.format("        \"coordinates\": [%.6f, %.6f]\n", md.lng, md.lat));
                    fw.write("      },\n");
                    fw.write("      \"properties\": {\n");
                    fw.write("        \"name\": \"" + jsonEscape(md.name) + "\",\n");
                    fw.write("        \"address\": \"" + jsonEscape(md.address) + "\",\n");
                    fw.write("        \"rssi\": " + md.rssi + ",\n");
                    fw.write("        \"type\": \"" + typeName(md.deviceType) + "\",\n");
                    if (md.deviceType == 3) {
                        fw.write("        \"frequency\": " + md.frequency + ",\n");
                        fw.write("        \"channel\": " + wifiChannel(md.frequency) + ",\n");
                        fw.write("        \"details\": \"" + jsonEscape(md.details) + "\",\n");
                    }
                    fw.write("        \"time\": \"" + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(md.timestamp)) + "\"\n");
                    fw.write("      }\n");
                    fw.write("    }" + (i < devs.size() - 1 ? "," : "") + "\n");
                }
                fw.write("  ]\n}\n");
                fw.close();
                final String p = f.getAbsolutePath();
                mainHandler.post(new Runnable() { public void run() {
                    log("📍 Exported " + devs.size() + " devices → " + p);
                    log("  Open in https://geojson.io or Google My Maps");
                }});
            } catch (Exception e) {
                final String m = e.getMessage();
                mainHandler.post(new Runnable() { public void run() { log("✕ Export: " + m); }});
            }
        }}).start();
        log("📍 Exporting " + mappedDevices.size() + " devices as GeoJSON...");
    }

    // ═══════════════════════════════════════════
    //  EXPORT — GPX Track
    // ═══════════════════════════════════════════

    private void exportGpx() {
        if (!hasStoragePermission()) {
            log("📁 Requesting storage permission for export...");
            requestStoragePermission(new Runnable() { public void run() { exportGpx(); }});
            return;
        }
        if (trackPoints.isEmpty()) { log("✕ No track points. Enable '🛤 TRACK PATH' first."); return; }
        new Thread(new Runnable() { public void run() {
            try {
                File f = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "obliterate_track_" + System.currentTimeMillis() + ".gpx");
                FileWriter fw = new FileWriter(f);
                fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                fw.write("<gpx version=\"1.1\" creator=\"OBLITERATE\">\n");
                fw.write("  <trk><name>OBLITERATE Track</name><trkseg>\n");
                
                ArrayList<TrackPoint> pts;
                synchronized (trackPoints) { pts = new ArrayList<>(trackPoints); }
                
                for (TrackPoint tp : pts) {
                    fw.write(String.format("    <trkpt lat=\"%.6f\" lon=\"%.6f\">", tp.lat, tp.lng));
                    fw.write(String.format("<ele>0</ele><time>%s</time>", new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(tp.timestamp))));
                    fw.write(String.format("<speed>%.1f</speed></trkpt>\n", tp.speed));
                }
                fw.write("  </trkseg></trk>\n</gpx>\n");
                fw.close();
                final String p = f.getAbsolutePath();
                mainHandler.post(new Runnable() { public void run() {
                    log("🛤 Exported " + pts.size() + " track points → " + p);
                    log("  Open in Google Earth or GPX Viewer");
                }});
            } catch (Exception e) {
                final String m = e.getMessage();
                mainHandler.post(new Runnable() { public void run() { log("✕ GPX: " + m); }});
            }
        }}).start();
        log("🛤 Exporting " + trackPoints.size() + " track points as GPX...");
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private String typeName(int type) {
        switch (type) {
            case 0: return "classic";
            case 1: return "ble";
            case 2: return "dual";
            case 3: return "wifi";
            default: return "unknown";
        }
    }

    private String typeLabel(int type) {
        switch (type) {
            case 0: return "BT";
            case 1: return "BLE";
            case 2: return "BT/BLE";
            case 3: return "WiFi";
            default: return "?";
        }
    }

    private String wifiBand(int frequency) {
        if (frequency >= 5925) return "6GHz";
        if (frequency >= 4900) return "5GHz";
        if (frequency >= 2400) return "2.4GHz";
        return "freq" + frequency;
    }

    private int wifiChannel(int frequency) {
        if (frequency >= 2412 && frequency <= 2472) return ((frequency - 2412) / 5) + 1;
        if (frequency == 2484) return 14;
        if (frequency >= 5000 && frequency <= 5895) return (frequency - 5000) / 5;
        if (frequency >= 5955 && frequency <= 7115) return ((frequency - 5955) / 5) + 1;
        return 0;
    }

    private String sanitize(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safeMsg(Exception e) {
        return e != null && e.getMessage() != null ? e.getMessage() : "unknown";
    }

    private double distance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng/2) * Math.sin(dLng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    private void seedLastKnownLocation() {
        if (locationManager == null || !hasLocationPermission()) return;
        try {
            Location gps = null;
            Location net = null;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            Location best = betterLocation(gps, net);
            if (best != null) {
                lastLocation = best;
                lat = best.getLatitude(); lng = best.getLongitude();
                accuracy = best.getAccuracy(); speed = best.getSpeed() * 3.6f;
                updateGpsDisplay();
                log(String.format("📍 Last known location loaded %.6f, %.6f ±%.0fm", lat, lng, accuracy));
            }
        } catch (SecurityException e) {
            log("✕ Last known location blocked by permission");
        } catch (Exception e) {}
    }

    private Location betterLocation(Location a, Location b) {
        if (a == null) return b;
        if (b == null) return a;
        if (b.getTime() > a.getTime() + 120000) return b;
        if (a.getTime() > b.getTime() + 120000) return a;
        return b.getAccuracy() < a.getAccuracy() ? b : a;
    }

    private boolean btReady() {
        if (btAdapter == null) { log("✕ No BT"); return false; }
        if (!btAdapter.isEnabled()) { log("✕ BT off"); startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1); return false; }
        return true;
    }

    private void updateStatus(String s) { mainHandler.post(new Runnable() { public void run() { statusText.setText("⚡ " + s); }}); }
    
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

    private void acquireLock() { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L); }
    private void releaseLock() {
        if (!isScanning && !isTracking && wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
    private void setBtnOn(Button b, String t) { ObUi.setButtonOn(b, t); }
    private void resetBtn(Button b, String t) { ObUi.resetButton(b, t); }
    private int dp(int px) { return ObUi.dp(this, px); }

    private TextView mkLabel(String t, int c, int s) {
        return ObUi.tightLabel(this, t, c, s);
    }

    private Button mkBtnWide(String t) {
        return ObUi.weightedWideButton(this, t);
    }
}
