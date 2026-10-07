package com.titan.audiorepair;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.SystemClock;

import java.util.List;
import java.util.Locale;

public class RepairService extends Service {
    public static final String CMD_MIC_PROBE = "MIC PROBE";
    public static final String CMD_SPEAKER_TEST = "SPEAKER TEST";
    public static final String CMD_SAFE = "SAFE REPAIR";
    public static final String CMD_AGGRESSIVE = "AGGRESSIVE REPAIR";
    public static final int RESULT_PROGRESS = 1;
    public static final int RESULT_DONE = 2;
    public static final int RESULT_ERROR = 3;
    public static final int RESULT_FAULT = 4;

    private ResultReceiver rr;
    private AudioManager am;
    private AudioFocusRequest focusRequest;

    @Override public void onCreate() {
        super.onCreate();
        am = (AudioManager)getSystemService(AUDIO_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        rr = (ResultReceiver) intent.getParcelableExtra("receiver");
        String cmd = intent.getStringExtra("command");
        send(RESULT_PROGRESS, "Repair-Engine pid=" + Process.myPid() + " gestartet", true);
        new Thread(() -> {
            try {
                if (CMD_MIC_PROBE.equals(cmd)) micProbe();
                else if (CMD_SPEAKER_TEST.equals(cmd)) speakerTest();
                else if (CMD_SAFE.equals(cmd)) safeRepair();
                else if (CMD_AGGRESSIVE.equals(cmd)) aggressiveRepair();
                else throw new IllegalArgumentException("Unbekannter Befehl: " + cmd);
                send(RESULT_DONE, "✓ " + cmd + " beendet", false);
            } catch (Throwable t) {
                send(RESULT_ERROR, "✗ Abbruch: " + t.getClass().getSimpleName() + ": " + t.getMessage(), false);
            } finally {
                try { am.clearCommunicationDevice(); } catch (Throwable ignored) {}
                try { am.setMode(AudioManager.MODE_NORMAL); } catch (Throwable ignored) {}
                stopSelf(startId);
            }
        }, "TitanAudioRepair").start();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void safeRepair() {
        log("SAFE 1/4: Framework-Zustand normalisieren");
        normalizeFramework();
        nap(500);

        log("SAFE 2/4: SoundEffects unload/load");
        try {
            am.unloadSoundEffects();
            nap(250);
            am.loadSoundEffects();
            nap(500);
            log("SoundEffects neu geladen");
        } catch (Throwable t) {
            log("SoundEffects: " + shortErr(t));
        }

        log("SAFE 3/4: Capture-Pfade seriell neu öffnen");
        int[] sources = new int[]{
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.VOICE_PERFORMANCE
        };
        for (int s : sources) {
            exerciseInput(s, 16000, false, 280);
            nap(260);
        }
        exerciseInput(MediaRecorder.AudioSource.MIC, 48000, false, 320);
        nap(400);

        log("SAFE 4/4: Playback-Pfade seriell neu öffnen");
        exerciseOutput(AudioAttributes.USAGE_MEDIA, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, false, 300);
        nap(300);
        exerciseOutput(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, false, 300);
        nap(300);
        exerciseOutput(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, false, 300);
        normalizeFramework();
    }

    private void aggressiveRepair() {
        log("AGGRESSIVE: beginnt mit SAFE-Sequenz");
        safeRepair();
        nap(700);

        log("AGG 1/5: Audio-Focus exklusiv pulsen");
        pulseFocus();
        nap(350);

        log("AGG 2/5: Mic-Mute kurz pulsen und zurücksetzen");
        boolean oldMute = false;
        try {
            oldMute = am.isMicrophoneMute();
            am.setMicrophoneMute(true);
            nap(180);
            am.setMicrophoneMute(false);
            nap(350);
            if (oldMute) am.setMicrophoneMute(true);
            log("Mic-Mute-Puls ok; vorher=" + oldMute);
        } catch (Throwable t) {
            log("Mic-Mute-Puls: " + shortErr(t));
        }

        log("AGG 3/5: Communication-Mode + Speaker/Earpiece Full-Duplex");
        try {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            log("MODE_IN_COMMUNICATION gesetzt");
        } catch (Throwable t) {
            log("setMode(IN_COMMUNICATION): " + shortErr(t));
        }
        nap(450);
        duplexOnType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 850);
        nap(650);
        duplexOnType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, 850);
        nap(650);
        try { am.clearCommunicationDevice(); } catch (Throwable ignored) {}
        try {
            am.setMode(AudioManager.MODE_NORMAL);
            log("MODE_NORMAL gesetzt");
        } catch (Throwable t) {
            log("setMode(NORMAL): " + shortErr(t));
        }
        nap(500);

        log("AGG 4/5: Capture mit AEC/NS/AGC neu erzeugen");
        exerciseInput(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, true, 700);
        nap(550);
        exerciseInput(MediaRecorder.AudioSource.VOICE_RECOGNITION, 48000, true, 700);
        nap(550);

        log("AGG 5/5: AOSP-HAL screen_state Puls (experimentell)");
        pulseScreenStateParameter();
        nap(700);
        normalizeFramework();
        log("AGGRESSIVE fertig. Jetzt Mic-Probe + Lautsprecher-Test ausführen.");
    }

