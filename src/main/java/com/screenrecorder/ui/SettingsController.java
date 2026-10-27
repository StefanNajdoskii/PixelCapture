package com.screenrecorder.ui;

import com.screenrecorder.models.AppSettings;
import com.screenrecorder.models.OverlayConfig;
import com.screenrecorder.recorder.AudioCapture;
import com.screenrecorder.recorder.WebcamCapture;
import com.screenrecorder.services.HotkeyManager;
import com.screenrecorder.utils.SettingsManager;
import com.screenrecorder.utils.ThemeManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class SettingsController implements Initializable {

    private static final Logger LOG = Logger.getLogger(SettingsController.class.getName());

    // ── Video ─────────────────────────────────────────────────────────────────
    @FXML private ComboBox<String>  qualityCombo;
    @FXML private ComboBox<Integer> fpsCombo;

    // ── Audio ─────────────────────────────────────────────────────────────────
    @FXML private CheckBox          captureAudioCheck;
    @FXML private ComboBox<String>  audioDeviceCombo;

    // ── Overlay: webcam ───────────────────────────────────────────────────────
    @FXML private CheckBox          webcamEnabledCheck;
    @FXML private ComboBox<String>  webcamDeviceCombo;
    @FXML private StackPane         webcamPreviewPane;
    @FXML private ImageView         webcamPreviewView;
    @FXML private Label             webcamPreviewLabel;
    @FXML private VBox              permissionWarningBox;
    @FXML private Label             permissionWarningLabel;
    @FXML private ComboBox<String>  webcamPositionCombo;
    @FXML private ComboBox<String>  webcamShapeCombo;
    @FXML private Slider            webcamSizeSlider;
    @FXML private Label             webcamSizeLabel;
    @FXML private Slider            webcamBorderSlider;
    @FXML private Label             webcamBorderLabel;
    @FXML private ColorPicker       webcamBorderColorPicker;
    @FXML private Slider            webcamOpacitySlider;
    @FXML private Label             webcamOpacityLabel;

    // ── Overlay: FPS counter ──────────────────────────────────────────────────
    @FXML private CheckBox          fpsCounterCheck;
    @FXML private ComboBox<String>  fpsPositionCombo;
    @FXML private Slider            fpsFontSizeSlider;
    @FXML private Label             fpsFontSizeLabel;
    @FXML private ColorPicker       fpsTextColorPicker;
    @FXML private CheckBox          fpsBgCheck;
    @FXML private ColorPicker       fpsBgColorPicker;

    // ── Hotkeys ───────────────────────────────────────────────────────────────
    @FXML private TextField         hotkeyStartField;
    @FXML private TextField         hotkeyStopField;
    @FXML private TextField         hotkeyPauseField;
    @FXML private TextField         hotkeyScreenshotField;

    // ── Appearance ────────────────────────────────────────────────────────────
    @FXML private ToggleGroup       themeGroup;
    @FXML private RadioButton       themeAutoRadio;
    @FXML private RadioButton       themeDarkRadio;
    @FXML private RadioButton       themeLightRadio;

    // ── Preview lifecycle ─────────────────────────────────────────────────────
    private WebcamCapture previewCapture;
    private Timeline      previewTimeline;
    private WritableImage previewBuffer;

    // ── Hotkey capture ────────────────────────────────────────────────────────
    private TextField capturingField;

    private Runnable onSaveCallback;
    public void setOnSave(Runnable cb) { this.onSaveCallback = cb; }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AppSettings s = SettingsManager.getInstance().getSettings();

        // Video
        qualityCombo.getItems().addAll("Low", "Medium", "High", "Ultra");
        qualityCombo.setValue(s.getVideoQuality());
        fpsCombo.getItems().addAll(15, 24, 30, 60);
        fpsCombo.setValue(s.getFps());

        // Audio
        captureAudioCheck.setSelected(s.isCaptureAudio());
        audioDeviceCombo.getItems().addAll(AudioCapture.availableDevices());
        audioDeviceCombo.setValue(s.getAudioDevice());
        audioDeviceCombo.disableProperty().bind(captureAudioCheck.selectedProperty().not());

        // Overlay — webcam
        webcamEnabledCheck.setSelected(s.isWebcamEnabled());
        webcamDeviceCombo.getItems().add("Scanning for cameras…");
        webcamDeviceCombo.getSelectionModel().selectFirst();

        // Style the preview pane
        webcamPreviewPane.setStyle("-fx-background-color: #000000;");
        webcamPreviewLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        // Auto-start preview when checkbox is checked
        webcamEnabledCheck.selectedProperty().addListener((obs, wasOn, isOn) -> {
            if (isOn) {
                int idx = WebcamCapture.indexFromLabel(webcamDeviceCombo.getValue());
                startInlinePreview(idx);
            } else {
                stopInlinePreview();
                hidePermissionWarning();
            }
        });

        // Restart preview if the user changes the device combo
        webcamDeviceCombo.setOnAction(e -> {
            if (webcamEnabledCheck.isSelected()) {
                int idx = WebcamCapture.indexFromLabel(webcamDeviceCombo.getValue());
                startInlinePreview(idx);
            }
        });

        // Enumerate cameras in background
        new Thread(() -> {
            System.out.println("[SettingsController] Starting webcam enumeration…");
            List<String> cams;
            try { cams = WebcamCapture.availableDevices(); }
            catch (Throwable t) { cams = List.of("Error: " + t.getMessage()); }
            System.out.println("[SettingsController] Cameras found: " + cams);
            final List<String> finalCams = cams;
            Platform.runLater(() -> {
                webcamDeviceCombo.getItems().setAll(finalCams);
                int idx = s.getWebcamDeviceIndex();
                webcamDeviceCombo.setValue(idx < finalCams.size() ? finalCams.get(idx) : finalCams.get(0));

                if (!WebcamCapture.isAvailable()) {
                    webcamEnabledCheck.setSelected(false);
                    webcamEnabledCheck.setDisable(true);
                    showPermissionWarning("OpenCV not available: " + WebcamCapture.getDiagnostic());
                } else if (s.isWebcamEnabled()) {
                    startInlinePreview(s.getWebcamDeviceIndex());
                }
            });
        }, "webcam-enum").start();

        webcamPositionCombo.getItems().addAll("Top-Left","Top-Right","Bottom-Left","Bottom-Right","Center");
        webcamPositionCombo.setValue(positionLabel(s.getWebcamPosition()));

        webcamShapeCombo.getItems().addAll("Circle", "Rounded Rectangle", "Rectangle");
        webcamShapeCombo.setValue(shapeLabel(s.getWebcamShape()));

        webcamSizeSlider.setValue(s.getWebcamSizePercent());
        webcamSizeLabel.setText(s.getWebcamSizePercent() + "%");
        webcamSizeSlider.valueProperty().addListener((obs,o,n) -> webcamSizeLabel.setText(n.intValue() + "%"));

        webcamBorderSlider.setValue(s.getWebcamBorderWidth());
        webcamBorderLabel.setText(s.getWebcamBorderWidth() + " px");
        webcamBorderSlider.valueProperty().addListener((obs,o,n) -> webcamBorderLabel.setText(n.intValue() + " px"));

        webcamBorderColorPicker.setValue(OverlayConfig.toFxColor(s.getWebcamBorderColor()));

        webcamOpacitySlider.setValue(s.getWebcamOpacityPct());
        webcamOpacityLabel.setText(s.getWebcamOpacityPct() + "%");
        webcamOpacitySlider.valueProperty().addListener((obs,o,n) -> webcamOpacityLabel.setText(n.intValue() + "%"));

        // Overlay — FPS counter
        fpsCounterCheck.setSelected(s.isFpsCounterEnabled());
        fpsPositionCombo.getItems().addAll("Top-Left","Top-Right","Bottom-Left","Bottom-Right");
        fpsPositionCombo.setValue(positionLabel(s.getFpsCounterPosition()));

        fpsFontSizeSlider.setValue(s.getFpsCounterFontSize());
        fpsFontSizeLabel.setText(s.getFpsCounterFontSize() + " px");
        fpsFontSizeSlider.valueProperty().addListener((obs,o,n) -> fpsFontSizeLabel.setText(n.intValue() + " px"));

        fpsTextColorPicker.setValue(OverlayConfig.toFxColor(s.getFpsCounterTextColor()));
        fpsBgCheck.setSelected(s.isFpsCounterBg());
        fpsBgColorPicker.setValue(OverlayConfig.toFxColor(s.getFpsCounterBgColor()));
        fpsBgColorPicker.disableProperty().bind(fpsBgCheck.selectedProperty().not());

        // Hotkeys
        hotkeyStartField.setText(s.getHotkeyStart());
        hotkeyStopField.setText(s.getHotkeyStop());
        hotkeyPauseField.setText(s.getHotkeyPause());
        hotkeyScreenshotField.setText(s.getHotkeyScreenshot());
        installHotkeyCaptureOn(hotkeyStartField);
        installHotkeyCaptureOn(hotkeyStopField);
        installHotkeyCaptureOn(hotkeyPauseField);
        installHotkeyCaptureOn(hotkeyScreenshotField);

        // Appearance
        switch (s.getTheme().toUpperCase()) {
            case "DARK"  -> themeGroup.selectToggle(themeDarkRadio);
            case "LIGHT" -> themeGroup.selectToggle(themeLightRadio);
            default      -> themeGroup.selectToggle(themeAutoRadio);
        }
    }

    // ── Inline webcam preview ─────────────────────────────────────────────────

    private void startInlinePreview(int deviceIndex) {
        stopInlinePreview(); // stop any running preview first
        hidePermissionWarning();

        if (!WebcamCapture.isAvailable()) {
            showPermissionWarning(WebcamCapture.getDiagnostic());
            return;
        }

        webcamPreviewLabel.setText("Opening camera " + deviceIndex + "…");
        webcamPreviewLabel.setVisible(true);

        WebcamCapture cap = new WebcamCapture();
        new Thread(() -> {
            try {
                cap.start(deviceIndex, 15); // preview runs at 15 fps
                Platform.runLater(() -> {
                    previewCapture = cap;
                    webcamPreviewLabel.setText(""); // hide when frames arrive
                    startPreviewTimeline();
                });
            } catch (WebcamCapture.CameraPermissionException ex) {
                Platform.runLater(() -> {
                    webcamPreviewLabel.setText("Camera could not be opened");
                    showPermissionWarning(ex.getMessage());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    webcamPreviewLabel.setText("Error: " + ex.getMessage());
                    showPermissionWarning(ex.getMessage());
                });
            }
        }, "preview-start").start();
    }

    private void startPreviewTimeline() {
        // 10 FPS is enough for a settings preview
        previewTimeline = new Timeline(
            new KeyFrame(Duration.millis(100), e -> tickPreview()));
        previewTimeline.setCycleCount(Animation.INDEFINITE);
        previewTimeline.play();
    }

    private void tickPreview() {
        if (previewCapture == null) return;
        BufferedImage frame = previewCapture.getLatestFrame();
        if (frame != null) {
            previewBuffer = SwingFXUtils.toFXImage(frame, previewBuffer);
            webcamPreviewView.setImage(previewBuffer);
            webcamPreviewLabel.setVisible(false); // hide placeholder
        }
    }

    private void stopInlinePreview() {
        if (previewTimeline != null) { previewTimeline.stop(); previewTimeline = null; }
        if (previewCapture  != null) { previewCapture.stop();  previewCapture  = null; }
        webcamPreviewView.setImage(null);
        previewBuffer = null;
        webcamPreviewLabel.setText("Enable webcam above to see live preview");
        webcamPreviewLabel.setVisible(true);
    }

    /** Called by MainController when the settings Stage is closed without Save. */
    public void onWindowClose() {
        stopInlinePreview();
    }

    // ── Permission warning ────────────────────────────────────────────────────

    private void showPermissionWarning(String detail) {
        permissionWarningLabel.setText(
            "⚠  " + detail
            + "\n\nIf you have a webcam, make sure PixelCapture is allowed to access it.");
        permissionWarningBox.setVisible(true);
        permissionWarningBox.setManaged(true);
    }

    private void hidePermissionWarning() {
        permissionWarningBox.setVisible(false);
        permissionWarningBox.setManaged(false);
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    @FXML
    private void onTestCamera() {
        if (!WebcamCapture.isAvailable()) {
            new Alert(Alert.AlertType.ERROR, WebcamCapture.getDiagnostic()).showAndWait();
            return;
        }

        String deviceLabel = webcamDeviceCombo.getValue();
        int deviceIndex = WebcamCapture.indexFromLabel(deviceLabel);

        // Build the test window
        Stage testStage = new Stage();
        testStage.setTitle("Camera Test — " + (deviceLabel != null ? deviceLabel : "Camera " + deviceIndex));
        testStage.initModality(Modality.NONE);
        testStage.initOwner(qualityCombo.getScene().getWindow());

        ImageView testView = new ImageView();
        testView.setFitWidth(400);
        testView.setFitHeight(300);
        testView.setPreserveRatio(true);
        testView.setSmooth(true);

        StackPane viewPane = new StackPane(testView);
        viewPane.setStyle("-fx-background-color: #000; -fx-min-width: 400; -fx-min-height: 300;");

        Label infoLabel  = new Label("Opening camera " + deviceIndex + "…");
        infoLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Label warnLabel  = new Label();
        warnLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 11px; -fx-wrap-text: true; -fx-max-width: 420;");
        warnLabel.setWrapText(true);

        Button settingsBtn = new Button("Open Camera Privacy Settings");
        settingsBtn.setStyle("-fx-cursor: hand;");
        settingsBtn.setVisible(false);
        settingsBtn.setOnAction(e -> onOpenCameraSettings());

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-cursor: hand;");

        VBox root = new VBox(10, viewPane, infoLabel, warnLabel, settingsBtn, closeBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(15));

        Scene scene = new Scene(root, 450, 430);
        ThemeManager.applyTheme(scene, SettingsManager.getInstance().getSettings().getTheme());
        testStage.setScene(scene);
        testStage.show();

        // Open the camera
        WebcamCapture testCap = new WebcamCapture();
        new Thread(() -> {
            try {
                testCap.start(deviceIndex, 15); // test dialog at 15 fps
                Platform.runLater(() ->
                    infoLabel.setText("✓  " + deviceLabel + "  — "
                        + testCap.getCapturedWidth() + "×" + testCap.getCapturedHeight()));
            } catch (WebcamCapture.CameraPermissionException ex) {
                Platform.runLater(() -> {
                    infoLabel.setText("✗  Cannot open camera");
                    warnLabel.setText(ex.getMessage()
                        + "\n\nIf you have a webcam, allow PixelCapture in "
                        + "Windows Settings → Privacy → Camera.");
                    settingsBtn.setVisible(true);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    infoLabel.setText("✗  " + ex.getMessage());
                    warnLabel.setText("Check the console for details.");
                });
            }
        }, "camera-test").start();

        // Update preview at 15 FPS
        WritableImage[] buf = {null};
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(67), e -> {
            BufferedImage frame = testCap.getLatestFrame();
            if (frame != null) {
                buf[0] = SwingFXUtils.toFXImage(frame, buf[0]);
                testView.setImage(buf[0]);
            }
        }));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();

        closeBtn.setOnAction(e -> { tl.stop(); testCap.stop(); testStage.close(); });
        testStage.setOnCloseRequest(e -> { tl.stop(); testCap.stop(); });
    }

    @FXML
    private void onOpenCameraSettings() {
        // ms-settings: URI requires ShellExecute, not Desktop.browse()
        try {
            new ProcessBuilder("cmd", "/c", "start", "", "ms-settings:privacy-webcam")
                .start();
        } catch (Exception e) {
            try { Desktop.getDesktop().browse(new URI("ms-settings:privacy-webcam")); }
            catch (Exception ex) { LOG.warning("Could not open camera settings: " + ex.getMessage()); }
        }
    }

    @FXML
    private void onRefreshWebcams() {
        webcamDeviceCombo.getItems().setAll("Scanning…");
        new Thread(() -> {
            System.out.println("[SettingsController] Manual webcam refresh");
            List<String> cams;
            try { cams = WebcamCapture.availableDevices(); }
            catch (Throwable t) { cams = List.of("Error: " + t.getMessage()); }
            System.out.println("[SettingsController] Refresh result: " + cams);
            final List<String> result = cams;
            Platform.runLater(() -> {
                webcamDeviceCombo.getItems().setAll(result);
                if (!result.isEmpty()) webcamDeviceCombo.getSelectionModel().selectFirst();
                if (webcamEnabledCheck.isSelected() && WebcamCapture.isAvailable()) {
                    startInlinePreview(WebcamCapture.indexFromLabel(result.get(0)));
                }
                if (WebcamCapture.isAvailable()) {
                    webcamEnabledCheck.setDisable(false);
                }
            });
        }, "webcam-refresh").start();
    }

    // ── Hotkey capture ────────────────────────────────────────────────────────

    private void installHotkeyCaptureOn(TextField field) {
        field.setEditable(false);
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) beginCapture(field);
            else if (capturingField == field) endCapture();
        });
    }

    private void beginCapture(TextField field) {
        capturingField = field;
        field.setStyle("-fx-border-color: #6366f1; -fx-border-width: 2;");
        if (field.getScene() != null) {
            field.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyCapture);
        }
    }

    private void onKeyCapture(KeyEvent event) {
        if (capturingField == null) return;
        if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) { endCapture(); event.consume(); return; }
        if (event.getCode() == javafx.scene.input.KeyCode.TAB) return;
        String combo = HotkeyManager.fromFxEvent(event);
        if (combo == null) return;
        capturingField.setText(combo.replace("CTRL","Ctrl").replace("ALT","Alt").replace("SHIFT","Shift"));
        endCapture();
        event.consume();
    }

    private void endCapture() {
        if (capturingField != null) {
            capturingField.setStyle("");
            if (capturingField.getScene() != null) {
                capturingField.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, this::onKeyCapture);
            }
            capturingField = null;
        }
    }

    @FXML private void onResetHotkeys() {
        hotkeyStartField.setText("Ctrl+Shift+R");
        hotkeyStopField.setText("Ctrl+Shift+X");
        hotkeyPauseField.setText("Ctrl+Shift+P");
        hotkeyScreenshotField.setText("Ctrl+Shift+S");
    }

    // ── Save / Cancel ─────────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        stopInlinePreview(); // release camera before recording might use it
        AppSettings s = SettingsManager.getInstance().getSettings();

        s.setVideoQuality(qualityCombo.getValue());
        s.setFps(fpsCombo.getValue());
        s.setCaptureAudio(captureAudioCheck.isSelected());
        if (audioDeviceCombo.getValue() != null) s.setAudioDevice(audioDeviceCombo.getValue());

        s.setWebcamEnabled(webcamEnabledCheck.isSelected());
        s.setWebcamDeviceIndex(WebcamCapture.indexFromLabel(webcamDeviceCombo.getValue()));
        s.setWebcamPosition(positionKey(webcamPositionCombo.getValue()));
        s.setWebcamShape(shapeKey(webcamShapeCombo.getValue()));
        s.setWebcamSizePercent((int) webcamSizeSlider.getValue());
        s.setWebcamBorderWidth((int) webcamBorderSlider.getValue());
        s.setWebcamBorderColor(OverlayConfig.fromFxColor(webcamBorderColorPicker.getValue()));
        s.setWebcamOpacityPct((int) webcamOpacitySlider.getValue());

        s.setFpsCounterEnabled(fpsCounterCheck.isSelected());
        s.setFpsCounterPosition(positionKey(fpsPositionCombo.getValue()));
        s.setFpsCounterFontSize((int) fpsFontSizeSlider.getValue());
        s.setFpsCounterTextColor(OverlayConfig.fromFxColor(fpsTextColorPicker.getValue()));
        s.setFpsCounterBg(fpsBgCheck.isSelected());
        s.setFpsCounterBgColor(OverlayConfig.fromFxColor(fpsBgColorPicker.getValue()));

        s.setHotkeyStart(hotkeyStartField.getText());
        s.setHotkeyStop(hotkeyStopField.getText());
        s.setHotkeyPause(hotkeyPauseField.getText());
        s.setHotkeyScreenshot(hotkeyScreenshotField.getText());

        RadioButton sel = (RadioButton) themeGroup.getSelectedToggle();
        s.setTheme(sel != null ? sel.getText().toUpperCase() : "AUTO");

        SettingsManager.getInstance().saveSettings(s);
        if (qualityCombo.getScene() != null)
            ThemeManager.applyTheme(qualityCombo.getScene(), s.getTheme());
        if (onSaveCallback != null) onSaveCallback.run();
        closeWindow();
    }

    @FXML
    private void onCancel() { stopInlinePreview(); closeWindow(); }

    private void closeWindow() {
        ((Stage) qualityCombo.getScene().getWindow()).close();
    }

    // ── Label ↔ key helpers ───────────────────────────────────────────────────

    private static String positionLabel(String key) {
        return switch (key) {
            case "TOP_LEFT"     -> "Top-Left";
            case "TOP_RIGHT"    -> "Top-Right";
            case "BOTTOM_LEFT"  -> "Bottom-Left";
            case "CENTER"       -> "Center";
            default             -> "Bottom-Right";
        };
    }
    private static String positionKey(String label) {
        if (label == null) return "BOTTOM_RIGHT";
        return switch (label) {
            case "Top-Left"    -> "TOP_LEFT";
            case "Top-Right"   -> "TOP_RIGHT";
            case "Bottom-Left" -> "BOTTOM_LEFT";
            case "Center"      -> "CENTER";
            default            -> "BOTTOM_RIGHT";
        };
    }
    private static String shapeLabel(String key) {
        return switch (key) {
            case "CIRCLE"       -> "Circle";
            case "ROUNDED_RECT" -> "Rounded Rectangle";
            default             -> "Rectangle";
        };
    }
    private static String shapeKey(String label) {
        if (label == null) return "CIRCLE";
        return switch (label) {
            case "Circle"            -> "CIRCLE";
            case "Rounded Rectangle" -> "ROUNDED_RECT";
            default                  -> "RECT";
        };
    }
}
