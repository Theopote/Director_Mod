package com.director.ui.coordinator;

import com.director.export.video.VideoExportManager;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.playback.PlaybackOrchestrator;
import net.minecraft.client.render.RenderTickCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI 与播放系统的桥接 - 连接渲染帧循环与 {@link PlaybackOrchestrator}。
 * <p>
 * 播放驱动必须在 {@code GameRenderer.renderWorld} 之前执行，确保当帧世界渲染
 * 使用与当前时间线一致的采样结果。此前在 ImGui {@code UIManager.render()} 中
 * 用墙钟计时会与世界渲染不同步，并在掉帧时因 delta 上限永久丢失时间，表现为卡顿。
 */
public final class UIPlaybackBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIPlaybackBridge.class);

    /** 单帧最多推进的播放时间（秒）；超出部分累积到下一帧，避免掉帧后永久丢时间 */
    private static final float MAX_STEP_SECONDS = 0.1f;

    /** 单帧原始间隔的硬上限，防止调试断点/挂起后一次性推进过多 */
    private static final float MAX_FRAME_SECONDS = 0.5f;

    private float playbackDebtSeconds = 0f;

    /**
     * 在 {@code GameRenderer.renderWorld(HEAD)} 调用：用渲染帧间隔驱动播放。
     */
    public void tickFromRenderWorldHead(RenderTickCounter tickCounter) {
        if (VideoExportManager.getInstance().isExporting()) {
            playbackDebtSeconds = 0f;
            return;
        }

        try {
            float frameDelta = resolveFrameDeltaSeconds(tickCounter);
            TimelineModel model = PlaybackOrchestrator.get().getBoundModel();
            if (model == null) {
                playbackDebtSeconds = 0f;
                return;
            }

            if (!model.isPlaying()) {
                playbackDebtSeconds = 0f;
                PlaybackOrchestrator.get().tick(frameDelta);
                return;
            }

            playbackDebtSeconds += frameDelta;
            float step = Math.min(playbackDebtSeconds, MAX_STEP_SECONDS);
            playbackDebtSeconds -= step;
            if (step > 0f) {
                PlaybackOrchestrator.get().tick(step);
            }
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
            float clamped = Math.min(Math.max(0f, deltaSeconds), MAX_FRAME_SECONDS);
            PlaybackOrchestrator.get().tick(clamped);
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[UIPlaybackBridge] 播放 tick 异常", e);
            }
        }
    }

    /**
     * 解析当前渲染帧的真实间隔（秒）。
     * <p>
     * 优先使用 {@code RenderTickCounter.getDynamicDeltaTicks()}（单位为 game tick），
     * 与 Minecraft 渲染循环对齐；反射兜底以兼容 IDE 索引差异。
     */
    public static float resolveFrameDeltaSeconds(RenderTickCounter tickCounter) {
        if (tickCounter == null) {
            return 1f / 60f;
        }

        float ticks = invokeFloat(tickCounter, "getDynamicDeltaTicks");
        if (ticks > 0f && Float.isFinite(ticks)) {
            return Math.min(ticks / 20f, MAX_FRAME_SECONDS);
        }

        ticks = invokeFloat(tickCounter, "getFixedDeltaTicks");
        if (ticks > 0f && Float.isFinite(ticks)) {
            return Math.min(ticks / 20f, MAX_FRAME_SECONDS);
        }

        return 1f / 60f;
    }

    private static float invokeFloat(Object target, String methodName) {
        try {
            var method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Float f) {
                return f;
            }
            if (value instanceof Double d) {
                return d.floatValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return -1f;
    }
}
