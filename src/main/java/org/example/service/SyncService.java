package org.example.service;

/**
 * 同步服务接口
 * 定义同步服务的方法
 */
public interface SyncService {

    /**
     * 执行同步过程
     * @return 是否有数据需要同步
     */
    boolean syncProcess();

    /**
     * 获取自定义码
     * @param currentDate 当前日期
     * @param lastCount 上次计数
     * @return 自定义码
     */
    String getCustomCode(java.time.LocalDate currentDate, int lastCount);
}
