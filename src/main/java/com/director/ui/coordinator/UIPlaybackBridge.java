package com.director.ui.coordinator;

import com.director.ui.timeline.playback.PlaybackOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI 与播放系统的桥接 - 连接 UI 帧循环与 PlaybackOrchestrator。
 * <p>
 * 职责：
 * <ul>
 *   <li>计算 deltaTime（基于上一帧时间）</li>
 *   <li>驱动 PlaybackOrchestrator.tick(deltaSeconds)</li>
 *   <li>未来可扩展：暂停 UI 但继续播放等模式</li>
 * </ul>
 * <p>
 * UIManager 不再直接调用 PlaybackOrchestrator.get().tick(...)，统一通过本桥接。
 *
 * @author Director Team
 */
public final class UIPlaybackBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIPlaybackBridge.class);

    /** 最大帧间隔（秒），避免长时间暂停后的巨大跳跃 */
    private static final float MAX_DELTA = 0.1f;

    private long lastUpdateTime = System.currentTimeMillis();

    /**
     * 驱动一帧播放逻辑。
     * 计算自上一帧以来的 delta，限制上限后交给 PlaybackOrchestrator。
     */
    public void tick() {
        try {
            long now = System.currentTimeMillis();
            float deltaSeconds = (now - lastUpdateTime) / 1000.0f;
            lastUpdateTime = now;

            deltaSeconds = Math.min(deltaSeconds, MAX_DELTA);

            PlaybackOrchestrator.get().tick(deltaSeconds);
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[UIPlaybackBridge] 播放 tick 异常", e);
            }
        }
    }

    /**
     * 使用外部提供的 delta 驱动一帧（用于与已有 delta 计算逻辑集成）。
     */
    public void tick(float deltaSeconds) {
        try {
            float clamped = Math.min(deltaSeconds, MAX_DELTA);
            PlaybackOrchestrator.get().tick(clamped);
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[UIPlaybackBridge] 播放 tick 异常", e);
            }
        }
    }
}
