# Titan Audio Repair — Unihertz Titan 2 Elite

> **The problem:** On the affected **Unihertz Titan 2 Elite**, the entire phone-audio path can suddenly fail: **microphone input and speaker/earpiece output stop working at the same time**, across all apps. Even the built-in Unihertz Factory Test Audio In/Out can be silent. Before this project, a **full reboot was the only reliable recovery**.
>
> **What this app does:** **Titan Audio Repair** is a diagnostic and recovery-test utility. It forces the Android/MediaTek audio path through controlled microphone/speaker re-initialization steps and records what still works while the phone is broken. **Current testing shows that it does not repair the real fault.**
>
> **Current result:** MIC PROBE, SPEAKER TEST, SAFE REPAIR and AGGRESSIVE REPAIR have all been tried during a confirmed failure. The audio subsystem remained broken. **Only a full phone reboot restores audio.** The app is therefore useful for diagnosis and reproducing the failure, not as a working fix.

## Download

**Current built APK (v1.2):** [TitanAudioRepair-1.2-Elite-release.apk](releases/TitanAudioRepair-1.2-Elite-release.apk)

**Source ZIP (v1.2):** [TitanAudioRepair-1.2-Elite-source.zip](releases/TitanAudioRepair-1.2-Elite-source.zip)

**Live source:** [app/](app/)

SHA256:

```text
da91336a07bd2488345c629764daf0aede75ae5d556f766e6f3088d17c9f37a3  APK
1a34877311be78d83eccc556c6ff77e8bb13ba253ecdddb84b63f16ddd0f7c20  source ZIP
```

## What v1.2 does now

- detects the confirmed fault signature when Android still delivers PCM buffers but every microphone sample is exactly zero;
- shows **AUDIO FAULT CONFIRMED — RESTART REQUIRED**;
- adds **OPEN POWER MENU → RESTART**;
- uses an optional minimal Accessibility service only to open Android's native power menu; it does not retrieve window content;
- fixes the stale 45-second watchdog message;
- keeps SAFE/AGGRESSIVE paths only as diagnostic experiments because they do not repair the confirmed fault.

## What the app actually does


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

This sequence is now primarily diagnostic. A later confirmed failure showed that none of the app-level recovery paths restored audio. If the device is needed immediately, a full reboot remains the only confirmed recovery.

## Recovery status

One captured run:

```text
MIC #1: peak=235, nonZero=96.40%, RMS first=12.7 last=15.4
MIC #2: peak=4289, nonZero=96.38%, RMS first=7.3 last=15.7
Speaker: preferred SPEAKER#3 accepted=true
```

A later confirmed failure disproved this as a reliable recovery method. During the fault, repeated MIC PROBE and SPEAKER TEST runs did not restore audio; SAFE REPAIR also reopened all tested capture sources but every source continued to return only zero-valued PCM samples. The tester also tried the remaining repair menu paths without restoring audio.

**Current conclusion: the app does not repair the underlying failure. A full reboot is still the only confirmed recovery.**

---

## New in 1.3 — targeted MediaTek vendor reinit

Version 1.3 adds one recovery path that is **technically different** from the previous SAFE/AGGRESSIVE app-level cycling:

- sends the MediaTek audio-system restart signalling pair `restarting=true` → `restarting=false`;
- enumerates real input devices;
- when an alternate physical input (Bluetooth SCO, USB, wired or BLE headset) is connected, it opens capture on that device and then explicitly routes capture back to the built-in microphone, forcing a real hardware input-device transition instead of reopening the same `MIC#16`;
- automatically reruns MIC PROBE and SPEAKER TEST after the sequence.

This is deliberately narrow: no root, ADB, NVRAM/calibration writes, shell commands, or unverified vendor parameters. **It is an experimental recovery candidate and is not yet confirmed to fix the fault.**

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
