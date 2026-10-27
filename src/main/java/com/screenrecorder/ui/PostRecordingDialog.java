package com.screenrecorder.ui;

import com.screenrecorder.utils.FileUtils;
import com.screenrecorder.utils.ThemeManager;
import com.screenrecorder.utils.SettingsManager;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modal dialog shown automatically after a recording is saved.
 * Displays a video thumbnail, file metadata, and action buttons.
 */
public class PostRecordingDialog {

    private static final Logger LOG = Logger.getLogger(PostRecordingDialog.class.getName());
    private static final int THUMB_W = 320;
    private static final int THUMB_H = 180;

    /**
     * Creates and shows the dialog.  Safe to call from any thread.
     *
     * @param owner          the parent Stage (for modality)
     * @param filePath       absolute path to the saved MP4 file
     * @param durationSeconds recording duration in seconds
     */
    public static void show(Stage owner, String filePath, long durationSeconds) {
        Platform.runLater(() -> {
            Stage dialog = new Stage();
            dialog.initOwner(owner);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Recording Complete");
            dialog.setResizable(false);

            VBox root = buildLayout(dialog, filePath, durationSeconds);
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene, SettingsManager.getInstance().getSettings().getTheme());

            dialog.setScene(scene);
            dialog.show();

            // Extract thumbnail on a background thread so the dialog appears instantly
            new Thread(() -> {
                Image thumb = extractThumbnail(filePath);
                Platform.runLater(() -> {
                    // Find the ImageView placeholder and swap in the real image
                    ImageView iv = (ImageView) root.lookup("#thumbView");
                    if (iv != null && thumb != null) iv.setImage(thumb);
                });
            }, "thumbnail-extract").start();
        });
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private static VBox buildLayout(Stage dialog, String filePath, long durationSec) {
        File file = new File(filePath);

        // ── Thumbnail placeholder ──────────────────────────────────────────
        ImageView thumbView = new ImageView();
        thumbView.setId("thumbView");
        thumbView.setFitWidth(THUMB_W);
        thumbView.setFitHeight(THUMB_H);
        thumbView.setPreserveRatio(true);
        thumbView.setStyle("-fx-background-color: #000;");

        StackPane thumbContainer = new StackPane(thumbView);
        thumbContainer.setStyle("-fx-background-color: #000000; -fx-min-height: " + THUMB_H + "px;");
        thumbContainer.setMinHeight(THUMB_H);

        // Loading label shown while thumbnail extracts
        Label loadingLabel = new Label("Generating preview…");
        loadingLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        thumbContainer.getChildren().add(loadingLabel);
        thumbView.imageProperty().addListener((obs, o, n) -> {
            if (n != null) thumbContainer.getChildren().remove(loadingLabel);
        });

        // ── Metadata ───────────────────────────────────────────────────────
        String fileName = file.getName();
        String fileSize = file.exists()
            ? String.format("%.2f MB", file.length() / 1_048_576.0)
            : "–";
        String duration = FileUtils.formatDuration(durationSec);

        VBox meta = new VBox(6);
        meta.setPadding(new Insets(16, 22, 8, 22));
        meta.getChildren().addAll(
            metaRow("File",     fileName),
            metaRow("Size",     fileSize),
            metaRow("Duration", duration)
        );

        // ── Buttons ────────────────────────────────────────────────────────
        Button playBtn   = actionButton("▶  Play Video",   "#22c55e");
        Button folderBtn = actionButton("📁  Open Folder", "#6366f1");
        Button deleteBtn = actionButton("🗑  Delete",       "#ef4444");
        Button closeBtn  = actionButton("Close",            "#374151");

        playBtn.setOnAction(e -> {
            try { Desktop.getDesktop().open(file); }
            catch (Exception ex) { showErr("Cannot open file", ex.getMessage()); }
        });
        folderBtn.setOnAction(e -> {
            try { Desktop.getDesktop().open(file.getParentFile()); }
            catch (Exception ex) { showErr("Cannot open folder", ex.getMessage()); }
        });
        deleteBtn.setOnAction(e -> confirmDelete(file, dialog));
        closeBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(10, playBtn, folderBtn, deleteBtn, closeBtn);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(10, 22, 18, 22));

        // ── Root ───────────────────────────────────────────────────────────
        VBox root = new VBox(thumbContainer, meta, new Separator(), buttons);
        root.setStyle("-fx-background-color: -fx-background;");
        root.setPrefWidth(THUMB_W + 20);
        return root;
    }

    private static HBox metaRow(String label, String value) {
        Label lbl = new Label(label + ":");
        lbl.setMinWidth(70);
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: -fx-text-base-color; -fx-font-size: 12px; -fx-font-weight: bold;");
        val.setWrapText(true);
        val.setMaxWidth(260);
        HBox row = new HBox(10, lbl, val);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button actionButton(String text, String colorHex) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + colorHex + "22;"
            + "-fx-text-fill: " + colorHex + ";"
            + "-fx-border-color: " + colorHex + "55;"
            + "-fx-border-width: 1;"
            + "-fx-border-radius: 7;"
            + "-fx-background-radius: 7;"
            + "-fx-padding: 7 12 7 12;"
            + "-fx-cursor: hand;"
            + "-fx-font-size: 12px;");
        return btn;
    }

    // ── Thumbnail extraction ──────────────────────────────────────────────────

    private static Image extractThumbnail(String filePath) {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filePath)) {
            grabber.start();
            // Seek to 5 % of the video for a more interesting frame
            long len = grabber.getLengthInTime(); // microseconds
            if (len > 0) grabber.setTimestamp(len / 20);

            Frame frame = grabber.grabImage();
            if (frame != null && frame.image != null) {
                Java2DFrameConverter conv = new Java2DFrameConverter();
                BufferedImage img = conv.convert(frame);
                if (img != null) {
                    return SwingFXUtils.toFXImage(img, null);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Thumbnail extraction failed", e);
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void confirmDelete(File file, Stage dialog) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Recording");
        alert.setHeaderText(null);
        alert.setContentText("Permanently delete " + file.getName() + "?");
        alert.initOwner(dialog);
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                if (file.delete()) {
                    dialog.close();
                } else {
                    showErr("Delete failed", "Could not delete " + file.getAbsolutePath());
                }
            }
        });
    }

    private static void showErr(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }
}
