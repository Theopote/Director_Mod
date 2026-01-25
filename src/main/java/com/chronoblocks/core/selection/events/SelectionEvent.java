package com.chronoblocks.core.selection.events;

import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎯 选择事件基类
 * <p>
 * 定义所有选择相关事件的通用接口和基础功能。
 * 事件系统用于解耦选择工具与UI组件，实现更好的模块化设计。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public abstract class SelectionEvent {
    
    protected static final Logger LOGGER = LoggerFactory.getLogger(SelectionEvent.class);
    
    /** 事件时间戳 */
    private final long timestamp;
    
    /** 事件来源世界 */
    private final World world;
    
    /** 事件是否已处理 */
    private boolean consumed = false;
    
    public SelectionEvent(World world) {
        this.world = world;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 获取事件类型
     */
    public abstract String getEventType();
    
    /**
     * 获取事件描述
     */
    public abstract String getDescription();
    
    /**
     * 标记事件为已处理
     */
    public void consume() {
        this.consumed = true;
    }
    
    /**
     * 检查事件是否已处理
     */
    public boolean isConsumed() {
        return consumed;
    }
    
    /**
     * 获取事件时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取事件来源世界
     */
    public World getWorld() {
        return world;
    }
    
    /**
     * 获取事件优先级（数字越小优先级越高）
     */
    public int getPriority() {
        return 100; // 默认优先级
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s (timestamp: %d, consumed: %s)", 
            getEventType(), getDescription(), timestamp, consumed);
    }
}
