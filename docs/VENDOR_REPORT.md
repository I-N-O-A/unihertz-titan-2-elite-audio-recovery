# Vendor report — ready to send to Unihertz / MediaTek

## Subject

Unihertz Titan 2 Elite / Android 16: microphone + speaker permanently fail until reboot; MediaTek DspRaw/SCP recovery failure visible in bugreport

## Device

- User identifies device as: Unihertz Titan 2 Elite
- Reported Android product: `Titan_2_EEA/Titan_2`
- Android: 16
- Build: `BP2A.250605.031.A3`
- Firmware: `V02.00.04`

## User-visible failure

Several times per day, microphone and speaker/receiver can both stop working. The failure survives app restarts and route changes. Unihertz Factory Test Audio In/Out also fail in the same state. Only a full reboot had reliably restored audio before the experimental recovery app was tested.

## Critical vendor log signatures

During the failed state:

```text
AudioALSAStreamIn: getCapturePosition(), mCaptureHandler == NULL
StreamHAL: Error from HAL stream in function get_capture_position: No data available
```

The MediaTek primary HAL stack contains:

```text
pthread_join
AudioALSACaptureDataProviderDspRaw::close()
AudioALSACaptureDataProviderBase::detach(...)
AudioALSACaptureHandlerNormal::close()
AudioALSAStreamIn::close_()
AudioALSAStreamIn::standby_l(...)
AudioALSAStreamIn::standby(...)
AudioALSAStreamManager::standbyAllInputStreams(...)
AudioALSAStreamManager::audioDspStatusUpdate(bool)
audio_dsp_stop_event
```

A DspRaw reader thread simultaneously contains:

```text
pcm_hw_ioctl
pcm_read
AudioALSACaptureDataProviderDspRaw::readThread(void*)
```

Kernel/SCP evidence includes repeated:

```text
scp_awake_lock: SCP A not enabled
Error: IPI [scp_ipidev_ipi#29] pre_cb fail
[SCP] scp_sys_full_reset
[SCP] recovery success
usnd_scp_recover_event(), SCP_EVENT_READY
audio_send_reset_event
mtk_dsp_ul_handler reset UL
```

The captured kernel dump contains 21 `scp_sys_full_reset` entries over ~17.94 s. This reset series happened during bugreport collection, so it may be a secondary manifestation of the already-failed state.

## Additional trace evidence

A Perfetto capture while the fault was active showed AudioRecord and AudioTrack creation succeeding, input/output HAL streams opening, and output PCM writes continuing at a normal cadence even though no physical audio worked. This suggests the framework is not aware that the underlying signal path is broken.

An earlier trace also captured a ~3 s stall in the MediaTek `IPrimaryDevice::setMode()` path.

## Suspected area

Please investigate the MediaTek audio DSP/SCP recovery path, especially interaction among:

- `AudioALSACaptureDataProviderDspRaw::close()`
- its reader thread blocked in `pcm_read()`
- `audioDspStatusUpdate()` / `audio_dsp_stop_event`
- SCP STOP/READY/reset handling
- recreation of `mCaptureHandler`
- downstream AFE/codec recovery after SCP reset

## Workaround discovery

A normal non-root app that opens a 48 kHz MIC AudioRecord for ~2 seconds and then performs a speaker AudioTrack test unexpectedly recovered audio once. This suggests reopening/recycling streams can occasionally force a successful vendor-path reinitialization. The minimal recovery trigger still needs confirmation.

All privacy-safe logs, Perfetto traces, source code, and the recovery APK are included in this repository.
