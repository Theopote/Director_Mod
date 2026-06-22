package com.director.ui.timeline.interaction;

import com.director.ui.timeline.commands.DeleteTrackCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.audio.AudioTrack;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 处理轨道相关的 CRUD 和菜单渲染。
 */
public class TimelineTrackController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineTrackController.class);

    private final TimelineModel model;
    private final TimelineCommandHistory commandHistory;
    private final Runnable onDataChangedCallback;

    public TimelineTrackController(TimelineModel model, TimelineCommandHistory history, Runnable callback) {
        this.model = model;
        this.commandHistory = history;
        this.onDataChangedCallback = callback;
    }

    /**
     * 打开新建轨道菜单。
     */
    public void openMenu() {
        ImGui.openPopup("新建轨道##AddTrackMenu");
    }

    /**
     * 渲染新建轨道弹出菜单。
     */
    public void renderAddTrackMenu() {
        if (ImGui.beginPopup("新建轨道##AddTrackMenu")) {
            ImGui.text("选择轨道类型");
            ImGui.separator();

            for (TrackType type : TrackType.values()) {
                if (ImGui.menuItem(type.getIconAndName())) {
                    createNewTrack(type);
                }

                // 显示轨道类型说明
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text(type.getDescription());
                    ImGui.endTooltip();
                }
            }

            ImGui.endPopup();
        }
    }

    /**
     * 创建新轨道。
     * 
     * <p>⭐ v3.5：如果是时间轨道且是第一条，自动激活
     * <p>⭐ v1.0：如果是音频轨道，使用 AudioTrack 类
     */
    private void createNewTrack(TrackType type) {
        long sameTypeCount = model.getTracks().stream()
                .filter(t -> t.getType() == type)
                .count();

        String trackName = type.getDisplayName() + " " + (sameTypeCount + 1);
        TimelineTrack newTrack;
        
        // 如果是音频轨道，使用 AudioTrack 类
        if (type == TrackType.AUDIO) {
            newTrack = new AudioTrack(trackName);
        } else {
            newTrack = new TimelineTrack(trackName, type);
            if (type == TrackType.ACTOR) {
                newTrack.setActorTrackPayload(new com.director.ui.timeline.model.entity.ActorTrack(trackName));
            }
        }
        
        model.addTrack(newTrack);

        // ⭐ v3.5：如果是时间轨道且是第一条（创建后只有一条），自动激活
        if (type == TrackType.TIME && sameTypeCount == 0) {
            com.director.timeline.playback.time.TimelineWorldController.getInstance()
                    .setActiveTimeTrack(newTrack);
            LOGGER.info("[TrackController] 创建第一条时间轨道，自动激活: {}", trackName);
        }
        
        // ⭐ v1.0：如果是季节轨道且是第一条，自动激活
        if (type == TrackType.SEASON && sameTypeCount == 0) {
            com.director.timeline.playback.season.TimelineSeasonController.getInstance()
                    .setActiveSeasonTrack(newTrack);
            LOGGER.info("[TrackController] 创建第一条季节轨道，自动激活: {}", trackName);
        }
        
        // ⭐ v1.0：如果是天气轨道且是第一条，自动激活
        if (type == TrackType.WEATHER && sameTypeCount == 0) {
            com.director.timeline.playback.weather.TimelineWeatherController.getInstance()
                    .bindActiveTrack(newTrack);
            LOGGER.info("[TrackController] 创建第一条天气轨道，自动激活: {}", trackName);
        }
        
        // ⭐ v1.0：如果是演员轨道且是第一条，自动激活
        if (type == TrackType.ACTOR && sameTypeCount == 0) {
            com.director.timeline.playback.entity.TimelineActorController.getInstance()
                    .bindActiveTrack(newTrack);
            LOGGER.info("[TrackController] 创建第一条演员轨道，自动激活: {}", trackName);
        }

        onDataChangedCallback.run();
        LOGGER.info("[TrackController] 创建新轨道 {} (类型: {}, 同类型第 {} 个)",
                trackName, type.getDisplayName(), sameTypeCount + 1);
    }

    /**
    * 删除轨道（支持撤销）。
    */
    public void deleteTrack(TimelineTrack track) {
        if (track == null) {
            return;
        }

        if (!track.getClips().isEmpty()) {
            LOGGER.warn("[TrackController] 轨道 '{}' 包含 {} 个片段，确认删除",
                    track.getName(), track.getClips().size());
        }

        DeleteTrackCommand cmd = new DeleteTrackCommand(model, track, model.getTracks());
        commandHistory.executeCommand(cmd);

        onDataChangedCallback.run();
        LOGGER.info("[TrackController] 已删除轨道 {} (类型: {})",
                track.getName(), track.getType().getDisplayName());
    }

    /**
     * 上移轨道（交换索引）。
     */
    public void moveTrackUp(TimelineTrack track) {
        if (track == null) {
            return;
        }

        List<TimelineTrack> tracks = model.getTracks();
        int index = tracks.indexOf(track);

        if (index > 0) {
            Collections.swap(tracks, index, index - 1);
            onDataChangedCallback.run();

            LOGGER.info("[TrackController] 上移轨道: {} (索引 {} -> {})",
                    track.getName(), index, index - 1);
        }
    }

    /**
     * 下移轨道（交换索引）。
     */
    public void moveTrackDown(TimelineTrack track) {
        if (track == null) {
            return;
        }

        List<TimelineTrack> tracks = model.getTracks();
        int index = tracks.indexOf(track);

        if (index >= 0 && index < tracks.size() - 1) {
            Collections.swap(tracks, index, index + 1);
            onDataChangedCallback.run();

            LOGGER.info("[TrackController] 下移轨道: {} (索引 {} -> {})",
                    track.getName(), index, index + 1);
        }
    }

    /**
     * 复制轨道（包括所有片段）。
     */
    public void duplicateTrack(TimelineTrack track) {
        if (track == null) {
            return;
        }

        long sameTypeCount = model.getTracks().stream()
                .filter(t -> t.getType() == track.getType())
                .count();
        String newName = track.getType().getDisplayName() + " " + (sameTypeCount + 1);

        TimelineTrack newTrack = new TimelineTrack(newName, track.getType());

        for (TimelineClip clip : track.getClips()) {
            if (clip.getBoundContainerId() != null
                && !(clip instanceof com.director.ui.timeline.model.audio.AudioTimelineClip)) {
                LOGGER.info("[TrackController] 复制轨道时跳过容器片段: {}", clip.getLabel());
                continue;
            }
            TimelineClip newClip = new TimelineClip(clip);
            newClip.setLabel(clip.getLabel() + " (副本)");
            newClip.offsetTime(0.5f);
            newTrack.addClip(newClip);
        }

        model.addTrack(newTrack);
        onDataChangedCallback.run();

        LOGGER.info("[TrackController] 已复制轨道 {} -> {}", track.getName(), newName);
    }
}
