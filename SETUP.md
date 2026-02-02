# Quick Setup Guide

Get VirtualCAmer running in 5 minutes!

---

## Step 1: Install Prerequisites

### Install LSPosed (if not already installed)

1. Download LSPosed from: https://github.com/LSPosed/LSPosed/releases
2. Flash via Magisk Manager or KernelSU
3. Reboot device

### Setup RTMP Server

**Option A: Using Docker (Easiest)**
```bash
docker run -d -p 1935:1935 tiangolo/nginx-rtmp
```

**Option B: Using OBS Studio**
1. Download OBS: https://obsproject.com/
2. Settings → Stream → Custom
3. Server: `rtmp://localhost/live`
4. Stream Key: `stream`

---

## Step 2: Install VirtualCAmer

```bash
# Install APK
adb install VirtualCAmer.apk

# Or drag and drop APK to device
```

---

## Step 3: Enable in LSPosed

1. Open **LSPosed Manager**
2. Go to **Modules** tab
3. Find **VirtualCAmer** and toggle it ON
4. Tap on **VirtualCAmer** → **Scope**
5. Select apps you want to inject (or use default list)
6. **Reboot device** (important!)

---

## Step 4: Configure RTMP URL

1. Open **VirtualCAmer** app
2. Enter your RTMP URL in the text field

### RTMP URL Examples

**For local server:**
```
rtmp://localhost:1935/live/stream
```

**For Android emulator (BlueStacks, etc.):**
```
rtmp://10.0.2.2:1935/live/stream
```

**For network server:**
```
rtmp://192.168.1.100:1935/live/stream
```

3. Select camera:
   - **Back Camera** - For photos/videos (most common)
   - **Front Camera** - For selfies/video calls

4. Toggle **Enable Injection** to ON
5. Tap **Connect to Stream**
6. Wait for "Stream connected" status

---

## Step 5: Start Streaming

### From OBS Studio
1. Add video source (Screen Capture, Video Capture, Media Source, etc.)
2. Click **Start Streaming**

### From FFmpeg (file)
```bash
ffmpeg -re -i your_video.mp4 -c copy -f flv rtmp://localhost/live/stream
```

### From FFmpeg (desktop)

**Linux:**
```bash
ffmpeg -f x11grab -i :0.0 -c:v libx264 -preset ultrafast \
  -f flv rtmp://localhost/live/stream
```

**Windows:**
```bash
ffmpeg -f gdigrab -i desktop -c:v libx264 -preset ultrafast \
  -f flv rtmp://localhost/live/stream
```

**macOS:**
```bash
ffmpeg -f avfoundation -i "1" -c:v libx264 -preset ultrafast \
  -f flv rtmp://localhost/live/stream
```

---

## Step 6: Test in Target App

1. Open **Instagram**, **TikTok**, or any supported app
2. Start the camera
3. Your RTMP stream should appear! 🎉

---

## ✅ Verification Checklist

Before testing, make sure:

- [ ] LSPosed module is **enabled**
- [ ] Device was **rebooted** after enabling module
- [ ] Target app is in LSPosed **scope**
- [ ] RTMP server is **running**
- [ ] Stream is **active** (test with ffplay/VLC)
- [ ] RTMP URL in app is **correct**
- [ ] Injection toggle is **ON**
- [ ] Status shows "**Stream connected**"

---

## 🐛 Quick Troubleshooting

### Black Screen in App

**Solution:**
```bash
# Check logs
adb logcat | grep VirtualCAmer

# Restart the app
# Disconnect and reconnect in VirtualCAmer
```

### "Connection failed"

**For emulator:**
- Use `10.0.2.2` instead of `localhost`

**For local network:**
- Check firewall allows port 1935
- Ping the server: `ping 192.168.1.100`

### App Crashes

**Solution:**
1. Disable injection in VirtualCAmer
2. Test if app works normally
3. Try different camera (front vs back)
4. Check LSPosed logs

---

## 📱 Supported Apps (Default Scope)

✅ Instagram
✅ TikTok
✅ Snapchat
✅ WhatsApp
✅ Zoom
✅ Discord
✅ Facebook
✅ YouTube Creator
✅ Twitter/X
✅ LinkedIn
✅ Skype
✅ Google Meet
✅ Microsoft Teams
✅ WeChat
✅ Viber
✅ Messenger
✅ Google Duo

---

## 🎬 Recommended Workflow

### For Content Creation

1. **Prepare content**
   - Record video or prepare screen capture
   - Ensure 720p or 1080p resolution
   - 30 FPS recommended

2. **Start RTMP stream**
   ```bash
   ffmpeg -re -i content.mp4 -c:v libx264 -preset ultrafast \
     -b:v 2000k -f flv rtmp://localhost/live/stream
   ```

3. **Open target app**
   - Instagram Stories / TikTok / etc.
   - Start camera
   - Record or go live

4. **Post directly**
   - Your pre-recorded content appears live!

### For Live Streaming

1. **Setup scene in OBS**
   - Add sources (screen, camera, etc.)
   - Configure layout

2. **Start OBS streaming**
   - RTMP URL: `rtmp://localhost/live`
   - Stream key: `stream`

3. **Open Instagram/etc.**
   - Go Live feature
   - Your OBS scene appears!

---

## ⚡ Performance Tips

### For smooth 1080p30 streaming:

```bash
ffmpeg -re -i input.mp4 \
  -c:v libx264 \
  -preset ultrafast \      # Fast encoding
  -tune zerolatency \      # Low latency
  -profile:v baseline \    # Compatible
  -b:v 2000k \            # 2 Mbps
  -maxrate 2000k \
  -bufsize 4000k \
  -vf scale=1920:1080 \   # Force 1080p
  -r 30 \                 # 30 FPS
  -c:a aac \
  -b:a 128k \
  -f flv rtmp://localhost/live/stream
```

### For low-end devices (480p15):

```bash
ffmpeg -re -i input.mp4 \
  -c:v libx264 \
  -preset ultrafast \
  -vf scale=640:480 \
  -r 15 \                 # 15 FPS
  -b:v 500k \            # 500 Kbps
  -c:a aac \
  -b:a 64k \
  -f flv rtmp://localhost/live/stream
```

---

## 🔍 Advanced Configuration

### Add Custom App

1. Find package name:
   ```bash
   adb shell pm list packages | grep "app_name"
   ```

2. Edit `xposed_scope`:
   ```bash
   adb pull /data/app/.../base.apk
   # Extract, edit assets/xposed_scope
   # Add: com.your.custom.app
   ```

3. Reinstall and reboot

### Change Buffer Size

Edit `RtmpFrameProvider.kt`:
```kotlin
frameBuffer = FrameBuffer(120) // 120 frames instead of 60
```

### Adjust Reconnection

Edit `RtmpStreamReader.kt`:
```kotlin
private val maxReconnectAttempts = 10 // Instead of 5
private val reconnectDelayMs = 1000L  // 1 second instead of 2
```

---

## 📞 Get Help

- **GitHub Issues**: https://github.com/yourusername/VirtualCAmer/issues
- **Logs**: `adb logcat | grep -E "VirtualCAmer|RTMP"`
- **Test RTMP**: `ffplay rtmp://your-url`
- **Check LSPosed**: LSPosed Manager → Logs

---

**You're all set! Happy streaming! 🎥**
