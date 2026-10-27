package com.screenrecorder.recorder;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Captures webcam frames on a daemon thread using OpenCV's {@code VideoCapture}
 * and {@code Mat} API directly — bypassing JavaCV's {@code OpenCVFrameGrabber}
 * which returns frames with {@code image == null} on many Windows systems.
 *
 * <h3>Capture pipeline</h3>
 * {@code VideoCapture.read(mat)} → {@code matToImage(mat)} → {@link FrameSnapshot} ring buffer
 * The {@link #getFrameClosestTo(long)} method returns the snapshot whose wall-clock timestamp
 * best matches the screen-capture time, keeping the overlay in sync even when the
 * recording thread runs slower than the configured FPS.
 *
 * <h3>Permission errors on Windows</h3>
 * If the Windows Privacy setting blocks camera access, {@code VideoCapture.isOpened()}
 * returns {@code false} after opening.  The {@link #start} method throws a descriptive
 * {@link CameraPermissionException} so callers can show the right guidance to the user.
 *
 * <h3>Dependency check</h3>
 * {@link #isAvailable()} tests in two steps:
 * <ol>
 *   <li>{@code Class.forName} — proves {@code org.bytedeco:opencv:4.9.0-1.5.10} (no classifier) is on the classpath.</li>
 *   <li>{@code new VideoCapture()} — proves {@code org.bytedeco:opencv:4.9.0-1.5.10:windows-x86_64} DLLs loaded.</li>
 * </ol>
 */
public class WebcamCapture {

    private static final Logger LOG = Logger.getLogger(WebcamCapture.class.getName());

    // ── Static availability check (lazy, runs once) ───────────────────────────

    private static volatile Boolean OPENCV_STATUS     = null;
    private static          String  OPENCV_DIAGNOSTIC = "";

    /** {@code true} if both the OpenCV Java bindings JAR and native DLLs are operational. */
    public static boolean isAvailable() {
        if (OPENCV_STATUS == null) runAvailabilityCheck();
        return OPENCV_STATUS;
    }

    public static String getDiagnostic() { return OPENCV_DIAGNOSTIC; }

    private static synchronized void runAvailabilityCheck() {
        if (OPENCV_STATUS != null) return;

        System.out.println("[WebcamCapture] ═══ OpenCV availability check ═══");

        // Step A — JAR presence (Class.forName never loads native code)
        System.out.println("[WebcamCapture] Step A: Java bindings on classpath?");
        try {
            Class.forName("org.bytedeco.opencv.opencv_videoio.VideoCapture");
            System.out.println("[WebcamCapture]   ✓ org.bytedeco.opencv.opencv_videoio.VideoCapture found");
        } catch (ClassNotFoundException e) {
            OPENCV_DIAGNOSTIC = "JAR missing: add org.bytedeco:opencv:4.9.0-1.5.10 (no classifier) to pom.xml";
            System.out.println("[WebcamCapture]   ✗ ClassNotFoundException — " + OPENCV_DIAGNOSTIC);
            OPENCV_STATUS = false;
            return;
        } catch (Throwable t) {
            OPENCV_DIAGNOSTIC = t.getClass().getSimpleName() + ": " + t.getMessage();
            System.out.println("[WebcamCapture]   ✗ " + OPENCV_DIAGNOSTIC);
            OPENCV_STATUS = false;
            return;
        }

        // Step B — native DLL (using fully-qualified name so it only resolves here,
        //           after Step A proved the class exists — prevents NoClassDefFoundError
        //           during WebcamCapture class-load in a broken classpath environment)
        System.out.println("[WebcamCapture] Step B: native DLL loads?");
        try {
            org.bytedeco.opencv.opencv_videoio.VideoCapture probe =
                new org.bytedeco.opencv.opencv_videoio.VideoCapture();
            probe.release();
            System.out.println("[WebcamCapture]   ✓ VideoCapture() instantiated — native DLL OK");
        } catch (UnsatisfiedLinkError e) {
            OPENCV_DIAGNOSTIC = "Native DLL failed: " + e.getMessage()
                + ". Ensure org.bytedeco:opencv:4.9.0-1.5.10:windows-x86_64 is in pom.xml "
                + "and Visual C++ Redistributable 2019+ is installed.";
            System.out.println("[WebcamCapture]   ✗ UnsatisfiedLinkError: " + e.getMessage());
            System.out.println("[WebcamCapture]     Fix A: check opencv:windows-x86_64 in pom.xml");
            System.out.println("[WebcamCapture]     Fix B: install Visual C++ Redistributable 2019+");
            OPENCV_STATUS = false;
            return;
        } catch (Throwable t) {
            OPENCV_DIAGNOSTIC = t.getClass().getSimpleName() + ": " + t.getMessage();
            System.out.println("[WebcamCapture]   ✗ " + OPENCV_DIAGNOSTIC);
            t.printStackTrace(System.err);
            OPENCV_STATUS = false;
            return;
        }

        System.out.println("[WebcamCapture] ✓ OpenCV fully available");
        OPENCV_STATUS = true;
    }

    // ── Camera permission exception ───────────────────────────────────────────

    /**
     * Thrown when a camera is physically present but the OS blocks access —
     * e.g. Windows Privacy → Camera setting, or the camera is locked by another app.
     */
    public static class CameraPermissionException extends Exception {
        public CameraPermissionException(int deviceIndex, String detail) {
            super("Cannot open Camera " + deviceIndex + ". " + detail);
        }
    }

    // ── Instance state ────────────────────────────────────────────────────────

    // Fields use fully-qualified names so the class compiles even if opencv JARs
    // are absent — the unavailability check gates all actual usage.
    private org.bytedeco.opencv.opencv_videoio.VideoCapture cap;
    private Thread             captureThread;
    private volatile boolean   running;
    private int                capturedWidth, capturedHeight;

    /**
     * Immutable snapshot of one captured webcam frame paired with the wall-clock
     * nanosecond timestamp at which it was grabbed from the camera.
     */
    public record FrameSnapshot(long captureNs, BufferedImage image) {}

    // ── Ring buffer (single writer: capture thread; single reader: compositor) ──
    // Six slots ≈ 200 ms of history at 30 fps — enough to cover any screen-capture jitter.
    private static final int RING = 6;
    private final FrameSnapshot[] ring = new FrameSnapshot[RING];
    /**
     * Index of the most recently written ring slot, or -1 before the first frame.
     * Volatile so the compositor thread sees all ring writes that preceded the update.
     */
    private volatile int ringHead = -1;

    private void publishFrame(BufferedImage img) {
        int next = (ringHead + 1 + RING) % RING;
        ring[next] = new FrameSnapshot(System.nanoTime(), img);
        ringHead = next; // volatile write — publishes the ring[] write above
    }

    /** Returns the most recent frame, or {@code null} if capture hasn't delivered one yet. */
    public BufferedImage getLatestFrame() {
        int head = ringHead;
        if (head < 0) return null;
        FrameSnapshot snap = ring[head];
        return snap != null ? snap.image() : null;
    }

    /**
     * Returns the {@link FrameSnapshot} whose wall-clock timestamp is closest to
     * {@code targetNs}, searching all ring slots visible at the time of the call.
     * Returns {@code null} if no frames have been captured yet.
     */
    public FrameSnapshot getFrameClosestTo(long targetNs) {
        int head = ringHead; // volatile read — establishes happens-before with publishFrame()
        if (head < 0) return null;
        FrameSnapshot best = null;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < RING; i++) {
            FrameSnapshot snap = ring[i];
            if (snap == null) continue;
            long dist = Math.abs(snap.captureNs() - targetNs);
            if (dist < bestDist) { bestDist = dist; best = snap; }
        }
        return best;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens the camera and starts the background capture thread.
     * The thread reads frames at the camera's native FPS (driven by {@code cap.read()}
     * blocking) rather than self-pacing with a sleep timer, ensuring the ring buffer
     * always reflects real wall-clock time rather than an independent tick counter.
     *
     * @throws IllegalStateException      if OpenCV is unavailable (check pom.xml)
     * @throws CameraPermissionException  if the camera is physically present but blocked
     * @throws Exception                  for any other camera open failure
     */
    public void start(int deviceIndex, int fps) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenCV not available: " + OPENCV_DIAGNOSTIC);
        }
        int targetFps = Math.max(1, fps);
        System.out.println("[WebcamCapture] Opening camera index " + deviceIndex
            + " via VideoCapture (target " + targetFps + " fps)…");

        cap = new org.bytedeco.opencv.opencv_videoio.VideoCapture(deviceIndex);
        if (!cap.isOpened()) {
            cap.release();
            cap = null;
            throw new CameraPermissionException(deviceIndex,
                "VideoCapture.isOpened() returned false. Possible causes:\n"
                + "  • Another app (Teams, Chrome, OBS) is already using the camera\n"
                + "  • Windows Privacy → Camera → allow desktop apps is turned OFF\n"
                + "  • The camera index is wrong (check the device list)");
        }

        // Hint to the driver to deliver frames near the recording FPS.
        // Not all backends honour this, but it keeps the camera from running
        // at its maximum rate (e.g. 60 fps) when 30 fps is all we need.
        cap.set(org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FPS, targetFps);

        // Buffer size 1: only the most-recent frame is kept by the driver.
        // This prevents the DirectShow / MSMF backend from building up a queue of
        // old frames that the capture loop would drain in a burst — which is what
        // makes the overlay appear to run at faster-than-realtime speed.
        cap.set(org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_BUFFERSIZE, 1);

        capturedWidth  = (int) cap.get(org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH);
        capturedHeight = (int) cap.get(org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT);
        System.out.println("[WebcamCapture] Camera " + deviceIndex + " opened at "
            + capturedWidth + "×" + capturedHeight);

        running = true;
        captureThread = new Thread(this::captureLoop, "webcam-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public int getCapturedWidth()  { return capturedWidth; }
    public int getCapturedHeight() { return capturedHeight; }

    public void stop() {
        running = false;
        if (captureThread != null) captureThread.interrupt();
        if (cap != null) {
            try { cap.release(); } catch (Throwable ignored) {}
            cap = null;
        }
        ringHead = -1; // mark ring as empty so getFrameClosestTo() returns null
        System.out.println("[WebcamCapture] Stopped");
    }

    // ── Capture loop ──────────────────────────────────────────────────────────

    /**
     * Reads webcam frames as fast as the camera delivers them — no artificial
     * sleep-based pacing.  {@code cap.read()} blocks inside the OS camera driver
     * until the hardware produces the next frame, so the loop naturally runs at
     * the camera's native FPS.
     *
     * <p>Why no sleep: if we pace the loop with our own timer, we advance the
     * {@link #ring} at a fixed wall-clock rate that is independent of the
     * recording thread.  When screen-capture overhead causes the recording thread
     * to run slower than the target FPS, the ring advances faster than frames are
     * composited.  The compositor then picks webcam frames that are ahead of real
     * time relative to the screen content, making the overlay appear sped-up.</p>
     *
     * <p>By letting the camera drive timing, the ring always reflects the true
     * wall-clock moment each frame was grabbed.  The compositor calls
     * {@link #getFrameClosestTo(long)} with the wall-clock timestamp of the
     * corresponding screen capture, so both overlays always reflect the same
     * real-world instant regardless of whether the recording is running slower
     * or faster than the configured FPS.</p>
     */
    private void captureLoop() {
        org.bytedeco.opencv.opencv_core.Mat mat =
            new org.bytedeco.opencv.opencv_core.Mat();
        try {
            while (running) {
                try {
                    if (cap.read(mat) && !mat.empty()) {
                        BufferedImage img = matToImage(mat);
                        if (img != null) publishFrame(img);
                        // No sleep: cap.read() blocks at the camera's native FPS.
                    } else {
                        // cap.read() returned false — no frame ready yet.
                        // Sleep 1 ms to avoid a busy-spin on cameras whose backend
                        // returns immediately instead of blocking.
                        Thread.sleep(1);
                    }
                } catch (InterruptedException ie) {
                    break;
                } catch (Throwable t) {
                    if (running) LOG.log(Level.FINE, "Webcam grab error", t);
                }
            }
        } finally {
            mat.release();
        }
    }

    /**
     * Converts an OpenCV BGR {@link org.bytedeco.opencv.opencv_core.Mat} to a
     * {@link BufferedImage#TYPE_3BYTE_BGR}.
     *
     * <p>OpenCV's default webcam output is 3-channel BGR.  Java's
     * {@code TYPE_3BYTE_BGR} stores pixels in the same B-G-R byte order,
     * so a direct {@code arraycopy} is sufficient — no channel swapping needed.</p>
     */
    private static BufferedImage matToImage(org.bytedeco.opencv.opencv_core.Mat mat) {
        int w = mat.cols(), h = mat.rows(), ch = mat.channels();
        if (w <= 0 || h <= 0 || ch != 3) return null; // only handle BGR

        byte[] srcBytes = new byte[w * h * ch];
        mat.data().get(srcBytes);

        BufferedImage img  = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] dstBytes    = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        System.arraycopy(srcBytes, 0, dstBytes, 0, srcBytes.length);
        return img;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    /**
     * Enumerates connected cameras by probing indices 0–3 with {@code VideoCapture}.
     * Triggers the lazy availability check on first call.
     * Never throws — returns a diagnostic string instead.
     */
    public static List<String> availableDevices() {
        if (!isAvailable()) {
            System.out.println("[WebcamCapture] Skipping enumeration — " + OPENCV_DIAGNOSTIC);
            return List.of("OpenCV unavailable: " + OPENCV_DIAGNOSTIC);
        }

        System.out.println("[WebcamCapture] Enumerating cameras (probing indices 0–3)…");
        List<String> devices = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            System.out.print("[WebcamCapture]   Camera " + i + ": ");
            org.bytedeco.opencv.opencv_videoio.VideoCapture probe = null;
            try {
                probe = new org.bytedeco.opencv.opencv_videoio.VideoCapture(i);
                if (probe.isOpened()) {
                    int w = (int) probe.get(org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH);
                    int h = (int) probe.get(org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT);
                    String label = "Camera " + i + (w > 0 ? " (" + w + "×" + h + ")" : "");
                    devices.add(label);
                    System.out.println("available ✓  " + w + "×" + h);
                } else {
                    System.out.println("not opened — no camera at this index or permission blocked");
                    probe.release();
                    break;
                }
                probe.release();
            } catch (Throwable t) {
                System.out.println("error — " + t.getClass().getSimpleName() + ": " + t.getMessage());
                try { if (probe != null) probe.release(); } catch (Throwable ignored) {}
                break;
            }
        }

        System.out.println("[WebcamCapture] Found " + devices.size() + " device(s): " + devices);
        if (devices.isEmpty()) devices.add("No webcam detected");
        return devices;
    }

    /** Extracts the numeric index from labels like {@code "Camera 0 (640×480)"}. */
    public static int indexFromLabel(String label) {
        if (label == null) return 0;
        try { return Integer.parseInt(label.replaceAll("[^0-9].*", "").trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
