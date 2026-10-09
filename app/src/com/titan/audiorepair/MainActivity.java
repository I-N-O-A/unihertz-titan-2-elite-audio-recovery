package com.titan.audiorepair;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.RingtoneManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
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
    private static final int REQ_NOTIFICATION = 11;
    private static final String AUDIO_CHANNEL = "titan_audio_test_v14";
    private static final int AUDIO_NOTIFICATION_ID = 1414;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder log = new StringBuilder();

    private TextView status;
    private TextView faultView;
    private TextView logView;
    private Button powerButton;

    private int repairPid = -1;
    private boolean running = false;
    private boolean audioFaultConfirmed = false;
    private String pendingCommand = null;

    private final Runnable watchdogRunnable = () -> {
        if (running) append("Watchdog: Vorgang läuft länger als 45 s. Bei Hänger 'Repair-Prozess stoppen' drücken.");
    };

    private final ResultReceiver receiver = new ResultReceiver(handler) {
        @Override protected void onReceiveResult(int resultCode, Bundle data) {
            if (data == null) return;
            if (data.containsKey("pid")) repairPid = data.getInt("pid", -1);
            String msg = data.getString("msg", "");
            if (!msg.isEmpty()) append(msg);

            if (resultCode == RepairService.RESULT_FAULT) {
                audioFaultConfirmed = true;
                showConfirmedFault();
            }
            if (resultCode == RepairService.RESULT_MIC_ACTIVE) {
                audioFaultConfirmed = false;
                faultView.setVisibility(View.GONE);
                append("✓ Mikrofon wieder aktiv; Lautsprecher bitte separat auf Hörbarkeit prüfen.");
                refreshStatus();
            }

            if (resultCode == RepairService.RESULT_DONE || resultCode == RepairService.RESULT_ERROR) {
                running = false;
                repairPid = -1;
                handler.removeCallbacks(watchdogRunnable);
                refreshStatus();
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(250,250,250));
        buildUi();
        refreshStatus();
        append("Titan Audio Repair 1.4 bereit. UI-Soft-Reset und Notification-Audio-Test.");
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
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
        sub.setText("Unihertz Titan 2 Elite\nBekannter Fehler: Mic + Speaker können komplett ausfallen. Nur Neustart ist bisher bestätigt.");
        sub.setTextSize(14);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0,0,0,dp(12));
        root.addView(sub);

        faultView = new TextView(this);
        faultView.setTextSize(15);
        faultView.setTextColor(Color.WHITE);
        faultView.setBackgroundColor(Color.rgb(183,28,28));
        faultView.setPadding(dp(12),dp(12),dp(12),dp(12));
        faultView.setVisibility(View.GONE);
        root.addView(faultView, new LinearLayout.LayoutParams(-1,-2));

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(Color.rgb(30,30,30));
        status.setBackgroundColor(Color.rgb(238,242,246));
        status.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1,-2);
        statusLp.topMargin = dp(8);
        root.addView(status, statusLp);

        addHeader(root, "Diagnose");
        addButton(root, "Mikrofon prüfen (2 s)", v -> startCommand(RepairService.CMD_MIC_PROBE, true));
        addButton(root, "Lautsprecher-Testton", v -> startCommand(RepairService.CMD_SPEAKER_TEST, false));

        addHeader(root, "Soft-Recovery (ohne Reboot)");
        addButton(root, "BENACHRICHTIGUNGSTON → MIC TEST", v -> notificationAudioTest());
        addButton(root, "ANDROID SOFT-RESET (UI + TON)", v -> uiSoftReset());
        TextView softNote = new TextView(this);
        softNote.setText("Testet den Android-Benachrichtigungstonpfad und danach das Mikrofon. UI-Soft-Reset aktualisiert nur die Android-Systemleisten; SystemUI-Prozess und Handy werden NICHT neu gestartet.");
        softNote.setTextSize(12);
        softNote.setTextColor(Color.DKGRAY);
        softNote.setPadding(0, dp(6), 0, dp(8));
        root.addView(softNote);

        addHeader(root, "Bestätigte Wiederherstellung");
        powerButton = addButton(root, "POWER-MENÜ ÖFFNEN → NEU STARTEN", v -> openPowerMenu());
        powerButton.setTextSize(17);

        TextView rebootInfo = new TextView(this);
        rebootInfo.setText("Die bisherigen App-Level-Resets reparieren den echten MediaTek-Audiofehler nicht. Ein vollständiger Neustart ist derzeit die einzige bestätigte Wiederherstellung.");
        rebootInfo.setTextSize(12);
        rebootInfo.setTextColor(Color.DKGRAY);
        rebootInfo.setPadding(0,dp(6),0,dp(8));
        root.addView(rebootInfo);

        addHeader(root, "MediaTek Recovery (neu, gezielt)");
        Button mtk = addButton(root, "MTK VENDOR REINIT", v -> startCommand(RepairService.CMD_MTK_VENDOR, true));
        mtk.setTextSize(17);

        TextView mtkInfo = new TextView(this);
        mtkInfo.setText("Neu in 1.3: sendet MediaTeks restarting=true/false-Signal und erzwingt – falls ein zweites physisches Input-Gerät verbunden ist – einen echten Hardware-Routing-Roundtrip, bevor Mic + Speaker automatisch geprüft werden.");
        mtkInfo.setTextSize(12);
        mtkInfo.setTextColor(Color.DKGRAY);
        mtkInfo.setPadding(0,dp(6),0,dp(8));
        root.addView(mtkInfo);

        addHeader(root, "Alte Experimente / Diagnose");
        Button safe = addButton(root, "SAFE REPAIR testen", v -> startCommand(RepairService.CMD_SAFE, true));
        safe.setTextSize(16);
        Button aggressive = addButton(root, "AGGRESSIVE REPAIR testen", v -> startCommand(RepairService.CMD_AGGRESSIVE, true));
        aggressive.setTextSize(16);
        addButton(root, "Hängenden Repair-Prozess stoppen", v -> killRepairProcess());

        TextView info = new TextView(this);
        info.setText("SAFE/AGGRESSIVE bleiben für Diagnose und weitere Forschung erhalten. Sie haben den bestätigten Fehler bisher nicht behoben. Keine Calibration/NVRAM-Schreibvorgänge.");
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
        row.addView(share, weight());
        row.addView(clear, weight());
        row.addView(refresh, weight());
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

    private void showConfirmedFault() {
        faultView.setText("⛔ AUDIO-FEHLER BESTÄTIGT\nAndroid liefert PCM-Buffer, aber nur Null-Samples. Soft-Recovery testen oder Neustart.");
        faultView.setVisibility(View.VISIBLE);
        powerButton.setText("POWER-MENÜ ÖFFNEN → NEU STARTEN");
        refreshStatus();
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
        s.append("Audio fault: ").append(audioFaultConfirmed ? "BESTÄTIGT" : "nicht bestätigt").append('\n');
        s.append("Power-Menü Service: ").append(PowerMenuAccessibilityService.isConnected() ? "bereit" : "nicht aktiviert").append('\n');
        s.append("Repair process: ").append(running ? "läuft (pid " + repairPid + ")" : "idle");
        status.setText(s.toString());
    }

    private void startCommand(String command, boolean needsMic) {
        if (running) {
            Toast.makeText(this, "Vorgang läuft bereits", Toast.LENGTH_SHORT).show();
            return;
        }
        if (needsMic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingCommand = command;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }

        handler.removeCallbacks(watchdogRunnable);

        Intent i = new Intent(this, RepairService.class);
        i.putExtra("command", command);
        i.putExtra("receiver", receiver);
        running = true;
        repairPid = -1;
        append("▶ " + command);
        startService(i);
        refreshStatus();
        handler.postDelayed(watchdogRunnable, 45000);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                notificationAudioTest();
            else append("Keine Benachrichtigungsfreigabe; Audio-Tontest nicht möglich.");
            return;
        }
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            String cmd = pendingCommand;
            pendingCommand = null;
            if (cmd != null) startCommand(cmd, false);
        } else if (requestCode == REQ_MIC) {
            append("Mikrofon-Berechtigung abgelehnt; Input-Diagnose kann nicht laufen.");
        }
    }

    private void openPowerMenu() {
        if (PowerMenuAccessibilityService.openPowerMenu()) {
            append("System-Power-Menü geöffnet. Bitte 'Neu starten' wählen.");
            return;
        }

        append("Power-Menü-Service noch nicht aktiviert.");
        new AlertDialog.Builder(this)
                .setTitle("Power-Menü einmalig freigeben")
                .setMessage("Android erlaubt normalen Apps keinen direkten Neustart. Optional kann Titan Audio Repair über einen minimalen Accessibility-Service nur das System-Power-Menü öffnen. Der Service liest keine Fensterinhalte.\n\nAktiviere dort 'Titan Audio Repair – Power Menu' und kehre dann zurück.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Accessibility öffnen", (d, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Throwable t) {
                        append("Accessibility-Einstellungen konnten nicht geöffnet werden: " + t);
                    }
                })
                .show();
    }


    /**
     * Replays the notification-system sound path (not AudioTrack/USAGE_MEDIA).
     * Success means the notification was posted; it does not prove audibility.
     */
    private void notificationAudioTest() {
        if (running) {
            append("Audio-Tontest: Repair-Vorgang läuft noch.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            return;
        }
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null || !nm.areNotificationsEnabled()) {
                append("Benachrichtigungen für Titan Audio Repair bitte in Android aktivieren.");
                return;
            }
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            NotificationChannel channel = new NotificationChannel(AUDIO_CHANNEL,
                    "Audio-Recovery-Tontest", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Manuell ausgelöster Benachrichtigungston zum Audio-Recovery-Test");
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attributes);
            channel.enableVibration(false);
            nm.createNotificationChannel(channel);

            NotificationChannel effective = nm.getNotificationChannel(AUDIO_CHANNEL);
            if (effective == null || effective.getImportance() < NotificationManager.IMPORTANCE_DEFAULT ||
                    effective.getSound() == null) {
                append("Benachrichtigungskanal ist lautlos/deaktiviert. In Android Ton erlauben.");
                return;
            }
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL)
                append("⚠ Lautlos-/Vibrationsmodus aktiv: Ton eventuell unterdrückt.");
            if (nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL)
                append("⚠ Bitte-nicht-stören/Benachrichtigungsfilter könnte den Ton blockieren.");

            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap = PendingIntent.getActivity(this, AUDIO_NOTIFICATION_ID, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, AUDIO_CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("Titan Audio Repair: Tonimpuls")
                    .setContentText("Test des Android-Benachrichtigungsaudios")
                    .setContentIntent(tap)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .build();
            nm.notify(AUDIO_NOTIFICATION_ID, notification);
            append("▶ Android-Benachrichtigung gepostet (USAGE_NOTIFICATION).");
            handler.postDelayed(() -> nm.cancel(AUDIO_NOTIFICATION_ID), 8000);
            handler.postDelayed(() -> {
                append("Tonimpuls abgeschlossen. Prüfe jetzt Mikrofon per PCM.");
                startCommand(RepairService.CMD_MIC_PROBE, true);
            }, 2600);
        } catch (Throwable t) {
            append("Benachrichtigungston-Test fehlgeschlagen: " + t);
        }
    }

    /** UI chrome refresh only; neither SystemUI nor the device is restarted. */
    private void uiSoftReset() {
        if (running) {
            append("UI-Soft-Reset: Repair-Vorgang läuft noch.");
            return;
        }
        if (!PowerMenuAccessibilityService.softRefreshUi()) {
            append("Accessibility-Dienst fehlt oder Android hat die UI-Aktion abgelehnt.");
            new AlertDialog.Builder(this)
                    .setTitle("UI-Soft-Reset aktivieren")
                    .setMessage("Bedienungshilfe für Titan Audio Repair aktivieren. Der Dienst öffnet und schließt nur die Systemleisten und liest keine Fensterinhalte.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Bedienungshilfen öffnen", (dialog,which) ->
                            safeStart(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .show();
            return;
        }
        append("▶ UI-Soft-Refresh: Systemleisten werden geöffnet und wieder geschlossen.");
        append("Wichtig: Kein Neustart von SystemUI, Audio-HAL oder Android.");
        handler.postDelayed(this::notificationAudioTest, 2400);
    }

    private void killRepairProcess() {
        handler.removeCallbacks(watchdogRunnable);
        if (repairPid > 0 && repairPid != Process.myPid()) {
            append("Kille isolierten Repair-Prozess pid=" + repairPid);
            try { Process.killProcess(repairPid); } catch (Throwable t) { append("Kill fehlgeschlagen: " + t); }
            running = false;
            repairPid = -1;
            refreshStatus();
        } else {
            append("Kein separater Repair-Prozess bekannt.");
        }
    }

    private void openFactory() {
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.agui.factorytest", "com.agui.factorytest.ModeSelectorActivity"));
        try {
            startActivity(i);
            append("Factory ModeSelector geöffnet.");
        } catch (Throwable t) {
            append("Direktstart Factory nicht möglich: " + t.getClass().getSimpleName());
            openDialCode("*#3377#");
        }
    }

    private void openDialCode(String code) {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code)));
            startActivity(i);
            append("Dialer mit Code geöffnet: " + code);
        } catch (Throwable t) {
            append("Dialer konnte nicht geöffnet werden: " + t);
        }
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
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(7);
        root.addView(b, lp);
        return b;
    }

    private Button miniButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(dp(46));
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1);
        lp.setMargins(dp(2),0,dp(2),0);
        return lp;
    }

    private void addHeader(LinearLayout root, String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(17);
        v.setTextColor(Color.BLACK);
        v.setGravity(Gravity.START);
        v.setPadding(0,dp(18),0,dp(2));
        root.addView(v);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
