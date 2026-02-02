# Troubleshooting Guide

Comprehensive solutions for common VirtualCAmer issues.

---

## 🔍 Diagnostic Commands

### Check Module Status
```bash
# Check if module is loaded
adb shell "su -c 'ls /data/adb/modules/*/module.prop | xargs grep VirtualCAmer'"

# Check LSPosed status
adb logcat | grep LSPosed
```

### View Logs
```bash
# VirtualCAmer specific logs
adb logcat -s VirtualCAmer:* RTMP:* Camera2Hook:* CameraXHook:*

# All relevant logs
adb logcat | grep -E "VirtualCAmer|RTMP|Camera2Hook|CameraXHook|FrameBuffer"

# Save logs to file
adb logcat > virtualcamer_logs.txt
```

### Test RTMP Stream
```bash
# Test with ffplay
ffplay rtmp://your-server:1935/live/stream

# Test with VLC
vlc rtmp://your-server:1935/live/stream

# Check connection
telnet your-server 1935
```

---

## ❌ Black Screen / No Video

### Symptom
Camera opens but shows black screen or frozen frame.

### Diagnosis
```bash
# Check if frames are being received
adb logcat | grep "Streaming:"

# Look for frame injection errors
adb logcat | grep "Frame injection"
```

### Solutions

#### 1. Verify RTMP Stream is Active
```bash
# Play stream locally
ffplay rtmp://10.0.2.2:1935/live/stream

# If this works, RTMP is fine
# If not, check RTMP server
```

#### 2. Check Module Scope
```bash
# List enabled apps in LSPosed
# Make sure target app is checked
```
**Fix:**
- Open LSPosed Manager
- Tap VirtualCAmer module
- Go to Scope tab
- Enable target app
- Reboot device

#### 3. Verify Configuration
```bash
# Check settings
adb logcat | grep "RTMP frame provider connected"
```
**Fix:**
- Open VirtualCAmer app
- Verify RTMP URL is correct
- Toggle injection OFF then ON
- Tap "Connect to Stream" again

#### 4. Frame Format Mismatch
```bash
# Look for format errors
adb logcat | grep "convertAndResize"
```
**Fix:**
- Try different video resolution (720p → 480p)
- Ensure RTMP stream is H.264 (not H.265)
- Use baseline profile for H.264

#### 5. Permission Issues
```bash
# Check permissions
adb shell dumpsys package com.example.virtualcamer | grep permission
```
**Fix:**
- Manually grant all permissions
- Settings → Apps → VirtualCAmer → Permissions
- Enable Camera, Internet, Storage

---

## 🔌 Connection Failed

### Symptom
"Connection failed" or "Failed to connect to RTMP stream"

### Diagnosis
```bash
# Check network connectivity
adb shell ping -c 3 your-server

# Check if port 1935 is open
adb shell "nc -zv your-server 1935"
```

### Solutions

#### 1. Emulator Network Issues
**Problem:** Using `localhost` in emulator

**Fix:**
Use `10.0.2.2` instead:
```
rtmp://10.0.2.2:1935/live/stream
```

#### 2. Firewall Blocking Port 1935
**Problem:** Server firewall blocking RTMP

**Fix (Linux):**
```bash
sudo ufw allow 1935/tcp
sudo iptables -A INPUT -p tcp --dport 1935 -j ACCEPT
```

**Fix (Windows):**
- Windows Defender Firewall
- Advanced Settings → Inbound Rules
- New Rule → Port → TCP 1935 → Allow

#### 3. Server Not Running
**Problem:** RTMP server is down

**Fix:**
```bash
# Check if nginx-rtmp is running
docker ps | grep nginx-rtmp

# Restart server
docker restart nginx-rtmp

# Or start new one
docker run -d -p 1935:1935 tiangolo/nginx-rtmp
```

#### 4. Wrong URL Format
**Problem:** Invalid RTMP URL

**Correct Format:**
```
rtmp://[host]:[port]/[application]/[stream-key]

Examples:
✅ rtmp://10.0.2.2:1935/live/stream
✅ rtmp://192.168.1.100:1935/app/mystream
❌ http://server.com/stream
❌ rtmp://server.com (missing application/key)
```

