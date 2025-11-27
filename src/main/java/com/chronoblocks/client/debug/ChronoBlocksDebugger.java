package com.chronoblocks.client.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * ChronoBlocks 调试工具
 * 提供详细的调试信息和性能监控
 */
public class ChronoBlocksDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronoBlocksDebugger.class);
    
    // 性能计数器
    private static final AtomicLong screenOpenCount = new AtomicLong(0);

    /**
     * 记录屏幕打开
     */
    public static void recordScreenOpen() {
        screenOpenCount.incrementAndGet();
        LOGGER.debug("[ChronoBlocks] Screen open recorded - total opens: {}", screenOpenCount.get());
    }
}