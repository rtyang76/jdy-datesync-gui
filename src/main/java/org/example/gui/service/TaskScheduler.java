package org.example.gui.service;

import org.example.gui.model.SyncTaskConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class TaskScheduler {

    private static final Logger logger = Logger.getLogger(TaskScheduler.class.getName());

    private final ConfigManager configManager;
    private final SyncEngine syncEngine;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    public TaskScheduler(ConfigManager configManager, SyncEngine syncEngine) {
        this.configManager = configManager;
        this.syncEngine = syncEngine;
        this.scheduler = new ScheduledThreadPoolExecutor(5, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    public void startAll() {
        List<SyncTaskConfig> tasks = configManager.loadSyncTasks();
        for (SyncTaskConfig task : tasks) {
            if (task.isEnabled()) {
                scheduleTask(task);
            }
        }
        logger.info("已启动 " + scheduledTasks.size() + " 个定时任务");
    }

    public void scheduleTask(SyncTaskConfig task) {
        cancelTask(task.getId());

        if (!task.isEnabled()) return;

        Runnable taskRunner = () -> {
            logger.info("定时触发任务: " + task.getName());
            SyncEngine.SyncResult result = syncEngine.executeTask(task.getId());
            if (result.isSuccess()) {
                logger.info("任务完成: " + task.getName() + " - " + result.getMessage());
            } else {
                logger.warning("任务失败: " + task.getName() + " - " + result.getMessage());
            }
        };

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                taskRunner,
                0,
                task.getSyncIntervalMinutes(),
                TimeUnit.MINUTES
        );

        scheduledTasks.put(task.getId(), future);
        logger.info("已调度任务: " + task.getName() + " (间隔: " + task.getSyncIntervalMinutes() + "分钟)");
    }

    public void cancelTask(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            logger.info("已取消任务: " + taskId);
        }
    }

    public SyncEngine.SyncResult executeTaskNow(String taskId) {
        return syncEngine.executeTask(taskId);
    }

    public void refresh() {
        List<SyncTaskConfig> tasks = configManager.loadSyncTasks();
        for (SyncTaskConfig task : tasks) {
            if (task.isEnabled()) {
                scheduleTask(task);
            } else {
                cancelTask(task.getId());
            }
        }

        for (String id : scheduledTasks.keySet()) {
            boolean exists = tasks.stream().anyMatch(t -> t.getId().equals(id));
            if (!exists) {
                cancelTask(id);
            }
        }
    }

    public void shutdown() {
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        logger.info("任务调度器已关闭");
    }

    public boolean isTaskRunning(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        return future != null && !future.isDone() && !future.isCancelled();
    }
}
