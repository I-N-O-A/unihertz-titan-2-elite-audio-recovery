# Titan Audio Recovery — Kurzfassung Deutsch

Dieses Repository dokumentiert einen wiederkehrenden Audio-Ausfall auf einem **Unihertz Titan 2 Elite**: **Mikrofon und Lautsprecher/Receiver fallen gleichzeitig aus und bleiben bis zum Neustart tot.** Auch die Unihertz-Factory-Tests für Audio In/Out funktionieren im Fehlerzustand nicht.

Der wichtigste Bugreport-Befund ist:

```text
AudioALSAStreamIn: getCapturePosition(), mCaptureHandler == NULL
StreamHAL: Error from HAL stream in function get_capture_position: No data available
```

Gleichzeitig hängt der MediaTek-HAL beim DSP-Stop im Pfad `AudioALSACaptureDataProviderDspRaw::close() -> pthread_join()`, während ein Reader-Thread in `AudioALSACaptureDataProviderDspRaw::readThread() -> pcm_read()` steckt. Im Kernel erscheinen wiederholte SCP-Reset-/Recovery-Ereignisse.

Arbeitshypothese: **MediaTek SCP/Audio-DSP/DspRaw-Recovery-Bug**, nicht ein Fehler einer einzelnen App.

Die beigefügte App **Titan Audio Repair** benötigt weder Root noch ADB. In einem Test kam Audio nach Mic-Probe(s) + Speaker-Test wieder zurück. Noch ist nicht bewiesen, welcher einzelne Schritt die Recovery auslöst.

Der rohe Android-Bugreport wird bewusst **nicht öffentlich hochgeladen**, weil Bugreports persönliche Konten, Netzwerkdaten und andere sensible Informationen enthalten können. Stattdessen enthält das Repo die relevanten technischen Auszüge und SHA256-Werte.
