# Titan Audio Repair — Unihertz Titan 2 Elite

> **Das Problem:** Beim betroffenen **Unihertz Titan 2 Elite** kann der komplette Telefon-Audiopfad plötzlich ausfallen: **Mikrofon und Lautsprecher/Receiver funktionieren gleichzeitig nicht mehr**, und zwar in allen Apps. Selbst die integrierten Unihertz-Factory-Tests für Audio In/Out bleiben dann stumm. Vor dieser App half zuverlässig nur ein **kompletter Neustart**.
>
> **Was die App macht:** **Titan Audio Repair** ist aktuell ein Diagnose- und Recovery-Testwerkzeug. Sie erzwingt verschiedene Mikrofon-/Lautsprecher-Reinitialisierungen und protokolliert genau, was im Fehlerzustand noch funktioniert. **Nach den aktuellen Tests behebt die App den echten Fehler jedoch nicht.**
>
> **Aktueller Stand:** MIC PROBE, SPEAKER TEST, SAFE REPAIR und AGGRESSIVE REPAIR wurden während eines bestätigten Ausfalls ausprobiert. Der Audiopfad blieb defekt. **Nur ein vollständiger Neustart des Smartphones stellt Audio wieder her.** Die App ist damit derzeit Diagnosewerkzeug, kein funktionierender Fix.

## Download

**Aktuelle APK (v1.2):** [TitanAudioRepair-1.2-Elite-release.apk](releases/TitanAudioRepair-1.2-Elite-release.apk)

**Source-ZIP (v1.2):** [TitanAudioRepair-1.2-Elite-source.zip](releases/TitanAudioRepair-1.2-Elite-source.zip)

**Aktueller Source:** [app/](app/)

SHA256:

```text
da91336a07bd2488345c629764daf0aede75ae5d556f766e6f3088d17c9f37a3  APK
1a34877311be78d83eccc556c6ff77e8bb13ba253ecdddb84b63f16ddd0f7c20  Source-ZIP
```

## Was v1.2 jetzt macht

- erkennt den bestätigten Fehler, wenn Android weiterhin PCM-Buffer liefert, aber wirklich jedes Mikrofonsample 0 ist;
- zeigt **AUDIO-FEHLER BESTÄTIGT – NEUSTART ERFORDERLICH**;
- bietet **POWER-MENÜ ÖFFNEN → NEU STARTEN**;
- nutzt dafür optional einen minimalen Accessibility-Service, der ausschließlich das native Android-Power-Menü öffnet und keine Fensterinhalte liest;
- behebt die veraltete 45-Sekunden-Watchdog-Meldung;
- lässt SAFE/AGGRESSIVE nur als Diagnose-Experimente stehen, da sie den bestätigten Fehler nicht reparieren.

## Was die App konkret macht


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

Diese Reihenfolge dient inzwischen vor allem der Diagnose. Ein später bestätigter Fehlerfall zeigte, dass keiner der App-Recovery-Pfade Audio wiederherstellt. Wenn das Gerät sofort benötigt wird, bleibt ein kompletter Neustart die einzige bestätigte Lösung.

## Aktueller Recovery-Status

```text
MIC #1: peak=235, nonZero=96.40%, RMS first=12.7 last=15.4
MIC #2: peak=4289, nonZero=96.38%, RMS first=7.3 last=15.7
Speaker: SPEAKER#3 akzeptiert
```

Ein späterer, eindeutig bestätigter Fehlerfall hat gezeigt, dass diese Sequenz **keine zuverlässige Reparatur** ist. Während des Fehlers blieben MIC PROBE und SPEAKER TEST wirkungslos; SAFE REPAIR öffnete sämtliche getesteten Audioquellen neu, aber alle lieferten weiterhin ausschließlich Null-Samples. Auch die übrigen Repair-Menüpunkte stellten Audio nicht wieder her.

**Aktueller Schluss: Die App repariert den zugrunde liegenden Fehler nicht. Nur ein kompletter Neustart behebt ihn zuverlässig.**

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
