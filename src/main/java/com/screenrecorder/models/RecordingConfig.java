package com.screenrecorder.models;

import java.awt.Rectangle;

/**
 * Immutable snapshot of all parameters needed to start one recording session.
 * Built via {@link Builder}.
 */
public class RecordingConfig {

    private final Rectangle    captureArea;   // null → full screen
    private final boolean      captureAudio;
    private final String       audioDeviceName;
    private final int          fps;
    private final int          videoBitrate;
    private final String       crf;
    private final String       outputPath;
    private final int          sampleRate;
    private final int          audioChannels;
    private final OverlayConfig overlayConfig;

    private RecordingConfig(Builder b) {
        this.captureArea     = b.captureArea;
        this.captureAudio    = b.captureAudio;
        this.audioDeviceName = b.audioDeviceName;
        this.fps             = b.fps;
        this.videoBitrate    = b.videoBitrate;
        this.crf             = b.crf;
        this.outputPath      = b.outputPath;
        this.sampleRate      = b.sampleRate;
        this.audioChannels   = b.audioChannels;
        this.overlayConfig   = b.overlayConfig;
    }

    public Rectangle    getCaptureArea()     { return captureArea; }
    public boolean      isCaptureAudio()     { return captureAudio; }
    public String       getAudioDeviceName() { return audioDeviceName; }
    public int          getFps()             { return fps; }
    public int          getVideoBitrate()    { return videoBitrate; }
    public String       getCrf()             { return crf; }
    public String       getOutputPath()      { return outputPath; }
    public int          getSampleRate()      { return sampleRate; }
    public int          getAudioChannels()   { return audioChannels; }
    public OverlayConfig getOverlayConfig()  { return overlayConfig; }

    // -------------------------------------------------------------------------

    public static class Builder {
        private Rectangle    captureArea     = null;
        private boolean      captureAudio    = true;
        private String       audioDeviceName = "default";
        private int          fps             = 30;
        private int          videoBitrate    = 6_000_000;
        private String       crf             = "18";
        private String       outputPath      = System.getProperty("user.home") + "/Videos/recording.mp4";
        private int          sampleRate      = 44_100;
        private int          audioChannels   = 2;
        private OverlayConfig overlayConfig  = new OverlayConfig();

        public Builder captureArea(Rectangle r)       { this.captureArea = r;        return this; }
        public Builder captureAudio(boolean v)        { this.captureAudio = v;       return this; }
        public Builder audioDeviceName(String n)      { this.audioDeviceName = n;    return this; }
        public Builder fps(int v)                     { this.fps = v;                return this; }
        public Builder quality(VideoQuality q)        {
            this.videoBitrate = q.getBitrate();
            this.crf = q.getCrf();
            return this;
        }
        public Builder outputPath(String p)           { this.outputPath = p;         return this; }
        public Builder sampleRate(int v)              { this.sampleRate = v;         return this; }
        public Builder audioChannels(int v)           { this.audioChannels = v;      return this; }
        public Builder overlayConfig(OverlayConfig oc){ this.overlayConfig = oc;     return this; }

        public RecordingConfig build() { return new RecordingConfig(this); }
    }
}
