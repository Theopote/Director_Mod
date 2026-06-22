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
            onPlay();
        } else if (!playing && lastPlaying) {
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
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[PlaybackOrchestrator] 播放开始");
        }
    }
    
    /**
     * 播放生命周期钩子：暂停播放（v1.2 新增）
     * 从 playing → paused 时调用
     */
    private void onPause() {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[PlaybackOrchestrator] 播放暂停");
        }
    }
    
    /**
     * 播放生命周期钩子：停止播放（v1.2 新增）
     * playing → stopped（time reset / end）时调用
     */
    private void onStop() {
        runSubsystem("onStop", () -> TimelineCameraController.getInstance().onPlaybackStopped());
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[PlaybackOrchestrator] 播放停止");
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
                newTime = rangeStart;
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackOrchestrator] 播放循环，回到开始 (时间: {:.2f}s)", newTime);
                }
            } else {
                newTime = rangeEnd;
                model.setPlaying(false);
                onStop();

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackOrchestrator] 播放结束，停在终点 (时间: {:.2f}s)", newTime);
                }
            }
        }

        model.setCurrentTime(newTime);

        float sampledTime = newTime;
        runSubsystem("季节采样", () -> com.director.timeline.playback.season.TimelineSeasonController
                .getInstance().onTimelineTick(sampledTime));
        runSubsystem("天气采样", () -> com.director.timeline.playback.weather.TimelineWeatherController
                .getInstance().updateForPlayback(sampledTime));
    }

    /**
     * 在指定时间点采样整条时间线（视频导出 / 离线抓帧）。
     */
    public void sampleAt(float timeSeconds) {
        com.director.export.video.TimelineExportSampling.sampleWorldAt(timeSeconds);
    }

    private void driveSubsystems(float deltaSeconds) {
        float renderDelta = getRenderDeltaFallback(deltaSeconds);

        runSubsystem("TimelineClipBlockController", () ->
                com.director.ui.timeline.playback.TimelineClipBlockController.getInstance().update());
        runSubsystem("TimelineCameraController", () ->
                TimelineCameraController.getInstance().updateOwnerAndSample(renderDelta));
        runSubsystem("TimelineWorldController", () ->
                com.director.timeline.playback.time.TimelineWorldController.getInstance().update());
        runSubsystem("TimelineAudioController", () ->
                com.director.ui.timeline.playback.TimelineAudioController.getInstance().update());
        runSubsystem("ActorRuntimeController", () ->
                com.director.actor.runtime.ActorRuntimeController.getInstance().update());
        runSubsystem("ActorGizmoController", () ->
                com.director.ui.actor.gizmo.ActorGizmoController.getInstance().update());
        runSubsystem("WeatherRenderer", () ->
                com.director.client.renderer.weather.WeatherRenderer.get().tick());
        runSubsystem("WeatherParticleController", () ->
                com.director.timeline.playback.weather.WeatherParticleController.getInstance().tick());

        float currentTime = model.getCurrentTime();
        runSubsystem("TimelineWeatherController.tickEvents", () ->
                com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
                        .tickEvents(currentTime));
    }

    private void runSubsystem(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            LOGGER.warn("[PlaybackOrchestrator] {} 失败", name, e);
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
