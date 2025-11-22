package com.chronoblocks.ui.timeline.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineModel;

/**
 * 时间线自动保存管理器
 */
public final class AutoSaveManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoSaveManager.class);
    
    private final TimelineModel model;
    private long lastSaveTime = System.currentTimeMillis();
    private static final long AUTO_SAVE_INTERVAL = 5000; // 5秒自动保存
    private boolean dirty = false;

    public AutoSaveManager(TimelineModel model) {
        this.model = model;
    }

    /**
     * 每帧调用，检查是否需要自动保存
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();
        if (dirty && (currentTime - lastSaveTime) >= AUTO_SAVE_INTERVAL) {
            model.save();
            dirty = false;
            lastSaveTime = currentTime;
            LOGGER.debug("[Timeline] 自动保存时间线数据");
        }
    }

    /**
     * 标记数据已改变
     */
    public void markDirty() {
        dirty = true;
    }
}

