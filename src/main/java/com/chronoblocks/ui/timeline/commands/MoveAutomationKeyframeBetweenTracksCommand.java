package com.chronoblocks.ui.timeline.commands;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.audio.AudioEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

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

    public MoveAutomationKeyframeBetweenTracksCommand(
            TimelineModel model,
            TimelineTrack fromTrack,
            TimelineTrack toTrack,
            TimelineSelectable keyframe,
            float oldTime,
            float newTime) {
        super(model, "automation_keyframe_moved_between_tracks");
        this.fromTrack = Objects.requireNonNull(fromTrack, "fromTrack cannot be null");
        this.toTrack = Objects.requireNonNull(toTrack, "toTrack cannot be null");
        this.keyframe = Objects.requireNonNull(keyframe, "keyframe cannot be null");
        this.oldTime = oldTime;
        this.newTime = newTime;
    }

    @Override
    protected void doExecute() {
        apply(fromTrack, toTrack, newTime);
        LOGGER.debug("[Command] MoveAutomationKeyframeBetweenTracks execute {} -> {}", oldTime, newTime);
    }

    @Override
    protected void doUndo() {
        apply(toTrack, fromTrack, oldTime);
        LOGGER.debug("[Command] MoveAutomationKeyframeBetweenTracks undo {} <- {}", newTime, oldTime);
    }

    private void apply(TimelineTrack from, TimelineTrack to, float time) {
        if (keyframe == null || from == null || to == null) {
            LOGGER.warn("[Timeline] MoveAutomationKeyframeBetweenTracks failed: null data");
            return;
        }

        setKeyframeTime(keyframe, time);
        from.removeAutomationKeyframe(keyframe.getId());
        to.addAutomationKeyframe(keyframe);

        if (keyframe instanceof com.chronoblocks.ui.timeline.model.camera.CameraKeyframe) {
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance()
                    .onKeyframeChanged();
        } else if (keyframe instanceof com.chronoblocks.ui.timeline.model.time.TimeKeyframe) {
            com.chronoblocks.ui.timeline.playback.time.TimelineWorldController.getInstance()
                    .rebuildFromActiveTrack();
        } else if (keyframe instanceof com.chronoblocks.ui.timeline.model.season.SeasonKeyframe) {
            com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance()
                    .rebuildFromActiveTrack();
        } else if (keyframe instanceof com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe) {
            com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance()
                    .rebuildFromActiveTrack();
        }
    }

    private void setKeyframeTime(TimelineSelectable keyframe, float time) {
        switch (keyframe) {
            case com.chronoblocks.ui.timeline.model.camera.CameraKeyframe cameraKeyframe ->
                    cameraKeyframe.setTime(time);
            case com.chronoblocks.ui.timeline.model.TimelineMarker marker -> marker.setTime(time);
            case com.chronoblocks.ui.timeline.model.time.TimeKeyframe timeKeyframe ->
                    timeKeyframe.setTime(time);
            case com.chronoblocks.ui.timeline.model.season.SeasonKeyframe seasonKeyframe ->
                    seasonKeyframe.setTime(time);
            case com.chronoblocks.ui.timeline.model.weather.WeatherKeyframe weatherKeyframe ->
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
}
