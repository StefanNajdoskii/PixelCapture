package com.screenrecorder.models;

public enum VideoQuality {

    LOW("Low", 1_000_000, "28"),
    MEDIUM("Medium", 3_000_000, "23"),
    HIGH("High", 6_000_000, "18"),
    ULTRA("Ultra", 12_000_000, "15");

    private final String label;
    private final int bitrate;
    private final String crf;

    VideoQuality(String label, int bitrate, String crf) {
        this.label = label;
        this.bitrate = bitrate;
        this.crf = crf;
    }

    public String getLabel()   { return label; }
    public int    getBitrate() { return bitrate; }
    public String getCrf()     { return crf; }

    public static VideoQuality fromLabel(String label) {
        for (VideoQuality q : values()) {
            if (q.label.equalsIgnoreCase(label)) return q;
        }
        return HIGH;
    }

    @Override
    public String toString() { return label; }
}
