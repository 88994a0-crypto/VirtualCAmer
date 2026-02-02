# VirtualCAmer - RTMP Camera Injector for Android

**An Xposed/LSPosed module that replaces Android camera feeds with RTMP streams in real-time.**

Perfect for streaming pre-recorded content, desktop captures, or remote camera feeds to Instagram, TikTok, Snapchat, and other social media apps.

---

## ✨ Features

- ✅ **Complete RTMP Streaming** - Uses FFmpeg/JavaCV for professional-grade RTMP stream decoding
- ✅ **Multi-API Support** - Hooks Camera, Camera2, and CameraX APIs for maximum app compatibility
- ✅ **Smart Frame Conversion** - Automatic YUV/RGB conversion and resizing to match camera expectations
- ✅ **Performance Optimized** - 60-frame buffer, thread-safe operations, minimal CPU usage
- ✅ **Auto-Reconnection** - Handles network interruptions gracefully with exponential backoff
- ✅ **Real-time Preview** - ExoPlayer-based preview in configuration app
- ✅ **Front/Back Camera Selection** - Choose which camera to replace
- ✅ **Easy Configuration** - Simple UI for RTMP URL and camera selection

---

## 📱 Supported Apps

Out of the box support for:

- Instagram (Stories, Reels, Live)
- TikTok
- Snapchat
- WhatsApp Video Calls
- Zoom
- Discord
- Facebook (+ Messenger)
- YouTube Creator
- Twitter/X
- LinkedIn
- Skype
- Google Meet
- Microsoft Teams
- WeChat
- Viber
- Google Duo

**Add custom apps** by editing `xposed_scope`

---

## 🚀 Quick Start

### 1. Prerequisites

- Rooted Android device/emulator
- LSPosed framework installed
- RTMP server (local or remote)

### 2. Install

```bash
adb install VirtualCAmer.apk
```

### 3. Enable in LSPosed

- Open LSPosed Manager → Modules
- Enable "VirtualCAmer"
- Reboot device

### 4. Configure

- Open VirtualCAmer app
- Enter RTMP URL: `rtmp://10.0.2.2:1935/live/stream`
- Select camera (Front/Back)
- Enable injection
- Connect

### 5. Test

- Open Instagram/TikTok
- Start camera
- Your RTMP stream appears! 🎉

---

## 🎥 RTMP Server Setup

### Quick Setup (Docker)

```bash
docker run -d -p 1935:1935 tiangolo/nginx-rtmp
```

### Stream from FFmpeg

```bash
ffmpeg -re -i video.mp4 -c copy -f flv rtmp://localhost/live/stream
```

### Stream from OBS

1. Settings → Stream
2. Custom: `rtmp://localhost/live`
3. Stream Key: `stream`
4. Start Streaming

### For Android Emulator

Use `10.0.2.2` instead of `localhost`:
```
rtmp://10.0.2.2:1935/live/stream
```

---

## 🔧 Troubleshooting

### No Video / Black Screen

```bash
# Check logs
adb logcat | grep -E "VirtualCAmer|RTMP"

# Verify RTMP stream
ffplay rtmp://your-url

# Checklist
✓ LSPosed module enabled for app
✓ Device rebooted after enabling
✓ Injection toggle is ON
✓ RTMP server is running
✓ Network accessible
```

### Connection Failed

- Use `10.0.2.2` for emulator (not `localhost`)
- Check firewall (port 1935)
- Verify URL format: `rtmp://host:port/app/key`

### Low FPS / Lag

- Reduce stream quality (720p → 480p)
- Lower bitrate (2000 → 1000 kbps)
- Check device CPU usage

---

## 📊 Optimal Settings

```
Resolution: 1280x720
FPS: 30
Codec: H.264 (baseline)
Bitrate: 2000 kbps
Format: FLV
```

---

## 🛠️ Development

### Build

```bash
git clone https://github.com/yourusername/VirtualCAmer.git
cd VirtualCAmer
./gradlew assembleDebug
```

### Project Structure

```
app/src/main/java/com/example/virtualcamer/
├── MainActivity.kt              # UI
└── xposed/
    ├── XposedInit.kt           # Entry point
    ├── CameraHook.kt           # Legacy API
    ├── Camera2Hook.kt          # Camera2 API
    ├── CameraXHook.kt          # CameraX API
    ├── RtmpStreamReader.kt     # FFmpeg decoder
    ├── RtmpFrameProvider.kt    # Frame manager
    ├── FrameBuffer.kt          # Buffering
    ├── FrameConverter.kt       # Format conversion
    └── InjectionConfig.kt      # Settings
```

---

## 🔐 Privacy

- No data collection
- Fully open source
- Runs locally
- No cloud services

---

## 📜 License

MIT License

---

## 🙏 Credits

- LSPosed Team
- JavaCV/FFmpeg
- ExoPlayer
- AOSP

---

**Made with ❤️ for the Android community**
