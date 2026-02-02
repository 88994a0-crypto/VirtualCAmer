# VirtualCAmer v2.0 - Quick Start Guide

## 🚀 Quick Installation (5 Minutes)

### Prerequisites
- ✅ Rooted Android device (Android 7.0+)
- ✅ LSPosed or EdXposed installed
- ✅ RTMP stream source running

### Step-by-Step Installation

#### 1. Install the APK (1 minute)
```bash
adb install VirtualCAmer-v2.0.apk
# OR transfer APK to device and install manually
```

#### 2. Enable in LSPosed (1 minute)
1. Open LSPosed app
2. Go to "Modules"
3. Enable "VirtualCAmer"
4. Go to "VirtualCAmer" module settings
5. Add target apps to scope:
   - For Chrome: `com.android.chrome`
   - For Camera: `com.android.camera2` or your device's camera package
   - For all apps: Check "Apply to all apps" (not recommended)
6. Reboot device

#### 3. Configure Stream (2 minutes)
1. Open VirtualCAmer app
2. Toggle "Enable Injection" ON
3. Enter RTMP URL: `rtmp://your-server/live/stream`
4. Select camera (front or back)
5. (Optional) Tap "Select Apps" to choose specific apps
6. Tap "Apply Settings"
7. Wait for "Stream connected ✓"

#### 4. Test (1 minute)
1. Open Chrome
2. Go to: `https://webrtc.github.io/samples/src/content/getusermedia/gum/`
3. Click "Open camera"
4. Should see your RTMP stream instead of real camera! 🎉

## 🔧 Common Setup Scenarios

### Scenario 1: Chrome Video Calls
```
Target Apps: Chrome, Meet, Zoom
Camera: Front camera
Apps to Select:
  - com.android.chrome
  - com.google.android.apps.meetings
  - us.zoom.videomeetings
```

### Scenario 2: Instagram/TikTok Streaming
```
Target Apps: Instagram, TikTok
Camera: Back camera (usually used for main camera)
Apps to Select:
  - com.instagram.android
  - com.zhiliaoapp.musically
```

### Scenario 3: Testing (All Apps)
```
Target Apps: All apps
Camera: Front or Back
Apps to Select: Leave empty (inject into all)
⚠️ Warning: May cause issues in some apps
```

## 🐛 Troubleshooting (Most Common Issues)

### Issue 1: "Stream not visible in Chrome"
```bash
# Fix:
1. Force stop Chrome: Settings → Apps → Chrome → Force Stop
2. Clear Chrome cache: Settings → Apps → Chrome → Storage → Clear Cache
3. Restart device
4. Try again
```

### Issue 2: "Config not readable" in logs
```bash
# Fix (Android 11+):
1. Uninstall VirtualCAmer
2. Reinstall APK
3. Reboot device
4. Reconfigure settings

# Check ContentProvider is accessible:
adb shell content query --uri content://com.example.virtualcamer.config/config
# Should return config values
```

### Issue 3: "No RTMP frame available"
```bash
# Fix:
1. Verify RTMP stream is broadcasting:
   ffplay rtmp://your-url  # Should show video
   
2. Check network connectivity:
   ping your-rtmp-server
   
3. Verify RTMP URL in app (no typos)
4. Check firewall isn't blocking RTMP (port 1935)
```

### Issue 4: "Module not active" in LSPosed
```bash
# Fix:
1. Open LSPosed
2. Go to VirtualCAmer module
3. Check "Enable module" is ON
4. Verify target apps are in scope
5. Reboot device (REQUIRED after enabling)
```

## 📱 RTMP Server Setup (Bonus)

### Option 1: nginx-rtmp (Recommended for testing)
```bash
# On Linux/Mac:
docker run -p 1935:1935 -e RTSP_STREAM=rtmp://source alfg/nginx-rtmp

# Stream to it:
ffmpeg -re -i input.mp4 -c:v libx264 -preset veryfast -b:v 2000k \
  -f flv rtmp://localhost/live/stream
  
# Use in VirtualCAmer:
rtmp://YOUR_IP/live/stream
```

### Option 2: SRS (Production-ready)
```bash
docker run -p 1935:1935 -p 1985:1985 -p 8080:8080 ossrs/srs:4
# Stream and use same as nginx-rtmp
```

### Option 3: OBS Studio (Easiest for non-technical users)
1. Download OBS Studio: https://obsproject.com/
2. Add sources (Screen Capture, Video, etc.)
3. Settings → Stream → Service: "Custom"
4. Server: `rtmp://YOUR_IP/live`
5. Stream Key: `stream`
6. Start Streaming
7. Use in VirtualCAmer: `rtmp://YOUR_IP/live/stream`

## 🎯 Testing Checklist

Before reporting issues, verify:
- [ ] Android version 7.0+ (check: Settings → About)
- [ ] LSPosed installed and working (check: LSPosed app opens)
- [ ] Module enabled in LSPosed (check: Modules list shows VirtualCAmer enabled)
- [ ] Target apps in scope (check: Module → Scope list)
- [ ] Rebooted after enabling module
- [ ] RTMP stream is broadcasting (test with ffplay)
- [ ] RTMP URL correct in app (no typos)
- [ ] Injection toggle is ON in app
- [ ] App selector has apps chosen (or left empty for all)
- [ ] Camera permission granted to target app

## 📊 Expected Performance

### Normal Operation
- Latency: 50-200ms (RTMP network delay)
- CPU: 5-10% additional
- Memory: ~50MB additional
- Battery: ~5-8% per hour additional
- Frame rate: Matches RTMP source (typically 30fps)

### If experiencing issues:
- High CPU (>20%): Lower RTMP resolution
- High latency (>500ms): Check network, reduce RTMP buffer
- Frame drops: Close background apps, lower resolution
- App crashes: Check logs, report issue with logcat

## 🔍 Verification Commands

```bash
# Check if module is loaded
adb shell "ps -A | grep xposed"

# Check VirtualCAmer logs
adb logcat | grep VirtualCAmer

# Check if ContentProvider working
adb shell content query --uri content://com.example.virtualcamer.config/config

# Check camera app permissions
adb shell dumpsys package YOUR_APP_PACKAGE | grep permission

# Test RTMP stream
ffplay -fflags nobuffer rtmp://your-url

# Check module in LSPosed
adb shell pm list packages | grep virtualcamer
```

## 🆘 Getting Help

### Before asking for help:
1. Check troubleshooting section above
2. Review README.md for detailed info
3. Run verification commands
4. Collect logs: `adb logcat > logs.txt`

### When reporting issues, include:
- Android version
- Device model
- LSPosed version
- Target app name and version
- RTMP URL format (hide IP/credentials)
- Relevant logcat output
- What you've already tried

## ✅ Success Indicators

You know it's working when:
- ✅ VirtualCAmer app shows "Stream connected ✓"
- ✅ Preview in app shows your RTMP stream
- ✅ Target app shows injected video instead of real camera
- ✅ No "Config not readable" errors in logs
- ✅ No crashes when opening camera in target app

## 🎉 You're Done!

If all checks pass, your setup is complete! You should now see your RTMP stream in place of the real camera in your target apps.

Enjoy your virtual camera! 📹✨
