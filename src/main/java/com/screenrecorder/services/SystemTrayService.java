package com.screenrecorder.services;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds the application to the Windows system tray.
 * The tray icon provides quick Show/Hide and Exit actions.
 */
public class SystemTrayService {

    private static final Logger LOG = Logger.getLogger(SystemTrayService.class.getName());

    private final Stage primaryStage;
    private TrayIcon trayIcon;

    public SystemTrayService(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void initialize() {
        if (!SystemTray.isSupported()) {
            LOG.warning("System tray not supported on this platform.");
            return;
        }

        // Build tray icon image programmatically (red record dot on dark bg)
        Image icon = buildTrayIconImage();

        PopupMenu popup = new PopupMenu();

        MenuItem showItem = new MenuItem("Show PixelCapture");
        showItem.addActionListener(e -> Platform.runLater(() -> {
            primaryStage.show();
            primaryStage.toFront();
        }));

        // TODO: add quick "Start Recording" / "Stop Recording" menu items
        // that delegate to RecordingService without opening the full window.

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            remove();
            Platform.exit();
            System.exit(0);
        });

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(icon, "PixelCapture", popup);
        trayIcon.setImageAutoSize(true);

        ActionListener doubleClick = e -> Platform.runLater(() -> {
            primaryStage.show();
            primaryStage.toFront();
        });
        trayIcon.addActionListener(doubleClick);

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            LOG.log(Level.WARNING, "Could not add tray icon", e);
        }
    }

    public void remove() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    public void showNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Image buildTrayIconImage() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Dark background circle
        g.setColor(new Color(30, 30, 46));
        g.fillOval(0, 0, size, size);
        // Red record dot
        g.setColor(new Color(239, 68, 68));
        g.fillOval(3, 3, size - 6, size - 6);
        g.dispose();
        return img;
    }
}
