package com.screenrecorder.models;

/**
 * Mutable bean for all user-configurable preferences.
 * Persisted via {@link com.screenrecorder.utils.SettingsManager}.
 */
public class AppSettings {

    private int     fps             = 30;
    private String  videoQuality    = "High";
    private String  outputFolder    = System.getProperty("user.home") + "\\Videos\\Screen Recordings";
    private String  theme           = "AUTO";
    private boolean captureAudio    = true;
    private String  audioDevice     = "Default Microphone";
    private String  hotkeyStart     = "Ctrl+Shift+R";
    private String  hotkeyStop      = "Ctrl+Shift+S";
    private String  hotkeyPause     = "Ctrl+Shift+P";

    public int     getFps()             { return fps; }
    public String  getVideoQuality()    { return videoQuality; }
    public String  getOutputFolder()    { return outputFolder; }
    public String  getTheme()           { return theme; }
    public boolean isCaptureAudio()     { return captureAudio; }
    public String  getAudioDevice()     { return audioDevice; }
    public String  getHotkeyStart()     { return hotkeyStart; }
    public String  getHotkeyStop()      { return hotkeyStop; }
    public String  getHotkeyPause()     { return hotkeyPause; }

    public void setFps(int fps)                    { this.fps = fps; }
    public void setVideoQuality(String q)          { this.videoQuality = q; }
    public void setOutputFolder(String f)          { this.outputFolder = f; }
    public void setTheme(String theme)             { this.theme = theme; }
    public void setCaptureAudio(boolean v)         { this.captureAudio = v; }
    public void setAudioDevice(String d)           { this.audioDevice = d; }
    public void setHotkeyStart(String h)           { this.hotkeyStart = h; }
    public void setHotkeyStop(String h)            { this.hotkeyStop = h; }
    public void setHotkeyPause(String h)           { this.hotkeyPause = h; }
}
