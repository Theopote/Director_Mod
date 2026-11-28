package com.director.core.container.commands;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 释放容器命令，支持撤销/重做。
 * <p>删除前统一预检并捕获层级、选择、active、时间线片段、音频和可见性状态。
 */
public class ReleaseContainerCommand implements ContainerCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseContainerCommand.class);

    private final ContainerManager manager;
    private final UUID containerId;

    private final List<Container> releasedTree = new ArrayList<>();
    private final List<Container> hierarchyParents = new ArrayList<>();
    private final List<Integer> hierarchyIndices = new ArrayList<>();
    private final List<ContainerStateSnapshot> stateSnapshots = new ArrayList<>();
    private final List<ClipSnapshot> removedClips = new ArrayList<>();
    private final List<Container> selectionSnapshot = new ArrayList<>();

    private Container container;
    private Container primarySelection;
    private Container lastSelection;
    private UUID previousActiveId;
    private TimelineClip previousSelectedClip;
    private boolean captured;
    private boolean executed;

    public ReleaseContainerCommand(ContainerManager manager, Container container) {
        this.manager = manager;
        this.container = container;
        this.containerId = container.getId();
    }

    @Override
    public void execute() {
        Container current = manager.getContainer(containerId);
        if (current == null || !manager.requireEditableTree(current, "释放")) {
            return;
        }
        if (!captured) {
            captureSnapshot(current);
        } else if (current != container) {
            LOGGER.warn("[ReleaseCommand] 拒绝释放：容器 {} 已被其它实例替换", containerId);
            return;
        }
        if (!captured) {
            return;
        }

        // 管理器统一清理 active/选择、时间线片段和可见性，避免命令先产生部分副作用。
        manager.removeContainer(current);
        executed = true;
    }

    @Override
    public void undo() {
        if (!captured || !executed) {
            return;
        }
        for (ContainerStateSnapshot snapshot : stateSnapshots) {
            if (snapshot.container.isLocked()) {
                LOGGER.warn("[ReleaseCommand] 撤销释放失败：容器 {} 已被锁定", snapshot.container.getName());
                return;
            }
        }

        restoreHierarchy();
        restoreContainerStates();
        restoreTimelineClips();
        manager.restoreSelection(selectionSnapshot, primarySelection, lastSelection);
        if (previousActiveId != null && manager.getActive() == null
                && manager.getContainer(previousActiveId) != null) {
            manager.setActive(previousActiveId);
        }
        executed = false;
    }

    @Override
    public String getDescription() {
        return "释放容器: " + (container != null ? container.getName() : containerId);
    }

    private void captureSnapshot(Container current) {
        container = current;
        Container active = manager.getActive();
        previousActiveId = active != null ? active.getId() : null;
        primarySelection = manager.getSelectedContainer();
        lastSelection = manager.getLastSelectedContainer();
        selectionSnapshot.clear();
        selectionSnapshot.addAll(manager.getSelectedContainers());

        releasedTree.clear();
        hierarchyParents.clear();
        hierarchyIndices.clear();
        stateSnapshots.clear();
        removedClips.clear();
        collectTree(current);
        captureTimelineClips();
        captured = true;
    }

    private void collectTree(Container current) {
        if (current == null) {
            return;
        }
        Container parent = current.getParent();
        int index = parent != null
            ? parent.indexOfChild(current)
            : manager.getRootContainersView().indexOf(current);
        releasedTree.add(current);
        hierarchyParents.add(parent);
        hierarchyIndices.add(index);
        stateSnapshots.add(new ContainerStateSnapshot(current, current.getState()));
        for (Container child : current.getChildrenView()) {
            collectTree(child);
        }
    }

    private void captureTimelineClips() {
        ContainerTimelineService timelineService = DirectorServices.get().containerTimeline();
        TimelineModel timelineModel = timelineService.getTimelineModel();
        if (timelineModel == null) {
            return;
        }
        previousSelectedClip = timelineModel.getSelectedClip();

        for (Container released : releasedTree) {
            for (TimelineClip clip : timelineService.findClipsForContainer(released.getId())) {
                TimelineTrack track = timelineModel.findTrackForClip(clip);
                if (track != null) {
                    removedClips.add(new ClipSnapshot(
                        track, clip, clip.snapshot(), timelineService.captureAudioRemovalState(clip)));
                }
            }
        }
    }

    private void restoreHierarchy() {
        List<Container> items = new ArrayList<>();
        List<Container> parents = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < releasedTree.size(); i++) {
            Container item = releasedTree.get(i);
            if (manager.isRegistered(item)) {
                continue;
            }
            Container parent = hierarchyParents.get(i);
            if (parent != null && !manager.isRegistered(parent)) {
                parent = null;
            }
            items.add(item);
            parents.add(parent);
            indices.add(hierarchyIndices.get(i));
        }
        if (!items.isEmpty()) {
            manager.restoreContainersAtPositions(items, parents, indices);
        }
    }

    private void restoreContainerStates() {
        for (ContainerStateSnapshot snapshot : stateSnapshots) {
            if (manager.isRegistered(snapshot.container)) {
                snapshot.container.setState(snapshot.state);
            }
        }
    }

    private void restoreTimelineClips() {
        ContainerTimelineService timelineService = DirectorServices.get().containerTimeline();
        TimelineModel timelineModel = timelineService.getTimelineModel();
        if (timelineModel == null) {
            return;
        }

        boolean restoredAny = false;
        for (ClipSnapshot snapshot : removedClips) {
            if (timelineModel.findTrackForClip(snapshot.clip) != null
                    || timelineModel.getAllTracks().stream().noneMatch(track -> track == snapshot.track)) {
                continue;
            }
            snapshot.clip.restoreStateFrom(snapshot.state);
            if (!snapshot.track.addClip(snapshot.clip)) {
                LOGGER.warn("[ReleaseCommand] 片段 {} 恢复被轨道拒绝", snapshot.clip.getLabel());
                continue;
            }
            restoredAny = true;
            try {
                snapshot.audioRestore.run();
            } catch (Exception ex) {
                snapshot.track.removeClip(snapshot.clip);
                LOGGER.warn("[ReleaseCommand] 恢复片段 {} 的音频失败: {}",
                    snapshot.clip.getLabel(), ex.getMessage());
            }
        }
        if (restoredAny) {
            timelineService.invalidateAfterClipRestore();
        }
        if (previousSelectedClip != null && timelineModel.getSelectedClip() == null
                && timelineModel.findTrackForClip(previousSelectedClip) != null) {
            timelineModel.setSelectedClip(previousSelectedClip);
        }
    }

    private static final class ContainerStateSnapshot {
        private final Container container;
        private final ContainerState state;

        private ContainerStateSnapshot(Container container, ContainerState state) {
            this.container = container;
            this.state = state;
        }
    }

    private static final class ClipSnapshot {
        private final TimelineTrack track;
        private final TimelineClip clip;
        private final TimelineClip state;
        private final Runnable audioRestore;

        private ClipSnapshot(TimelineTrack track, TimelineClip clip, TimelineClip state, Runnable audioRestore) {
            this.track = track;
            this.clip = clip;
            this.state = state;
            this.audioRestore = audioRestore;
        }
    }
}
