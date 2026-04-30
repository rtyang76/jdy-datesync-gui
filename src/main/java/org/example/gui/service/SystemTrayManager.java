package org.example.gui.service;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public class SystemTrayManager {

    private static final Logger logger = Logger.getLogger(SystemTrayManager.class.getName());

    private final Stage primaryStage;
    private final Runnable onExit;
    private SystemTray tray;
    private TrayIcon trayIcon;
    private boolean initialized = false;

    public SystemTrayManager(Stage primaryStage, Runnable onExit) {
        this.primaryStage = primaryStage;
        this.onExit = onExit;
    }

    public boolean initialize() {
        if (!SystemTray.isSupported()) {
            logger.warning("系统托盘不支持");
            return false;
        }

        try {
            tray = SystemTray.getSystemTray();

            Image image = loadTrayImage();
            if (image == null) {
                logger.warning("无法加载托盘图标");
                return false;
            }

            PopupMenu popup = createPopupMenu();

            trayIcon = new TrayIcon(image, "简道云数据同步工具", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    System.out.println(">>> 托盘图标点击事件触发！button=" + e.getButton() + ", count=" + e.getClickCount());
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                        EventQueue.invokeLater(() -> {
                            System.out.println(">>> 托盘图标点击 -> 准备显示窗口");
                            Platform.runLater(() -> showMainWindow("托盘图标点击"));
                        });
                    }
                }
            });

            tray.add(trayIcon);
            initialized = true;
            logger.info("系统托盘初始化成功");
            return true;

        } catch (Exception e) {
            logger.warning("系统托盘初始化失败: " + e.getMessage());
            return false;
        }
    }

    private Image loadTrayImage() {
        try {
            var is = getClass().getResourceAsStream("/icons/tray-icon.png");
            if (is != null) {
                return ImageIO.read(is);
            }
        } catch (Exception e) {
            logger.fine("无法加载自定义托盘图标，使用默认图标");
        }

        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(59, 130, 246));
        g2d.fillOval(0, 0, size, size);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 10));
        g2d.drawString("J", 5, 12);
        g2d.dispose();

        return img;
    }

    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();

        MenuItem showItem = new MenuItem("显示主窗口");
        showItem.addActionListener(e -> {
            System.out.println(">>> 菜单栏点击: 显示主窗口");
            EventQueue.invokeLater(() -> Platform.runLater(() -> showMainWindow("菜单栏-显示主窗口")));
        });
        popup.add(showItem);

        MenuItem syncItem = new MenuItem("立即同步所有任务");
        syncItem.addActionListener(e -> {
            System.out.println(">>> 菜单栏点击: 立即同步");
            logger.info("从托盘触发立即同步");
        });
        popup.add(syncItem);

        popup.addSeparator();

        MenuItem aboutItem = new MenuItem("关于");
        aboutItem.addActionListener(e -> {
            System.out.println(">>> 菜单栏点击: 关于");
            EventQueue.invokeLater(() -> Platform.runLater(this::showAboutDialog));
        });
        popup.add(aboutItem);

        popup.addSeparator();

        MenuItem exitItem = new MenuItem("退出");
        exitItem.addActionListener(e -> {
            System.out.println(">>> 菜单栏点击: 退出");
            EventQueue.invokeLater(() -> {
                shutdown();
                if (onExit != null) {
                    Platform.runLater(onExit);
                }
            });
        });
        popup.add(exitItem);

        return popup;
    }

    public void showMainWindow(String source) {
        System.out.println(">>> showMainWindow() 被调用，来源: " + source);
        System.out.println(">>> primaryStage=" + primaryStage + ", isShowing=" + (primaryStage != null ? primaryStage.isShowing() : "null"));
        if (primaryStage != null) {
            if (!primaryStage.isShowing()) {
                primaryStage.show();
                System.out.println(">>> primaryStage.show() 已调用");
            }
            primaryStage.toFront();
            primaryStage.requestFocus();
            primaryStage.setIconified(false);
            System.out.println(">>> 窗口已置于前台，来源: " + source);
        } else {
            System.out.println(">>> 错误: primaryStage 为 null!");
        }
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText("简道云数据同步工具");
        alert.setContentText("版本: 1.1.0\n\n用于MySQL数据库与简道云表单的数据同步工具。");
        alert.showAndWait();
    }

    public void hideMainWindow() {
        if (primaryStage != null) {
            Platform.runLater(primaryStage::hide);
        }
    }

    public void updateStatus(String status) {
        if (trayIcon != null) {
            EventQueue.invokeLater(() -> 
                trayIcon.setToolTip("简道云数据同步工具 - " + status)
            );
        }
    }

    public void shutdown() {
        if (tray != null && trayIcon != null) {
            EventQueue.invokeLater(() -> {
                tray.remove(trayIcon);
                logger.info("系统托盘已移除");
            });
        }
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
