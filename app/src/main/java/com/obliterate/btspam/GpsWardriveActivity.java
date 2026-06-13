package com.obliterate.btspam;

import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.graphics.Typeface;
import android.location.*;
import android.os.*;
import android.text.method.ScrollingMovementMethod;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * OBLITERATE GPS Wardriving — BT/BLE device mapping with GPS coordinates.
 * Walk or drive around, every device gets tagged with lat/lng.
 * Export as GeoJSON for Google Maps / GIS tools.
 */
public class GpsWardriveActivity extends Activity {

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

    // ── Track Logging ──────────────────────────
    private final ArrayList<TrackPoint> trackPoints = new ArrayList<>();
    private volatile boolean isTracking = false;

    // ── UI ─────────────────────────────────────
    private TextView statusText, gpsText, statsText, logText;
    private Button btnStart, btnStop, btnExportGeo, btnExportTrack, btnBack;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);

    // ── Data classes ───────────────────────────
    private static class MappedDevice {
        String name, address;
        double lat, lng;
        int rssi;
        long timestamp;
        int deviceType; // 0=classic, 1=ble, 2=dual
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

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OB:GPS");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildUI();
        
        // Register BT receiver
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBtReceiver, f);
        
        log("╔══════════════════════════════════╗");
        log("║  OBLITERATE GPS WARDRIVING      ║");
        log("║  Map BT/BLE devices with GPS    ║");
        log("╚══════════════════════════════════╝");
        updateGpsDisplay();
    }

    @Override
    protected void onDestroy() {
        stopAll();
        mainHandler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(mBtReceiver); } catch (Exception e) {}
        try { locationManager.removeUpdates(locationListener); } catch (Exception e) {}
        super.onDestroy();
    }

    // ═══════════════════════════════════════════
    //  GPS LISTENER
    // ═══════════════════════════════════════════

    private LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location loc) {
            lastLocation = loc;
            lat = loc.getLatitude(); lng = loc.getLongitude();
            accuracy = loc.getAccuracy(); speed = loc.getSpeed() * 3.6f; // m/s to km/h
            if (loc.getExtras() != null) satCount = loc.getExtras().getInt("satellites", 0);
            if (isTracking) {
                TrackPoint tp = new TrackPoint();
                tp.lat = lat; tp.lng = lng; tp.speed = speed; tp.accuracy = accuracy;
                tp.timestamp = System.currentTimeMillis();
                trackPoints.add(tp);
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
        root.addView(mkLabel("[ map bt/ble devices with gps coordinates ]", 0xFF888888, 9));

        // Status
        statusText = mkLabel("⚫ IDLE", 0xFF00FF41, 13);
        statusText.setPadding(0, 10, 0, 2);
        root.addView(statusText);

        // GPS display
        gpsText = mkLabel("🔴 NO FIX  ---, ---  --  --km/h", 0xFFFFCC00, 12);
        gpsText.setPadding(0, 2, 0, 8);
        root.addView(gpsText);

        // Stats
        statsText = mkLabel("Devices: 0  Track: 0pts", 0xFF888888, 11);
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
        root.addView(mkLabel("DISCOVERIES:", 0xFF888888, 9));
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
            try { GpsWardriveActivity.class.getDeclaredMethod(action).invoke(GpsWardriveActivity.this); } catch (Exception e) { log("✕ " + e.getMessage()); }
        }});
        return b;
    }

    // ═══════════════════════════════════════════
    //  START / STOP
    // ═══════════════════════════════════════════

    private void startAll() {
        if (!btReady()) return;
        
        // Start GPS
        if (locationManager == null) { log("✕ No location manager"); return; }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
        } catch (SecurityException e) { log("✕ GPS permission denied"); return; }
        
        // Start BT + BLE scan
        isScanning = true;
        setBtnOn(btnStart, "▶ SCANNING");
        log("📍 Wardriving started — GPS + BT/BLE active");
        updateStatus("WARDIVING");
        acquireLock();
        
        startScanCycle();
    }

    private void startScanCycle() {
        if (!isScanning) return;
        if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        btAdapter.startDiscovery();

        if (leScanner != null) {
            if (activeBleScanCb != null) {
                try { leScanner.stopScan(activeBleScanCb); } catch (Exception e) {}
            }
            activeBleScanCb = new BleScanCb();
            BleScanCb cb = activeBleScanCb;
            try {
                leScanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb);
            } catch (SecurityException e) {}
        }

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
        log("📍 Wardriving stopped — " + mappedDevices.size() + " devices, " + trackPoints.size() + " track points");
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
        if (!isScanning || lastLocation == null) return;
        
        String addr = d.getAddress();
        MappedDevice existing;
        synchronized (mappedDevices) { existing = deviceMap.get(addr); }
        
        // Only log if new or position changed significantly
        if (existing != null) {
            double dist = distance(lat, lng, existing.lat, existing.lng);
            if (dist < 20) { // within 20m — update RSSI only
                existing.rssi = rssi;
                existing.timestamp = System.currentTimeMillis();
                return;
            }
            // Remove old, add new sighting at new location
            synchronized (mappedDevices) { mappedDevices.remove(existing); }
        }

        MappedDevice md = new MappedDevice();
        md.address = addr;
        md.name = d.getName() != null ? d.getName() : addr;
        md.lat = lat; md.lng = lng;
        md.rssi = rssi;
        md.timestamp = System.currentTimeMillis();
        md.deviceType = devType;
        
        synchronized (mappedDevices) {
            mappedDevices.add(md);
            deviceMap.put(addr, md);
        }
        
        final String entry = String.format("  📍 %s  %.5f,%.5f  %ddBm  %s",
            md.name, md.lat, md.lng, md.rssi, sdf.format(new Date(md.timestamp)));
        mainHandler.post(new Runnable() { public void run() {
            log(entry);
            updateStats();
        }});
    }

    private void updateStats() {
        final int devs = mappedDevices.size();
        final int track = trackPoints.size();
        mainHandler.post(new Runnable() { public void run() {
            statsText.setText(String.format("Devices: %d  Track: %dpts", devs, track));
        }});
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
                        logDevice(fd, fr, 0); // classic
                    }});
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
                    logDevice(fd, fr, 1); // BLE
                }});
            }
        }
    }

    // ═══════════════════════════════════════════
    //  EXPORT — GeoJSON
    // ═══════════════════════════════════════════

    private void exportGeoJson() {
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
                    fw.write("        \"name\": \"" + md.name.replace("\"", "'") + "\",\n");
                    fw.write("        \"address\": \"" + md.address + "\",\n");
                    fw.write("        \"rssi\": " + md.rssi + ",\n");
                    fw.write("        \"type\": \"" + (md.deviceType == 0 ? "classic" : "ble") + "\",\n");
                    fw.write("        \"time\": \"" + sdf.format(new Date(md.timestamp)) + "\"\n");
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
                    fw.write(String.format("<ele>0</ele><time>%s</time>", sdf.format(new Date(tp.timestamp))));
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

    private double distance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng/2) * Math.sin(dLng/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
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

    private void acquireLock() { if (wakeLock != null) wakeLock.acquire(10 * 60 * 1000L); }
    private void releaseLock() {
        if (!isScanning && !isTracking && wakeLock != null) wakeLock.release();
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
