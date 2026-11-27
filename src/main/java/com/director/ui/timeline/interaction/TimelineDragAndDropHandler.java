package com.director.ui.timeline.interaction;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.commands.ContainerDropCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 处理时间轴上的拖放逻辑：容器拖入轨道、时间吸附、初始时长估算。
 */
public class TimelineDragAndDropHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineDragAndDropHandler.class);

    private final TimelineModel model;
    private final Runnable onDataChangedCallback;

    public TimelineDragAndDropHandler(TimelineModel model, Runnable onDataChangedCallback) {
        this.model = model;
        this.onDataChangedCallback = onDataChangedCallback;
    }

    /**
     * 处理拖放目标区域。
     *
     * @return 目标轨道索引（用于高亮显示）-1 表示无有效目标。
     */
    public int handleDragDropTarget(float trackRegionTop, float scrollY, float contentX,
                                   com.director.ui.timeline.render.TimelineRenderMath math) {
        int dropTargetIndex = -1;

        if (ImGui.beginDragDropTarget()) {
            try {
                float mouseX = ImGui.getMousePosX();
                int targetTrackIndex = math.headerAtContentY(ImGui.getMousePosY() - trackRegionTop + scrollY);
                boolean isValidTrack = checkTrackValidity(targetTrackIndex);
                dropTargetIndex = isValidTrack ? targetTrackIndex : -1;

                Object payload = ImGui.acceptDragDropPayload("DIRECTOR_CONTAINER");
                if (payload != null) {
                    String containerId = (String) payload;
                    dropTargetIndex = -1;
                    if (isValidTrack) {
                        handleContainerDropToTrack(containerId, targetTrackIndex, mouseX, contentX);
                    } else {
                        LOGGER.warn("[DragDrop] Reject drop: target track is unavailable for containers");
                    }
                }

                ImGui.acceptDragDropPayload("DIRECTOR_ITEM");
            } finally {
                ImGui.endDragDropTarget();
            }
        }

        return dropTargetIndex;
    }

    private boolean checkTrackValidity(int index) {
        if (model.isPlaying() || model.isScrubbing() || index < 0 || index >= model.getTrackCount()) {
            return false;
        }
        TimelineTrack track = model.getTrackAt(index);
        return track != null
                && track.getType() == TrackType.CONTAINER
                && !track.isLocked();
    }

    private void handleContainerDropToTrack(String payload, int trackIndex, float mouseX, float contentX) {
        try {
            List<UUID> ids = parsePayload(payload);
            if (ids.isEmpty()) {
                return;
            }
            TimelineTrack targetTrack = model.getTrackAt(trackIndex);
            if (targetTrack == null || targetTrack.getType() != TrackType.CONTAINER
                    || targetTrack.isLocked() || model.isPlaying() || model.isScrubbing()) {
                return;
            }

            List<Container> containers = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                Container container = DirectorServices.get().containers().getContainer(id);
                if (container == null || container.getType() == ContainerType.GROUP || container.isLocked()) {
                    LOGGER.warn("[DragDrop] Reject drop: container is missing, grouped or locked: {}", id);
                    return;
                }
                containers.add(container);
            }

            float dropTime = model.screenToTime(mouseX, contentX);
            TimelineCommandBridge.execute(new ContainerDropCommand(
                    model, targetTrack, containers, dropTime, onDataChangedCallback));
        } catch (IllegalArgumentException | IllegalStateException e) {
            LOGGER.warn("[DragDrop] Reject container drop: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("[DragDrop] Failed to drop containers", e);
        }
    }

    private static List<UUID> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (String value : payload.split(",")) {
            UUID id = UUID.fromString(value.trim());
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Duplicate container in drop payload");
            }
            ids.add(id);
        }
        return ids;
    }
}
