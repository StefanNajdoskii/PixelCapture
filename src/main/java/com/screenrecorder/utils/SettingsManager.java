package com.screenrecorder.utils;

import com.screenrecorder.models.AppSettings;

import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton that loads/saves {@link AppSettings} using the Java Preferences API.
 * Settings are stored in the Windows Registry under HKCU\Software\JavaSoft\Prefs.
 *
 * <p>The {@link Preferences} node is obtained lazily (not in a static field) so
 * that a Preferences failure never causes an {@code ExceptionInInitializerError}
 * that would prevent the whole application from starting.</p>
 */
public class SettingsManager {

    private static final Logger LOG = Logger.getLogger(SettingsManager.class.getName());
    private static final String PREFS_NODE = "/com/screenrecorder";

    // Singleton — created on first access
    private static SettingsManager instance;

    private AppSettings current;

    private SettingsManager() {
        System.out.println("[SettingsManager] Initialising…");
        this.current = load();
        System.out.println("[SettingsManager] Settings loaded: theme=" + current.getTheme()
            + ", fps=" + current.getFps() + ", output=" + current.getOutputFolder());
    }

    public static synchronized SettingsManager getInstance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }

    public AppSettings getSettings() { return current; }

    public void saveSettings(AppSettings settings) {
        this.current = settings;
        try {
            Preferences prefs = prefs();
            prefs.putInt   ("fps",           settings.getFps());
            prefs.put      ("videoQuality",  settings.getVideoQuality());
            prefs.put      ("outputFolder",  settings.getOutputFolder());
            prefs.put      ("theme",         settings.getTheme());
            prefs.putBoolean("captureAudio", settings.isCaptureAudio());
            prefs.put      ("audioDevice",   settings.getAudioDevice());
            prefs.put      ("hotkeyStart",   settings.getHotkeyStart());
            prefs.put      ("hotkeyStop",    settings.getHotkeyStop());
            prefs.put      ("hotkeyPause",   settings.getHotkeyPause());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not save settings to Preferences store", e);
            System.out.println("[SettingsManager] Save failed (non-fatal): " + e.getMessage());
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private AppSettings load() {
        AppSettings s = new AppSettings(); // all defaults
        try {
            Preferences p = prefs();
            s.setFps          (p.getInt    ("fps",           30));
            s.setVideoQuality (p.get       ("videoQuality",  "High"));
            s.setOutputFolder (p.get       ("outputFolder",
                System.getProperty("user.home") + "\\Videos\\Screen Recordings"));
            s.setTheme        (p.get       ("theme",         "AUTO"));
            s.setCaptureAudio (p.getBoolean("captureAudio",  true));
            s.setAudioDevice  (p.get       ("audioDevice",   "Default Microphone"));
            s.setHotkeyStart  (p.get       ("hotkeyStart",   "Ctrl+Shift+R"));
            s.setHotkeyStop   (p.get       ("hotkeyStop",    "Ctrl+Shift+S"));
            s.setHotkeyPause  (p.get       ("hotkeyPause",   "Ctrl+Shift+P"));
        } catch (Exception e) {
            // Preferences unavailable (e.g. sandboxed environment) — use defaults
            LOG.log(Level.WARNING, "Could not read settings, using defaults", e);
            System.out.println("[SettingsManager] Preferences read failed, using defaults: "
                + e.getMessage());
        }
        return s;
    }

    /** Returns the Preferences node, or throws with a clear message if unavailable. */
    private static Preferences prefs() {
        return Preferences.userRoot().node(PREFS_NODE);
    }
}
