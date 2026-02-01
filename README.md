# VirtualCAmer

This project provides an Android APK starter that lets you configure an OBS RTMP endpoint and toggle
controls for audio, video, live streaming, and decoder mode.

## What this app does today
- Collects RTMP server URL + stream key.
- Accepts a virtual camera device path for rooted devices/emulators (for example, `/dev/video0`).
- Provides switches for audio, video, and live streaming.
- Provides a soft/hard decoder mode selector.
- Shows the chosen configuration in a status block.

## Virtual camera setup (rooted devices/emulators)
The app now attempts to install the `v4l2loopback` module automatically on startup using `su`. It also
auto-selects the first available `/dev/video*` path (preferring `/dev/video0`). You can still override
the detected device path in the UI if you need to.

When the app is running, it opens the device and forwards decoded frames into it through a JNI bridge.
Frames captured from ExoPlayer are converted from ARGB into planar YUV420 (I420) in Kotlin before they
are written to the virtual camera. The native module configures the v4l2loopback stream for YUV420 and
validates the expected frame size.

If root access is unavailable or the module cannot be loaded, the app reports the failure and leaves
the device path unchanged so you can retry with a manually provisioned virtual camera.

## Build
Use Android Studio or Gradle:

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
