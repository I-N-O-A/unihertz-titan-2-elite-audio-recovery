# Titan Audio Repair app

Minimal standalone Android app for testing/recovering the observed Unihertz Titan 2 Elite audio failure without root or ADB.

## Permissions

```text
android.permission.RECORD_AUDIO
android.permission.MODIFY_AUDIO_SETTINGS
```

## Main actions

- **MIC PROBE** — 2-second built-in microphone PCM16/48 kHz capture; logs sample/peak/RMS characteristics.
- **SPEAKER TEST** — 1.5-second 850 Hz tone routed toward the built-in speaker.
- **SAFE REPAIR** — normalizes AudioManager state and serially reopens multiple capture/output paths.
- **AGGRESSIVE REPAIR** — additionally exercises communication mode, audio focus, mic mute pulse, full-duplex speaker/earpiece paths, AEC/NS/AGC and an experimental AOSP-style `screen_state` parameter pulse.

The service runs in an isolated app process (`:repair`) so the UI can remain responsive if a vendor audio call blocks.

## Safety choices

The app does not use root, ADB, shell commands, hidden NVRAM writers, calibration writes, or SmartPA calibration.

## Build

See `build.sh`. The public repository intentionally contains no signing key.
