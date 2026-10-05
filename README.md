# Titan Audio Recovery — Unihertz Titan 2 Elite investigation

Public reproduction package, traces, technical findings and an experimental recovery app for a recurring Android audio failure observed on a **Unihertz Titan 2 Elite**.

## Symptom

The device can enter a state where **both microphone input and speaker/receiver output stop working** and remain broken across apps. Force-stopping voice apps, toggling Bluetooth, changing routes, and running the built-in Factory Test audio tests do not recover it. A full reboot does.

The affected hardware is **Unihertz Titan 2 Elite**. Android reports the product/build as:

```text
Unihertz/Titan_2_EEA/Titan_2:16/BP2A.250605.031.A3/V02.00.04:user/release-keys
```

This repository does **not** claim every Titan 2 Elite has this bug.

## Strongest findings

1. An earlier Perfetto capture showed a ~3 s MediaTek audio mode transition stall around `IPrimaryDevice::setMode()` / `setPhoneState()`.
2. A later capture taken while audio was already broken showed Android successfully opening input/output streams and continuously submitting PCM, while the physical microphone and speaker were still dead.
3. A bugreport captured in the failed state repeatedly logged:

```text
AudioALSAStreamIn: getCapturePosition(), mCaptureHandler == NULL
StreamHAL: Error from HAL stream in function get_capture_position: No data available
```

4. The MediaTek audio HAL native stack showed one thread in:

```text
audioDspStatusUpdate()
 -> standbyAllInputStreams()
 -> AudioALSAStreamIn::standby()
 -> AudioALSACaptureHandlerNormal::close()
 -> AudioALSACaptureDataProviderDspRaw::close()
 -> pthread_join()
```

while a corresponding reader thread was inside:

```text
AudioALSACaptureDataProviderDspRaw::readThread()
 -> pcm_read()
 -> pcm_hw_ioctl()
```

5. The kernel portion of the bugreport showed repeated SCP recovery activity, including **21 `scp_sys_full_reset` events in ~17.94 s**, plus `SCP_EVENT_READY`, audio reset events, and repeated SCP/IPI failures. Important caveat: this reset storm occurred during bugreport collection, so it may reflect or aggravate an already-corrupted state rather than prove the original trigger.

## Working hypothesis

The evidence is most consistent with a **MediaTek SCP / audio-DSP / DspRaw capture recovery failure**, possibly a race or deadlock in the capture shutdown/restart path after an SCP/DSP fault. Android's high-level audio framework can look healthy while the real signal path remains unusable.

This is a hypothesis, not a vendor-confirmed root cause.

## Experimental recovery app

`Titan Audio Repair` is a small Android app built to exercise only public app-level audio APIs. It requires **no root and no ADB**.

The app can:

- run a 2-second 48 kHz PCM microphone probe;
- run an 850 Hz speaker test;
- normalize `AudioManager` communication state;
- reopen multiple capture sources serially;
- reopen speaker/earpiece output paths serially;
- optionally exercise communication mode, AEC/NS/AGC, audio focus and an experimental `screen_state` audio parameter pulse;
- run recovery work in a separate process that can be killed from the UI if a vendor call hangs.

It does **not** write NVRAM, run SmartPA calibration, modify persistent factory values, or require privileged/root access.

### Observed recovery

During one failed-device test, audio became functional again after running the app's microphone probe(s) followed by the speaker test. The recorded app log is in `evidence/repair-observation-2026-10-05.txt`.

The **minimal recovery trigger is not yet isolated**. It may be the microphone probe, the speaker test, or the sequence. More reproductions are needed.

## Repository layout

```text
app/                                  clean source; no signing keys
releases/                             prebuilt APK + hashes/permissions/signature report
evidence/traces/                      gzip-compressed Perfetto traces
evidence/bugreport-extracts/          privacy-filtered technical excerpts
evidence/hubwidget-investigation/     earlier hypothesis + logs/patch; later ruled out as required trigger
docs/TECHNICAL_FINDINGS.md            detailed chronology and interpretation
docs/VENDOR_REPORT.md                 ready-to-send Unihertz/MediaTek report
docs/RELATED_REPORTS.md               related public reports
docs/PRIVACY.md                       why the raw Android bugreport is not public
```

## Build

The project intentionally uses a minimal SDK command-line build script instead of Gradle.

Requirements:

- Android SDK Platform 35
- Build Tools 35.0.1
- JDK 11+

```bash
cd app
./build.sh /path/to/android-sdk
```

That creates an aligned unsigned APK. To sign it, provide your own keystore through environment variables documented in `app/build.sh`.

**No private signing key is included in this repository.**

## Safety / disclaimer

This is experimental diagnostic software for a vendor audio bug. Use at your own risk. The aggressive repair path intentionally exercises audio routing and stream lifecycle transitions, but it does not attempt privileged SCP resets or persistent calibration changes.

If you have the same failure, please attach your device model, exact firmware build, whether Factory Test is also silent, and a repair-app log to a GitHub issue.

## Related reports

Public reports show other Unihertz devices and the Titan 2 family have had microphone/audio issues, although no public report found so far matches this exact dual input/output failure with the same MediaTek stack signature. See `docs/RELATED_REPORTS.md`.

## License

MIT. See `LICENSE`.
