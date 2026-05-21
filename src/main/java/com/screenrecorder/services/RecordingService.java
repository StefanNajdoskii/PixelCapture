package com.screenrecorder.services;

import com.screenrecorder.models.RecordingConfig;
import com.screenrecorder.models.RecordingState;
import com.screenrecorder.recorder.AudioCapture;
import com.screenrecorder.recorder.ScreenCapture;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.util.Duration;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bytedeco.ffmpeg.global.avcodec.*;

/**
 * Core recording service — IDLE ↔ RECORDING ↔ PAUSED state machine.
 *
 * Thread-safety notes:
 *  - All JavaFX property writes happen on the FX thread.
 *  - {@code activelyRecording} (AtomicBoolean) is safe to read from any thread
 *    and is the authoritative flag for the video-capture loop.
 *  - All {@link FFmpegFrameRecorder} writes are guarded by {@code recorderLock}.
 */
public class RecordingService {

    private static final Logger LOG = Logger.getLogger(RecordingService.class.getName());

    // ── Observable state (FX thread) ─────────────────────────────────────────
    private final ObjectProperty<RecordingState> state =
        new SimpleObjectProperty<>(RecordingState.IDLE);
    private final LongProperty   elapsedSeconds    = new SimpleLongProperty(0);
    private final StringProperty currentOutputPath = new SimpleStringProperty("");
    private final StringProperty lastSavedPath     = new SimpleStringProperty("");

    // ── Thread-safe recording flag (readable from any thread) ────────────────
    private final AtomicBoolean activelyRecording = new AtomicBoolean(false);

    // ── Recording infrastructure ─────────────────────────────────────────────
    private FFmpegFrameRecorder  recorder;
    private ScreenCapture        screenCapture;
    private AudioCapture         audioCapture;
    private Java2DFrameConverter frameConverter;
    private ScheduledExecutorService videoScheduler;

    /**
     * Pre-allocated BGR24 scratch buffer reused for every frame.
     * Robot returns TYPE_INT_RGB (packed as 0x00RRGGBB per int).
     * Java2DFrameConverter passes the raw int bytes to FFmpeg, which on a
     * little-endian CPU means the byte order is actually B-G-R-00 (BGRA).
     * By drawing each frame into a TYPE_3BYTE_BGR image first we get a true
     * 3-bytes-per-pixel BGR24 layout that FFmpegFrameRecorder expects.
     */
    private BufferedImage bgrBuffer;

    /** Guards all recorder.record() / recorder.recordSamples() calls. */
    private final Object recorderLock = new Object();

    // ── Timing ───────────────────────────────────────────────────────────────
    private Timeline elapsedTimer;
    private long     recordingStartMs;
    private long     totalPausedMs;
    private long     pauseStartMs;
    private long[]   frameCount = {0};

    // =========================================================================
    // Public API
    // =========================================================================

    public void startRecording(RecordingConfig config) throws Exception {
        if (state.get() != RecordingState.IDLE) {
            throw new IllegalStateException("Already recording (state=" + state.get() + ")");
        }

        System.out.println("[RecordingService] ===== startRecording() =====");
        System.out.println("[RecordingService]   Output path : " + config.getOutputPath());
        System.out.println("[RecordingService]   FPS         : " + config.getFps());
        System.out.println("[RecordingService]   Bitrate     : " + config.getVideoBitrate());
        System.out.println("[RecordingService]   CRF         : " + config.getCrf());
        System.out.println("[RecordingService]   Audio       : " + config.isCaptureAudio());

        // --- Validate output directory ----------------------------------------
        File outputFile = new File(config.getOutputPath());
        File outputDir  = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            System.out.println("[RecordingService]   Created dir : " + outputDir + " → " + created);
            if (!created) {
                throw new RuntimeException("Cannot create output directory: " + outputDir);
            }
        }

        // --- Screen capture --------------------------------------------------
        Rectangle area = ScreenCapture.toEvenDimensions(
            config.getCaptureArea() != null
                ? config.getCaptureArea()
                : ScreenCapture.fullScreenBounds()
        );
        System.out.println("[RecordingService]   Capture area: " + area);

        screenCapture  = new ScreenCapture(area);
        frameConverter = new Java2DFrameConverter();

        int w = area.width;
        int h = area.height;

        // Pre-allocate the BGR conversion buffer for this session.
        // Reused every frame — no per-frame allocation in the hot path.
        bgrBuffer = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        System.out.println("[RecordingService]   BGR buffer  : " + w + "×" + h
            + " TYPE_3BYTE_BGR (true BGR24 for FFmpeg)");

