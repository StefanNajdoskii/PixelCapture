package com.screenrecorder.recorder;

import org.bytedeco.javacv.FFmpegFrameRecorder;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Captures microphone audio and feeds it into an {@link FFmpegFrameRecorder}.
 *
 * Audio runs on a dedicated daemon thread.  All recorder writes are serialised
 * via the caller-supplied {@code recorderLock} object.
 */
public class AudioCapture {

    private static final Logger LOG = Logger.getLogger(AudioCapture.class.getName());

    public static final int SAMPLE_RATE   = 44_100;
    public static final int CHANNELS      = 2;
    private static final int BUFFER_BYTES = 4_096;

    private TargetDataLine line;
    private Thread captureThread;
    private volatile boolean running;

    public void start(FFmpegFrameRecorder recorder, Object recorderLock)
            throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE, 16, CHANNELS, CHANNELS * 2, SAMPLE_RATE,
            true  // big-endian
        );
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("No supported microphone line found");
        }

        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format, BUFFER_BYTES * 4);
        line.start();
        running = true;

        captureThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_BYTES];
            while (running) {
                int read = line.read(buffer, 0, buffer.length);
                if (read <= 0) continue;

                short[] shorts = new short[read / 2];
                ByteBuffer.wrap(buffer, 0, read)
                          .order(ByteOrder.BIG_ENDIAN)
                          .asShortBuffer()
                          .get(shorts);
                try {
                    synchronized (recorderLock) {
                        recorder.recordSamples(SAMPLE_RATE, CHANNELS, ShortBuffer.wrap(shorts));
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Audio record error", e);
                }
            }
        }, "audio-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public void stop() {
        running = false;
        if (line != null) { line.stop(); line.close(); }
        if (captureThread != null) captureThread.interrupt();
    }

    /**
     * Returns available microphone device names.
     * <p>
     * This method is called during UI initialisation and <strong>must never
     * throw</strong> — any exception is caught and logged so a missing sound
     * card doesn't prevent the application from starting.
     */
    public static List<String> availableDevices() {
        List<String> names = new ArrayList<>();
        names.add("Default Microphone");
        try {
            AudioFormat probe = new AudioFormat(44_100, 16, 2, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, probe);
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mi);
                    if (mixer.isLineSupported(info)) {
                        String name = mi.getName();
                        // Skip the generic Windows driver alias
                        if (!name.equals("Primary Sound Capture Driver")
                                && !names.contains(name)) {
                            names.add(name);
                        }
                    }
                } catch (Exception inner) {
                    // A single bad mixer must not abort the whole enumeration
                    LOG.fine("Skipping mixer '" + mi.getName() + "': " + inner.getMessage());
                }
            }
        } catch (Throwable t) {
            // SecurityException, ExceptionInInitializerError from AudioSystem, etc.
            LOG.log(Level.WARNING,
                "Could not enumerate audio devices — using fallback list", t);
            System.out.println("[AudioCapture] availableDevices() failed: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        System.out.println("[AudioCapture] availableDevices() → " + names);
        return names;
    }
}