#### 5. Network Unreachable
**Problem:** Device can't reach server

**Fix:**
```bash
# Check route
adb shell ip route

# Check DNS
adb shell nslookup your-server

# Try direct IP instead of hostname
```

---

## 💥 App Crashes

### Symptom
Target app crashes when opening camera.

### Diagnosis
```bash
# Capture crash logs
adb logcat -s AndroidRuntime:E

# Check for hook errors
adb logcat | grep "XposedBridge"
```

### Solutions

#### 1. Incompatible App Version
**Problem:** App updated with new camera API

**Fix:**
- Downgrade app to previous version
- Report issue on GitHub with app version
- Temporarily disable VirtualCAmer for that app

#### 2. Memory Issues
**Problem:** Frame buffers exhausting memory

**Fix:**
Reduce buffer size in `RtmpFrameProvider.kt`:
```kotlin
frameBuffer = FrameBuffer(30) // Down from 60
```

#### 3. Threading Issues
**Problem:** Race condition in frame delivery

**Fix:**
```bash
# Check for threading errors
adb logcat | grep "ConcurrentModificationException\|IllegalStateException"
```

Try increasing delay between frames in `RtmpStreamReader.kt`

#### 4. Format Incompatibility
**Problem:** App requires specific frame format

**Fix:**
```bash
# Check what format app requests
adb logcat | grep "setPreviewFormat\|ImageFormat"

# Try forcing different format in code
```

---

## 🐌 Low FPS / Laggy Video

### Symptom
Stream works but is choppy or low frame rate.

### Diagnosis
```bash
# Check actual FPS
adb logcat | grep "Streaming:.*fps"

# Monitor CPU usage
adb shell top | grep com.instagram.android
```

### Solutions

#### 1. High Resolution Stream
**Problem:** 1080p is too demanding

**Fix - Reduce Resolution:**
```bash
ffmpeg -re -i input.mp4 \
  -vf scale=640:480 \    # 480p instead of 1080p
  -r 15 \                # 15 fps instead of 30
  -b:v 500k \            # Lower bitrate
  -f flv rtmp://localhost/live/stream
```

#### 2. Slow Decoding
**Problem:** Software decoding is slow

**Fix - Use Hardware Encoding:**
```bash
# Encode with h264_nvenc (NVIDIA)
ffmpeg -i input.mp4 -c:v h264_nvenc -preset fast \
  -f flv rtmp://localhost/live/stream

# Or h264_qsv (Intel Quick Sync)
ffmpeg -i input.mp4 -c:v h264_qsv -preset fast \
  -f flv rtmp://localhost/live/stream
```

#### 3. Network Latency
**Problem:** Stream buffering

**Fix - Reduce Latency:**
```bash
ffmpeg -re -i input.mp4 \
  -tune zerolatency \
  -fflags nobuffer \
  -flags low_delay \
  -f flv rtmp://localhost/live/stream
```

#### 4. CPU Throttling
**Problem:** Device overheating

**Fix:**
- Lower stream quality
- Close background apps
- Cool down device
- Reduce buffer size in code

---

## 🔇 No Audio

### Symptom
Video works but no audio in video calls.

### Solutions

#### 1. Audio Not in RTMP Stream
**Problem:** Stream is video-only

**Fix - Add Audio:**
```bash
ffmpeg -re -i input.mp4 \
  -c:v copy \
  -c:a aac -b:a 128k \   # Add audio encoding
  -f flv rtmp://localhost/live/stream
```

#### 2. Audio Passthrough Not Implemented
**Note:** Current version injects VIDEO only

**Workaround:**
- Use device microphone for audio
- Or route system audio separately

---

## 🔄 Module Not Working After Reboot

### Symptom
Module works initially but stops after reboot.

### Solutions

#### 1. LSPosed Not Enabled
**Fix:**
```bash
# Enable LSPosed in Magisk/KernelSU
# Ensure module is active
adb shell "su -c 'ls /data/adb/lsposes/*/manager'"
```

#### 2. Scope Not Saved
**Fix:**
- Re-enable module in LSPosed
- Re-select apps in Scope
- Force stop and clear cache of LSPosed Manager
- Reboot again

