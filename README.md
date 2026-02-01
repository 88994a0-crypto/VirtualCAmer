# VirtualCAmer

This project provides an Android APK starter that lets you configure an OBS RTMP endpoint and toggle
controls for audio, video, live streaming, and decoder mode.

## What this app does today
- Collects RTMP server URL + stream key.
- Provides switches for audio, video, and live streaming.
- Provides a soft/hard decoder mode selector.
- Shows the chosen configuration in a status block.

## Next steps to enable real streaming
To turn this UI into a real-time camera replacement driven by OBS/RTMP, you still need to integrate a
streaming SDK (for example, ExoPlayer/RTMP, LibVLC, or a commercial SDK). Hook the toggles to start/stop
receivers, apply decoder mode, and route the decoded frames into a virtual camera or media projection
pipeline.

## Build
Use Android Studio or Gradle:

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
