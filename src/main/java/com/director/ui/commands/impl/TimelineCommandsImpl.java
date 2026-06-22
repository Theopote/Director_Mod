package com.director.ui.commands.impl;

import com.director.ui.commands.TimelineCommands;
import com.director.ui.dialogs.DialogManager;
import com.director.ui.panels.TimelinePanel;
import com.director.ui.timeline.commands.AddTrackCommand;
import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.audio.AudioTrack;
import com.director.ui.timeline.model.camera.path.CameraPathType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TimelineCommandsImpl implements TimelineCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCommandsImpl.class);

    private final TimelinePanel timelinePanel;
    private final TimelineModel timelineModel;
    private final DialogManager dialogManager;

    public TimelineCommandsImpl(TimelinePanel timelinePanel, TimelineModel timelineModel, DialogManager dialogManager) {
        this.timelinePanel = timelinePanel;
        this.timelineModel = timelineModel;
        this.dialogManager = dialogManager;
    }

    @Override
    public void openTimelineSettings() {
        dialogManager.showTimelineSettingsDialog();
    }

    @Override
    public void zoomToFit() {
        if (timelinePanel != null) {
            timelinePanel.zoomToFit();
        }
    }

    @Override
    public void scrollToPlayhead() {
        if (timelinePanel != null) {
            timelinePanel.scrollToPlayhead();
        }
    }

    @Override
    public void addCameraTrack(CameraPathType type) {
        if (timelineModel == null) {
            LOGGER.warn("[Commands] TimelineModel 不可用，无法添加摄像机轨道");
            return;
        }

        try {
            long sameTypeCount = timelineModel.getTracks().stream()
                    .filter(t -> t.getType() == TrackType.CAMERA)
                    .count();

            String trackName = "摄像机轨道 " + (sameTypeCount + 1);
            TimelineTrack newTrack = new TimelineTrack(trackName, TrackType.CAMERA);

            addTrackViaCommand(newTrack);
            LOGGER.info("[Commands] 已添加摄像机轨道: {}", trackName);
        } catch (Exception e) {
            LOGGER.error("[Commands] 添加摄像机轨道失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void addActorTrack() {
        if (timelineModel == null) {
            LOGGER.warn("[Commands] TimelineModel 不可用，无法添加演员轨道");
            return;
        }

        try {
            long sameTypeCount = timelineModel.getTracks().stream()
                    .filter(t -> t.getType() == TrackType.ACTOR)
                    .count();

            String trackName = "演员轨道 " + (sameTypeCount + 1);
            TimelineTrack newTrack = new TimelineTrack(trackName, TrackType.ACTOR);

            addTrackViaCommand(newTrack);
            LOGGER.info("[Commands] 已添加演员轨道: {}", trackName);
        } catch (Exception e) {
            LOGGER.error("[Commands] 添加演员轨道失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void addWeatherTrack() {
        if (timelineModel == null) {
            LOGGER.warn("[Commands] TimelineModel 不可用，无法添加天气轨道");
            return;
        }

        try {
            long sameTypeCount = timelineModel.getTracks().stream()
                    .filter(t -> t.getType() == TrackType.WEATHER)
                    .count();

            String trackName = "天气轨道 " + (sameTypeCount + 1);
            TimelineTrack newTrack = new TimelineTrack(trackName, TrackType.WEATHER);

            addTrackViaCommand(newTrack);
            LOGGER.info("[Commands] 已添加天气轨道: {}", trackName);
        } catch (Exception e) {
            LOGGER.error("[Commands] 添加天气轨道失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void addAudioTrack() {
        if (timelineModel == null) {
            LOGGER.warn("[Commands] TimelineModel 不可用，无法添加音频轨道");
            return;
        }

        try {
            long sameTypeCount = timelineModel.getTracks().stream()
                    .filter(t -> t.getType() == TrackType.AUDIO)
                    .count();

            String trackName = "音频轨道 " + (sameTypeCount + 1);
            AudioTrack newTrack = new AudioTrack(trackName);

            addTrackViaCommand(newTrack);
            LOGGER.info("[Commands] 已添加音频轨道: {}", trackName);
        } catch (Exception e) {
            LOGGER.error("[Commands] 添加音频轨道失败: {}", e.getMessage(), e);
        }
    }

    private void addTrackViaCommand(TimelineTrack track) {
        AddTrackCommand command = new AddTrackCommand(timelineModel, track);
        if (timelinePanel != null) {
            timelinePanel.executeCommand(command);
        } else {
            command.execute();
        }
    }

    @Override
    public void undo() {
        if (timelinePanel != null) {
            timelinePanel.undo();
            LOGGER.debug("[TimelineCommands] 撤销操作");
        } else {
            LOGGER.warn("[TimelineCommands] TimelinePanel 不可用，无法撤销");
        }
    }

    @Override
    public void redo() {
        if (timelinePanel != null) {
            timelinePanel.redo();
            LOGGER.debug("[TimelineCommands] 重做操作");
        } else {
            LOGGER.warn("[TimelineCommands] TimelinePanel 不可用，无法重做");
        }
    }

    @Override
    public void deleteSelected() {
        if (timelinePanel != null) {
            timelinePanel.deleteSelected();
            LOGGER.debug("[TimelineCommands] 删除选中项");
        } else {
            LOGGER.warn("[TimelineCommands] TimelinePanel 不可用，无法删除");
        }
    }
}
