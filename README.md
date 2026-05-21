# PixelCapture — Professional Screen Recorder

A modern, dark-themed JavaFX screen recorder that captures video (H264/MP4) and microphone audio using FFmpeg via JavaCV.

---

## Requirements

| Tool | Version |
|------|---------|
| JDK  | 17 or higher |
| Maven | 3.8+ |

Download JDK 17+: https://adoptium.net/  
Download Maven: https://maven.apache.org/download.cgi  
Add both `JAVA_HOME` and Maven's `bin/` folder to your `PATH`.

---

## Project Structure

```
PixelCapture/
├── pom.xml
└── src/main/
    ├── java/com/screenrecorder/
    │   ├── Main.java                      ← JavaFX entry point
    │   ├── models/
    │   │   ├── AppSettings.java           ← User preferences bean
    │   │   ├── RecordingConfig.java       ← Per-session recording parameters
    │   │   ├── RecordingState.java        ← IDLE / RECORDING / PAUSED enum
    │   │   └── VideoQuality.java          ← Quality presets (LOW…ULTRA)
    │   ├── recorder/
    │   │   ├── ScreenCapture.java         ← AWT Robot screen grabber
    │   │   └── AudioCapture.java          ← javax.sound microphone capture
    │   ├── services/
    │   │   ├── RecordingService.java      ← Core state machine + FFmpeg writer
    │   │   └── SystemTrayService.java     ← Windows system tray icon
    │   ├── ui/
    │   │   ├── MainController.java        ← Main window controller
    │   │   ├── SettingsController.java    ← Settings window controller
    │   │   └── ScreenAreaSelector.java    ← Drag-to-select region overlay
    │   └── utils/
    │       ├── FileUtils.java             ← Path generation, time formatting
    │       ├── SettingsManager.java       ← Java Preferences API persistence
    │       └── ThemeManager.java          ← Dark/light CSS switcher
    └── resources/com/screenrecorder/
        ├── fxml/
        │   ├── main.fxml                  ← Main window layout
        │   └── settings.fxml             ← Settings window layout
        └── css/
            ├── dark-theme.css
            └── light-theme.css
```

---

## Build & Run

```bash
# 1. Download dependencies & compile
mvn compile

# 2. Run directly via JavaFX Maven plugin
mvn javafx:run

# 3. Package as a fat JAR
mvn package
java -jar target/pixel-capture-1.0.0-jar-with-dependencies.jar
```

> **First run**: Maven will download ~300 MB of JavaCV/FFmpeg native binaries.
> Subsequent builds are instant.

---

## Features

| Feature | Status |
|---------|--------|
| Full-screen recording → MP4 (H264 + AAC) | ✅ |
| Custom area drag-selection | ✅ |
| Microphone audio recording | ✅ |
| Start / Pause / Resume / Stop | ✅ |
| Live recording timer | ✅ |
| Dark & light themes (auto-detects Windows setting) | ✅ |
| Settings window (FPS, quality, audio, theme) | ✅ |
| System tray (minimise to tray, tray menu) | ✅ |
| Persistent settings (Windows Registry via Preferences API) | ✅ |
| Global hotkeys (Ctrl+Shift+R/S/P) | TODO — JNativeHook wired but not bound |
| Multi-monitor area selection | TODO |
| Webcam overlay | TODO |
| Built-in video trimmer | TODO |

---

## Default Hotkeys (planned)

| Action | Shortcut |
|--------|----------|
| Start recording | `Ctrl + Shift + R` |
| Stop recording  | `Ctrl + Shift + S` |
| Pause / Resume  | `Ctrl + Shift + P` |

---

## Output

Recordings are saved to `%USERPROFILE%\Videos\` by default as:

```
recording_2024-06-01_14-30-00.mp4
```

Change the folder at any time via the **Browse** button or Settings.
