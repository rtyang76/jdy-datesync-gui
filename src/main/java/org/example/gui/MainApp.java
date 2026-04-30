package org.example.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.example.gui.service.SystemTrayManager;
import org.example.gui.view.MainWindow;

public class MainApp extends Application {

    private MainWindow mainWindow;
    private SystemTrayManager trayManager;
    private Stage primaryStage;
    private boolean isExiting = false;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        Platform.setImplicitExit(false);

        try {
            mainWindow = new MainWindow();
            Scene scene = new Scene(mainWindow.getView(), 1200, 800);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

            primaryStage.setTitle("简道云数据同步工具");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(600);

            initSystemTray();
            initMacOSDockHandler();

            primaryStage.setOnCloseRequest(this::onCloseRequest);

            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Application start error: " + e.getMessage());
            Platform.exit();
            System.exit(1);
        }
    }

    private void initMacOSDockHandler() {
        try {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            
            System.out.println("检查 APP_EVENT_REOPENED 支持: " + desktop.isSupported(java.awt.Desktop.Action.APP_EVENT_REOPENED));
            System.out.println("检查 APP_EVENT_FOREGROUND 支持: " + desktop.isSupported(java.awt.Desktop.Action.APP_EVENT_FOREGROUND));
            System.out.println("检查 APP_EVENT_HIDDEN 支持: " + desktop.isSupported(java.awt.Desktop.Action.APP_EVENT_HIDDEN));
            
            if (desktop.isSupported(java.awt.Desktop.Action.APP_EVENT_REOPENED)) {
                desktop.addAppEventListener(new java.awt.desktop.AppReopenedListener() {
                    @Override
                    public void appReopened(java.awt.desktop.AppReopenedEvent e) {
                        System.out.println(">>> AppReopened事件触发！ <<<");
                        Platform.runLater(() -> showMainWindow("Dock点击"));
                    }
                });
                System.out.println("macOS AppReopened事件已注册");
            }

            if (desktop.isSupported(java.awt.Desktop.Action.APP_EVENT_FOREGROUND)) {
                desktop.addAppEventListener(new java.awt.desktop.AppForegroundListener() {
                    @Override
                    public void appRaisedToForeground(java.awt.desktop.AppForegroundEvent e) {
                        System.out.println(">>> AppForeground事件触发！ <<<");
                        Platform.runLater(() -> {
                            if (primaryStage != null && !primaryStage.isShowing()) {
                                showMainWindow("前台切换");
                            }
                        });
                    }

                    @Override
                    public void appMovedToBackground(java.awt.desktop.AppForegroundEvent e) {
                        System.out.println(">>> AppMovedToBackground事件触发！ <<<");
                    }
                });
                System.out.println("macOS AppForeground事件已注册");
            }

            if (desktop.isSupported(java.awt.Desktop.Action.APP_EVENT_HIDDEN)) {
                desktop.addAppEventListener(new java.awt.desktop.AppHiddenListener() {
                    @Override
                    public void appHidden(java.awt.desktop.AppHiddenEvent e) {
                        System.out.println(">>> AppHidden事件触发！ <<<");
                    }

                    @Override
                    public void appUnhidden(java.awt.desktop.AppHiddenEvent e) {
                        System.out.println(">>> AppUnhidden事件触发！ <<<");
                        Platform.runLater(() -> {
                            if (primaryStage != null && !primaryStage.isShowing()) {
                                showMainWindow("取消隐藏");
                            }
                        });
                    }
                });
                System.out.println("macOS AppHidden事件已注册");
            }

            initAppleEawtHandler();

        } catch (Exception e) {
            System.out.println("macOS Dock事件注册失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initAppleEawtHandler() {
        try {
            Class<?> appClass = Class.forName("com.apple.eawt.Application");
            Object app = appClass.getMethod("getApplication").invoke(null);

            try {
                Class<?> handlerClass = Class.forName("com.apple.eawt.OpenURIHandler");
                System.out.println("OpenURIHandler 可用");
            } catch (ClassNotFoundException ignored) {}

            try {
                Class<?> handlerClass = Class.forName("com.apple.eawt.OpenFilesHandler");
                System.out.println("OpenFilesHandler 可用");
            } catch (ClassNotFoundException ignored) {}

            try {
                Class<?> handlerClass = Class.forName("com.apple.eawt.QuitHandler");
                Object quitHandler = java.lang.reflect.Proxy.newProxyInstance(
                    handlerClass.getClassLoader(),
                    new Class<?>[]{ handlerClass },
                    (proxy, method, args) -> {
                        if ("handleQuitRequestWith".equals(method.getName())) {
                            System.out.println(">>> QuitHandler事件触发！ <<<");
                            Platform.runLater(this::exitApplication);
                        }
                        return null;
                    }
                );
                appClass.getMethod("setQuitHandler", handlerClass).invoke(app, quitHandler);
                System.out.println("macOS QuitHandler已注册");
            } catch (Exception e) {
                System.out.println("QuitHandler注册失败: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Apple EAWT初始化失败: " + e.getMessage());
        }
    }

    private void showMainWindow(String source) {
        System.out.println(">>> MainApp.showMainWindow() 被调用，来源: " + source);
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

    private void initSystemTray() {
        try {
            trayManager = new SystemTrayManager(primaryStage, this::exitApplication);
            boolean initialized = trayManager.initialize();

            if (initialized) {
                System.out.println("系统托盘初始化成功，关闭窗口将最小化到托盘");
            } else {
                System.out.println("系统托盘初始化失败，关闭窗口将直接退出");
                Platform.setImplicitExit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Platform.setImplicitExit(true);
        }
    }

    private void onCloseRequest(WindowEvent event) {
        if (isExiting) {
            return;
        }

        if (trayManager != null && trayManager.isInitialized()) {
            event.consume();
            primaryStage.hide();
            trayManager.updateStatus("后台运行中");
            System.out.println("窗口已隐藏，后台运行中...");
        }
    }

    private void exitApplication() {
        if (isExiting) {
            return;
        }
        isExiting = true;

        System.out.println("正在退出应用...");

        if (mainWindow != null) {
            mainWindow.shutdown();
        }
        if (trayManager != null) {
            trayManager.shutdown();
        }

        Platform.setImplicitExit(true);
        Platform.exit();
        System.exit(0);
    }

    @Override
    public void stop() {
        if (!isExiting) {
            exitApplication();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
