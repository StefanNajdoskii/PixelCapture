package com.screenrecorder;

import com.screenrecorder.services.SystemTrayService;
import com.screenrecorder.ui.MainController;
import com.screenrecorder.utils.SettingsManager;
import com.screenrecorder.utils.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.net.URL;

public class Main extends Application {

    private SystemTrayService systemTrayService;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);
        System.out.println("[PixelCapture] ===== Starting application =====");

        try {
            URL fxmlUrl = getClass().getResource("/com/screenrecorder/fxml/main.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException(
                    "main.fxml not found — did you run 'mvn compile'? " +
                    "Resource must be at src/main/resources/com/screenrecorder/fxml/main.fxml");
            }
            System.out.println("[PixelCapture] Loading FXML: " + fxmlUrl);

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();   // <-- calls MainController.initialize() internally
            System.out.println("[PixelCapture] FXML loaded OK");

            MainController controller = loader.getController();

            String theme = SettingsManager.getInstance().getSettings().getTheme();
            System.out.println("[PixelCapture] Applying theme: " + theme);
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene, theme);

            primaryStage.setTitle("PixelCapture");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.setOnCloseRequest(event -> {
                event.consume();
                primaryStage.hide();
            });

            systemTrayService = new SystemTrayService(primaryStage);
            systemTrayService.initialize();
            controller.setSystemTrayService(systemTrayService);

            primaryStage.show();
            System.out.println("[PixelCapture] Application started successfully");

        } catch (Throwable t) {
            // Catch Throwable (not just Exception) so Errors like
            // ExceptionInInitializerError and NoClassDefFoundError are visible.
            printCauseChain(t);
            showFatalDialog(t);
        }
    }

    @Override
    public void stop() {
        System.out.println("[PixelCapture] stop() — shutting down");
        if (systemTrayService != null) systemTrayService.remove();
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        System.out.println("[PixelCapture] main() — launching JavaFX");
        launch(args);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Prints the full cause chain to STDOUT so it appears in the Maven console. */
    private static void printCauseChain(Throwable t) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║            PixelCapture — STARTUP FAILURE                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        Throwable cur = t;
        int level = 0;
        while (cur != null) {
            String prefix = level == 0 ? "EXCEPTION" : "CAUSED BY";
            System.out.println("[" + prefix + "] " + cur.getClass().getName()
                + ": " + cur.getMessage());
            for (StackTraceElement ste : cur.getStackTrace()) {
                // Only print frames from our own code + one JavaFX/JavaCV frame for context
                String cls = ste.getClassName();
                if (cls.startsWith("com.screenrecorder") || level == 0) {
                    System.out.println("    at " + ste);
                }
            }
            cur = cur.getCause();
            level++;
            if (level > 10) break; // guard against circular causes
        }
        System.out.println("──────────────────────────────────────────────────────────────");
        // Also dump full trace to stderr for IDEs that separate the streams
        t.printStackTrace(System.err);
    }

    private static void showFatalDialog(Throwable t) {
        // Walk to the root cause for a more useful dialog message
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();

        String msg = root.getClass().getSimpleName() + ": " + root.getMessage()
            + "\n\nSee console / terminal for the full stack trace.";

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("PixelCapture — Startup Error");
            alert.setHeaderText("Application failed to start");
            alert.setContentText(msg);
            alert.showAndWait();
            Platform.exit();
        });
    }
}
