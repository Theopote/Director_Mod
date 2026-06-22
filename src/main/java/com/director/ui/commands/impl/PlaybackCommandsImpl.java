package com.director.ui.commands.impl;

import com.director.ui.commands.PlaybackCommands;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.playback.PlaybackOrchestrator;
import com.director.ui.timeline.playback.TimelineClipBlockController;
import com.director.ui.timeline.playback.camera.TimelineCameraController;
import com.director.ui.timeline.playback.season.TimelineSeasonController;
import com.director.ui.timeline.playback.weather.TimelineWeatherController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class PlaybackCommandsImpl implements PlaybackCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackCommandsImpl.class);

    private final TimelineModel timelineModel;

    public PlaybackCommandsImpl(TimelineModel timelineModel) {
        this.timelineModel = timelineModel;
    }

    private TimelineModel resolvePlaybackModel() {
        TimelineModel boundModel = PlaybackOrchestrator.get().getBoundModel();
        return boundModel != null ? boundModel : timelineModel;
    }

    private void withPlaybackModel(Consumer<TimelineModel> action, String actionName) {
        TimelineModel model = resolvePlaybackModel();
        if (model == null) {
            LOGGER.warn("[PlaybackCommands] {} skipped: TimelineModel unavailable", actionName);
            return;
        }
        action.accept(model);
    }

    @Override
    public void play() {
        withPlaybackModel(model -> {
            float start = model.getPlaybackRangeStart();
            float end = model.getPlaybackRangeEnd();
            float current = model.getCurrentTime();
            if (current < start || current > end) {
                model.setCurrentTime(start);
            }
            model.setPlaying(true);
        }, "play");
    }

    @Override
    public void pause() {
        withPlaybackModel(model -> model.setPlaying(false), "pause");
    }

    @Override
    public void stop() {
        withPlaybackModel(model -> {
            model.setPlaying(false);
            model.setCurrentTime(model.getPlaybackRangeStart());
        }, "stop");
    }

    @Override
    public void togglePlayPause() {
        TimelineModel model = resolvePlaybackModel();
        if (model != null) {
            boolean wasPlaying = model.isPlaying();
            model.togglePlay();
            boolean isPlaying = model.isPlaying();
            LOGGER.info("[PlaybackCommands] togglePlayPause: {} -> {}", wasPlaying, isPlaying);
        } else {
            LOGGER.warn("[PlaybackCommands] togglePlayPause skipped: TimelineModel unavailable");
        }
    }

    @Override
    public void setInPoint() {
        withPlaybackModel(model -> {
            float current = model.getCurrentTime();
            model.setInPoint(current);
            LOGGER.info("[PlaybackCommands] 设置入点: {:.2f}s", model.getInPoint());
        }, "setInPoint");
    }

    @Override
    public void setOutPoint() {
        withPlaybackModel(model -> {
            float current = model.getCurrentTime();
            model.setOutPoint(current);
            LOGGER.info("[PlaybackCommands] 设置出点: {:.2f}s", model.getOutPoint());
        }, "setOutPoint");
    }

    @Override
    public void clearInOut() {
        withPlaybackModel(model -> {
            model.clearInOut();
            LOGGER.info("[PlaybackCommands] 已清除入/出点");
        }, "clearInOut");
    }

    @Override
    public boolean hasInPoint() {
        TimelineModel model = resolvePlaybackModel();
        return model != null && model.hasInPoint();
    }

    @Override
    public boolean hasOutPoint() {
        TimelineModel model = resolvePlaybackModel();
        return model != null && model.hasOutPoint();
    }

    @Override
    public float getInPoint() {
        TimelineModel model = resolvePlaybackModel();
        return model != null ? model.getInPoint() : 0.0f;
    }

    @Override
    public float getOutPoint() {
        TimelineModel model = resolvePlaybackModel();
        return model != null ? model.getOutPoint() : getTotalDuration();
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        withPlaybackModel(model -> model.setPlaybackSpeed(speed), "setPlaybackSpeed");
    }

    @Override
    public void toggleLoop() {
        withPlaybackModel(model -> model.setLoopEnabled(!model.isLoopEnabled()), "toggleLoop");
    }

    @Override
    public void goToStart() {
        withPlaybackModel(model -> model.setCurrentTime(model.getPlaybackRangeStart()), "goToStart");
    }

    @Override
    public void goToEnd() {
        withPlaybackModel(model -> model.setCurrentTime(model.getPlaybackRangeEnd()), "goToEnd");
    }

    @Override
    public boolean isPlaying() {
        TimelineModel model = resolvePlaybackModel();
        return model != null && model.isPlaying();
    }

    @Override
    public boolean isLooping() {
        TimelineModel model = resolvePlaybackModel();
        return model != null && model.isLoopEnabled();
    }

    @Override
    public float getPlaybackSpeed() {
        TimelineModel model = resolvePlaybackModel();
        return model != null ? model.getPlaybackSpeed() : 1.0f;
    }

    @Override
    public float getCurrentTime() {
        TimelineModel model = resolvePlaybackModel();
        return model != null ? model.getCurrentTime() : 0.0f;
    }

    @Override
    public float getTotalDuration() {
        TimelineModel model = resolvePlaybackModel();
        return model != null ? model.getDuration() : 0.0f;
    }

    @Override
    public void seek(float time) {
        TimelineModel model = resolvePlaybackModel();
        if (model != null) {
            float clampedTime = Math.max(0f, Math.min(model.getDuration(), time));
            model.setCurrentTime(clampedTime);

            try {
                TimelineSeasonController.getInstance().onTimelineTick(clampedTime);
            } catch (Exception e) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackCommands] 季节采样失败", e);
                }
            }

            try {
                TimelineWeatherController.getInstance().previewAt(clampedTime);
            } catch (Exception e) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackCommands] 天气采样失败", e);
                }
            }

            try {
                TimelineCameraController.getInstance().jumpToTimeAsPlayer(clampedTime);
            } catch (Exception e) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackCommands] 摄像机跳转失败", e);
                }
            }

            try {
                TimelineClipBlockController.getInstance().update();
            } catch (Exception e) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[PlaybackCommands] 片段方块更新失败", e);
                }
            }

            LOGGER.debug("[PlaybackCommands] Seek to {:.2f}s", clampedTime);
        } else {
            LOGGER.warn("[PlaybackCommands] seek skipped: TimelineModel unavailable");
        }
    }

    @Override
    public void tick(float deltaTime) {
        if (timelineModel != null) {
            PlaybackOrchestrator.get().bindModel(timelineModel);
            PlaybackOrchestrator.get().tick(deltaTime);
        }
    }
}
