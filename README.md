# VirtualCAmer

This project provides an Android APK starter that lets you configure an RTMP endpoint and toggle
controls for audio, video, live streaming, and decoder mode while forwarding the decoded frames to
an OBS virtual camera device (for example, `/dev/video0`).

## What this app does today
- Collects RTMP server URL + stream key.
- Accepts an OBS virtual camera device path (for example, `/dev/video0`).
- Uses ExoPlayer with the FFmpeg decoder extension for software decoding or hardware codecs for
  accelerated decoding.
- Provides switches for audio, video, and live streaming.
- Provides a soft/hard decoder mode selector.
- Shows status updates (buffering, ready, errors) in the UI.

## Virtual camera setup (OBS)
The app expects OBS (latest version with the built-in Virtual Camera output) to be running and the
Virtual Camera started on the host. It auto-selects the first available `/dev/video*` path (preferring
`/dev/video0`). You can still override the detected device path in the UI if you need to.

When the app is running, it opens the device and forwards decoded frames into it through a JNI bridge.
Frames captured from ExoPlayer are converted from ARGB into planar YUV420 (I420) in Kotlin before they
are written to the virtual camera. The native module validates the expected frame size.

## Android 11/12 compatibility notes
- Android 11 (API 30) and Android 12 (API 31) are supported because the app targets API 34 while
  remaining compatible with API 24+.
- OBS must be running with Virtual Camera enabled so the `/dev/video*` node exists before the app
  starts forwarding frames.
- The FFmpeg decoder extension ships as part of the build dependencies. Select **Soft decoding** in
  the UI to prefer FFmpeg-based software decoding. **Hard decoding** forces MediaCodec hardware
  decoders.

## How to use
1. Launch OBS (latest version) and start the **Virtual Camera** output.
2. Launch the app and enter the RTMP server URL and stream key for the **incoming** stream.
3. Confirm the OBS virtual camera device path (for example, `/dev/video0`).
4. Toggle **Audio** to enable audio playback. Toggle **Video** to enable frame forwarding to the
   virtual camera. Toggle **Live streaming** to start/stop the RTMP playback session.
5. Choose **Soft decoding** (FFmpeg) or **Hard decoding** (MediaCodec) and tap **Apply settings**.
6. Watch the status text. When it says **RTMP stream ready**, the virtual camera is live and can be
   selected in camera-aware apps.

## Build
Use Android Studio (Arctic Fox or newer) or the Gradle wrapper:

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Current limitations
- OBS Virtual Camera must be started before the app can detect `/dev/video*` nodes.
- Only RTMP playback is supported as the input source.
- Video forwarding is capped to a low frame rate (the app currently throttles to ~15 fps).
- Audio is played back on the device only; it is not forwarded into the virtual camera device.
