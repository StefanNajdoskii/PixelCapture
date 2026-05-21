package com.screenrecorder.utils;

import javafx.scene.Scene;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies dark or light CSS stylesheets to a {@link Scene}.
 * When theme is "AUTO" the Windows registry is queried for the system preference.
 */
public class ThemeManager {

    private static final Logger LOG = Logger.getLogger(ThemeManager.class.getName());

    private static final String DARK_CSS  = "/com/screenrecorder/css/dark-theme.css";
    private static final String LIGHT_CSS = "/com/screenrecorder/css/light-theme.css";

    private ThemeManager() {}

    public static void applyTheme(Scene scene, String themeSetting) {
        boolean useDark = switch (themeSetting.toUpperCase()) {
            case "DARK"  -> true;
            case "LIGHT" -> false;
            default      -> isSystemDarkMode();
        };

        scene.getStylesheets().clear();
        String cssPath = useDark ? DARK_CSS : LIGHT_CSS;
        var resource = ThemeManager.class.getResource(cssPath);
        if (resource != null) {
            scene.getStylesheets().add(resource.toExternalForm());
        } else {
            LOG.warning("CSS resource not found: " + cssPath);
        }
    }

    /** Reads AppsUseLightTheme from the Windows registry. Falls back to false (dark). */
    public static boolean isSystemDarkMode() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
            });
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        return line.trim().endsWith("0x1");
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not read system theme from registry", e);
        }
        return false; // default to dark
    }
}