        // --- FFmpeg recorder -------------------------------------------------
        System.out.println("[RecordingService] Initialising FFmpegFrameRecorder " + w + "×" + h + "…");
        int audioChannels = config.isCaptureAudio() ? config.getAudioChannels() : 0;
        recorder = new FFmpegFrameRecorder(config.getOutputPath(), w, h, audioChannels);
        recorder.setFormat("mp4");
        recorder.setVideoCodec(AV_CODEC_ID_H264);
        recorder.setFrameRate(config.getFps());
        recorder.setVideoBitrate(config.getVideoBitrate());
        // DO NOT call recorder.setPixelFormat() here.
        // FFmpegFrameRecorder defaults to AV_PIX_FMT_BGR24 for the *source* (input)
        // pixel format — which is exactly what our TYPE_3BYTE_BGR bgrBuffer produces.
        // Setting it to YUV420P would tell libswscale the input is YUV420P, causing
        // it to misinterpret our BGR bytes and producing the red colour tint.
        // The H264 *codec* always uses YUV420P internally regardless of this setting.
        recorder.setVideoOption("preset", "ultrafast");
        recorder.setVideoOption("crf",    config.getCrf());
        recorder.setVideoOption("tune",   "zerolatency");

        if (config.isCaptureAudio()) {
            recorder.setAudioCodec(AV_CODEC_ID_AAC);
            recorder.setSampleRate(config.getSampleRate());
            recorder.setAudioBitrate(128_000);
            recorder.setAudioChannels(config.getAudioChannels());
        }

        System.out.println("[RecordingService] Calling recorder.start()…");
        recorder.start();
        System.out.println("[RecordingService] recorder.start() OK");

        // --- Timing ----------------------------------------------------------
        recordingStartMs = System.currentTimeMillis();
        totalPausedMs    = 0;
        frameCount[0]    = 0;

        Platform.runLater(() -> {
            currentOutputPath.set(config.getOutputPath());
            elapsedSeconds.set(0);
        });

        elapsedTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long elapsed = (System.currentTimeMillis() - recordingStartMs - totalPausedMs) / 1000;
            elapsedSeconds.set(elapsed);
        }));
        elapsedTimer.setCycleCount(Animation.INDEFINITE);
        elapsedTimer.play();

        // --- Video capture thread (ScheduledExecutorService) -----------------
        long frameIntervalMs = 1000L / config.getFps();
        videoScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "video-capture");
            t.setDaemon(true);
            return t;
        });

        // Mark actively recording BEFORE scheduling so the first frame isn't skipped
        activelyRecording.set(true);

        videoScheduler.scheduleAtFixedRate(() -> {
            if (!activelyRecording.get()) return;
            try {
                BufferedImage image = screenCapture.captureFrame();
                if (image.getWidth() != w || image.getHeight() != h) {
                    image = image.getSubimage(0, 0, w, h);
                }

                // ── Colour-correct conversion ─────────────────────────────────
                // Robot returns TYPE_INT_RGB where each pixel int = 0x00RRGGBB.
                // On a little-endian CPU the 4 bytes in memory are B-G-R-00, so
                // passing the raw data to FFmpeg as-is would swap R↔B and produce
                // the well-known "red tint" bug.
                //
                // Fix: copy the Robot int-array directly into a TYPE_3BYTE_BGR
                // BufferedImage, giving a true B-G-R byte layout (AV_PIX_FMT_BGR24).
                // FFmpegFrameRecorder's default source format is BGR24, so libswscale
                // will convert it to YUV420P correctly before H264 encoding.
                convertToBgr(image, bgrBuffer);

                Frame frame = frameConverter.convert(bgrBuffer);
                long ts = (long) (frameCount[0]++ * (1_000_000.0 / config.getFps()));
                synchronized (recorderLock) {
                    if (recorder != null) {
                        recorder.setTimestamp(ts);
                        recorder.record(frame);
                    }
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Video frame capture error", ex);
            }
        }, 0, frameIntervalMs, TimeUnit.MILLISECONDS);

        System.out.println("[RecordingService] Video capture thread scheduled at "
            + frameIntervalMs + " ms intervals");

        // --- Audio capture ---------------------------------------------------
        if (config.isCaptureAudio()) {
            audioCapture = new AudioCapture();
            try {
                audioCapture.start(recorder, recorderLock);
                System.out.println("[RecordingService] Audio capture started");
            } catch (Exception ex) {
                System.out.println("[RecordingService] Audio unavailable (" + ex.getMessage()
                    + ") — continuing without audio");
                LOG.log(Level.WARNING, "Audio capture unavailable", ex);
                audioCapture = null;
            }
        }

        Platform.runLater(() -> state.set(RecordingState.RECORDING));
        System.out.println("[RecordingService] startRecording() complete — state → RECORDING");
    }

    public void stopRecording() throws Exception {
        if (state.get() == RecordingState.IDLE) return;

        System.out.println("[RecordingService] ===== stopRecording() =====");
        String savedPath = new File(currentOutputPath.get()).getAbsolutePath();

        // Stop capture threads FIRST (atomically visible to video thread)
        activelyRecording.set(false);

        shutdownVideoScheduler();
        stopAudio();

        Platform.runLater(() -> {
            if (elapsedTimer != null) elapsedTimer.stop();
            state.set(RecordingState.IDLE);
            elapsedSeconds.set(0);
        });

        // Flush + close the MP4 file
        synchronized (recorderLock) {
            if (recorder != null) {
                System.out.println("[RecordingService] Flushing recorder…");
                try {
                    recorder.stop();
                    recorder.release();
                    System.out.println("[RecordingService] Recording saved to: " + savedPath);
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Recorder stop error", ex);
                    System.err.println("[RecordingService] Recorder stop error: " + ex.getMessage());
                    ex.printStackTrace(System.err);
                } finally {
                    recorder = null;
                }
            }
        }

        // Verify the file was actually written
        File saved = new File(savedPath);
        if (saved.exists()) {
            System.out.println("[RecordingService] File confirmed: " + saved.length() + " bytes");
        } else {
            System.err.println("[RecordingService] WARNING: output file not found at: " + savedPath);
        }

        Platform.runLater(() -> lastSavedPath.set(savedPath));

        // Release the BGR scratch buffer so GC can reclaim it
        bgrBuffer = null;
    }

    public void pauseRecording() {
        if (state.get() != RecordingState.RECORDING) return;
        pauseStartMs = System.currentTimeMillis();
        activelyRecording.set(false);
        Platform.runLater(() -> {
            state.set(RecordingState.PAUSED);
            if (elapsedTimer != null) elapsedTimer.pause();
        });
        System.out.println("[RecordingService] Recording paused");
    }

    public void resumeRecording() {
        if (state.get() != RecordingState.PAUSED) return;
        totalPausedMs += System.currentTimeMillis() - pauseStartMs;
        activelyRecording.set(true);
        Platform.runLater(() -> {
            state.set(RecordingState.RECORDING);
            if (elapsedTimer != null) elapsedTimer.play();
        });
        System.out.println("[RecordingService] Recording resumed");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public ReadOnlyObjectProperty<RecordingState> stateProperty()      { return state; }
    public RecordingState getState()                                    { return state.get(); }
    public ReadOnlyLongProperty   elapsedSecondsProperty()             { return elapsedSeconds; }
    public ReadOnlyStringProperty currentOutputPathProperty()          { return currentOutputPath; }
    public ReadOnlyStringProperty lastSavedPathProperty()              { return lastSavedPath; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void shutdownVideoScheduler() {
        if (videoScheduler == null) return;
        videoScheduler.shutdown();
        try {
            if (!videoScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                videoScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            videoScheduler.shutdownNow();
        }
        videoScheduler = null;
        System.out.println("[RecordingService] Video scheduler shut down");
    }

    private void stopAudio() {
        if (audioCapture != null) {
            audioCapture.stop();
            audioCapture = null;
            System.out.println("[RecordingService] Audio capture stopped");
        }
    }

    /**
     * Converts a Robot screenshot into the pre-allocated {@code dst} buffer
     * (which must be {@link BufferedImage#TYPE_3BYTE_BGR}).
     *
     * <h3>Why this is needed</h3>
     * {@code Robot.createScreenCapture()} returns {@code TYPE_INT_RGB} or
     * {@code TYPE_INT_ARGB}: each pixel is a 32-bit int {@code 0x00RRGGBB}.
     * On a little-endian CPU the four bytes in memory are {@code B G R 00}.
     * If we hand that directly to {@link Java2DFrameConverter} and then to
     * {@code FFmpegFrameRecorder}, libswscale sees 4-byte-per-pixel data
     * whose first byte is <em>Blue</em>, but treats it as the Red channel
     * (because RGB24 starts with Red).  Result: the classic red-tint bug.
     *
     * <h3>Fast path</h3>
     * For {@code TYPE_INT_RGB} and {@code TYPE_INT_ARGB} (both produced by
     * Robot on Windows) we read the int array directly and write the three
     * colour bytes into the BGR byte array with no object allocation.
     * A Graphics2D fallback handles any other image type.
     */
    private static void convertToBgr(BufferedImage src, BufferedImage dst) {
        int type = src.getType();
        if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB) {
            // Fast path: zero heap allocation, direct array access
            int[]  srcData = ((DataBufferInt)  src.getRaster().getDataBuffer()).getData();
            byte[] dstData = ((DataBufferByte) dst.getRaster().getDataBuffer()).getData();
            int numPixels = srcData.length;
            for (int i = 0, j = 0; i < numPixels; i++, j += 3) {
                int px = srcData[i];          // 0x00RRGGBB
                dstData[j]     = (byte)  (px        & 0xFF); // B
                dstData[j + 1] = (byte) ((px >>  8) & 0xFF); // G
                dstData[j + 2] = (byte) ((px >> 16) & 0xFF); // R
            }
        } else {
            // Fallback for unexpected image types (TYPE_3BYTE_BGR, etc.)
            java.awt.Graphics2D g = dst.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
        }
    }
}
