# Related public reports

None of the reports below has been proven to be the exact same MediaTek SCP/DspRaw failure. They are included because they show software/firmware audio problems across Titan 2-family or other Unihertz devices, and one comparable non-Unihertz firmware bug that required reboot until patched.

## Titan 2 / Titan 2 Elite

- Titan 2 Elite: intermittent muffled microphone during calls; Unihertz asks the user for in-failure testing and samples:
  https://www.reddit.com/r/unihertz/comments/1vri2sv/mic_issues_during_calls/

- Titan 2: poor/distorted microphone recording; changing the recorder to `Microphone (unprocessed)` reportedly produced clear audio, supporting an audio-pipeline/software component:
  https://www.reddit.com/r/unihertz/comments/1sepj9h/titan_2_distorted_audio_on_recorded_videos/

- Titan 2 microphone-quality discussion:
  https://www.reddit.com/r/unihertz/comments/1rc4le7/titan_2_microphone_quality/

- Titan 2 Elite / earbuds call-quality issue where disabling AAC reportedly fixed the user's Bluetooth microphone quality. This is likely a different problem but demonstrates route/codec-specific behavior on the same device family:
  https://www.reddit.com/r/unihertz/comments/1wwhh1k/titan_2e_bad_earbuds_call_quality/

## Other Unihertz devices

- Jelly Max no speaker audio; in the same thread an Atom owner reports regularly losing speaker sound until power-off/restart:
  https://www.reddit.com/r/unihertz/comments/1kpl4j4/

- Jelly Star speaker stopped working; users discuss software/hardware troubleshooting and a workaround video:
  https://www.reddit.com/r/unihertz/comments/1ejjms1/

## Comparable firmware bug elsewhere

Fairphone documented an Android issue where the microphone could stop working until reboot, and later shipped a software update explicitly fixing it:

- https://forum.fairphone.com/t/android-10-reboot-restores-audio-recording-capabilities/65040
- https://forum.fairphone.com/t/an-update-from-our-ceo-reported-software-issues-and-our-next-steps/66306/1

This does not establish a shared root cause; it simply shows that "audio input dies until reboot" can be a firmware/software defect rather than microphone hardware failure.
