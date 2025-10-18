package com.chronoblocks.client.debug;

import com.chronoblocks.client.ChronoBlocksClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ChronoBlocks 调试工具
 * 提供详细的调试信息和性能监控
 */
public class ChronoBlocksDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronoBlocksDebugger.class);
    
    // 性能计数器
    private static final AtomicLong initCallCount = new AtomicLong(0);
    private static final AtomicLong screenOpenCount = new AtomicLong(0);
    private static final AtomicLong themeChangeCount = new AtomicLong(0);
    private static final AtomicLong errorCount = new AtomicLong(0);
    
    // 时间跟踪
    private static volatile long lastInitTime = 0;
    private static volatile long lastScreenOpenTime = 0;
    private static volatile long lastThemeChangeTime = 0;
    
    /**
     * 记录初始化调用
     */
    public static void recordInitCall() {
        initCallCount.incrementAndGet();
        lastInitTime = System.currentTimeMillis();
        LOGGER.debug("[ChronoBlocks] Init call recorded - total calls: {}", initCallCount.get());
    }
    
    /**
     * 记录屏幕打开
     */
    public static void recordScreenOpen() {
        screenOpenCount.incrementAndGet();
        lastScreenOpenTime = System.currentTimeMillis();
        LOGGER.debug("[ChronoBlocks] Screen open recorded - total opens: {}", screenOpenCount.get());
    }
    
    /**
     * 记录主题更改
     */
    public static void recordThemeChange() {
        themeChangeCount.incrementAndGet();
        lastThemeChangeTime = System.currentTimeMillis();
        LOGGER.debug("[ChronoBlocks] Theme change recorded - total changes: {}", themeChangeCount.get());
    }
    
    /**
     * 记录错误
     */
    public static void recordError() {
        errorCount.incrementAndGet();
        LOGGER.debug("[ChronoBlocks] Error recorded - total errors: {}", errorCount.get());
    }
    

    /**
     * 获取性能统计
     */
    private static String getPerformanceStats() {
        return ChronoBlocksClient.getPerformanceStats();
    }
    
    /**
     * 获取内存使用情况
     */
    private static String getMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        return String.format(
                """
                        Heap Used: %d MB / %d MB (%.1f%%)
                        Non-Heap Used: %d MB / %d MB (%.1f%%)
                        Max Heap: %d MB
                        Max Non-Heap: %d MB""",
            heapUsage.getUsed() / 1024 / 1024,
            heapUsage.getMax() / 1024 / 1024,
            (double) heapUsage.getUsed() / heapUsage.getMax() * 100,
            nonHeapUsage.getUsed() / 1024 / 1024,
            nonHeapUsage.getMax() / 1024 / 1024,
            (double) nonHeapUsage.getUsed() / nonHeapUsage.getMax() * 100,
            heapUsage.getMax() / 1024 / 1024,
            nonHeapUsage.getMax() / 1024 / 1024
        );
    }
    
    /**
     * 计算错误率
     */
    private static double calculateErrorRate() {
        long totalCalls = initCallCount.get() + screenOpenCount.get() + themeChangeCount.get();
        if (totalCalls == 0) return 0.0;
        return (double) errorCount.get() / totalCalls * 100;
    }
    
    /**
     * 获取运行时间
     */
    private static long getUptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }
    
    /**
     * 格式化时间
     */
    private static String formatTime(long timestamp) {
        if (timestamp == 0) return "Never";
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 1000) return diff + "ms ago";
        if (diff < 60000) return (diff / 1000) + "s ago";
        if (diff < 3600000) return (diff / 60000) + "m ago";
        return (diff / 3600000) + "h ago";
    }
    
    /**
     * 重置所有计数器
     */
    public static void resetCounters() {
        initCallCount.set(0);
        screenOpenCount.set(0);
        themeChangeCount.set(0);
        errorCount.set(0);
        lastInitTime = 0;
        lastScreenOpenTime = 0;
        lastThemeChangeTime = 0;
        LOGGER.info("[ChronoBlocks] Debug counters reset");
    }
    
    /**
     * 获取计数器统计
     */
    public static String getCounterStats() {
        return String.format(
            "Counters - Init: %d, Screen Opens: %d, Theme Changes: %d, Errors: %d",
            initCallCount.get(),
            screenOpenCount.get(),
            themeChangeCount.get(),
            errorCount.get()
        );
    }
    
    /**
     * 检查系统健康状态
     */
    public static boolean isSystemHealthy() {
        // 检查基本状态
        if (!com.chronoblocks.ui.imgui.ImGuiRenderer.getInstance().isInitialized()) return false;
        
        // 检查错误率
        double errorRate = calculateErrorRate();
        if (errorRate > 10.0) return false; // 错误率超过10%认为不健康
        
        // 检查内存使用
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsagePercent = (double) heapUsage.getUsed() / heapUsage.getMax() * 100;
        return !(memoryUsagePercent > 90.0); // 内存使用超过90%认为不健康
    }
    
    /**
     * 获取健康状态报告
     */
    public static String getHealthReport() {
        boolean healthy = isSystemHealthy();
        return String.format(
                """
                        System Health: %s
                        Error Rate: %.2f%%
                        Memory Usage: %.1f%%
                        Uptime: %d ms""",
            healthy ? "HEALTHY" : "UNHEALTHY",
            calculateErrorRate(),
            getMemoryUsagePercent(),
            getUptime()
        );
    }
    
    /**
     * 获取内存使用百分比
     */
    private static double getMemoryUsagePercent() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return (double) heapUsage.getUsed() / heapUsage.getMax() * 100;
    }
}