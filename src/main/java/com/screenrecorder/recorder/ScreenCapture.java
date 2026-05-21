package com.screenrecorder.recorder;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Captures screen frames using {@link Robot}.
 * Call {@link #captureFrame()} repeatedly from the recording thread.
 */
public class ScreenCapture {

    private final Robot  robot;
    private       Rectangle captureArea;

    public ScreenCapture(Rectangle captureArea) throws AWTException {
        this.robot = new Robot();
        this.captureArea = captureArea != null ? captureArea : fullScreenBounds();
    }

    public BufferedImage captureFrame() {
        return robot.createScreenCapture(captureArea);
    }

    public void setCaptureArea(Rectangle area) {
        this.captureArea = area != null ? area : fullScreenBounds();
    }

    public Rectangle getCaptureArea() { return captureArea; }

    public int getWidth()  { return captureArea.width; }
    public int getHeight() { return captureArea.height; }

    public static Rectangle fullScreenBounds() {
        return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    /**
     * Returns an area with even width/height (required by H264 encoder).
     */
    public static Rectangle toEvenDimensions(Rectangle r) {
        int w = r.width  % 2 == 0 ? r.width  : r.width  - 1;
        int h = r.height % 2 == 0 ? r.height : r.height - 1;
        return new Rectangle(r.x, r.y, w, h);
    }
}
