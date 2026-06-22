package com.director.ui.timeline.playback;

import net.minecraft.client.MinecraftClient;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.playback.camera.TimelineCameraController;
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
 * @author Director Team
 * @version 1.2 - 生命周期钩子、模型绑定优化、Facade 闭环对齐
 */
public final class PlaybackOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackOrchestrator.class);
    private static final PlaybackOrchestrator INSTANCE = new PlaybackOrchestrator();

    public static PlaybackOrchestrator get() {
        return INSTANCE;
    }

    private TimelineModel model;
    private boolean lastPlaying = false; // 用于检测播放状态变化

    // 防止长时间卡顿后的巨大跳跃
    private static final float MAX_DELTA_SECONDS = 0.1f; // 100ms

    private PlaybackOrchestrator() {}

    /**
     * 绑定 TimelineModel（通常来自 TimelinePanel）
     * 可重复绑定（例如世界切换/重建模型）
     * 
     * <p>v1.2 改进：避免重复绑定相同模型，只在生命周期节点绑定
     */
    public void bindModel(TimelineModel model) {
        if (this.model == model) {
            return; // 避免重复绑定
        }
        this.model = model;
        onModelBound(model);
    }
    
    /**
     * 模型绑定后的初始化（v1.2 新增）
     */
    private void onModelBound(TimelineModel model) {
        // 初始化播放状态（必要时）
        if (model != null && model.isPlaying()) {
            model.setPlaying(false); // 确保初始状态为暂停
        }
        lastPlaying = false; // 重置状态追踪
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

        // ⭐ v1.2 改进：检测播放状态变化并触发生命周期钩子
        boolean playing = model.isPlaying();
        if (playing && !lastPlaying) {
            // 从 paused → playing
            onPlay();
        } else if (!playing && lastPlaying) {
            // 从 playing → paused
            onPause();
        }
        lastPlaying = playing;

        // 1) 驱动"播放时间推进"（只有 playing 时推进）
        if (playing) {
            advanceTime(deltaSeconds);
        }

        // 2) 驱动"子系统更新"（每帧都跑：内部可根据 scrubbing/playing 自行决定行为）
        driveSubsystems(deltaSeconds);
    }
    
    /**
     * 播放生命周期钩子：开始播放（v1.2 新增）
     * 从 paused → playing 时调用
     */
    private void onPlay() {
        try {
            // 摄像机 / 音频 / 世界状态准备
            // 注意：TimelineCameraController.startPlayback() 已由 TimelineModel.setPlaying() 自动调用
            // 这里可以添加其他需要在播放开始时执行的逻辑（如音频、录制等）
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] 播放开始");
            }
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] onPlay 钩子执行失败", e);
            }
        }
    }
    
    /**
     * 播放生命周期钩子：暂停播放（v1.2 新增）
     * 从 playing → paused 时调用
     */
    private void onPause() {
        try {
            // 暂停态（通常不需要做事，留扩展）
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] 播放暂停");
            }
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] onPause 钩子执行失败", e);
            }
        }
    }
    
    /**
     * 播放生命周期钩子：停止播放（v1.2 新增）
     * playing → stopped（time reset / end）时调用
     */
    private void onStop() {
        try {
            TimelineCameraController.getInstance().onPlaybackStopped();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] 播放停止");
            }
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] onStop 钩子执行失败", e);
            }
        }
    }

    private void advanceTime(float deltaSeconds) {
        float speed = model.getPlaybackSpeed();
        float newTime = model.getCurrentTime() + deltaSeconds * speed;
        float rangeStart = model.getPlaybackRangeStart();
        float rangeEnd = model.getPlaybackRangeEnd();

        if (newTime < rangeStart) {
            newTime = rangeStart;
        }

        // 处理超界：loop or stop
        if (newTime > rangeEnd) {
            if (model.isLoopEnabled() && rangeEnd > rangeStart) {
                // loop：回到起点继续
                newTime = rangeStart;
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackOrchestrator] 播放循环，回到开始 (时间: {:.2f}s)", newTime);
                }
            } else {
                // stop：停在终点并停止
                newTime = rangeEnd;
                model.setPlaying(false);
                
                // ⭐ v1.2 改进：显式调用 onStop 生命周期钩子
                onStop();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackOrchestrator] 播放结束，停在终点 (时间: {:.2f}s)", newTime);
                }
            }
        }

        // 写入统一真相源
        model.setCurrentTime(newTime);

        // ⭐ 播放采样：季节/天气（保持 TimelinePanel 原逻辑）
        try {
            com.director.timeline.playback.season.TimelineSeasonController.getInstance()
                    .onTimelineTick(newTime);
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] 季节采样失败", e);
            }
        }

        try {
            com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
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
            com.director.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
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
            com.director.timeline.playback.time.TimelineWorldController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] TimelineWorldController 更新失败", e);
            }
        }

        try {
            com.director.ui.timeline.playback.TimelineAudioController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] TimelineAudioController 更新失败", e);
            }
        }

        try {
            com.director.actor.runtime.ActorRuntimeController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] ActorRuntimeController 更新失败", e);
            }
        }

        try {
            com.director.ui.actor.gizmo.ActorGizmoController.getInstance().update();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] ActorGizmoController 更新失败", e);
            }
        }

        try {
            com.director.client.renderer.weather.WeatherRenderer.get().tick();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] WeatherRenderer 更新失败", e);
            }
        }

        try {
            com.director.timeline.playback.weather.WeatherParticleController.getInstance().tick();
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[PlaybackOrchestrator] WeatherParticleController 更新失败", e);
            }
        }

        // v1.1：天气事件 tick（依赖当前时间）
        try {
            com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
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
