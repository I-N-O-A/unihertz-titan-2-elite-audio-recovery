# Titan Audio Repair — Unihertz Titan 2 Elite

Eine experimentelle **Repair- und Diagnose-App ohne Root und ohne ADB** für den Fehler, bei dem beim **Unihertz Titan 2 Elite** Mikrofon und Lautsprecher/Receiver gleichzeitig ausfallen können.

## APK herunterladen

**Fertig gebaute APK:** [TitanAudioRepair-1.0-Elite-release.apk](releases/TitanAudioRepair-1.0-Elite-release.apk)

SHA256:

```text
f846268dd8c4dc30b406cb093ca57dfc5183f5e3e10381a3894c1a06e41975b2
```

**Vollständiger Source:** [app/](app/)

Im Source liegen `MainActivity.java`, `RepairService.java`, Manifest, Ressourcen und das reproduzierbare Build-Skript.

## Was die APK macht

Die App versucht den festhängenden Audiopfad **ohne Neustart** durch gezieltes Öffnen, Schließen und Neu-Routen von Android-Audioströmen wieder zu initialisieren.

Sie benötigt nur:

```text
RECORD_AUDIO
MODIFY_AUDIO_SETTINGS
```

### MIC PROBE

Öffnet das eingebaute Mikrofon etwa 2 Sekunden als PCM16 / 48 kHz und misst:

- erfolgreiche und leere Reads;
- Anzahl Samples;
- Peak;
- Anteil nicht-null Samples;
- RMS am Anfang und Ende;
- tatsächlich geroutetes Mikrofon.

Gerade bei diesem Fehler ist das wichtig, weil Android einen Stream erfolgreich öffnen kann, obwohl der reale Audiopfad bereits defekt ist.

### SPEAKER TEST

Öffnet einen `AudioTrack`, fordert den eingebauten Lautsprecher an und spielt etwa 1,5 Sekunden einen 850-Hz-Testton.

### SAFE REPAIR

Probiert ausschließlich normale Android-Audio-APIs:

1. Communication Device löschen;
2. AudioManager auf `MODE_NORMAL`;
3. Mikrofon entmuten;
4. SoundEffects entladen/neu laden;
5. mehrere Mikrofon-/Capture-Quellen nacheinander öffnen und sauber schließen;
6. Speaker- und Voice-Output-Pfade neu öffnen.

### AGGRESSIVE REPAIR

Führt zuerst SAFE REPAIR aus und probiert zusätzlich:

- exklusiven kurzfristigen Audio-Fokus;
- kurzen Mic-Mute/Unmute-Puls;
- `MODE_IN_COMMUNICATION` → zurück auf `MODE_NORMAL`;
- Speaker/Earpiece als Communication Device;
- kurze Full-Duplex Input/Output-Sequenzen;
- AEC / Noise Suppression / AGC;
- experimentellen AOSP-`screen_state`-HAL-Puls.

**Keine NVRAM-Schreibvorgänge, keine SmartPA-Kalibrierung, keine Factory-Werte, kein Root, kein ADB.**

### Separater Repair-Prozess

Die eigentlichen Recovery-Schritte laufen im Prozess `:repair`. Falls ein MediaTek-Aufruf hängen bleibt, kann die Oberfläche weiter bedienbar bleiben und der Repair-Prozess kann separat beendet werden.

## Reihenfolge beim nächsten Ausfall

Vor einem Neustart:

1. **einmal MIC PROBE**;
2. sofort außerhalb der App Mikrofon **und** Lautsprecher testen;
3. falls weiter defekt: **SPEAKER TEST**;
4. erneut extern testen;
5. danach **SAFE REPAIR**;
6. erst zuletzt **AGGRESSIVE REPAIR**;
7. Log teilen.

Der Grund: Bei einem echten Fehlerzustand kam Audio nach der Mic-Probe-/Speaker-Test-Sequenz bereits wieder zurück. Wir wollen jetzt herausfinden, welcher minimale Schritt wirklich heilt.

## Bisherige Recovery-Beobachtung

```text
MIC #1: peak=235, nonZero=96.40%, RMS first=12.7 last=15.4
MIC #2: peak=4289, nonZero=96.38%, RMS first=7.3 last=15.7
Speaker: SPEAKER#3 akzeptiert
```

Danach funktionierten normales Mikrofon und Lautsprecher wieder.

Das ist ein sehr guter Hinweis, aber noch **kein universell bestätigter Fix**.

---

# Recherche und technische Diagnose

## Gerät

- **Unihertz Titan 2 Elite**
- Android 16
- Build `BP2A.250605.031.A3`
- Firmware `V02.00.04`

## Fehlerbild

- Mikrofon und Lautsprecher/Receiver fallen gemeinsam aus.
- Fehler bleibt über verschiedene Apps bestehen.
- Force-Stop von ChatGPT/Gemini hilft nicht.
- Routing/Bluetooth-Wechsel halfen nicht.
- Unihertz Factory Test Audio In/Out war ebenfalls stumm.
- Vollständiger Neustart stellt Audio wieder her.

## Wichtigster Bugreport-Fund

Im Fehlerzustand erscheint wiederholt:

```text
AudioALSAStreamIn: getCapturePosition(), mCaptureHandler == NULL
StreamHAL: Error from HAL stream in function get_capture_position: No data available
```

Der MediaTek-HAL zeigt gleichzeitig:

```text
audioDspStatusUpdate()
 -> standbyAllInputStreams()
 -> AudioALSAStreamIn::standby()
 -> AudioALSACaptureHandlerNormal::close()
 -> AudioALSACaptureDataProviderDspRaw::close()
 -> pthread_join()
```

Ein zugehöriger DspRaw-Reader steckt in:

```text
AudioALSACaptureDataProviderDspRaw::readThread()
 -> pcm_read()
 -> pcm_hw_ioctl()
```

Zusätzlich tauchen SCP-Reset-/Recovery-Ereignisse auf.

Details:

- [Technische Analyse](docs/TECHNICAL_FINDINGS.md)
- [Vendor-Report für Unihertz/MediaTek](docs/VENDOR_REPORT.md)
- [Ähnliche öffentliche Berichte](docs/RELATED_REPORTS.md)
- [Datenschutz](docs/PRIVACY.md)
- [Recovery-Testlog](evidence/repair-observation-2026-10-05.txt)

## Arbeitshypothese

Die Hinweise sprechen am stärksten für einen **MediaTek SCP / Audio-DSP / DspRaw-Recovery-Fehler**. Android kann AudioRecord/AudioTrack teilweise weiterhin erfolgreich verwalten, während der physische Audiopfad bereits festhängt.

Die Diagnose ist technisch gut belegt, aber noch nicht von Unihertz/MediaTek bestätigt.

## Build

```bash
cd app
./build.sh /path/to/android-sdk
```

Android Platform 35, Build Tools 35.0.1, JDK 11+.

## Datenschutz

Der rohe Android-Bugreport wird nicht öffentlich veröffentlicht, weil er persönliche Konto-, Netzwerk-, Notification- und Gerätedaten enthalten kann. Im Repository liegen nur die technisch relevanten, bereinigten Auszüge.

## Lizenz

MIT.
