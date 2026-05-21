package com.screenrecorder.ui;

import com.screenrecorder.models.AppSettings;
import com.screenrecorder.models.RecordingConfig;
import com.screenrecorder.models.RecordingState;
import com.screenrecorder.models.VideoQuality;
import com.screenrecorder.recorder.AudioCapture;
import com.screenrecorder.services.RecordingService;
import com.screenrecorder.services.SystemTrayService;
import com.screenrecorder.utils.FileUtils;
import com.screenrecorder.utils.SettingsManager;
import com.screenrecorder.utils.ThemeManager;
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

import java.awt.Rectangle;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainController implements Initializable {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());

    // ── FXML bindings ────────────────────────────────────────────────────────
    @FXML private Circle    statusDot;
    @FXML private Label     statusLabel;
    @FXML private Label     timerLabel;
    @FXML private ComboBox<String> captureAreaCombo;
    @FXML private Button    selectAreaButton;
    @FXML private ComboBox<String> audioSourceCombo;
    @FXML private TextField outputFolderField;
    @FXML private Button    startButton;
    @FXML private Button    pauseButton;
    @FXML private Button    stopButton;
    @FXML private Label     fpsLabel;
    @FXML private Label     qualityLabel;
    @FXML private Label     recordingPathLabel;

    // ── Services (instantiated before initialize() is called) ────────────────
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
            // Print to stdout (always visible in Maven console) AND stderr
            System.out.println("[MainController] *** CRASH INSIDE initialize() ***");
            System.out.println("[MainController] " + t.getClass().getName() + ": " + t.getMessage());
            printInitCauseChain(t);
            // Re-throw so FXMLLoader.load() and Main.start() both see it
            throw (t instanceof RuntimeException re) ? re : new RuntimeException(t);
        }
    }

    /** The real initialization logic — separated so the try-catch in initialize() stays clean. */
    private void doInitialize() {
        // ── Step 0: verify @FXML injections ─────────────────────────────────
        // A null field means the fx:id in main.fxml doesn't match the field name.
        checkInjected("statusDot",         statusDot);
        checkInjected("statusLabel",       statusLabel);
        checkInjected("timerLabel",        timerLabel);
        checkInjected("captureAreaCombo",  captureAreaCombo);
        checkInjected("selectAreaButton",  selectAreaButton);
        checkInjected("audioSourceCombo",  audioSourceCombo);
        checkInjected("outputFolderField", outputFolderField);
        checkInjected("startButton",       startButton);
        checkInjected("pauseButton",       pauseButton);
        checkInjected("stopButton",        stopButton);
        checkInjected("fpsLabel",          fpsLabel);
        checkInjected("qualityLabel",      qualityLabel);
        checkInjected("recordingPathLabel",recordingPathLabel);
        System.out.println("[MainController] All @FXML fields injected OK");

        // ── Step 1: load settings ────────────────────────────────────────────
        System.out.println("[MainController] Loading settings…");
        AppSettings settings = SettingsManager.getInstance().getSettings();
        System.out.println("[MainController] Settings: theme=" + settings.getTheme()
            + ", fps=" + settings.getFps() + ", quality=" + settings.getVideoQuality()
            + ", output=" + settings.getOutputFolder());

        // ── Step 2: capture area combo ───────────────────────────────────────
        System.out.println("[MainController] Configuring capture area combo…");
        captureAreaCombo.getItems().addAll("Full Screen", "Custom Area");
        captureAreaCombo.getSelectionModel().selectFirst();
        captureAreaCombo.setOnAction(e -> {
            boolean custom = "Custom Area".equals(captureAreaCombo.getValue());
            selectAreaButton.setDisable(!custom);
            if (!custom) customArea = null;
        });
        selectAreaButton.setDisable(true);

        // ── Step 3: audio device list ────────────────────────────────────────
        System.out.println("[MainController] Enumerating audio devices…");
        try {
            audioSourceCombo.getItems().addAll(AudioCapture.availableDevices());
        } catch (Throwable t) {
            // Audio enumeration failure must not kill the whole app — fall back
            System.out.println("[MainController] Audio enumeration failed (" + t + "), using fallback");
            audioSourceCombo.getItems().add("Default Microphone");
        }
        if (audioSourceCombo.getItems().isEmpty()) {
            audioSourceCombo.getItems().add("Default Microphone");
        }
        audioSourceCombo.getSelectionModel().selectFirst();
        System.out.println("[MainController] Audio devices: " + audioSourceCombo.getItems());

        // ── Step 4: output folder ────────────────────────────────────────────
        System.out.println("[MainController] Setting output folder…");
        String folder = settings.getOutputFolder();
        outputFolderField.setText(folder);
        boolean dirOk = FileUtils.ensureDirectory(folder);
        System.out.println("[MainController] Output dir '" + folder + "' exists/created: " + dirOk);

        // ── Step 5: footer labels ────────────────────────────────────────────
        fpsLabel.setText(settings.getFps() + " FPS");
        qualityLabel.setText(settings.getVideoQuality() + " Quality");

        // ── Step 6: recording path label (initially hidden) ──────────────────
        recordingPathLabel.setVisible(false);
        recordingPathLabel.setManaged(false);

        // ── Step 7: property listeners ───────────────────────────────────────
        System.out.println("[MainController] Attaching property listeners…");

        recordingService.stateProperty().addListener((obs, oldVal, newVal) ->
            updateUiForState(newVal));

        recordingService.elapsedSecondsProperty().addListener((obs, oldVal, newVal) ->
            timerLabel.setText(FileUtils.formatDuration(newVal.longValue())));

        recordingService.currentOutputPathProperty().addListener((obs, oldVal, path) -> {
            if (path != null && !path.isBlank()) {
                try {
                    String name = Paths.get(path).getFileName().toString();
                    recordingPathLabel.setText("Saving → " + name);
                } catch (Exception ex) {
                    recordingPathLabel.setText("Saving…");
                }
                recordingPathLabel.setVisible(true);
                recordingPathLabel.setManaged(true);
            }
        });

        recordingService.lastSavedPathProperty().addListener((obs, oldVal, path) -> {
            if (path != null && !path.isBlank()) {
                try {
                    String name = Paths.get(path).getFileName().toString();
                    recordingPathLabel.setText("Saved  ✔  " + name);
                } catch (Exception ex) {
                    recordingPathLabel.setText("Saved ✔");
                }
                recordingPathLabel.setVisible(true);
                recordingPathLabel.setManaged(true);
                System.out.println("[MainController] File saved: " + path);
            }
        });

        // ── Step 8: set initial UI state ─────────────────────────────────────
        System.out.println("[MainController] Setting initial UI state…");
        updateUiForState(RecordingState.IDLE);
    }

    public void setSystemTrayService(SystemTrayService svc) {
        this.systemTrayService = svc;
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    @FXML
    private void onStartRecording() {
        if (recordingService.getState() != RecordingState.IDLE) return;

        AppSettings settings = SettingsManager.getInstance().getSettings();
        String outputFolder = outputFolderField.getText().trim();
        if (outputFolder.isBlank()) outputFolder = settings.getOutputFolder();

        FileUtils.ensureDirectory(outputFolder);
        String outputPath = FileUtils.generateOutputPath(outputFolder);

        boolean captureAudio = settings.isCaptureAudio()
            && audioSourceCombo.getValue() != null
            && !"None".equals(audioSourceCombo.getValue());
        Rectangle area = "Custom Area".equals(captureAreaCombo.getValue()) ? customArea : null;

        System.out.println("[MainController] ===== Starting recording =====");
        System.out.println("[MainController]   Output  : " + outputPath);
        System.out.println("[MainController]   Area    : " + (area != null ? area : "Full Screen"));
        System.out.println("[MainController]   Audio   : " + captureAudio);
        System.out.println("[MainController]   FPS     : " + settings.getFps());
        System.out.println("[MainController]   Quality : " + settings.getVideoQuality());

        RecordingConfig config = new RecordingConfig.Builder()
            .captureArea(area)
            .captureAudio(captureAudio)
            .fps(settings.getFps())
            .quality(VideoQuality.fromLabel(settings.getVideoQuality()))
            .outputPath(outputPath)
            .build();

        // Show "initialising" feedback immediately (before background thread runs)
        startButton.setText("⏳  Initializing…");
        startButton.setDisable(true);
        recordingPathLabel.setText("Starting…");
        recordingPathLabel.setVisible(true);
        recordingPathLabel.setManaged(true);

        new Thread(() -> {
            try {
                recordingService.startRecording(config);
                if (systemTrayService != null) {
                    systemTrayService.showNotification("PixelCapture", "Recording started");
                }
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Failed to start recording", t);
                System.out.println("[MainController] Start recording FAILED: " + t);
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
        if (recordingService.getState() == RecordingState.RECORDING) {
            recordingService.pauseRecording();
        } else if (recordingService.getState() == RecordingState.PAUSED) {
            recordingService.resumeRecording();
        }
    }

    @FXML
    private void onStopRecording() {
        if (recordingService.getState() == RecordingState.IDLE) return;
        System.out.println("[MainController] Stop requested");
        stopButton.setDisable(true);
        stopButton.setText("⏳ Saving…");
        new Thread(() -> {
            try {
                recordingService.stopRecording();
                Platform.runLater(() -> {
                    if (systemTrayService != null) {
                        systemTrayService.showNotification("PixelCapture", "Recording saved");
                    }
                });
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Failed to stop recording", t);
                System.out.println("[MainController] Stop FAILED: " + t);
                t.printStackTrace(System.err);
                Platform.runLater(() ->
                    showError("Error stopping recording",
                        t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        }, "stop-recording").start();
    }

    @FXML
    private void onSelectArea() {
        Stage owner = (Stage) selectAreaButton.getScene().getWindow();
        owner.setIconified(true);
        new Thread(() -> {
            try {
                Thread.sleep(400);
                ScreenAreaSelector selector = new ScreenAreaSelector();
                Rectangle area = selector.selectArea();
                Platform.runLater(() -> {
                    owner.setIconified(false);
                    if (area != null) {
                        customArea = area;
                        selectAreaButton.setText(area.width + " × " + area.height);
                        System.out.println("[MainController] Custom area: " + area);
                    }
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
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
            System.out.println("[MainController] Output folder → " + chosen.getAbsolutePath());
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
            });

            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene, SettingsManager.getInstance().getSettings().getTheme());

            Stage stage = new Stage();
            stage.setTitle("Settings — PixelCapture");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(startButton.getScene().getWindow());
            stage.showAndWait();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Could not open settings", e);
            showError("Settings error", e.getMessage());
        }
    }

    // ── UI state machine ───────────────────────────────────────────────────────

    private void updateUiForState(RecordingState state) {
        System.out.println("[MainController] UI state → " + state);
        switch (state) {
            case IDLE -> {
                statusDot.getStyleClass().setAll("status-dot", "status-idle");
                statusLabel.setText("READY");
                timerLabel.setText("00:00:00");
                startButton.setText("▶   Start Recording");
                startButton.setDisable(false);
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

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "An unknown error occurred.");
        alert.showAndWait();
    }

    // ── Diagnostic helpers ────────────────────────────────────────────────────

    /** Throws a clear RuntimeException if an @FXML field was not injected. */
    private static void checkInjected(String fieldName, Object value) {
        if (value == null) {
            throw new RuntimeException(
                "@FXML field '" + fieldName + "' is null — " +
                "check that fx:id=\"" + fieldName + "\" exists in main.fxml");
        }
    }

    /** Prints the full cause chain to stdout so it is always visible in the Maven console. */
    private static void printInitCauseChain(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 8) {
            System.out.println("[MainController]   "
                + (depth == 0 ? "Root" : "Cause #" + depth) + ": "
                + cur.getClass().getName() + ": " + cur.getMessage());
            for (StackTraceElement ste : cur.getStackTrace()) {
                if (ste.getClassName().startsWith("com.screenrecorder") || depth == 0) {
                    System.out.println("[MainController]     at " + ste);
                }
            }
            cur = cur.getCause();
            depth++;
        }
    }
}
