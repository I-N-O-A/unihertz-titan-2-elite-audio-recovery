# Titan Audio Repair release signing

The **public certificate** and certificate fingerprints are committed here.

The private PKCS#12 keystore is committed only in **AES-256-CBC encrypted form**:
`TitanAudioRepair-release.p12.enc`.

The decryption password is intentionally **not** stored in this public repository.
Keep the separate `TitanAudioRepair-signing-secret.txt` file private.

To decrypt locally:

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000             -in signing/TitanAudioRepair-release.p12.enc             -out TitanAudioRepair-release.p12
```

Use the password from the private signing-secret file.

Future APK builds must use this exact keystore. Do not generate a new signing key.
