package com.screenrecorder.utils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class FileUtils {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private FileUtils() {}

    /**
     * Generates a unique MP4 output path inside {@code outputFolder}.
     * Example: C:\Users\...\Videos\recording_2024-06-01_14-30-00.mp4
     */
    public static String generateOutputPath(String outputFolder) {
        String timestamp = LocalDateTime.now().format(FMT);
        return outputFolder + File.separator + "recording_" + timestamp + ".mp4";
    }

    /** Ensures the directory exists; creates it (including parents) if not. */
    public static boolean ensureDirectory(String path) {
        File dir = new File(path);
        if (dir.exists()) return true;
        return dir.mkdirs();
    }

    /** Formats seconds as HH:MM:SS. */
    public static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
