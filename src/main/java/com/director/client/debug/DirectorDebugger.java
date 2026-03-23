package com.director.client.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Director 调试工具
 * 提供详细的调试信息和性能监控
 */
public class DirectorDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorDebugger.class);
    
    // 性能计数器
    private static final AtomicLong screenOpenCount = new AtomicLong(0);

    /**
     * 记录屏幕打开
     */
    public static void recordScreenOpen() {
        screenOpenCount.incrementAndGet();
        LOGGER.debug("[Director] Screen open recorded - total opens: {}", screenOpenCount.get());
    }
}