# Privacy policy for the evidence package

The original Android bugreport ZIP and its 157 MB plaintext dump are intentionally **not included** in this public repository.

Android bugreports may contain highly sensitive data such as:

- account identifiers;
- phone/network information;
- Wi-Fi/Bluetooth identifiers;
- app/package state;
- notification metadata;
- file paths and other device-specific information.

Instead, this repository contains narrowly scoped technical excerpts showing the MediaTek audio-HAL and SCP/DSP evidence needed to reproduce the diagnosis.

The original local bugreport is identified by SHA256 in `evidence/ORIGINAL_PRIVATE_FILE_HASHES.txt` so a vendor can confirm that later private evidence comes from the same source file.

Likewise, no Android signing keystore or password is committed. The prebuilt APK is included, but its private signing key remains private.
