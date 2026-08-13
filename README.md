# FloatSpace

FloatSpace is an experimental Android floating-window launcher for devices whose firmware does not provide native freeform or split-screen support. It uses an app-owned virtual display, a movable overlay window, and Shizuku for privileged app launching and input forwarding.

> Current status: **0.3.1 alpha**. Tested by the developer build pipeline; device behavior still depends on OEM restrictions.

## Features

- Virtual floating windows independent of native freeform firmware
- Move, resize, minimize, maximize, and close controls
- Virtual top/bottom split layout
- Floating sidebar with favorite applications
- Compact monochrome UI with restrained 8–12 dp corners
- Shizuku Wireless Debugging support
- Indonesian diagnostics and setup guide
- Local `info.txt`, `error.txt`, and `crash.txt` logs
- No Internet permission and no analytics

## Requirements

- Android 8.0 or newer
- Shizuku 11+; current stable version recommended
- Wireless debugging or root-backed Shizuku
- Permission to display over other applications

## Redmi A2 setup

1. Enable Developer options.
2. Enable Wireless debugging.
3. Start Shizuku and grant FloatSpace access.
4. Open FloatSpace and select **Hubungkan**.
5. Enable **Bilah samping** and allow display over other apps.
6. Run **Cek sistem**. `UJI VIRTUAL DISPLAY` should report `BERHASIL`.
7. Test a lightweight app first before WhatsApp or video editors.

## Logs

```text
/storage/emulated/0/Android/media/io.xystudio.floatspace/logs/
```

Attach `info.txt`, `error.txt`, and `crash.txt` when reporting a problem. Logs remain local unless the user shares them manually.

## Building

```bash
./gradlew assembleDebug
```

Every push to `main` builds a debug APK. Tags matching `v*` build a signed APK and publish it to GitHub Releases.

## Release secrets

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow uses GitHub's built-in `GITHUB_TOKEN` with `contents: write`; no personal token is stored as an Actions secret.

## License

Apache License 2.0. See `LICENSE` and `NOTICE`.
