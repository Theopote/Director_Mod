package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.timeline.playback.camera.TimelineCameraController;
import com.director.timeline.playback.entity.TimelineActorController;
import com.director.timeline.playback.season.TimelineSeasonController;
import com.director.timeline.playback.time.TimelineWorldController;
import com.director.timeline.playback.weather.TimelineWeatherController;

/**
 * 添加轨道命令（支持撤销）。
 */
public class AddTrackCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddTrackCommand.class);

    private final TimelineTrack track;
    private boolean added = false;
    private TimelineTrack previousActiveTrack;
    private boolean previousWeatherEnabled;
    private boolean previousEntityEnabled;
    private boolean shouldAutoActivate;

    public AddTrackCommand(TimelineModel model, TimelineTrack track) {
        super(model, "track_added");
        this.track = track;
    }

    @Override
    protected void doExecute() {
        if (track != null && model != null) {
            capturePreviousActiveState();
            shouldAutoActivate = shouldAutoActivateTrack(track);
            model.addTrack(track);
            added = true;
            if (shouldAutoActivate) {
                activateTrack(track);
            }
            LOGGER.debug("[Timeline] 添加轨道: {}", track.getName());
        }
    }

    @Override
    protected void doUndo() {
        if (added && track != null && model != null) {
            model.removeTrack(track);
            restorePreviousActiveState();
            added = false;
            LOGGER.debug("[Timeline] 撤销添加轨道: {}", track.getName());
        }
    }

    private void capturePreviousActiveState() {
        TrackType type = track.getType();
        previousActiveTrack = switch (type) {
            case CAMERA -> TimelineCameraController.getInstance().getActiveCameraTrack();
            case TIME -> TimelineWorldController.getInstance().getActiveTimeTrack();
            case SEASON -> TimelineSeasonController.getInstance().getActiveSeasonTrack();
            case WEATHER -> TimelineWeatherController.getInstance().getActiveWeatherTrack();
            case ACTOR -> TimelineActorController.getInstance().getActiveActorTrack();
            default -> null;
        };

        previousWeatherEnabled = TimelineWeatherController.getInstance().isEnabled();
        previousEntityEnabled = TimelineActorController.getInstance().isEnabled();
    }

    private boolean shouldAutoActivateTrack(TimelineTrack track) {
        TrackType type = track.getType();
        if (type != TrackType.CAMERA
                && type != TrackType.TIME
                && type != TrackType.SEASON
                && type != TrackType.WEATHER
                && type != TrackType.ACTOR) {
            return false;
        }

        long sameTypeCountBeforeAdd = model.getTracks().stream()
                .filter(t -> t.getType() == type)
                .count();
        return sameTypeCountBeforeAdd == 0;
    }

    private void activateTrack(TimelineTrack track) {
        switch (track.getType()) {
            case CAMERA -> TimelineCameraController.getInstance().setActiveCameraTrack(track);
            case TIME -> TimelineWorldController.getInstance().setActiveTimeTrack(track);
            case SEASON -> TimelineSeasonController.getInstance().setActiveSeasonTrack(track);
            case WEATHER -> TimelineWeatherController.getInstance().bindActiveTrack(track);
            case ACTOR -> TimelineActorController.getInstance().bindActiveTrack(track);
            default -> { /* no-op */ }
        }
    }

    private void restorePreviousActiveState() {
        if (!shouldAutoActivate) {
            return;
        }

        switch (track.getType()) {
            case CAMERA -> TimelineCameraController.getInstance().setActiveCameraTrack(previousActiveTrack);
            case TIME -> TimelineWorldController.getInstance().setActiveTimeTrack(previousActiveTrack);
            case SEASON -> TimelineSeasonController.getInstance().setActiveSeasonTrack(previousActiveTrack);
            case WEATHER -> {
                TimelineWeatherController controller = TimelineWeatherController.getInstance();
                controller.bindActiveTrack(previousActiveTrack);
                controller.setEnabled(previousWeatherEnabled);
            }
            case ACTOR -> {
                TimelineActorController controller = TimelineActorController.getInstance();
                controller.bindActiveTrack(previousActiveTrack);
                controller.setEnabled(previousEntityEnabled);
            }
            default -> { /* no-op */ }
        }
    }

    @Override
    public String getDescription() {
        return track != null ? "添加轨道: " + track.getName() : "添加轨道";
    }
}

