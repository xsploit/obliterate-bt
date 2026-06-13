package com.obliterate.btspam;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import android.text.method.ScrollingMovementMethod;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * OBLITERATE Ghost ESP Bridge — Phone as screen/controller for Ghost ESP on ESP32-S3.
 * Connects to Ghost ESP WiFi (GhostNet / GhostNet) and sends commands via HTTP API.
 * Endpoint: POST http://192.168.4.1/api/command  body: {"command": "scanap"}
 * Logs:     GET  http://192.168.4.1/api/logs
 */
public class EspControlActivity extends Activity {

    private static final String GHOST_IP = "192.168.4.1";
    private static final String GHOST_SSID = "GhostNet";
    private static final String GHOST_PASS = "GhostNet";
    private static final String API_COMMAND = "/api/command";
    private static final String API_LOGS = "/api/logs";

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread logPoller;
    private volatile boolean destroyed = false;

    // UI
    private TextView statusText, wifiText, logText;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check WiFi connection
        String currentSSID = getCurrentSSID();
        boolean onGhostNet = GHOST_SSID.equals(currentSSID);

        buildUI(onGhostNet, currentSSID);

        if (onGhostNet) {
            log("✓ Connected to GhostNet");
            log("  Ghost ESP API: http://" + GHOST_IP + API_COMMAND);
            log("  Ready. Select a command.");
            startLogPoller();
        } else {
            log("⚠ NOT connected to GhostNet WiFi");
            log("  Current: " + (currentSSID != null ? currentSSID : "unknown"));
            log("  Connect manually: Settings → WiFi → GhostNet");
            log("  Password: GhostNet");
            log("  Then come back to this page.");
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        stopLogPoller();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ═══════════════════════════════════════════
    //  UI
    // ═══════════════════════════════════════════

    private void buildUI(boolean connected, String currentSSID) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0A);
        root.setPadding(16, 48, 16, 24);

        root.addView(mkLabel("📡  ESP32 CONTROL", 0xFFFF2222, 22));
        root.addView(mkLabel("[ ghost esp http bridge ]", 0xFF888888, 11));

        // Status
        String status = connected ? "🟢 CONNECTED" : "🔴 NOT CONNECTED";
        statusText = mkLabel(status, connected ? 0xFF00FF41 : 0xFFFF2222, 14);
        statusText.setPadding(0, 12, 0, 4);
        root.addView(statusText);

        wifiText = mkLabel("WiFi: " + (currentSSID != null ? currentSSID : "unknown"), 0xFF888888, 10);
        root.addView(wifiText);

        // Separator
        root.addView(mkLabel("▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔", 0xFF333333, 8));
        root.addView(mkLabel("COMMANDS:", 0xFFFFCC00, 10));

        // Row 1: Scan + List
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(mkCmdBtn("📶 SCAN APs", "scanap"));
        r1.addView(mkCmdBtn("📋 LIST APs", "list -a"));
        r1.addView(mkCmdBtn("🛑 STOP SCAN", "stopscan"));
        root.addView(r1);

        // Row 2: Deauth
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(mkCmdBtn("💀 DEAUTH", "attack -d"));
        r2.addView(mkCmdBtn("🛑 STOP DEAUTH", "stopdeauth"));
        r2.addView(mkCmdBtn("📡 BEACON -r", "beaconspam -r"));
        root.addView(r2);

        // Row 3: Beacon custom + RR
        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        r3.addView(mkCmdBtn("📡 BEACON -rr", "beaconspam -rr"));
        r3.addView(mkCmdBtn("📡 BEACON LIST", "beaconspam -l"));
        r3.addView(mkCmdBtn("🛑 STOP SPAM", "stopspam"));
        root.addView(r3);

