package com.screenrecorder.models;

/**
 * All parameters that control what gets baked on top of each video frame:
 * the webcam picture-in-picture and the live FPS counter.
 *
 * Colors are stored as packed 0xRRGGBB ints so they can be persisted easily
 * via the Java Preferences API and converted to/from {@link java.awt.Color} or
 * {@link javafx.scene.paint.Color} by the UI layer.
 */
public class OverlayConfig {

    // ── Webcam overlay ────────────────────────────────────────────────────────
    private boolean webcamEnabled      = false;
    private int     webcamDeviceIndex  = 0;
    /** TOP_LEFT | TOP_RIGHT | BOTTOM_LEFT | BOTTOM_RIGHT | CENTER */
    private String  webcamPosition     = "BOTTOM_RIGHT";
    /** 10–50 — percentage of the shorter recording dimension */
    private int     webcamSizePercent  = 22;
    /** CIRCLE | ROUNDED_RECT | RECT */
    private String  webcamShape        = "CIRCLE";
    private int     webcamBorderWidth  = 3;
    private int     webcamBorderColor  = 0xFFFFFF;   // packed RGB
    /** 0.0–1.0 */
    private float   webcamOpacity      = 1.0f;
    /** pixels from the nearest edge */
    private int     webcamPadding      = 14;

    // ── FPS counter ───────────────────────────────────────────────────────────
    private boolean fpsCounterEnabled     = false;
    /** TOP_LEFT | TOP_RIGHT | BOTTOM_LEFT | BOTTOM_RIGHT */
    private String  fpsCounterPosition    = "TOP_LEFT";
    private int     fpsCounterFontSize    = 16;
    private int     fpsCounterTextColor   = 0xFFFFFF;
    private boolean fpsCounterBgEnabled   = true;
    private int     fpsCounterBgColor     = 0x000000;
    private float   fpsCounterBgOpacity   = 0.55f;
    private int     fpsCounterPadding     = 8;

    // ── Getters / setters ─────────────────────────────────────────────────────

    public boolean isWebcamEnabled()          { return webcamEnabled; }
    public int     getWebcamDeviceIndex()     { return webcamDeviceIndex; }
    public String  getWebcamPosition()        { return webcamPosition; }
    public int     getWebcamSizePercent()     { return webcamSizePercent; }
    public String  getWebcamShape()           { return webcamShape; }
    public int     getWebcamBorderWidth()     { return webcamBorderWidth; }
    public int     getWebcamBorderColor()     { return webcamBorderColor; }
    public float   getWebcamOpacity()         { return webcamOpacity; }
    public int     getWebcamPadding()         { return webcamPadding; }

    public boolean isFpsCounterEnabled()      { return fpsCounterEnabled; }
    public String  getFpsCounterPosition()    { return fpsCounterPosition; }
    public int     getFpsCounterFontSize()    { return fpsCounterFontSize; }
    public int     getFpsCounterTextColor()   { return fpsCounterTextColor; }
    public boolean isFpsCounterBgEnabled()    { return fpsCounterBgEnabled; }
    public int     getFpsCounterBgColor()     { return fpsCounterBgColor; }
    public float   getFpsCounterBgOpacity()   { return fpsCounterBgOpacity; }
    public int     getFpsCounterPadding()     { return fpsCounterPadding; }

    public void setWebcamEnabled(boolean v)         { webcamEnabled = v; }
    public void setWebcamDeviceIndex(int v)         { webcamDeviceIndex = v; }
    public void setWebcamPosition(String v)         { webcamPosition = v; }
    public void setWebcamSizePercent(int v)         { webcamSizePercent = v; }
    public void setWebcamShape(String v)            { webcamShape = v; }
    public void setWebcamBorderWidth(int v)         { webcamBorderWidth = v; }
    public void setWebcamBorderColor(int v)         { webcamBorderColor = v; }
    public void setWebcamOpacity(float v)           { webcamOpacity = v; }
    public void setWebcamPadding(int v)             { webcamPadding = v; }

    public void setFpsCounterEnabled(boolean v)     { fpsCounterEnabled = v; }
    public void setFpsCounterPosition(String v)     { fpsCounterPosition = v; }
    public void setFpsCounterFontSize(int v)        { fpsCounterFontSize = v; }
    public void setFpsCounterTextColor(int v)       { fpsCounterTextColor = v; }
    public void setFpsCounterBgEnabled(boolean v)   { fpsCounterBgEnabled = v; }
    public void setFpsCounterBgColor(int v)         { fpsCounterBgColor = v; }
    public void setFpsCounterBgOpacity(float v)     { fpsCounterBgOpacity = v; }
    public void setFpsCounterPadding(int v)         { fpsCounterPadding = v; }

    // ── Color conversion helpers ───────────────────────────────────────────────

    /** Packed 0xRRGGBB → java.awt.Color */
    public static java.awt.Color toAwtColor(int rgb) {
        return new java.awt.Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** java.awt.Color → packed 0xRRGGBB */
    public static int fromAwtColor(java.awt.Color c) {
        return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    /** javafx.scene.paint.Color → packed 0xRRGGBB */
    public static int fromFxColor(javafx.scene.paint.Color c) {
        return ((int)(c.getRed() * 255) << 16)
             | ((int)(c.getGreen() * 255) << 8)
             |  (int)(c.getBlue() * 255);
    }

    /** Packed 0xRRGGBB → javafx.scene.paint.Color */
    public static javafx.scene.paint.Color toFxColor(int rgb) {
        return javafx.scene.paint.Color.rgb(
            (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}
