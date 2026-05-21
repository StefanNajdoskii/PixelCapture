package com.screenrecorder.ui;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.Rectangle;
import java.util.concurrent.CountDownLatch;

/**
 * Full-screen transparent overlay that lets the user drag-select a capture region.
 *
 * <p>Usage (must be called from the FX thread or via Platform.runLater):</p>
 * <pre>
 *   ScreenAreaSelector selector = new ScreenAreaSelector();
 *   Rectangle area = selector.selectArea(); // blocks until selection is made
 * </pre>
 */
public class ScreenAreaSelector {

    private Rectangle selected;

    /**
     * Shows the selection overlay and blocks the calling thread until the user
     * finishes dragging.  Returns null if the user pressed Escape or produced
     * a zero-size selection.
     *
     * <p><b>Must be called from a non-FX thread</b> (e.g. a button handler that
     * dispatches via {@code Platform.runLater} + a latch).</p>
     */
    public Rectangle selectArea() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> showOverlay(latch));
        latch.await();

        return selected;
    }

    // ── Private ────────────────────────────────────────────────────────────

    private void showOverlay(CountDownLatch latch) {
        Rectangle2D screen = Screen.getPrimary().getBounds();

        Canvas canvas = new Canvas(screen.getWidth(), screen.getHeight());
        drawDimOverlay(canvas.getGraphicsContext2D(), screen, 0, 0, 0, 0, false);

        final double[] drag = new double[4]; // startX, startY, currentX, currentY

        canvas.setCursor(Cursor.CROSSHAIR);

        canvas.setOnMousePressed(e -> {
            drag[0] = e.getX();
            drag[1] = e.getY();
        });

        canvas.setOnMouseDragged(e -> {
            drag[2] = e.getX();
            drag[3] = e.getY();
            drawDimOverlay(canvas.getGraphicsContext2D(), screen,
                drag[0], drag[1], drag[2], drag[3], true);
        });

        canvas.setOnMouseReleased(e -> {
            double x = Math.min(drag[0], e.getX());
            double y = Math.min(drag[1], e.getY());
            double w = Math.abs(e.getX() - drag[0]);
            double h = Math.abs(e.getY() - drag[1]);

            if (w > 10 && h > 10) {
                selected = new Rectangle((int) x, (int) y, (int) w, (int) h);
            } else {
                selected = null;
            }

            Stage stage = (Stage) canvas.getScene().getWindow();
            stage.close();
            latch.countDown();
        });

        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, screen.getWidth(), screen.getHeight());
        scene.setFill(null);

        // Escape cancels the selection
        scene.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ESCAPE")) {
                selected = null;
                Stage stage = (Stage) scene.getWindow();
                stage.close();
                latch.countDown();
            }
        });

        Stage overlay = new Stage(StageStyle.TRANSPARENT);
        overlay.setScene(scene);
        overlay.setX(screen.getMinX());
        overlay.setY(screen.getMinY());
        overlay.setAlwaysOnTop(true);
        overlay.show();
    }

    private void drawDimOverlay(GraphicsContext gc, Rectangle2D screen,
                                 double sx, double sy, double ex, double ey,
                                 boolean hasSelection) {
        gc.clearRect(0, 0, screen.getWidth(), screen.getHeight());

        // Semi-transparent dark vignette
        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRect(0, 0, screen.getWidth(), screen.getHeight());

        if (hasSelection) {
            double x = Math.min(sx, ex);
            double y = Math.min(sy, ey);
            double w = Math.abs(ex - sx);
            double h = Math.abs(ey - sy);

            // Clear inside the selection so the real screen shows through
            gc.clearRect(x, y, w, h);

            // Selection border
            gc.setStroke(Color.rgb(99, 102, 241));
            gc.setLineWidth(2);
            gc.strokeRect(x, y, w, h);

            // Corner handles
            double cs = 6;
            gc.setFill(Color.rgb(99, 102, 241));
            gc.fillRect(x - cs / 2, y - cs / 2, cs, cs);
            gc.fillRect(x + w - cs / 2, y - cs / 2, cs, cs);
            gc.fillRect(x - cs / 2, y + h - cs / 2, cs, cs);
            gc.fillRect(x + w - cs / 2, y + h - cs / 2, cs, cs);

            // Dimension label
            String dim = String.format("%d × %d", (int) w, (int) h);
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font("Segoe UI", 13));
            gc.fillText(dim, x + 6, y - 6 < 14 ? y + 18 : y - 6);
        } else {
            // Hint text
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font("Segoe UI", 16));
            String hint = "Drag to select capture area  •  Esc to cancel";
            gc.fillText(hint,
                screen.getWidth() / 2 - 200,
                screen.getHeight() / 2);
        }
    }
}