        // Beacon custom SSID
        LinearLayout rBeaconCustom = new LinearLayout(this);
        rBeaconCustom.setOrientation(LinearLayout.HORIZONTAL);
        final EditText beaconSSID = new EditText(this);
        beaconSSID.setText("OBLITERATE_FreeWiFi");
        beaconSSID.setTextColor(0xFFE0E0E0);
        beaconSSID.setBackgroundColor(0xFF1A1A1A);
        beaconSSID.setTypeface(Typeface.MONOSPACE);
        beaconSSID.setTextSize(11);
        beaconSSID.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
        bp.setMargins(4, 6, 4, 6);
        beaconSSID.setLayoutParams(bp);
        rBeaconCustom.addView(beaconSSID);
        Button btnBeaconCustom = mkBtnWide("📡 SEND");
        btnBeaconCustom.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            sendCmd("beaconspam " + beaconSSID.getText().toString().trim());
        }});
        rBeaconCustom.addView(btnBeaconCustom);
        root.addView(rBeaconCustom);

        // Row 4: BLE
        LinearLayout r4 = new LinearLayout(this);
        r4.setOrientation(LinearLayout.HORIZONTAL);
        r4.addView(mkCmdBtn("📶 BLE SCAN -f", "blescan -f"));
        r4.addView(mkCmdBtn("📶 BLE SCAN -a", "blescan -a"));
        r4.addView(mkCmdBtn("📶 BLE SCAN -r", "blescan -r"));
        root.addView(r4);

        // Row 5: Evil Portal
        LinearLayout r5 = new LinearLayout(this);
        r5.setOrientation(LinearLayout.HORIZONTAL);
        r5.addView(mkCmdBtn("🕸 START PORTAL", "startportal https://example.com MyWiFi pass123 \"Free WiFi\" login.com"));
        r5.addView(mkCmdBtn("🛑 STOP PORTAL", "stopportal"));
        root.addView(r5);

        // Evil Portal custom form
        LinearLayout rPortal = new LinearLayout(this);
        rPortal.setOrientation(LinearLayout.HORIZONTAL);
        final EditText portalName = new EditText(this);
        portalName.setText("Free WiFi");
        portalName.setTextColor(0xFFE0E0E0);
        portalName.setBackgroundColor(0xFF1A1A1A);
        portalName.setTypeface(Typeface.MONOSPACE);
        portalName.setTextSize(10);
        portalName.setPadding(4, 8, 4, 8);
        portalName.setHint("Portal name");
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pp.setMargins(2, 4, 2, 4);
        portalName.setLayoutParams(pp);
        rPortal.addView(portalName);
        final EditText portalDomain = new EditText(this);
        portalDomain.setText("login.com");
        portalDomain.setTextColor(0xFFE0E0E0);
        portalDomain.setBackgroundColor(0xFF1A1A1A);
        portalDomain.setTypeface(Typeface.MONOSPACE);
        portalDomain.setTextSize(10);
        portalDomain.setPadding(4, 8, 4, 8);
        portalDomain.setHint("Domain");
        portalDomain.setLayoutParams(pp);
        rPortal.addView(portalDomain);
        Button btnPortalGo = mkBtnWide("🕸 GO");
        btnPortalGo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String cmd = "startportal https://example.com " + 
                GHOST_SSID + " " + GHOST_PASS + " \"" + 
                portalName.getText().toString().trim() + "\" " + 
                portalDomain.getText().toString().trim();
            sendCmd(cmd);
        }});
        rPortal.addView(btnPortalGo);
        root.addView(rPortal);

        // Row 6: Capture
        LinearLayout r6 = new LinearLayout(this);
        r6.setOrientation(LinearLayout.HORIZONTAL);
        r6.addView(mkCmdBtn("📦 CAP PROBE", "capture -probe"));
        r6.addView(mkCmdBtn("📦 CAP DEAUTH", "capture -deauth"));
        r6.addView(mkCmdBtn("📦 CAP RAW", "capture -raw"));
        root.addView(r6);

        // Row 7: Capture more
        LinearLayout r7 = new LinearLayout(this);
        r7.setOrientation(LinearLayout.HORIZONTAL);
        r7.addView(mkCmdBtn("📦 CAP EAPOL", "capture -eapol"));
        r7.addView(mkCmdBtn("📦 CAP WPS", "capture -wps"));
        r7.addView(mkCmdBtn("🛑 CAP STOP", "capture -stop"));
        root.addView(r7);

        // Row 8: Status + Control
        LinearLayout r8 = new LinearLayout(this);
        r8.setOrientation(LinearLayout.HORIZONTAL);
        r8.addView(mkCmdBtn("📊 HELP", "help"));
        r8.addView(mkCmdBtn("🛑 STOP ALL", "stop"));
        r8.addView(mkCmdBtn("♻ REBOOT", "reboot"));
        root.addView(r8);

        // Custom command
        LinearLayout rCustom = new LinearLayout(this);
        rCustom.setOrientation(LinearLayout.HORIZONTAL);
        final EditText customCmd = new EditText(this);
        customCmd.setTextColor(0xFFE0E0E0);
        customCmd.setBackgroundColor(0xFF1A1A1A);
        customCmd.setTypeface(Typeface.MONOSPACE);
        customCmd.setTextSize(11);
        customCmd.setPadding(12, 10, 12, 10);
        customCmd.setHint("custom ghost esp command...");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
        cp.setMargins(4, 6, 4, 6);
        customCmd.setLayoutParams(cp);
        rCustom.addView(customCmd);
        Button btnCustomSend = mkBtnWide("▶ SEND");
        btnCustomSend.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String cmd = customCmd.getText().toString().trim();
            if (cmd.length() > 0) { sendCmd(cmd); customCmd.setText(""); }
        }});
        rCustom.addView(btnCustomSend);
        root.addView(rCustom);

        // Separator
        root.addView(mkLabel("▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔", 0xFF333333, 8));

        // Back button
        btnBack = mkBtn("← BACK TO OBLITERATE");
        btnBack.setTextColor(0xFF888888);
        btnBack.setBackgroundColor(0xFF1A1A1A);
        btnBack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); }});
        root.addView(btnBack);

        // Terminal log
        root.addView(mkLabel("TERMINAL:", 0xFF888888, 10));
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
            LinearLayout.LayoutParams.MATCH_PARENT, dp(180)));
        root.addView(logText);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0A0A0A);
        sv.addView(root);
        sv.setFillViewport(true);
        setContentView(sv);
    }

    // ═══════════════════════════════════════════
    //  SEND COMMAND (HTTP POST)
    // ═══════════════════════════════════════════

    private void sendCmd(final String command) {
        log("  → " + command);
        updateStatus("⚡ SENDING...");
        new Thread(new Runnable() { public void run() {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://" + GHOST_IP + API_COMMAND);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{\"command\":\"" + escapeJson(command) + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    // Read response
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    final String resp = br.readLine();
                    br.close();
                    mainHandler.post(new Runnable() { public void run() {
                        if (destroyed) return;
                        log("  ← " + (resp != null ? resp : "OK"));
                        updateStatus("✓ DONE");
                        // Trigger immediate log fetch
                        fetchLogs();
                    }});
                } else {
                    mainHandler.post(new Runnable() { public void run() {
                        if (destroyed) return;
                        log("  ✕ HTTP " + code);
                        updateStatus("✕ HTTP " + code);
                    }});
                }
            } catch (Exception e) {
                final String msg = e.getMessage();
                mainHandler.post(new Runnable() { public void run() {
                    if (destroyed) return;
                    log("  ✕ " + (msg != null ? msg : "Connection failed"));
                    log("  Make sure WiFi is connected to GhostNet");
                    updateStatus("✕ OFFLINE");
                }});
            } finally {
                if (conn != null) conn.disconnect();
            }
        }}).start();
    }

    // ═══════════════════════════════════════════
    //  LOG POLLER
    // ═══════════════════════════════════════════

    private void startLogPoller() {
        stopLogPoller();
        logPoller = new Thread(new Runnable() { public void run() {
            String lastLogs = "";
            while (!Thread.currentThread().isInterrupted() && !destroyed) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                try {
                    URL url = new URL("http://" + GHOST_IP + API_LOGS);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    conn.setRequestMethod("GET");

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append("\n");
                        br.close();

                        final String logs = sb.toString();
                        if (!logs.equals(lastLogs) && logs.length() > 0) {
                            lastLogs = logs;
                            // Only show new lines
                            String[] lines = logs.split("\n");
                            int show = Math.min(lines.length, 20); // last 20 lines
                            final StringBuilder newLines = new StringBuilder();
                            for (int i = lines.length - show; i < lines.length; i++) {
                                if (lines[i].trim().length() > 0) {
                                    newLines.append("  ").append(lines[i].trim()).append("\n");
                                }
                            }
                            if (newLines.length() > 0) {
                                mainHandler.post(new Runnable() { public void run() {
                                    if (destroyed) return;
                                    log(newLines.toString().trim());
                                }});
                            }
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) { /* poll silently */ }
            }
        }});
        logPoller.setDaemon(true);
        logPoller.start();
    }

    private void stopLogPoller() {
        if (logPoller != null) { logPoller.interrupt(); logPoller = null; }
    }

    private void fetchLogs() {
        new Thread(new Runnable() { public void run() {
            try {
                URL url = new URL("http://" + GHOST_IP + API_LOGS);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.trim().length() > 0) {
                            final String l = line.trim();
                            mainHandler.post(new Runnable() { public void run() {
                                if (destroyed) return;
                                log("  " + l);
                            }});
                        }
                    }
                    br.close();
                }
                conn.disconnect();
            } catch (Exception e) {}
        }}).start();
    }

    // ═══════════════════════════════════════════
    //  WIFI CHECK
    // ═══════════════════════════════════════════

    private String getCurrentSSID() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null) {
                    String ssid = info.getSSID();
                    if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid = ssid.substring(1, ssid.length() - 1);
                    }
                    return ssid;
                }
            }
        } catch (Exception e) {}
        return null;
    }

    // ═══════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private void updateStatus(String s) {
        statusText.setText(s);
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

    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density); }

    private TextView mkLabel(String txt, int color, int size) {
        TextView tv = new TextView(this);
        tv.setText(txt); tv.setTextColor(color); tv.setTextSize(size);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(0, 8, 0, 3);
        return tv;
    }

    private Button mkCmdBtn(String txt, final String cmd) {
        Button b = new Button(this);
        b.setText(txt); b.setTextColor(0xFFFF2222);
        b.setBackgroundColor(0xFF1A1A1A); b.setTypeface(Typeface.MONOSPACE);
        b.setTextSize(10); b.setAllCaps(true);
        b.setPadding(4, 14, 4, 14); b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(3, 5, 3, 5);
        b.setLayoutParams(p);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            sendCmd(cmd);
        }});
        return b;
    }

    private Button mkBtnWide(String txt) {
        Button b = new Button(this);
        b.setText(txt); b.setTextColor(0xFFFF2222);
        b.setBackgroundColor(0xFF1A1A1A); b.setTypeface(Typeface.MONOSPACE);
        b.setTextSize(10); b.setAllCaps(true);
        b.setPadding(8, 14, 8, 14); b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(4, 6, 4, 6);
        b.setLayoutParams(p);
        return b;
    }

    private Button mkBtn(String txt) {
        Button b = new Button(this);
        b.setText(txt); b.setTextColor(0xFFFF2222);
        b.setBackgroundColor(0xFF1A1A1A); b.setTypeface(Typeface.MONOSPACE);
        b.setTextSize(11); b.setAllCaps(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 6, 0, 6);
        b.setLayoutParams(p); b.setPadding(16, 14, 16, 14);
        return b;
    }
}
