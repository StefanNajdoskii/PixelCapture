package com.screenrecorder.ui;

import com.screenrecorder.models.AppSettings;
import com.screenrecorder.models.RecordingConfig;
import com.screenrecorder.models.RecordingState;
import com.screenrecorder.models.VideoQuality;
import com.screenrecorder.recorder.AudioCapture;
import com.screenrecorder.recorder.ScreenCapture;
import com.screenrecorder.services.HotkeyManager;
import com.screenrecorder.services.RecordingService;
import com.screenrecorder.services.SystemTrayService;
import com.screenrecorder.utils.FileUtils;
import com.screenrecorder.utils.SettingsManager;
import com.screenrecorder.utils.ThemeManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainController implements Initializable {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());

    // ── FXML bindings ─────────────────────────────────────────────────────────
    @FXML private Circle    statusDot;
    @FXML private Label     statusLabel;
    @FXML private Label     timerLabel;
    @FXML private ComboBox<String> captureAreaCombo;
    @FXML private Button    selectAreaButton;
    @FXML private ComboBox<String> audioSourceCombo;
    @FXML private TextField outputFolderField;
    @FXML private Button    startButton;
    @FXML private Button    screenshotButton;
    @FXML private Button    pauseButton;
    @FXML private Button    stopButton;
    @FXML private Label     fpsLabel;
    @FXML private Label     qualityLabel;
    @FXML private Label     recordingPathLabel;

    // ── Services ──────────────────────────────────────────────────────────────
    private final RecordingService recordingService = new RecordingService();
    private SystemTrayService systemTrayService;

    // ── State ─────────────────────────────────────────────────────────────────
    private Rectangle customArea = null;

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("[MainController] initialize() — START");
        try {
            doInitialize();
            System.out.println("[MainController] initialize() — DONE");
        } catch (Throwable t) {
            System.out.println("[MainController] *** CRASH IN initialize() ***");
            System.out.println("[MainController] " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace(System.err);
            throw (t instanceof RuntimeException re) ? re : new RuntimeException(t);
        }
    }

    private void doInitialize() {
        // Null-check all @FXML injections
        checkInjected("statusDot",          statusDot);
        checkInjected("statusLabel",        statusLabel);
        checkInjected("timerLabel",         timerLabel);
        checkInjected("captureAreaCombo",   captureAreaCombo);
        checkInjected("selectAreaButton",   selectAreaButton);
        checkInjected("audioSourceCombo",   audioSourceCombo);
        checkInjected("outputFolderField",  outputFolderField);
        checkInjected("startButton",        startButton);
        checkInjected("screenshotButton",   screenshotButton);
        checkInjected("pauseButton",        pauseButton);
        checkInjected("stopButton",         stopButton);
        checkInjected("fpsLabel",           fpsLabel);
        checkInjected("qualityLabel",       qualityLabel);
        checkInjected("recordingPathLabel", recordingPathLabel);
        System.out.println("[MainController] All @FXML fields injected OK");

        AppSettings settings = SettingsManager.getInstance().getSettings();

        captureAreaCombo.getItems().addAll("Full Screen", "Custom Area");
        captureAreaCombo.getSelectionModel().selectFirst();
        captureAreaCombo.setOnAction(e -> {
            boolean custom = "Custom Area".equals(captureAreaCombo.getValue());
            selectAreaButton.setDisable(!custom);
            if (!custom) customArea = null;
        });
        selectAreaButton.setDisable(true);

        try { audioSourceCombo.getItems().addAll(AudioCapture.availableDevices()); }
        catch (Throwable t) { audioSourceCombo.getItems().add("Default Microphone"); }
        if (audioSourceCombo.getItems().isEmpty()) audioSourceCombo.getItems().add("Default Microphone");
        audioSourceCombo.getSelectionModel().selectFirst();

        outputFolderField.setText(settings.getOutputFolder());
        FileUtils.ensureDirectory(settings.getOutputFolder());

        fpsLabel.setText(settings.getFps() + " FPS");
        qualityLabel.setText(settings.getVideoQuality() + " Quality");

        recordingPathLabel.setVisible(false);
        recordingPathLabel.setManaged(false);

        // Property listeners
        recordingService.stateProperty().addListener((obs, o, n) -> updateUiForState(n));
        recordingService.elapsedSecondsProperty().addListener((obs, o, n) ->
            timerLabel.setText(FileUtils.formatDuration(n.longValue())));

        recordingService.currentOutputPathProperty().addListener((obs, o, path) -> {
            if (path != null && !path.isBlank()) {
                try {
                    recordingPathLabel.setText("Saving → " + Paths.get(path).getFileName());
                } catch (Exception ex) { recordingPathLabel.setText("Saving…"); }
                recordingPathLabel.setVisible(true);
                recordingPathLabel.setManaged(true);
            }
        });

        recordingService.lastSavedPathProperty().addListener((obs, o, path) -> {
            if (path == null || path.isBlank()) return;
            try {
                recordingPathLabel.setText("Saved  ✔  " + Paths.get(path).getFileName());
            } catch (Exception ex) { recordingPathLabel.setText("Saved ✔"); }
            recordingPathLabel.setVisible(true);
            recordingPathLabel.setManaged(true);

            // Show post-recording dialog
            Stage owner = (Stage) startButton.getScene().getWindow();
            PostRecordingDialog.show(owner, path, recordingService.getLastRecordingDurationSeconds());
        });

        updateUiForState(RecordingState.IDLE);

        // Register global hotkeys
        rebindHotkeys(settings);
    }

    public void setSystemTrayService(SystemTrayService svc) {
        this.systemTrayService = svc;
    }

    // ── Hotkey wiring ─────────────────────────────────────────────────────────

    private void rebindHotkeys(AppSettings s) {
        HotkeyManager hm = HotkeyManager.getInstance();
        hm.unbindAll();
        hm.bind(s.getHotkeyStart(),      this::onStartRecording);
        hm.bind(s.getHotkeyStop(),       this::onStopRecording);
        hm.bind(s.getHotkeyPause(),      this::onPauseRecording);
        hm.bind(s.getHotkeyScreenshot(), this::onTakeScreenshot);
        System.out.println("[MainController] Hotkeys bound: start=" + s.getHotkeyStart()
            + " stop=" + s.getHotkeyStop()
            + " pause=" + s.getHotkeyPause()
            + " screenshot=" + s.getHotkeyScreenshot());
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    @FXML
    private void onStartRecording() {
        if (recordingService.getState() != RecordingState.IDLE) return;

        AppSettings settings = SettingsManager.getInstance().getSettings();
        String folder = outputFolderField.getText().trim();
        if (folder.isBlank()) folder = settings.getOutputFolder();
        FileUtils.ensureDirectory(folder);

        String outputPath = FileUtils.generateOutputPath(folder);
        boolean captureAudio = settings.isCaptureAudio()
            && audioSourceCombo.getValue() != null
            && !"None".equals(audioSourceCombo.getValue());
        Rectangle area = "Custom Area".equals(captureAreaCombo.getValue()) ? customArea : null;

        System.out.println("[MainController] Starting recording → " + outputPath);

        RecordingConfig config = new RecordingConfig.Builder()
            .captureArea(area)
            .captureAudio(captureAudio)
            .fps(settings.getFps())
            .quality(VideoQuality.fromLabel(settings.getVideoQuality()))
            .outputPath(outputPath)
            .overlayConfig(settings.toOverlayConfig())
            .build();

        startButton.setText("⏳  Initializing…");
        startButton.setDisable(true);
        recordingPathLabel.setText("Starting…");
        recordingPathLabel.setVisible(true);
        recordingPathLabel.setManaged(true);

        new Thread(() -> {
            try {
                recordingService.startRecording(config);
                if (systemTrayService != null)
                    systemTrayService.showNotification("PixelCapture", "Recording started");
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Failed to start recording", t);
                t.printStackTrace(System.err);
                Platform.runLater(() -> {
                    startButton.setText("▶   Start Recording");
                    startButton.setDisable(false);
                    recordingPathLabel.setVisible(false);
                    recordingPathLabel.setManaged(false);
                    showError("Recording failed to start",
                        t.getClass().getSimpleName() + ": " + t.getMessage()
                        + "\n\nSee console for full stack trace.");
                });
            }
        }, "start-recording").start();
    }

    @FXML
    private void onPauseRecording() {
        if (recordingService.getState() == RecordingState.RECORDING)
            recordingService.pauseRecording();
        else if (recordingService.getState() == RecordingState.PAUSED)
            recordingService.resumeRecording();
    }

    @FXML
    private void onStopRecording() {
        if (recordingService.getState() == RecordingState.IDLE) return;
        stopButton.setDisable(true);
        stopButton.setText("⏳ Saving…");
        new Thread(() -> {
            try {
                recordingService.stopRecording();
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Failed to stop recording", t);
                Platform.runLater(() ->
                    showError("Error stopping recording",
                        t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        }, "stop-recording").start();
    }

    @FXML
    private void onTakeScreenshot() {
        AppSettings settings = SettingsManager.getInstance().getSettings();
        String folder = outputFolderField.getText().trim();
        if (folder.isBlank()) folder = settings.getOutputFolder();
        FileUtils.ensureDirectory(folder);

        Rectangle area = "Custom Area".equals(captureAreaCombo.getValue()) ? customArea : null;
        final String outputFolder = folder;

        new Thread(() -> {
            try {
                ScreenCapture capture = new ScreenCapture(area);
                BufferedImage screenshot = capture.captureFrame();

                String ts = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                String path = outputFolder + File.separator + "screenshot_" + ts + ".png";
                ImageIO.write(screenshot, "PNG", new File(path));
                System.out.println("[MainController] Screenshot saved: " + path);

                Platform.runLater(() -> flashNotification("Screenshot saved!"));

            } catch (Exception e) {
                LOG.log(Level.WARNING, "Screenshot failed", e);
                Platform.runLater(() -> showError("Screenshot failed", e.getMessage()));
            }
        }, "screenshot").start();
    }

    @FXML
    private void onSelectArea() {
        Stage owner = (Stage) selectAreaButton.getScene().getWindow();
        owner.setIconified(true);
        new Thread(() -> {
            try {
                Thread.sleep(400);
                Rectangle area = new ScreenAreaSelector().selectArea();
                Platform.runLater(() -> {
                    owner.setIconified(false);
                    if (area != null) {
                        customArea = area;
                        selectAreaButton.setText(area.width + " × " + area.height);
                    }
                });
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }, "area-selector").start();
    }

    @FXML
    private void onBrowseOutput() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Folder");
        File initial = new File(outputFolderField.getText());
        if (initial.exists()) chooser.setInitialDirectory(initial);
        File chosen = chooser.showDialog(outputFolderField.getScene().getWindow());
        if (chosen != null) {
            outputFolderField.setText(chosen.getAbsolutePath());
            AppSettings s = SettingsManager.getInstance().getSettings();
            s.setOutputFolder(chosen.getAbsolutePath());
            SettingsManager.getInstance().saveSettings(s);
        }
    }

    @FXML
    private void openSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/screenrecorder/fxml/settings.fxml"));
            Parent root = loader.load();
            SettingsController ctrl = loader.getController();
            ctrl.setOnSave(() -> {
                AppSettings s = SettingsManager.getInstance().getSettings();
                fpsLabel.setText(s.getFps() + " FPS");
                qualityLabel.setText(s.getVideoQuality() + " Quality");
                ThemeManager.applyTheme(startButton.getScene(), s.getTheme());
                rebindHotkeys(s);
            });

            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene, SettingsManager.getInstance().getSettings().getTheme());
            Stage stage = new Stage();
            stage.setTitle("Settings — PixelCapture");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(startButton.getScene().getWindow());
            // Stop the inline webcam preview when the window is closed via the X button
            // so it doesn't keep the camera open while recording starts.
            stage.setOnCloseRequest(e -> ctrl.onWindowClose());
            stage.showAndWait();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Could not open settings", e);
            showError("Settings error", e.getMessage());
        }
    }

    // ── UI state machine ───────────────────────────────────────────────────────

    private void updateUiForState(RecordingState state) {
        System.out.println("[MainController] UI → " + state);
        switch (state) {
            case IDLE -> {
                statusDot.getStyleClass().setAll("status-dot", "status-idle");
                statusLabel.setText("READY");
                timerLabel.setText("00:00:00");
                startButton.setText("▶   Start Recording");
                startButton.setDisable(false);
                screenshotButton.setDisable(false);
                pauseButton.setDisable(true);
                pauseButton.setText("⏸  Pause");
                stopButton.setText("■  Stop");
                stopButton.setDisable(true);
                captureAreaCombo.setDisable(false);
                audioSourceCombo.setDisable(false);
                outputFolderField.setDisable(false);
            }
            case RECORDING -> {
                statusDot.getStyleClass().setAll("status-dot", "status-recording");
                statusLabel.setText("RECORDING");
                startButton.setText("▶   Start Recording");
                startButton.setDisable(true);
                pauseButton.setDisable(false);
                pauseButton.setText("⏸  Pause");
                stopButton.setText("■  Stop");
                stopButton.setDisable(false);
                captureAreaCombo.setDisable(true);
                audioSourceCombo.setDisable(true);
                outputFolderField.setDisable(true);
            }
            case PAUSED -> {
                statusDot.getStyleClass().setAll("status-dot", "status-paused");
                statusLabel.setText("PAUSED");
                pauseButton.setText("▶  Resume");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Briefly shows a message in the recording-path label then fades it. */
    private void flashNotification(String message) {
        recordingPathLabel.setText(message);
        recordingPathLabel.setVisible(true);
        recordingPathLabel.setManaged(true);
        new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (recordingService.getState() == RecordingState.IDLE) {
                recordingPathLabel.setVisible(false);
                recordingPathLabel.setManaged(false);
            }
        })).play();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "An unknown error occurred.");
        alert.showAndWait();
    }

    private static void checkInjected(String name, Object value) {
        if (value == null) throw new RuntimeException(
            "@FXML field '" + name + "' is null — check fx:id=\"" + name + "\" in main.fxml");
    }
}
