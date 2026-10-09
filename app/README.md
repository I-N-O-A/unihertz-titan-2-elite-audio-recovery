# Titan Audio Repair app

Minimal standalone Android app for testing/recovering the observed Unihertz Titan 2 Elite audio failure without root or ADB.

## Permissions

```text
android.permission.RECORD_AUDIO
android.permission.MODIFY_AUDIO_SETTINGS
android.permission.POST_NOTIFICATIONS (Android 13+, optional notification-sound test)
```

## Main actions

- **MIC PROBE** — 2-second built-in microphone PCM16/48 kHz capture; logs sample/peak/RMS characteristics.
- **SPEAKER TEST** — 1.5-second 850 Hz tone routed toward the built-in speaker.
- **SAFE REPAIR** — normalizes AudioManager state and serially reopens multiple capture/output paths.
- **AGGRESSIVE REPAIR** — additionally exercises communication mode, audio focus, mic mute pulse, full-duplex speaker/earpiece paths, AEC/NS/AGC and an experimental AOSP-style `screen_state` parameter pulse.

The service runs in an isolated app process (`:repair`) so the UI can remain responsive if a vendor audio call blocks.

## v1.4: notification audio and UI-only soft refresh

- Manual real Android notification with audible `USAGE_NOTIFICATION` channel; permission requested on Android 13+.
- The notification is canceled after 8 s, and MIC PROBE runs automatically after 2.6 s.
- Optional Accessibility-only notifications/quick-settings refresh, either followed by a MIC PROBE **without a sound**, or by the notification sound test. This enables an A/B comparison between UI-only and notification-triggered audio wakeup.
- No full reboot, SystemUI process restart or Audio HAL restart in the soft-recovery tests.
- Mic recovery clears the red alert only after meaningful non-zero PCM input; speaker output needs manual audible confirmation.
- Silent mode, DND and channel settings can suppress notification sounds; posting is not proof of playback.

## Safety choices

The app does not use root, ADB, shell commands, hidden NVRAM writers, calibration writes, or SmartPA calibration.

## Build

See `build.sh`. The public repository intentionally contains no signing key.
