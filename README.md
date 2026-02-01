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
The app expects a v4l2loopback device to exist at the device path you enter. On a rooted emulator or
device, install and create the virtual camera before launching the app:

```bash
su
modprobe v4l2loopback devices=1 video_nr=0 card_label="VirtualCam" exclusive_caps=1
```

Confirm the device exists:

```bash
ls -l /dev/video0
```

When the app is running, enter the device path (for example, `/dev/video0`) in the UI. The app will try
to open the device and forward decoded frames into it. You still need a native bridge that converts the
decoded frames into the raw format expected by v4l2loopback.

## Build
Use Android Studio or Gradle:

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
