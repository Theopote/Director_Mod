package com.chronoblocks.ui.timeline.playback;

import net.minecraft.client.MinecraftClient;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PlaybackOrchestrator
 *
 * ✅ 单一播放驱动入口（替代 TimelinePanel 内的 updatePlayback + 一堆 Controller.tick）
 * ✅ 每帧由 UIManager 调用 tick(deltaSeconds)
 * ✅ TimelineModel 作为单一真相源（playing/currentTime/speed/loop/scrubbing）
 *
 * <p>设计目标：
 * <ul>
 *   <li>TimelinePanel 只做"编辑器视图"，不做系统驱动</li>
 *   <li>播放系统在任何情况下（面板隐藏/失焦）都保持一致</li>
 *   <li>Facade 负责改 TimelineModel 的播放状态</li>
 *   <li>Orchestrator 负责"把 TimelineModel 的状态变成世界表现"</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class PlaybackOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackOrchestrator.class);
    private static final PlaybackOrchestrator INSTANCE = new PlaybackOrchestrator();

    public static PlaybackOrchestrator get() {
        return INSTANCE;
    }

    private TimelineModel model;

    // 防止长时间卡顿后的巨大跳跃
    private static final float MAX_DELTA_SECONDS = 0.1f; // 100ms

    private PlaybackOrchestrator() {}

    /**
     * 绑定 TimelineModel（通常来自 TimelinePanel）
     * 可重复绑定（例如世界切换/重建模型）
     */
    public void bindModel(TimelineModel model) {
        this.model = model;
    }

    public TimelineModel getBoundModel() {
        return model;
    }

    /**
     * 每帧更新（由 UIManager 驱动）
     * @param deltaSeconds UI帧间隔秒数（建议已做clamp）
     */
    public void tick(float deltaSeconds) {
        if (model == null) {
            return;
        }

        // Clamp delta，避免后台/暂停造成跳跃
        if (deltaSeconds < 0f) {
            deltaSeconds = 0f;
        }
        if (deltaSeconds > MAX_DELTA_SECONDS) {
            deltaSeconds = MAX_DELTA_SECONDS;
        }

        // 1) 驱动"播放时间推进"（只有 playing 时推进）
        if (model.isPlaying()) {
            advanceTime(deltaSeconds);
        }

        // 2) 驱动"子系统更新"（每帧都跑：内部可根据 scrubbing/playing 自行决定行为）
        driveSubsystems(deltaSeconds);
    }

    private void advanceTime(float deltaSeconds) {
        float speed = model.getPlaybackSpeed();
        float newTime = model.getCurrentTime() + deltaSeconds * speed;
        float duration = model.getDuration();

        // 处理超界：loop or stop
        if (newTime > duration) {
            if (model.isLoopEnabled() && duration > 0) {
                // loop：回到起点继续
                newTime = 0f;
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackOrchestrator] 播放循环，回到开始 (时间: {:.2f}s)", newTime);
                }
            } else {
                // stop：停在终点并停止
                newTime = duration;
                boolean wasPlaying = model.isPlaying();
                model.setPlaying(false);

                // 播放停止通知（保持原本逻辑）
                try {
                    TimelineCameraController.getInstance().onPlaybackStopped();
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackOrchestrator] 播放停止通知失败", e);
                    }
                }

                if (wasPlaying && LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackOrchestrator] 播放结束，停在终点 (时间: {:.2f}s)", newTime);
                }
            }
        }

        // 写入统一真相源
        model.setCurrentTime(newTime);

        // ⭐ 播放采样：季节/天气（保持 TimelinePanel 原逻辑）
        try {
            com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance()
                    .onTimelineTick(newTime);
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] 季节采样失败", e);
            }
        }

        try {
            com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance()
                    .updateForPlayback(newTime);
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] 天气采样失败", e);
            }
        }
    }

    private void driveSubsystems(float deltaSeconds) {
        // 渲染 delta：用于摄像机采样（和 TimelinePanel 的 getRenderDelta 等价）
        float renderDelta = getRenderDeltaFallback(deltaSeconds);

        // 这些更新原来都在 TimelinePanel.doRender() 里
        // 现在统一收口在这里：TimelinePanel 不再负责驱动

        try {
            com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] TimelineClipBlockController 更新失败", e);
            }
        }

        try {
            TimelineCameraController.getInstance().updateOwnerAndSample(renderDelta);
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] TimelineCameraController 更新失败", e);
            }
        }

        try {
            com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] TimelineWorldController 更新失败", e);
            }
        }

        try {
            com.chronoblocks.ui.timeline.playback.TimelineAudioController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] TimelineAudioController 更新失败", e);
            }
        }

        try {
            com.chronoblocks.actor.runtime.ActorRuntimeController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] ActorRuntimeController 更新失败", e);
            }
        }

        try {
            com.chronoblocks.ui.actor.gizmo.ActorGizmoController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] ActorGizmoController 更新失败", e);
            }
        }

        try {
            com.chronoblocks.client.renderer.weather.WeatherRenderer.get().tick();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] WeatherRenderer 更新失败", e);
            }
        }

        try {
            com.chronoblocks.ui.timeline.playback.weather.WeatherParticleController.getInstance().tick();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] WeatherParticleController 更新失败", e);
            }
        }

        // v1.1：天气事件 tick（依赖当前时间）
        try {
            com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance()
                    .tickEvents(model.getCurrentTime());
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] TimelineWeatherController.tickEvents 更新失败", e);
            }
        }
    }

    private float getRenderDeltaFallback(float deltaSeconds) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getRenderTickCounter() != null) {
            return client.getRenderTickCounter().getTickProgress(false);
        }
        return deltaSeconds;
    }
}
