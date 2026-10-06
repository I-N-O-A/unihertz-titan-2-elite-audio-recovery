# Titan Audio Repair — Unihertz Titan 2 Elite

Experimental **no-root / no-ADB recovery app** for a recurring Titan 2 Elite failure where microphone input and speaker/receiver output can stop working together.

## Download the app

**Built APK:** [TitanAudioRepair-1.0-Elite-release.apk](releases/TitanAudioRepair-1.0-Elite-release.apk)

SHA256:

```text
f846268dd8c4dc30b406cb093ca57dfc5183f5e3e10381a3894c1a06e41975b2
```

**Full source:** [app/](app/)

The source includes `MainActivity.java`, `RepairService.java`, AndroidManifest, resources and a reproducible Android SDK command-line build script.

## What the APK does

Titan Audio Repair is a small diagnostic/recovery utility built specifically for the observed **Unihertz Titan 2 Elite** audio lockup.

It requests only:

```text
android.permission.RECORD_AUDIO
android.permission.MODIFY_AUDIO_SETTINGS
```

Main actions:

### MIC PROBE

Opens the built-in microphone as PCM16 / 48 kHz for about 2 seconds and logs:

- successful/empty reads;
- sample count;
- peak amplitude;
- non-zero sample percentage;
- first/last RMS;
- routed input device.

This is important because the failed phone can still appear "healthy" to Android while the real signal path is broken.

### SPEAKER TEST

Creates an `AudioTrack`, requests the built-in speaker, and plays an 850 Hz test tone for about 1.5 seconds.

### SAFE REPAIR

Uses public Android audio APIs to try to rebuild the audio path without rebooting:

1. clears the communication device;
2. returns AudioManager to `MODE_NORMAL`;
3. unmutes the microphone;
4. reloads Android sound effects;
5. serially opens/closes several capture sources;
6. reopens speaker/voice output paths.

### AGGRESSIVE REPAIR

Runs SAFE REPAIR and additionally exercises:

- transient exclusive audio focus;
- a short microphone-mute pulse;
- `MODE_IN_COMMUNICATION` followed by `MODE_NORMAL`;
- speaker/earpiece communication-device routing;
- short full-duplex input/output cycles;
- AEC / noise suppression / AGC creation;
- an experimental AOSP-style `screen_state` audio parameter pulse.

It does **not** perform NVRAM writes, SmartPA calibration, factory-data modification, root commands or ADB commands.

### Separate repair process

Recovery operations run in an app process named `:repair`. If a vendor audio call blocks, the UI can remain responsive and provides a button to kill the repair process.

## Recommended test order when audio fails

Before rebooting:

1. run **MIC PROBE once**;
2. immediately test microphone and speaker in another app;
3. if still broken, run **SPEAKER TEST** and test externally again;
4. if still broken, run **SAFE REPAIR**;
5. only then try **AGGRESSIVE REPAIR**;
6. share the repair log.

This order is intentional: on one real failed-state test, audio recovered after the microphone-probe / speaker-test sequence. We still need more failures to isolate the minimal recovery trigger.

## Observed recovery

One captured run:

```text
MIC #1: peak=235, nonZero=96.40%, RMS first=12.7 last=15.4
MIC #2: peak=4289, nonZero=96.38%, RMS first=7.3 last=15.7
Speaker: preferred SPEAKER#3 accepted=true
```

After that sequence the tester reported that normal microphone and speaker audio worked again.

This is promising but **not yet proven to be a universal fix**.

---

# Investigation and technical evidence

## Affected device

- **Unihertz Titan 2 Elite**
- Android 16
- Build `BP2A.250605.031.A3`
- Firmware `V02.00.04`
- Android product string:
  `Unihertz/Titan_2_EEA/Titan_2:16/BP2A.250605.031.A3/V02.00.04:user/release-keys`

## Failure pattern

- microphone and speaker/receiver can fail together;
- the failure persists across apps;
- force-stopping voice apps does not recover it;
- Bluetooth/route changes did not recover it;
- Unihertz Factory Test Audio In/Out also fail in the broken state;
- a full reboot restores audio.

## Strongest bugreport evidence

The failed-state bugreport repeatedly contains:

```text
AudioALSAStreamIn: getCapturePosition(), mCaptureHandler == NULL
StreamHAL: Error from HAL stream in function get_capture_position: No data available
```

The MediaTek HAL stack shows a DSP-stop path waiting in:

```text
audioDspStatusUpdate()
 -> standbyAllInputStreams()
 -> AudioALSAStreamIn::standby()
 -> AudioALSACaptureHandlerNormal::close()
 -> AudioALSACaptureDataProviderDspRaw::close()
 -> pthread_join()
```

while a DspRaw reader thread is inside:

```text
AudioALSACaptureDataProviderDspRaw::readThread()
 -> pcm_read()
 -> pcm_hw_ioctl()
```

Kernel/SCP logs also show repeated SCP recovery/reset activity.

See:

- [Technical findings](docs/TECHNICAL_FINDINGS.md)
- [Vendor report](docs/VENDOR_REPORT.md)
- [Related public reports](docs/RELATED_REPORTS.md)
- [Privacy notes](docs/PRIVACY.md)
- [Repair observation](evidence/repair-observation-2026-10-05.txt)

## Perfetto evidence

An earlier trace captured an approximately 3-second MediaTek audio-mode transition stall around `IPrimaryDevice::setMode()` / `setPhoneState()`.

A later failed-state trace showed Android successfully creating input/output streams and continuously feeding PCM while the physical microphone and speaker remained unusable.

This supports a fault **below normal app-level audio handling**, likely in the MediaTek vendor audio/DSP/SCP recovery path.

## Working hypothesis

The evidence is most consistent with a **MediaTek SCP / audio-DSP / DspRaw recovery failure**, possibly involving capture shutdown/restart while the DspRaw reader remains blocked lower in the PCM path.

This is a working diagnosis, not a vendor-confirmed root cause.

## Source and build

```bash
cd app
./build.sh /path/to/android-sdk
```

Requirements:

- Android SDK Platform 35
- Build Tools 35.0.1
- JDK 11+

No private signing key is included in the repository.

## Privacy

The raw Android bugreport is intentionally not public because Android bugreports may contain account, network, notification, device and other private information. Privacy-filtered technical evidence is published instead.

## License

MIT.