#### 3. SharedPreferences Lost
**Fix:**
```bash
# Check preferences exist
adb shell "su -c 'ls /data/data/com.example.virtualcamer/shared_prefs/'"

# If missing, reconfigure in app
```

---

## 📱 App-Specific Issues

### Instagram

**Issue:** Black screen in Stories
**Fix:**
- Use back camera mode
- Reduce resolution to 720p
- Disable beauty filters in Instagram

**Issue:** Stream stops after few seconds
**Fix:**
- Instagram may pause inactive cameras
- Keep screen on
- Enable "Keep screen on" in Developer Options

### TikTok

**Issue:** Flipped/mirrored video
**Fix:**
```bash
# Flip video horizontally
ffmpeg -i input.mp4 -vf hflip -c:a copy output.mp4
```

**Issue:** Wrong aspect ratio
**Fix:**
- Use 9:16 vertical video
- Or crop to 9:16:
```bash
ffmpeg -i input.mp4 -vf "crop=ih*9/16:ih" output.mp4
```

### Zoom

**Issue:** Video freezes during call
**Fix:**
- Lower resolution to 480p
- Reduce frame rate to 15fps
- Close other apps

### Snapchat

**Issue:** Camera not detected
**Fix:**
- Snapchat is aggressive with anti-spoofing
- Use front camera mode
- May need SafetyNet bypass

---

## 🔐 SafetyNet / Root Detection

### Symptom
App detects root and refuses to work.

### Solutions

#### 1. Use Magisk Hide
```bash
# Enable Magisk Hide for app
# Rename Magisk Manager
# Use Shamiko module
```

#### 2. Use Universal SafetyNet Fix
```bash
# Install Universal SafetyNet Fix module
# Reboot
# Test with SafetyNet checker app
```

#### 3. Use App Cloning
```bash
# Clone app with Shelter or Island
# Use VirtualXposed for cloned app
```

---

## 🛠️ Advanced Debugging

### Enable Verbose Logging

Edit `build.gradle`:
```kotlin
buildConfigField("boolean", "DEBUG_MODE", "true")
```

### Dump Camera Properties
```bash
adb shell dumpsys media.camera
```

### Check Memory Usage
```bash
adb shell dumpsys meminfo com.example.virtualcamer
```

### Profile Performance
```bash
adb shell am profile start com.instagram.android /sdcard/profile.trace
# Use camera
adb shell am profile stop com.instagram.android
adb pull /sdcard/profile.trace
```

### Inspect Xposed Logs
```bash
adb logcat -s LSPosed:* Xposed:* EdXposed:*
```

---

## 📞 Still Need Help?

### Before Reporting Issue

Collect the following information:

1. **Device Info**
   ```bash
   adb shell getprop ro.build.version.release  # Android version
   adb shell getprop ro.product.model           # Device model
   ```

2. **Module Info**
   ```bash
   # LSPosed version
   # VirtualCAmer version
   # Target app name and version
   ```

3. **Logs**
   ```bash
   adb logcat > full_log.txt
   # Reproduce issue
   # Save log
   ```

4. **Configuration**
   - RTMP URL used
   - Camera selected (front/back)
   - Stream resolution and format

### Report Issue

GitHub Issues: https://github.com/yourusername/VirtualCAmer/issues

Include:
- [ ] Device and Android version
- [ ] LSPosed version
- [ ] VirtualCAmer version
- [ ] Target app and version
- [ ] Full logs
- [ ] RTMP stream details
- [ ] Steps to reproduce

---

## ✅ Quick Checklist

Before asking for help, verify:

- [ ] Device is rooted
- [ ] LSPosed is installed and active
- [ ] VirtualCAmer module is enabled in LSPosed
- [ ] Device was rebooted after enabling
- [ ] Target app is in module scope
- [ ] RTMP server is running and accessible
- [ ] Stream plays fine in ffplay/VLC
- [ ] RTMP URL is correct format
- [ ] Injection is enabled in VirtualCAmer app
- [ ] Status shows "Stream connected"
- [ ] Permissions granted to VirtualCAmer
- [ ] Tried restarting target app
- [ ] Checked logs for errors

---

**Most issues are configuration-related. Double-check everything! 🔍**
