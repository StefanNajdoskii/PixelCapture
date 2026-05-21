package com.screenrecorder.ui;

import com.screenrecorder.models.AppSettings;
import com.screenrecorder.recorder.AudioCapture;
import com.screenrecorder.utils.SettingsManager;
import com.screenrecorder.utils.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {

    // ── Video ─────────────────────────────────────────────────────────────────
    @FXML private ComboBox<String>  qualityCombo;
    @FXML private ComboBox<Integer> fpsCombo;

    // ── Audio ─────────────────────────────────────────────────────────────────
    @FXML private CheckBox          captureAudioCheck;
    @FXML private ComboBox<String>  audioDeviceCombo;

    // ── Hotkeys ───────────────────────────────────────────────────────────────
    @FXML private TextField         hotkeyStartField;
    @FXML private TextField         hotkeyStopField;
    @FXML private TextField         hotkeyPauseField;

    // ── Theme ─────────────────────────────────────────────────────────────────
    @FXML private ToggleGroup       themeGroup;
    @FXML private RadioButton       themeAutoRadio;
    @FXML private RadioButton       themeDarkRadio;
    @FXML private RadioButton       themeLightRadio;

    private Runnable onSaveCallback;

    public void setOnSave(Runnable callback) {
        this.onSaveCallback = callback;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AppSettings s = SettingsManager.getInstance().getSettings();

        // Video quality
        qualityCombo.getItems().addAll("Low", "Medium", "High", "Ultra");
        qualityCombo.setValue(s.getVideoQuality());

        // FPS
        fpsCombo.getItems().addAll(15, 24, 30, 60);
        fpsCombo.setValue(s.getFps());

        // Audio
        captureAudioCheck.setSelected(s.isCaptureAudio());
        audioDeviceCombo.getItems().addAll(AudioCapture.availableDevices());
        audioDeviceCombo.setValue(s.getAudioDevice());
        audioDeviceCombo.disableProperty().bind(captureAudioCheck.selectedProperty().not());

        // Hotkeys
        hotkeyStartField.setText(s.getHotkeyStart());
        hotkeyStopField.setText(s.getHotkeyStop());
        hotkeyPauseField.setText(s.getHotkeyPause());

        // TODO: implement live hotkey recording (capture next key combo pressed)
        hotkeyStartField.setEditable(false);
        hotkeyStopField.setEditable(false);
        hotkeyPauseField.setEditable(false);

        // Theme
        switch (s.getTheme().toUpperCase()) {
            case "DARK"  -> themeGroup.selectToggle(themeDarkRadio);
            case "LIGHT" -> themeGroup.selectToggle(themeLightRadio);
            default      -> themeGroup.selectToggle(themeAutoRadio);
        }
    }

    @FXML
    private void onSave() {
        AppSettings s = SettingsManager.getInstance().getSettings();
        s.setVideoQuality(qualityCombo.getValue());
        s.setFps(fpsCombo.getValue());
        s.setCaptureAudio(captureAudioCheck.isSelected());
        s.setAudioDevice(audioDeviceCombo.getValue() != null
            ? audioDeviceCombo.getValue() : "Default Microphone");
        s.setHotkeyStart(hotkeyStartField.getText());
        s.setHotkeyStop(hotkeyStopField.getText());
        s.setHotkeyPause(hotkeyPauseField.getText());

        RadioButton selected = (RadioButton) themeGroup.getSelectedToggle();
        s.setTheme(selected != null ? selected.getText().toUpperCase() : "AUTO");

        SettingsManager.getInstance().saveSettings(s);

        // Re-apply theme to this settings window
        ThemeManager.applyTheme(qualityCombo.getScene(), s.getTheme());

        if (onSaveCallback != null) onSaveCallback.run();

        closeWindow();
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) qualityCombo.getScene().getWindow();
        stage.close();
    }
}
