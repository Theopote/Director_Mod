package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.audio.AudioEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Move automation keyframe between tracks (supports undo/redo).
 */
public class MoveAutomationKeyframeBetweenTracksCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoveAutomationKeyframeBetweenTracksCommand.class);

    private final TimelineTrack fromTrack;
    private final TimelineTrack toTrack;
    private final TimelineSelectable keyframe;
    private final float oldTime;
    private final float newTime;
    private boolean moved;

    public MoveAutomationKeyframeBetweenTracksCommand(
            TimelineModel model,
            TimelineTrack fromTrack,
            TimelineTrack toTrack,
            TimelineSelectable keyframe,
            float oldTime,
            float newTime) {
        super(model, "automation_keyframe_moved_between_tracks");
        this.fromTrack = fromTrack;
        this.toTrack = toTrack;
        this.keyframe = keyframe;
        this.oldTime = oldTime;
        this.newTime = newTime;
    }

    @Override
    protected void doExecute() {
        if (fromTrack == null || toTrack == null || keyframe == null) {
            setResult(TimelineCommand.Result.REJECTED);
            return;
        }
        if (apply(fromTrack, toTrack, newTime, false)) {
            moved = true;
            LOGGER.debug("[Command] MoveAutomationKeyframeBetweenTracks execute {} -> {}", oldTime, newTime);
        }
    }

    @Override
    protected void doUndo() {
        apply(toTrack, fromTrack, oldTime, true);
        moved = false;
        LOGGER.debug("[Command] MoveAutomationKeyframeBetweenTracks undo {} <- {}", newTime, oldTime);
    }

    private boolean apply(TimelineTrack from, TimelineTrack to, float time, boolean undo) {
        if (!from.getAutomationKeyframes().contains(keyframe)) {
            if (undo) {
                throw new IllegalStateException("Automation keyframe is missing: " + keyframe.getId());
            }
            setResult(TimelineCommand.Result.NO_CHANGE);
            return false;
        }
        if (from == to && Float.compare(keyframe.getStartTime(), time) == 0) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return false;
        }
        if (to.getAutomationKeyframes().stream().anyMatch(k -> k != keyframe
                && Float.compare(k.getStartTime(), time) == 0)) {
            setResult(TimelineCommand.Result.NO_CHANGE);
            return false;
        }
        float previousTime = keyframe.getStartTime();
        if (!from.removeAutomationKeyframe(keyframe.getId())) {
            throw new IllegalStateException("Failed to remove automation keyframe: " + keyframe.getId());
        }
        try {
            setKeyframeTime(keyframe, time);
            to.addAutomationKeyframe(keyframe);
        } catch (RuntimeException | Error e) {
            setKeyframeTime(keyframe, previousTime);
            from.addAutomationKeyframe(keyframe);
            throw e;
        }
        notifyPlayback();
        return true;
    }

    private void notifyPlayback() {
        if (keyframe instanceof com.director.timeline.model.camera.CameraKeyframe) {
            com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                    .onKeyframeChanged();
        } else if (keyframe instanceof com.director.timeline.model.time.TimeKeyframe) {
            com.director.timeline.playback.time.TimelineWorldController.getInstance()
                    .rebuildFromActiveTrack();
        } else if (keyframe instanceof com.director.timeline.model.season.SeasonKeyframe) {
            com.director.timeline.playback.season.TimelineSeasonController.getInstance()
                    .rebuildFromActiveTrack();
        } else if (keyframe instanceof com.director.timeline.model.weather.WeatherKeyframe) {
            com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
                    .rebuildFromActiveTrack();
        }
    }

    private void setKeyframeTime(TimelineSelectable keyframe, float time) {
        switch (keyframe) {
            case com.director.timeline.model.camera.CameraKeyframe cameraKeyframe ->
                    cameraKeyframe.setTime(time);
            case com.director.timeline.model.TimelineMarker marker -> marker.setTime(time);
            case com.director.timeline.model.time.TimeKeyframe timeKeyframe ->
                    timeKeyframe.setTime(time);
            case com.director.timeline.model.season.SeasonKeyframe seasonKeyframe ->
                    seasonKeyframe.setTime(time);
            case com.director.timeline.model.weather.WeatherKeyframe weatherKeyframe ->
                    weatherKeyframe.setTime(time);
            case AudioEvent audioEvent -> audioEvent.setTime(time);
            default -> LOGGER.warn("[Timeline] Unsupported keyframe type {}", keyframe.getClass().getSimpleName());
        }
    }

    @Override
    public String getDescription() {
        return keyframe != null
                ? "move_automation_keyframe_between_tracks " + keyframe.getName()
                : "move_automation_keyframe_between_tracks";
    }

    @Override
    public boolean canUndo() {
        return moved;
    }
}
