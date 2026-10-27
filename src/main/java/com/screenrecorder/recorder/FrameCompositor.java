package com.screenrecorder.recorder;

import com.screenrecorder.models.OverlayConfig;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Composites the webcam PiP and/or FPS counter directly onto each
 * {@link BufferedImage} screen-capture frame before it is encoded by FFmpeg.
 *
 * <p><b>Threading</b>: {@link #composite(BufferedImage, long)} is called from the
 * video-capture thread.  The webcam frame is selected via
 * {@link WebcamCapture#getFrameClosestTo(long)}, which reads a volatile ring-buffer
 * index, so no additional synchronisation is required.</p>
 *
 * <p><b>Performance</b>: The method mutates the supplied image in-place using
 * Graphics2D.  No intermediate allocations occur per frame for the FPS counter.
 * A temporary scaled webcam image is created once per frame when webcam is enabled.</p>
 */
public class FrameCompositor {

    private final OverlayConfig config;
    private final WebcamCapture webcamCapture;

    // FPS tracking
    private long   fpsWindowStartMs = System.currentTimeMillis();
    private int    fpsFrameCounter  = 0;
    private double measuredFps      = 0;

    // Pre-built font (reused every frame)
    private Font fpsFont;

    public FrameCompositor(OverlayConfig config, WebcamCapture webcamCapture) {
        this.config       = config;
        this.webcamCapture = webcamCapture;
        this.fpsFont = new Font(Font.MONOSPACED, Font.BOLD, config.getFpsCounterFontSize());
    }

    /**
     * Draws all enabled overlays onto {@code frame} in-place.
     *
     * @param frame      mutable screen-capture image (TYPE_INT_RGB or TYPE_INT_ARGB)
     * @param captureNs  {@link System#nanoTime()} recorded just before the screen frame
     *                   was captured — used to select the webcam snapshot whose own
     *                   capture time is closest, ensuring the overlay reflects the same
     *                   real-world instant as the screen content
     */
    public void composite(BufferedImage frame, long captureNs) {
        // Update FPS counter (always, even if not displayed, so it stays accurate)
        updateFps();

        if (!config.isWebcamEnabled() && !config.isFpsCounterEnabled()) return;

        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = frame.getWidth();
        int h = frame.getHeight();

        if (config.isWebcamEnabled() && webcamCapture != null) {
            WebcamCapture.FrameSnapshot snap = webcamCapture.getFrameClosestTo(captureNs);
            if (snap != null) {
                drawWebcam(g, snap.image(), w, h);
            }
        }

        if (config.isFpsCounterEnabled()) {
            drawFpsCounter(g, w, h);
        }

        g.dispose();
    }

    // ── Webcam PiP ────────────────────────────────────────────────────────────

    private void drawWebcam(Graphics2D g, BufferedImage src, int fw, int fh) {
        int pad  = config.getWebcamPadding();
        int size = (int) (Math.min(fw, fh) * config.getWebcamSizePercent() / 100.0);
        size = Math.max(size, 60); // minimum 60 px

        int x = calcX(config.getWebcamPosition(), fw, size, pad);
        int y = calcY(config.getWebcamPosition(), fh, size, pad);

        // Scaled webcam image (aspect-ratio-preserved inside the square)
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gs = scaled.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gs.drawImage(src, 0, 0, size, size, null);
        gs.dispose();

        // Clip shape
        Shape clip = buildClip(config.getWebcamShape(), x, y, size);

        // Save composite + clip state
        Composite oldComp = g.getComposite();
        Shape     oldClip = g.getClip();

        g.setClip(clip);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, config.getWebcamOpacity()));
        g.drawImage(scaled, x, y, null);

        // Restore clip before drawing border (border should be outside the clip)
        g.setClip(oldClip);
        g.setComposite(oldComp);

        // Border
        int bw = config.getWebcamBorderWidth();
        if (bw > 0) {
            g.setColor(OverlayConfig.toAwtColor(config.getWebcamBorderColor()));
            g.setStroke(new BasicStroke(bw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Shrink the stroke so it sits just inside the clip edge
            float inset = bw / 2.0f;
            Shape borderShape = buildClip(config.getWebcamShape(),
                (int)(x + inset), (int)(y + inset), (int)(size - bw));
            g.draw(borderShape);
        }
    }

    private Shape buildClip(String shape, int x, int y, int size) {
        return switch (shape) {
            case "CIRCLE"       -> new Ellipse2D.Float(x, y, size, size);
            case "ROUNDED_RECT" -> new RoundRectangle2D.Float(x, y, size, size, 20, 20);
            default             -> new Rectangle(x, y, size, size);
        };
    }

    // ── FPS Counter ───────────────────────────────────────────────────────────

    private void drawFpsCounter(Graphics2D g, int fw, int fh) {
        String text = String.format("%.0f FPS", measuredFps);
        int pad = config.getFpsCounterPadding();

        g.setFont(fpsFont);
        FontRenderContext frc = g.getFontRenderContext();
        TextLayout layout = new TextLayout(text, fpsFont, frc);
        Rectangle2D bounds = layout.getBounds();

        int tw = (int) bounds.getWidth()  + pad * 2;
        int th = (int) bounds.getHeight() + pad * 2;

        int x = calcX(config.getFpsCounterPosition(), fw, tw, 10);
        int y = calcY(config.getFpsCounterPosition(), fh, th, 10);

        // Background
        if (config.isFpsCounterBgEnabled()) {
            Composite oldComp = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, config.getFpsCounterBgOpacity()));
            g.setColor(OverlayConfig.toAwtColor(config.getFpsCounterBgColor()));
            g.fillRoundRect(x, y, tw, th, 6, 6);
            g.setComposite(oldComp);
        }

        // Text
        g.setColor(OverlayConfig.toAwtColor(config.getFpsCounterTextColor()));
        float tx = x + pad + (float)(-bounds.getX());
        float ty = y + pad + (float)(-bounds.getY());
        layout.draw(g, tx, ty);
    }

    // ── Position helpers ──────────────────────────────────────────────────────

    private static int calcX(String pos, int frameW, int elemW, int pad) {
        return switch (pos) {
            case "TOP_RIGHT", "BOTTOM_RIGHT" -> frameW - elemW - pad;
            case "CENTER"                    -> (frameW - elemW) / 2;
            default                          -> pad;  // TOP_LEFT / BOTTOM_LEFT
        };
    }

    private static int calcY(String pos, int frameH, int elemH, int pad) {
        return switch (pos) {
            case "BOTTOM_LEFT", "BOTTOM_RIGHT" -> frameH - elemH - pad;
            case "CENTER"                      -> (frameH - elemH) / 2;
            default                            -> pad; // TOP_LEFT / TOP_RIGHT
        };
    }

    // ── FPS measurement ───────────────────────────────────────────────────────

    private void updateFps() {
        fpsFrameCounter++;
        long now = System.currentTimeMillis();
        long elapsed = now - fpsWindowStartMs;
        if (elapsed >= 1000) {
            measuredFps = fpsFrameCounter * 1000.0 / elapsed;
            fpsFrameCounter  = 0;
            fpsWindowStartMs = now;
        }
    }
}
