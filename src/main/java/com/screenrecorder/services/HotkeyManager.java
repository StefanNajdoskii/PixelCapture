package com.screenrecorder.services;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.application.Platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton wrapper around JNativeHook for application-wide global hotkeys.
 *
 * <p>Register actions with {@link #bind(String, Runnable)} using hotkey strings
 * like {@code "Ctrl+Shift+R"} or {@code "F9"}.  When the user presses that
 * combination from any window, the action is dispatched on the JavaFX thread.</p>
 *
 * <p>Call {@link #shutdown()} when the application exits to cleanly unregister
 * the native hook.</p>
 */
public class HotkeyManager implements NativeKeyListener {

    private static final Logger LOG = Logger.getLogger(HotkeyManager.class.getName());
    private static HotkeyManager instance;

    private final Map<String, Runnable> bindings = new ConcurrentHashMap<>();
    private boolean hookRegistered = false;

    // ── Singleton ─────────────────────────────────────────────────────────────

    public static synchronized HotkeyManager getInstance() {
        if (instance == null) instance = new HotkeyManager();
        return instance;
    }

    private HotkeyManager() {
        try {
            // Silence JNativeHook's per-event logging spam
            Logger jnhLog = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            jnhLog.setLevel(Level.OFF);
            jnhLog.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            hookRegistered = true;
            System.out.println("[HotkeyManager] JNativeHook registered");
        } catch (NativeHookException e) {
            LOG.warning("[HotkeyManager] Native hook unavailable: " + e.getMessage()
                + " — global hotkeys disabled");
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Binds a hotkey string (e.g. {@code "Ctrl+Shift+R"} or {@code "F9"}) to an action.
     * The action is always invoked on the JavaFX application thread.
     * Replaces any previous binding for the same key.
     */
    public void bind(String hotkeyString, Runnable action) {
        if (hotkeyString == null || hotkeyString.isBlank() || action == null) return;
        bindings.put(normalise(hotkeyString), action);
    }

    /** Removes all registered bindings. */
    public void unbindAll() {
        bindings.clear();
    }

    /** Must be called on application exit to release the OS-level native hook. */
    public void shutdown() {
        bindings.clear();
        if (hookRegistered) {
            try {
                GlobalScreen.removeNativeKeyListener(this);
                GlobalScreen.unregisterNativeHook();
            } catch (Exception ignored) {}
            hookRegistered = false;
        }
    }

    // ── NativeKeyListener ─────────────────────────────────────────────────────

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        String combo = buildCombo(e);
        Runnable action = bindings.get(combo);
        if (action != null) {
            Platform.runLater(action);
        }
    }

    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e)    {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a normalised combo string from a NativeKeyEvent, e.g. "CTRL+SHIFT+R". */
    private String buildCombo(NativeKeyEvent e) {
        int mod = e.getModifiers();
        StringBuilder sb = new StringBuilder();
        if ((mod & (NativeKeyEvent.CTRL_L_MASK  | NativeKeyEvent.CTRL_R_MASK))  != 0) sb.append("CTRL+");
        if ((mod & (NativeKeyEvent.ALT_L_MASK   | NativeKeyEvent.ALT_R_MASK))   != 0) sb.append("ALT+");
        if ((mod & (NativeKeyEvent.SHIFT_L_MASK | NativeKeyEvent.SHIFT_R_MASK)) != 0) sb.append("SHIFT+");
        sb.append(NativeKeyEvent.getKeyText(e.getKeyCode()).toUpperCase());
        return sb.toString();
    }

    /**
     * Normalises a user-readable hotkey string to the same format as
     * {@link #buildCombo(NativeKeyEvent)}, so lookups always match.
     * e.g. "ctrl+shift+r" → "CTRL+SHIFT+R", "F9" → "F9"
     */
    public static String normalise(String raw) {
        if (raw == null) return "";
        String upper = raw.toUpperCase().trim();
        // Split on '+' but keep the key name (last token) intact
        String[] parts = upper.split("\\+");
        StringBuilder sb = new StringBuilder();
        // Sort modifier order: CTRL, ALT, SHIFT, then the key
        boolean ctrl = false, alt = false, shift = false;
        String key = "";
        for (String p : parts) {
            switch (p.trim()) {
                case "CTRL", "CONTROL" -> ctrl  = true;
                case "ALT"             -> alt   = true;
                case "SHIFT"           -> shift = true;
                default                -> key   = p.trim();
            }
        }
        if (ctrl)  sb.append("CTRL+");
        if (alt)   sb.append("ALT+");
        if (shift) sb.append("SHIFT+");
        sb.append(key);
        return sb.toString();
    }

    /**
     * Converts a JavaFX {@link javafx.scene.input.KeyEvent} to the same
     * normalised string, so the Settings key-capture field produces tokens
     * that match what JNativeHook fires.
     */
    public static String fromFxEvent(javafx.scene.input.KeyEvent e) {
        javafx.scene.input.KeyCode code = e.getCode();
        if (code.isModifierKey()) return null; // Incomplete combo — wait for non-modifier

        StringBuilder sb = new StringBuilder();
        if (e.isControlDown()) sb.append("CTRL+");
        if (e.isAltDown())     sb.append("ALT+");
        if (e.isShiftDown())   sb.append("SHIFT+");
        // KeyCode.getName() returns "F9", "A", "Backspace" etc.
        sb.append(code.getName().toUpperCase());
        return sb.toString();
    }
}
