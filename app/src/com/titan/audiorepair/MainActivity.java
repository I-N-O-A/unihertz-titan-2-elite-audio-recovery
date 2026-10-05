package com.titan.audiorepair;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 10;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder log = new StringBuilder();
    private TextView status;
    private TextView logView;
    private int repairPid = -1;
    private boolean running = false;
    private String pendingCommand = null;

    private final ResultReceiver receiver = new ResultReceiver(handler) {
        @Override protected void onReceiveResult(int resultCode, Bundle data) {
            if (data == null) return;
            if (data.containsKey("pid")) repairPid = data.getInt("pid", -1);
            String msg = data.getString("msg", "");
            if (!msg.isEmpty()) append(msg);
            if (resultCode == RepairService.RESULT_DONE || resultCode == RepairService.RESULT_ERROR) {
                running = false;
                repairPid = -1;
                refreshStatus();
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(250,250,250));
        buildUi();
        refreshStatus();
        append("Titan Audio Repair 1.0 bereit. Keine Root-/ADB-Funktionen.");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Titan Audio Repair");
        title.setTextSize(27);
        title.setTextColor(Color.BLACK);
        title.setPadding(0,0,0,dp(4));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Unihertz Titan 2 Elite • MediaTek Audio/SCP Recovery Lab\nNicht während eines Telefonats ausführen.");
        sub.setTextSize(14);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0,0,0,dp(14));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(Color.rgb(30,30,30));
        status.setBackgroundColor(Color.rgb(238,242,246));
        status.setPadding(dp(12),dp(10),dp(12),dp(10));
        root.addView(status, new LinearLayout.LayoutParams(-1,-2));

        addHeader(root, "Diagnose");
        addButton(root, "Mikrofon prüfen (2 s)", v -> startCommand(RepairService.CMD_MIC_PROBE, true));
        addButton(root, "Lautsprecher-Testton", v -> startCommand(RepairService.CMD_SPEAKER_TEST, false));

        addHeader(root, "Recovery");
        Button safe = addButton(root, "SAFE REPAIR", v -> startCommand(RepairService.CMD_SAFE, true));
        safe.setTextSize(18);
        Button aggressive = addButton(root, "AGGRESSIVE REPAIR", v -> startCommand(RepairService.CMD_AGGRESSIVE, true));
        aggressive.setTextSize(18);
        addButton(root, "Hängenden Repair-Prozess stoppen", v -> killRepairProcess());

        TextView info = new TextView(this);
        info.setText("SAFE: AudioManager zurücksetzen + Input/Output-Streams neu öffnen.\nAGGRESSIVE: zusätzlich Communication-Mode, Speaker/Earpiece-Routing, Full-Duplex, AEC/NS/AGC und AOSP-HAL screen_state-Puls. Keine Calibration/NVRAM-Schreibvorgänge.");
        info.setTextSize(12);
        info.setTextColor(Color.DKGRAY);
        info.setPadding(0,dp(6),0,dp(8));
        root.addView(info);

        addHeader(root, "Unihertz / MediaTek");
        addButton(root, "Factory Test öffnen", v -> openFactory());
        addButton(root, "Engineer-Code im Dialer öffnen", v -> openDialCode("*#*#34635280#*#*"));
        addButton(root, "Android Sound-Einstellungen", v -> safeStart(new Intent(Settings.ACTION_SOUND_SETTINGS)));

        addHeader(root, "Log");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button share = miniButton("Teilen");
        share.setOnClickListener(v -> shareLog());
        Button clear = miniButton("Leeren");
        clear.setOnClickListener(v -> { log.setLength(0); logView.setText(""); });
        Button refresh = miniButton("Status");
        refresh.setOnClickListener(v -> refreshStatus());
        row.addView(share, weight()); row.addView(clear, weight()); row.addView(refresh, weight());
        root.addView(row);

        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextColor(Color.rgb(25,25,25));
        logView.setBackgroundColor(Color.rgb(245,245,245));
        logView.setPadding(dp(10),dp(10),dp(10),dp(10));
        logView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(logView, new LinearLayout.LayoutParams(-1, dp(360)));

        setContentView(scroll);
    }

    private void refreshStatus() {
        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        StringBuilder s = new StringBuilder();
        s.append("Android ").append(android.os.Build.VERSION.RELEASE)
         .append(" / SDK ").append(android.os.Build.VERSION.SDK_INT).append('\n');
        s.append("Gerät: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n');
        s.append("Audio mode: ").append(am.getMode()).append("   Mic mute: ").append(am.isMicrophoneMute()).append('\n');
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            AudioDeviceInfo d = am.getCommunicationDevice();
            s.append("Communication device: ").append(d == null ? "default" : deviceName(d)).append('\n');
        }
        s.append("Repair process: ").append(running ? "läuft (pid " + repairPid + ")" : "idle");
        status.setText(s.toString());
    }

    private void startCommand(String command, boolean needsMic) {
        if (running) { Toast.makeText(this, "Repair läuft bereits", Toast.LENGTH_SHORT).show(); return; }
        if (needsMic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCommand = command;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        Intent i = new Intent(this, RepairService.class);
        i.putExtra("command", command);
        i.putExtra("receiver", receiver);
        running = true;
        repairPid = -1;
        append("▶ " + command);
        startService(i);
        refreshStatus();
        handler.postDelayed(() -> {
            if (running) append("Watchdog: Repair läuft länger als 45 s. Bei Hänger 'Repair-Prozess stoppen' drücken.");
        }, 45000);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            String cmd = pendingCommand; pendingCommand = null;
            if (cmd != null) startCommand(cmd, false);
        } else if (requestCode == REQ_MIC) {
            append("Mikrofon-Berechtigung abgelehnt; Input-Recovery kann nicht laufen.");
        }
    }

    private void killRepairProcess() {
        if (repairPid > 0 && repairPid != Process.myPid()) {
            append("Kille isolierten Repair-Prozess pid=" + repairPid);
            try { Process.killProcess(repairPid); } catch (Throwable t) { append("Kill fehlgeschlagen: " + t); }
            running = false; repairPid = -1; refreshStatus();
        } else append("Kein separater Repair-Prozess bekannt.");
    }

    private void openFactory() {
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.agui.factorytest", "com.agui.factorytest.ModeSelectorActivity"));
        try { startActivity(i); append("Factory ModeSelector geöffnet."); }
        catch (Throwable t) { append("Direktstart Factory nicht möglich: " + t.getClass().getSimpleName()); openDialCode("*#3377#"); }
    }

    private void openDialCode(String code) {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code)));
            startActivity(i);
            append("Dialer mit Code geöffnet: " + code);
        } catch (Throwable t) { append("Dialer konnte nicht geöffnet werden: " + t); }
    }

    private void safeStart(Intent i) {
        try { startActivity(i); } catch (Throwable t) { append("Start fehlgeschlagen: " + t); }
    }

    private void shareLog() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Titan Audio Repair Log");
        i.putExtra(Intent.EXTRA_TEXT, log.toString());
        startActivity(Intent.createChooser(i, "Repair-Log teilen"));
    }

    private void append(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY).format(new Date());
        log.append(ts).append("  ").append(msg).append('\n');
        if (logView != null) {
            logView.setText(log.toString());
            logView.post(() -> {
                if (logView.getLayout() != null) {
                    int y = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
                    logView.scrollTo(0, Math.max(0, y));
                }
            });
        }
    }

    private static String deviceName(AudioDeviceInfo d) { return typeName(d.getType()) + "#" + d.getId(); }
    private static String typeName(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "EARPIECE";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "SPEAKER";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "MIC";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "BT_SCO";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "BT_A2DP";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "WIRED_HEADSET";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "WIRED_HEADPHONES";
            default: return "type" + t;
        }
    }

    private Button addButton(LinearLayout root, String text, View.OnClickListener l) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.topMargin = dp(7);
        root.addView(b, lp); return b;
    }
    private Button miniButton(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setMinHeight(dp(46)); return b; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1); lp.setMargins(dp(2),0,dp(2),0); return lp; }
    private void addHeader(LinearLayout root, String text) { TextView v = new TextView(this); v.setText(text); v.setTextSize(17); v.setTextColor(Color.BLACK); v.setGravity(Gravity.START); v.setPadding(0,dp(18),0,dp(2)); root.addView(v); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
