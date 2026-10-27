package com.screenrecorder.utils;

import com.screenrecorder.models.AppSettings;

import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsManager {

    private static final Logger LOG = Logger.getLogger(SettingsManager.class.getName());
    private static final String PREFS_NODE = "/com/screenrecorder";

    private static SettingsManager instance;
    private AppSettings current;

    private SettingsManager() {
        System.out.println("[SettingsManager] Initialising…");
        this.current = load();
    }

    public static synchronized SettingsManager getInstance() {
        if (instance == null) instance = new SettingsManager();
        return instance;
    }

    public AppSettings getSettings() { return current; }

    public void saveSettings(AppSettings s) {
        this.current = s;
        try {
            Preferences p = prefs();
            p.putInt    ("fps",           s.getFps());
            p.put       ("videoQuality",  s.getVideoQuality());
            p.put       ("outputFolder",  s.getOutputFolder());
            p.put       ("theme",         s.getTheme());
            p.putBoolean("captureAudio",  s.isCaptureAudio());
            p.put       ("audioDevice",   s.getAudioDevice());
            p.put       ("hotkeyStart",   s.getHotkeyStart());
            p.put       ("hotkeyStop",    s.getHotkeyStop());
            p.put       ("hotkeyPause",   s.getHotkeyPause());
            p.put       ("hotkeyScreenshot", s.getHotkeyScreenshot());

            // Overlay — webcam
            p.putBoolean("webcamEnabled",     s.isWebcamEnabled());
            p.putInt    ("webcamDeviceIndex", s.getWebcamDeviceIndex());
            p.put       ("webcamPosition",    s.getWebcamPosition());
            p.putInt    ("webcamSizePct",     s.getWebcamSizePercent());
            p.put       ("webcamShape",       s.getWebcamShape());
            p.putInt    ("webcamBorderWidth", s.getWebcamBorderWidth());
            p.putInt    ("webcamBorderColor", s.getWebcamBorderColor());
            p.putInt    ("webcamOpacityPct",  s.getWebcamOpacityPct());

            // Overlay — FPS counter
            p.putBoolean("fpsCounterEnabled",   s.isFpsCounterEnabled());
            p.put       ("fpsCounterPosition",  s.getFpsCounterPosition());
            p.putInt    ("fpsCounterFontSize",  s.getFpsCounterFontSize());
            p.putInt    ("fpsCounterTextColor", s.getFpsCounterTextColor());
            p.putBoolean("fpsCounterBg",        s.isFpsCounterBg());
            p.putInt    ("fpsCounterBgColor",   s.getFpsCounterBgColor());

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not save settings", e);
        }
    }

    private AppSettings load() {
        AppSettings s = new AppSettings();
        try {
            Preferences p = prefs();
            String defaultFolder = System.getProperty("user.home") + "\\Videos\\Screen Recordings";

            s.setFps           (p.getInt    ("fps",           30));
            s.setVideoQuality  (p.get       ("videoQuality",  "High"));
            s.setOutputFolder  (p.get       ("outputFolder",  defaultFolder));
            s.setTheme         (p.get       ("theme",         "AUTO"));
            s.setCaptureAudio  (p.getBoolean("captureAudio",  true));
            s.setAudioDevice   (p.get       ("audioDevice",   "Default Microphone"));
            s.setHotkeyStart   (p.get       ("hotkeyStart",   "Ctrl+Shift+R"));
            s.setHotkeyStop    (p.get       ("hotkeyStop",    "Ctrl+Shift+X"));
            s.setHotkeyPause   (p.get       ("hotkeyPause",   "Ctrl+Shift+P"));
            s.setHotkeyScreenshot(p.get     ("hotkeyScreenshot", "Ctrl+Shift+S"));

            s.setWebcamEnabled    (p.getBoolean("webcamEnabled",     false));
            s.setWebcamDeviceIndex(p.getInt    ("webcamDeviceIndex", 0));
            s.setWebcamPosition   (p.get       ("webcamPosition",    "BOTTOM_RIGHT"));
            s.setWebcamSizePercent(p.getInt    ("webcamSizePct",     22));
            s.setWebcamShape      (p.get       ("webcamShape",       "CIRCLE"));
            s.setWebcamBorderWidth(p.getInt    ("webcamBorderWidth", 3));
            s.setWebcamBorderColor(p.getInt    ("webcamBorderColor", 0xFFFFFF));
            s.setWebcamOpacityPct (p.getInt    ("webcamOpacityPct",  100));

            s.setFpsCounterEnabled  (p.getBoolean("fpsCounterEnabled",   false));
            s.setFpsCounterPosition (p.get       ("fpsCounterPosition",  "TOP_LEFT"));
            s.setFpsCounterFontSize (p.getInt    ("fpsCounterFontSize",  16));
            s.setFpsCounterTextColor(p.getInt    ("fpsCounterTextColor", 0xFFFFFF));
            s.setFpsCounterBg       (p.getBoolean("fpsCounterBg",        true));
            s.setFpsCounterBgColor  (p.getInt    ("fpsCounterBgColor",   0x000000));

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not read settings, using defaults", e);
        }
        return s;
    }

    private static Preferences prefs() {
        return Preferences.userRoot().node(PREFS_NODE);
    }
}
