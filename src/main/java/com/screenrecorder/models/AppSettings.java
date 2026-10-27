package com.screenrecorder.models;

/**
 * Mutable bean for all user-configurable preferences.
 * Persisted via {@link com.screenrecorder.utils.SettingsManager}.
 */
public class AppSettings {

    // ── Video ──────────────────────────────────────────────────────────────────
    private int     fps             = 30;
    private String  videoQuality    = "High";

    // ── Output ────────────────────────────────────────────────────────────────
    private String  outputFolder    =
        System.getProperty("user.home") + "\\Videos\\Screen Recordings";

    // ── Appearance ────────────────────────────────────────────────────────────
    private String  theme           = "AUTO";

    // ── Audio ─────────────────────────────────────────────────────────────────
    private boolean captureAudio    = true;
    private String  audioDevice     = "Default Microphone";

    // ── Hotkeys ───────────────────────────────────────────────────────────────
    private String  hotkeyStart      = "Ctrl+Shift+R";
    private String  hotkeyStop       = "Ctrl+Shift+X";   // was Ctrl+Shift+S — changed for screenshot
    private String  hotkeyPause      = "Ctrl+Shift+P";
    private String  hotkeyScreenshot = "Ctrl+Shift+S";   // NEW

    // ── Overlay: webcam ───────────────────────────────────────────────────────
    private boolean webcamEnabled     = false;
    private int     webcamDeviceIndex = 0;
    private String  webcamPosition    = "BOTTOM_RIGHT";
    private int     webcamSizePercent = 22;
    private String  webcamShape       = "CIRCLE";
    private int     webcamBorderWidth = 3;
    private int     webcamBorderColor = 0xFFFFFF;
    private int     webcamOpacityPct  = 100;   // 50–100 mapped to 0.5–1.0

    // ── Overlay: FPS counter ──────────────────────────────────────────────────
    private boolean fpsCounterEnabled   = false;
    private String  fpsCounterPosition  = "TOP_LEFT";
    private int     fpsCounterFontSize  = 16;
    private int     fpsCounterTextColor = 0xFFFFFF;
    private boolean fpsCounterBg        = true;
    private int     fpsCounterBgColor   = 0x000000;

    // ── Getters / setters ─────────────────────────────────────────────────────

    public int     getFps()             { return fps; }
    public String  getVideoQuality()    { return videoQuality; }
    public String  getOutputFolder()    { return outputFolder; }
    public String  getTheme()           { return theme; }
    public boolean isCaptureAudio()     { return captureAudio; }
    public String  getAudioDevice()     { return audioDevice; }
    public String  getHotkeyStart()     { return hotkeyStart; }
    public String  getHotkeyStop()      { return hotkeyStop; }
    public String  getHotkeyPause()     { return hotkeyPause; }
    public String  getHotkeyScreenshot(){ return hotkeyScreenshot; }

    public boolean isWebcamEnabled()     { return webcamEnabled; }
    public int     getWebcamDeviceIndex(){ return webcamDeviceIndex; }
    public String  getWebcamPosition()   { return webcamPosition; }
    public int     getWebcamSizePercent(){ return webcamSizePercent; }
    public String  getWebcamShape()      { return webcamShape; }
    public int     getWebcamBorderWidth(){ return webcamBorderWidth; }
    public int     getWebcamBorderColor(){ return webcamBorderColor; }
    public int     getWebcamOpacityPct() { return webcamOpacityPct; }

    public boolean isFpsCounterEnabled()  { return fpsCounterEnabled; }
    public String  getFpsCounterPosition(){ return fpsCounterPosition; }
    public int     getFpsCounterFontSize(){ return fpsCounterFontSize; }
    public int     getFpsCounterTextColor(){ return fpsCounterTextColor; }
    public boolean isFpsCounterBg()       { return fpsCounterBg; }
    public int     getFpsCounterBgColor() { return fpsCounterBgColor; }

    public void setFps(int v)               { fps = v; }
    public void setVideoQuality(String v)   { videoQuality = v; }
    public void setOutputFolder(String v)   { outputFolder = v; }
    public void setTheme(String v)          { theme = v; }
    public void setCaptureAudio(boolean v)  { captureAudio = v; }
    public void setAudioDevice(String v)    { audioDevice = v; }
    public void setHotkeyStart(String v)    { hotkeyStart = v; }
    public void setHotkeyStop(String v)     { hotkeyStop = v; }
    public void setHotkeyPause(String v)    { hotkeyPause = v; }
    public void setHotkeyScreenshot(String v){ hotkeyScreenshot = v; }

    public void setWebcamEnabled(boolean v)    { webcamEnabled = v; }
    public void setWebcamDeviceIndex(int v)    { webcamDeviceIndex = v; }
    public void setWebcamPosition(String v)    { webcamPosition = v; }
    public void setWebcamSizePercent(int v)    { webcamSizePercent = v; }
    public void setWebcamShape(String v)       { webcamShape = v; }
    public void setWebcamBorderWidth(int v)    { webcamBorderWidth = v; }
    public void setWebcamBorderColor(int v)    { webcamBorderColor = v; }
    public void setWebcamOpacityPct(int v)     { webcamOpacityPct = v; }

    public void setFpsCounterEnabled(boolean v){ fpsCounterEnabled = v; }
    public void setFpsCounterPosition(String v){ fpsCounterPosition = v; }
    public void setFpsCounterFontSize(int v)   { fpsCounterFontSize = v; }
    public void setFpsCounterTextColor(int v)  { fpsCounterTextColor = v; }
    public void setFpsCounterBg(boolean v)     { fpsCounterBg = v; }
    public void setFpsCounterBgColor(int v)    { fpsCounterBgColor = v; }

    /** Builds an {@link OverlayConfig} from current overlay settings. */
    public OverlayConfig toOverlayConfig() {
        OverlayConfig oc = new OverlayConfig();
        oc.setWebcamEnabled(webcamEnabled);
        oc.setWebcamDeviceIndex(webcamDeviceIndex);
        oc.setWebcamPosition(webcamPosition);
        oc.setWebcamSizePercent(webcamSizePercent);
        oc.setWebcamShape(webcamShape);
        oc.setWebcamBorderWidth(webcamBorderWidth);
        oc.setWebcamBorderColor(webcamBorderColor);
        oc.setWebcamOpacity(webcamOpacityPct / 100.0f);

        oc.setFpsCounterEnabled(fpsCounterEnabled);
        oc.setFpsCounterPosition(fpsCounterPosition);
        oc.setFpsCounterFontSize(fpsCounterFontSize);
        oc.setFpsCounterTextColor(fpsCounterTextColor);
        oc.setFpsCounterBgEnabled(fpsCounterBg);
        oc.setFpsCounterBgColor(fpsCounterBgColor);
        return oc;
    }
}
