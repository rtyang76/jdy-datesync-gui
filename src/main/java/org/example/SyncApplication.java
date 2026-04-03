package org.example;

import org.example.service.OrderSyncService;
import org.example.service.ItemSyncService;
import org.example.service.DeliveryNoticeSyncService;
import org.example.service.impl.OrderSyncServiceImpl;
import org.example.service.impl.ItemSyncServiceImpl;
import org.example.service.impl.DeliveryNoticeSyncServiceImpl;
import org.example.dm.service.DmJdySyncService;
import org.example.util.LogUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 同步应用程序主类
 * 负责启动和管理同步任务
 */
public class SyncApplication {

    private static final int SYNC_INTERVAL_MINUTES = 5; // 定时同步间隔（分钟）

    /**
     * 主程序入口
     */
    public static void main(String[] args) {
        System.out.println("\n==================================================");
        System.out.println("数据同步程序启动");
        System.out.println("==================================================\n");

        try {
            System.out.println("正在验证数据库连接...");
            // 验证数据库连接
            try {
                // 通过调用一个简单的查询来测试数据库连接
                org.example.service.DatabaseService.getInstance().getLastSyncId();
                LogUtil.logInfo("数据库连接成功");
                System.out.println("数据库连接成功");
            } catch (Exception e) {
                LogUtil.logError("数据库连接失败: " + e.getMessage());
                System.out.println("数据库连接失败: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }

            // 检查是否有参数指定特定同步类型
            if (args.length > 0) {
                String syncType = args[0].toLowerCase();
                System.out.println("执行同步类型: " + syncType);
                switch (syncType) {
                    case "dmpush":
                        // 只执行DM数据推送简道云
                        System.out.println("开始执行DM数据推送简道云...");
                        DmJdySyncService dmPushService = DmJdySyncService.getInstance();
                        dmPushService.pushDataToJiandaoyun();
                        System.out.println("DM数据推送简道云完成");
                        return;
                    case "delivery":
                        // 只执行采购物料通知单同步
                        System.out.println("开始执行采购物料通知单同步...");
                        DeliveryNoticeSyncService deliveryService = DeliveryNoticeSyncServiceImpl.getInstance();
                        deliveryService.syncProcess();
                        System.out.println("采购物料通知单同步完成");
                        return;
                    case "order":
                        // 只执行订单同步
                        OrderSyncService orderService = OrderSyncServiceImpl.getInstance();
                        orderService.syncProcess();
                        System.out.println("订单同步完成");
                        return;
                    case "item":
                        // 只执行物料同步
                        ItemSyncService itemService = ItemSyncServiceImpl.getInstance();
                        itemService.syncProcess();
                        System.out.println("物料同步完成");
                        return;
                    default:
                        System.out.println("未知的同步类型: " + syncType);
                        System.out.println("支持的类型: dmpush, delivery, order, item");
                        return;
                }
            }

            // 没有参数时，启动定时同步
            final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

            // 创建同步任务
            Runnable syncTask = new SyncTask();

            scheduler.scheduleAtFixedRate(syncTask, 0, SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES);
            LogUtil.logInfo("同步任务已启动，每" + SYNC_INTERVAL_MINUTES + "分钟执行一次");

            // 添加关闭钩子
            Thread shutdownHook = new Thread(() -> {
                LogUtil.logInfo("正在关闭程序...");
                shutdownGracefully(scheduler);
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            awaitShutdown(scheduler);

        } catch (Exception e) {
            LogUtil.logError("程序初始化失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // 数据库连接池会自动管理连接
            LogUtil.logInfo("程序已退出");
        }
    }

    /**
     * 等待程序关闭
     */
    private static void awaitShutdown(ScheduledExecutorService scheduler) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        System.out.println("[INFO] 程序正在运行中，按 Ctrl+C 停止...");

        try {
            latch.await(); // 等待程序被中断
        } catch (InterruptedException e) {
            System.out.println("[INFO] 收到中断信号，准备关闭程序...");
            throw e;
        }
    }

    /**
     * 优雅关闭调度器
     */
    private static void shutdownGracefully(ScheduledExecutorService scheduler) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                // 等待最多 30 秒让调度器完成任务
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.err.println("调度器未及时终止，强制关闭...");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                System.err.println("关闭调度器时被中断，强制终止...");
                scheduler.shutdownNow();
                Thread.currentThread().interrupt(); // 恢复中断状态
            }
        }
    }

    /**
     * 同步任务类
     */
    private static class SyncTask implements Runnable {
        private final DmJdySyncService dmJdySyncService;
        private final OrderSyncService orderSyncService;
        private final ItemSyncService itemSyncService;
        private final DeliveryNoticeSyncService deliveryNoticeSyncService;

        public SyncTask() {
            this.dmJdySyncService = DmJdySyncService.getInstance();
            this.orderSyncService = OrderSyncServiceImpl.getInstance();
            this.itemSyncService = ItemSyncServiceImpl.getInstance();
            this.deliveryNoticeSyncService = DeliveryNoticeSyncServiceImpl.getInstance();
        }

        @Override
        public void run() {
            try {
                // 阶段1: 推送DM数据到简道云（本地数据库 → 简道云）
                // 注：DM数据现在通过EDI推送服务接收，不再需要远程拉取
                boolean dmHasData = dmJdySyncService.pushDataToJiandaoyun();

                // 阶段2: 同步MSD数据（本地数据库 → 简道云）
                // 执行订单同步
                boolean orderHasData = orderSyncService.syncProcess();

                // 执行物料同步
                boolean itemHasData = itemSyncService.syncProcess();

                // 执行采购物料通知单同步
                boolean deliveryHasData = deliveryNoticeSyncService.syncProcess();

                // 如果所有服务都没有数据，输出一行汇总日志
                if (!dmHasData && !orderHasData && !itemHasData && !deliveryHasData) {
                    LogUtil.logInfo("[定时同步] 无新数据需要同步");
                }

            } catch (Exception e) {
                LogUtil.logError("同步过程发生异常: " + e.getMessage());
            }
        }
    }
}