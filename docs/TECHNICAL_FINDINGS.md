# Technical findings

## Device / firmware

User-reported device: Unihertz Titan 2 Elite.

Android build string observed in traces/bugreport:

```text
Unihertz/Titan_2_EEA/Titan_2:16/BP2A.250605.031.A3/V02.00.04:user/release-keys
```

## Symptom characteristics

- Microphone and speaker/receiver become unusable together.
- Failure persists across applications.
- Force-stopping ChatGPT/Gemini does not recover it.
- Bluetooth route changes did not recover it.
- Unihertz Factory Test Audio In/Out also fail while the fault is present.
- Full reboot restores audio.
- Microphone recording can show a few initial spikes/very small activity and then collapse.

## Trace 1 — 2026-10-04 19:07

An earlier Perfetto trace showed a synchronized ~3-second stall across AudioService, audioserver and the MediaTek primary audio HAL during a phone/audio mode transition. Approximate observed spans from the trace analysis:

- `IAudioPolicyService::setPhoneState`: ~3041 ms
- `HIDL::IPrimaryDevice::setMode::client`: ~3009 ms
- MediaTek HAL `IPrimaryDevice::setMode::server`: ~3008 ms
- AudioRecord creation was delayed by a similar interval

This is evidence of a MediaTek mode-transition failure, but the trace did not expose the exact mode argument. Do not interpret it as proof that a specific Android mode always triggers the bug.

## Trace 2 — 2026-10-04 20:11

This trace did **not** capture a failing mode transition. It was useful mainly to rule out a constant audio-HAL deadlock.

A separate HubWidget cleanup occurred after the trace ended. That led to a temporary hypothesis that bulk notification actions might trigger the problem. A 750 ms READ pacing patch was built and verified, but the audio failure was later reproduced without using HubWidget. Therefore HubWidget is **not required to trigger the failure**.

The HubWidget material is retained under `evidence/hubwidget-investigation/` to preserve the investigation history.

## Trace 3 — 2026-10-05 21:34, fault captured

This trace was captured while the audio fault was present.

Key observation: the Android framework and MediaTek HAL continued to behave as if streams were operational.

### Input

AudioRecord/input setup completed successfully, including operations equivalent to:

```text
createRecord
openInputStream
createAudioPatch
AudioRecord.start
prepareForReading
```

The MediaTek input/ADC clock path was also observed being enabled.

### Output

A speaker test created an AudioTrack successfully. The MediaTek side continued to accept/write PCM at a normal-looking cadence; analysis counted 399 PCM write cycles over the test interval. Playback clocks were enabled.

### Interpretation

The physical signal path was dead while high-level stream creation and PCM flow remained active. This strongly argues against a simple app-level focus/routing problem and points below AudioFlinger/AudioPolicy, into the MediaTek vendor audio path, DSP/SCP state or codec/AFE interaction.

## Bugreport — stronger evidence

### `mCaptureHandler == NULL`

During a normal MIC capture attempt, the bugreport repeatedly logs:

```text
AudioALSAStreamIn: getCapturePosition(), mCaptureHandler == NULL
StreamHAL: Error from HAL stream in function get_capture_position: No data available
```

The full privacy-filtered excerpt is in:

`evidence/bugreport-extracts/01-capture-handler-null.txt`

### MediaTek HAL close path waits in `pthread_join`

A native stack for the MediaTek audio HAL shows:

```text
audio_dsp_stop_event
 -> AudioALSAStreamManager::audioDspStatusUpdate(bool)
 -> standbyAllInputStreams(...)
 -> AudioALSAStreamIn::standby(...)
 -> AudioALSAStreamIn::close_()
 -> AudioALSACaptureHandlerNormal::close()
 -> AudioALSACaptureDataProviderBase::detach(...)
 -> AudioALSACaptureDataProviderDspRaw::close()
 -> pthread_join()
```

See `02-hal-close-pthread-join-stack.txt`.

### DspRaw reader remains in PCM read

A corresponding thread is in:

```text
AudioALSACaptureDataProviderDspRaw::readThread(void*)
 -> pcm_read
 -> pcm_hw_ioctl
 -> ioctl
```

See `03-dspraw-readthread-pcm-read-stack.txt`.

This pairing is consistent with a recovery/shutdown path waiting for a reader that is blocked lower in the PCM path. It is not, by itself, mathematical proof of a permanent deadlock; it is a snapshot of the failed state and should be treated as strong diagnostic evidence.

### SCP reset / recovery activity

The kernel portion of the bugreport contains repeated messages including:

```text
scp_awake_lock: SCP A not enabled
Error: IPI [scp_ipidev_ipi#29] pre_cb fail
[SCP] scp_sys_full_reset
[SCP] start scp
[SCP] recovery success
usnd_scp_recover_event(), SCP_EVENT_READY
audio_send_reset_event ...
mtk_dsp_ul_handler reset UL
```

There are 21 `scp_sys_full_reset` entries spanning approximately 17.94 seconds in the captured kernel state.

Important caveat: these resets occur during bugreport collection. The bugreport may have stressed an already-broken subsystem. The reset series therefore supports an unstable SCP/audio-DSP state but does not prove the initial fault began with the first reset in this log.

## Factory Test result

The tester ran Unihertz Factory Test Audio In/Out while the failure was active. Both still failed. Therefore the issue is not limited to a single third-party app and is not bypassed by the vendor test UI.

## Recovery-app observation

The first public recovery experiment used:

1. 2-second MIC probe, PCM16, 48 kHz, built-in mic
2. a second MIC probe
3. 850 Hz speaker test

Observed log:

```text
MIC #1: peak=235, nonZero=96.40%, RMS first=12.7 last=15.4
MIC #2: peak=4289, nonZero=96.38%, RMS first=7.3 last=15.7
Speaker route: SPEAKER#3, preferred-device request accepted
```

The tester reported that audio worked again after this sequence.

This is the most practically useful finding so far, but **the exact minimal recovery operation is still unknown**. Future reproductions should test only one MIC PROBE first, verify audio externally, and only then run the speaker test.

## Current hypothesis

Most likely area:

```text
Android Audio APIs / AudioFlinger / AudioPolicy
                    |
                    | mostly operational during failure
                    v
MediaTek primary audio HAL
                    |
       DspRaw / PCM / SCP recovery
                    |   <--- strongest evidence here
                    v
AFE / DSP / codec signal path
                    |
             physical mic/speaker
```

The evidence supports a MediaTek vendor firmware/recovery bug much more strongly than an application-specific bug.