    private void normalizeFramework() {
        try { am.clearCommunicationDevice(); log("clearCommunicationDevice()"); }
        catch (Throwable t) { log("clearCommunicationDevice: " + shortErr(t)); }

        try { am.setSpeakerphoneOn(false); log("legacy speakerphone OFF"); }
        catch (Throwable t) { log("setSpeakerphoneOn: " + shortErr(t)); }

        try { am.setMode(AudioManager.MODE_NORMAL); log("MODE_NORMAL"); }
        catch (Throwable t) { log("MODE_NORMAL: " + shortErr(t)); }

        try { am.setMicrophoneMute(false); log("setMicrophoneMute(false)"); }
        catch (Throwable t) { log("unmute mic: " + shortErr(t)); }

        try {
            if (focusRequest != null) am.abandonAudioFocusRequest(focusRequest);
            am.abandonAudioFocus(null);
        } catch (Throwable ignored) {}
    }

    private void pulseFocus() {
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(aa)
                    .setAcceptsDelayedFocusGain(false)
                    .build();
            int r = am.requestAudioFocus(focusRequest);
            log("requestAudioFocus=" + r);
            nap(250);
            log("abandonAudioFocus=" + am.abandonAudioFocusRequest(focusRequest));
        } catch (Throwable t) {
            log("AudioFocus: " + shortErr(t));
        }
    }

    private void exerciseInput(int source, int rate, boolean effects, int durationMs) {
        AudioRecord rec = null;
        AcousticEchoCanceler aec = null;
        NoiseSuppressor ns = null;
        AutomaticGainControl agc = null;
        try {
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min > 0 ? min * 2 : rate / 2, 4096);

            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            rec = new AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(size)
                    .build();

            AudioDeviceInfo mic = findDevice(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioManager.GET_DEVICES_INPUTS);
            if (mic != null) {
                try {
                    log("Input source=" + sourceName(source) + " rate=" + rate
                            + " preferredMic=" + rec.setPreferredDevice(mic));
                } catch (Throwable ignored) {}
            } else {
                log("Input source=" + sourceName(source) + " rate=" + rate);
            }

            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                log("  AudioRecord nicht initialisiert");
                return;
            }

            if (effects) {
                int session = rec.getAudioSessionId();
                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        aec = AcousticEchoCanceler.create(session);
                        if (aec != null) aec.setEnabled(true);
                    }
                } catch (Throwable ignored) {}
                try {
                    if (NoiseSuppressor.isAvailable()) {
                        ns = NoiseSuppressor.create(session);
                        if (ns != null) ns.setEnabled(true);
                    }
                } catch (Throwable ignored) {}
                try {
                    if (AutomaticGainControl.isAvailable()) {
                        agc = AutomaticGainControl.create(session);
                        if (agc != null) agc.setEnabled(true);
                    }
                } catch (Throwable ignored) {}
                log("  FX AEC=" + (aec != null) + " NS=" + (ns != null) + " AGC=" + (agc != null));
            }

            rec.startRecording();

            short[] buf = new short[1024];
            long end = SystemClock.elapsedRealtime() + durationMs;
            int total = 0;
            int nonZero = 0;
            int peak = 0;
            int reads = 0;

            while (SystemClock.elapsedRealtime() < end) {
                int n = rec.read(buf, 0, buf.length, AudioRecord.READ_NON_BLOCKING);
                if (n > 0) {
                    reads++;
                    total += n;
                    for (int i = 0; i < n; i++) {
                        int a = Math.abs((int)buf[i]);
                        if (a > 0) nonZero++;
                        if (a > peak) peak = a;
                    }
                } else if (n < 0) {
                    log("  read error=" + n);
                    break;
                }
                nap(10);
            }

            AudioDeviceInfo routed = null;
            try { routed = rec.getRoutedDevice(); } catch (Throwable ignored) {}
            log("  reads=" + reads + " samples=" + total + " nonZero=" + nonZero
                    + " peak=" + peak + " route=" + dev(routed));
        } catch (Throwable t) {
            log("  Input " + sourceName(source) + " Fehler: " + shortErr(t));
        } finally {
            try { if (aec != null) aec.release(); } catch (Throwable ignored) {}
            try { if (ns != null) ns.release(); } catch (Throwable ignored) {}
            try { if (agc != null) agc.release(); } catch (Throwable ignored) {}

            if (rec != null) {
                try {
                    if (rec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) rec.stop();
                } catch (Throwable t) {
                    log("  AudioRecord.stop: " + shortErr(t));
                }
                try { rec.release(); }
                catch (Throwable t) { log("  AudioRecord.release: " + shortErr(t)); }
            }
        }
    }

    private void exerciseOutput(int usage, int preferredType, boolean audible, int durationMs) {
        AudioTrack track = null;
        try {
            int rate = 48000;
            int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min > 0 ? min * 2 : 4096, 4096);

            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(usage == AudioAttributes.USAGE_VOICE_COMMUNICATION
                            ? AudioAttributes.CONTENT_TYPE_SPEECH
                            : AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            track = new AudioTrack.Builder()
                    .setAudioAttributes(aa)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(size)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            AudioDeviceInfo d = findDevice(preferredType, AudioManager.GET_DEVICES_OUTPUTS);
            if (d != null) {
                try {
                    log("Output usage=" + usage + " preferred=" + dev(d)
                            + " accepted=" + track.setPreferredDevice(d));
                } catch (Throwable ignored) {}
            } else {
                log("Output usage=" + usage + " preferred type " + preferredType + " nicht gefunden");
            }

            track.play();

            int frames = Math.max(256, rate / 50);
            short[] buf = new short[frames];
            long end = SystemClock.elapsedRealtime() + durationMs;
            double phase = 0;

            while (SystemClock.elapsedRealtime() < end) {
                if (audible) {
                    for (int i = 0; i < buf.length; i++) {
                        buf[i] = (short)(Math.sin(phase) * 5000);
                        phase += 2.0 * Math.PI * 850.0 / rate;
                    }
                }
                int n = track.write(buf, 0, buf.length, AudioTrack.WRITE_BLOCKING);
                if (n < 0) {
                    log("  AudioTrack.write error=" + n);
                    break;
                }
            }

            log("  output route=" + dev(track.getRoutedDevice())
                    + " playState=" + track.getPlayState());
        } catch (Throwable t) {
            log("  Output Fehler: " + shortErr(t));
        } finally {
            if (track != null) {
                try { track.pause(); track.flush(); } catch (Throwable ignored) {}
                try { track.stop(); }
                catch (Throwable t) { log("  AudioTrack.stop: " + shortErr(t)); }
                try { track.release(); }
                catch (Throwable t) { log("  AudioTrack.release: " + shortErr(t)); }
            }
        }
    }

    private void duplexOnType(int type, int durationMs) {
        AudioDeviceInfo out = findCommunicationDevice(type);
        if (out == null) {
            log("Duplex: comm device type=" + type + " nicht verfügbar");
            return;
        }

        try {
            log("setCommunicationDevice(" + dev(out) + ")=" + am.setCommunicationDevice(out));
        } catch (Throwable t) {
            log("setCommunicationDevice: " + shortErr(t));
            return;
        }

        nap(250);

        AudioRecord rec = null;
        AudioTrack track = null;

        try {
            int rate = 16000;
            int inMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int outMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            AudioFormat inFmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            AudioFormat outFmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            rec = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(inFmt)
                    .setBufferSizeInBytes(Math.max(4096, inMin * 2))
                    .build();

            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(outFmt)
                    .setBufferSizeInBytes(Math.max(4096, outMin * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            short[] in = new short[512];
            short[] zero = new short[512];

            rec.startRecording();
            track.play();

            long end = SystemClock.elapsedRealtime() + durationMs;
            int reads = 0;
            int writes = 0;
            int nz = 0;
            int peak = 0;

            while (SystemClock.elapsedRealtime() < end) {
                int n = rec.read(in, 0, in.length, AudioRecord.READ_NON_BLOCKING);
                if (n > 0) {
                    reads++;
                    for (int i = 0; i < n; i++) {
                        int a = Math.abs((int)in[i]);
                        if (a > 0) nz++;
                        if (a > peak) peak = a;
                    }
                }

                int w = track.write(zero, 0, zero.length, AudioTrack.WRITE_NON_BLOCKING);
                if (w > 0) writes++;
                nap(12);
            }

            log("Duplex " + dev(out)
                    + ": reads=" + reads
                    + " writes=" + writes
                    + " nz=" + nz
                    + " peak=" + peak
                    + " inRoute=" + dev(rec.getRoutedDevice())
                    + " outRoute=" + dev(track.getRoutedDevice()));
        } catch (Throwable t) {
            log("Duplex " + dev(out) + " Fehler: " + shortErr(t));
        } finally {
            if (rec != null) {
                try { rec.stop(); }
                catch (Throwable t) { log("Duplex rec.stop: " + shortErr(t)); }
                try { rec.release(); } catch (Throwable ignored) {}
            }

            if (track != null) {
                try { track.pause(); track.flush(); track.stop(); }
                catch (Throwable t) { log("Duplex track.stop: " + shortErr(t)); }
                try { track.release(); } catch (Throwable ignored) {}
            }

            try { am.clearCommunicationDevice(); } catch (Throwable ignored) {}
        }
    }

    private void micProbe() {
        log("MIC PROBE: 2 s, source=MIC, non-blocking PCM16/48k");

        AudioRecord rec = null;
        try {
            int rate = 48000;
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            rec = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(8192, min * 2))
                    .build();

            AudioDeviceInfo mic = findDevice(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioManager.GET_DEVICES_INPUTS);
            if (mic != null) {
                try { rec.setPreferredDevice(mic); } catch (Throwable ignored) {}
            }

            rec.startRecording();

            short[] b = new short[1024];
            long start = SystemClock.elapsedRealtime();
            long end = start + 2000;

            long sqFirst = 0;
            long sqLast = 0;
            int nFirst = 0;
            int nLast = 0;
            int total = 0;
            int nz = 0;
            int peak = 0;
            int reads = 0;
            int emptyReads = 0;

            while (SystemClock.elapsedRealtime() < end) {
                int n = rec.read(b, 0, b.length, AudioRecord.READ_NON_BLOCKING);
                long now = SystemClock.elapsedRealtime();

                if (n > 0) {
                    reads++;
                    total += n;
                    for (int i = 0; i < n; i++) {
                        int v = b[i];
                        int a = Math.abs(v);
                        if (a > 0) nz++;
                        if (a > peak) peak = a;

                        if (now - start < 500) {
                            sqFirst += (long)v * v;
                            nFirst++;
                        }

                        if (end - now < 500) {
                            sqLast += (long)v * v;
                            nLast++;
                        }
                    }
                } else if (n == 0) {
                    emptyReads++;
                } else {
                    log("read error=" + n);
                    break;
                }

                nap(8);
            }

            double rmsFirst = nFirst > 0 ? Math.sqrt((double)sqFirst / nFirst) : 0;
            double rmsLast = nLast > 0 ? Math.sqrt((double)sqLast / nLast) : 0;
            double nzPct = total > 0 ? 100.0 * nz / total : 0;

            log(String.format(Locale.US,
                    "MIC result: reads=%d empty=%d samples=%d peak=%d nonZero=%.2f%% RMS first=%.1f last=%.1f route=%s",
                    reads, emptyReads, total, peak, nzPct, rmsFirst, rmsLast, dev(rec.getRoutedDevice())));

            if (total >= 24000 && peak == 0 && nz == 0) {
                send(RESULT_FAULT,
                        "⛔ AUDIO-FEHLER BESTÄTIGT: PCM-Buffer kommen, aber alle Samples sind 0. Neustart erforderlich.",
                        false);
            } else if (rmsFirst > 30 && rmsLast < Math.max(10, rmsFirst * 0.12)) {
                log("⚠ Muster erkannt: Anfangssignal/Spikes → danach Kollaps");
            } else if (total == 0 || (peak < 8 && rmsLast < 3)) {
                log("⚠ praktisch kein verwertbares Mikrofonsignal");
            } else {
                log("Mic-Probe liefert weiterhin messbare Samples");
            }
        } catch (Throwable t) {
            log("MIC PROBE Fehler: " + shortErr(t));
        } finally {
            if (rec != null) {
                try { rec.stop(); }
                catch (Throwable t) { log("MIC stop: " + shortErr(t)); }
                try { rec.release(); } catch (Throwable ignored) {}
            }
        }
    }

    private void speakerTest() {
        log("SPEAKER TEST: 850-Hz-Ton 1.5 s");
        exerciseOutput(AudioAttributes.USAGE_MEDIA,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                true,
                1500);
        log("Wenn nichts hörbar war, Output bleibt defekt.");
    }

    private void pulseScreenStateParameter() {
        try {
            android.os.PowerManager pm =
                    (android.os.PowerManager)getSystemService(POWER_SERVICE);

            boolean actualOn = pm.isInteractive();
            String first = actualOn ? "off" : "on";
            String restore = actualOn ? "on" : "off";

            am.setParameters("screen_state=" + first);
            log("setParameters screen_state=" + first);
            nap(350);

            am.setParameters("screen_state=" + restore);
            log("setParameters screen_state=" + restore + " (restore)");
        } catch (Throwable t) {
            log("screen_state parameter nicht möglich: " + shortErr(t));
        }
    }

    private AudioDeviceInfo findCommunicationDevice(int type) {
        try {
            List<AudioDeviceInfo> list = am.getAvailableCommunicationDevices();
            for (AudioDeviceInfo d : list) {
                if (d.getType() == type) return d;
            }
        } catch (Throwable t) {
            log("getAvailableCommunicationDevices: " + shortErr(t));
        }
        return null;
    }

    private AudioDeviceInfo findDevice(int type, int flags) {
        try {
            for (AudioDeviceInfo d : am.getDevices(flags)) {
                if (d.getType() == type) return d;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void send(int code, String msg, boolean includePid) {
        ResultReceiver r = rr;
        if (r == null) return;

        Bundle b = new Bundle();
        b.putString("msg", msg);
        if (includePid) b.putInt("pid", Process.myPid());

        try { r.send(code, b); } catch (Throwable ignored) {}
    }

    private void log(String msg) {
        send(RESULT_PROGRESS, msg, false);
    }

    private static void nap(long ms) {
        SystemClock.sleep(ms);
    }

    private static String shortErr(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    private static String dev(AudioDeviceInfo d) {
        return d == null ? "default/null" : typeName(d.getType()) + "#" + d.getId();
    }

    private static String typeName(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "EARPIECE";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "SPEAKER";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "MIC";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "BT_SCO";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "BT_A2DP";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "WIRED_HEADSET";
            default: return "type" + t;
        }
    }

    private static String sourceName(int s) {
        if (s == MediaRecorder.AudioSource.MIC) return "MIC";
        if (s == MediaRecorder.AudioSource.VOICE_RECOGNITION) return "VOICE_RECOGNITION";
        if (s == MediaRecorder.AudioSource.VOICE_COMMUNICATION) return "VOICE_COMMUNICATION";
        if (s == MediaRecorder.AudioSource.UNPROCESSED) return "UNPROCESSED";
        if (s == MediaRecorder.AudioSource.CAMCORDER) return "CAMCORDER";
        if (s == MediaRecorder.AudioSource.VOICE_PERFORMANCE) return "VOICE_PERFORMANCE";
        return String.valueOf(s);
    }
}
