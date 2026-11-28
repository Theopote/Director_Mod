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
 * 清空所有容器的命令，支持撤销恢复。
 * <p>撤销只恢复本命令捕获的对象，绝不调用 {@link ContainerManager#clear()} 清除后续新建的容器。
 */
public class ClearAllContainersCommand implements ContainerCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearAllContainersCommand.class);

    private final ContainerManager manager;
    private final List<Container> rootSnapshot = new ArrayList<>();
    private final List<Container> treeSnapshot = new ArrayList<>();
    private final List<Container> hierarchyParents = new ArrayList<>();
    private final List<Integer> hierarchyIndices = new ArrayList<>();
    private final List<ContainerStateSnapshot> stateSnapshots = new ArrayList<>();
    private final List<ClipSnapshot> removedClips = new ArrayList<>();
    private final List<Container> selectionSnapshot = new ArrayList<>();

    private Container primarySelection;
    private Container lastSelection;
    private UUID previousActiveId;
    private TimelineClip previousSelectedClip;
    private boolean captured;
    private boolean executed;

    public ClearAllContainersCommand(ContainerManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute() {
        if (!captured) {
            captureSnapshot();
        }
        if (!captured || rootSnapshot.isEmpty()) {
            return;
        }
        for (Container root : rootSnapshot) {
            if (!manager.requireEditableTree(root, "清空")) {
                return;
            }
        }

        LOGGER.info("[ClearAllCommand] 清空容器 (共 {} 个根容器)", rootSnapshot.size());
        manager.removeContainers(rootSnapshot);
        executed = true;
    }

    @Override
    public void undo() {
        if (!captured || !executed) {
            return;
        }
        for (ContainerStateSnapshot snapshot : stateSnapshots) {
            if (snapshot.container.isLocked()) {
                LOGGER.warn("[ClearAllCommand] 撤销清空失败：容器 {} 已被锁定", snapshot.container.getName());
                return;
            }
        }

        LOGGER.info("[ClearAllCommand] 撤销清空，恢复 {} 个根容器", rootSnapshot.size());
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
        return "清空所有容器";
    }

    private void captureSnapshot() {
        rootSnapshot.clear();
        rootSnapshot.addAll(manager.getRootContainersView());

        Container active = manager.getActive();
        previousActiveId = active != null ? active.getId() : null;
        primarySelection = manager.getSelectedContainer();
        lastSelection = manager.getLastSelectedContainer();
        selectionSnapshot.clear();
        selectionSnapshot.addAll(manager.getSelectedContainers());

        treeSnapshot.clear();
        hierarchyParents.clear();
        hierarchyIndices.clear();
        stateSnapshots.clear();
        removedClips.clear();
        for (Container root : rootSnapshot) {
            collectTree(root);
        }
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
        treeSnapshot.add(current);
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

        for (Container container : treeSnapshot) {
            for (TimelineClip clip : timelineService.findClipsForContainer(container.getId())) {
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
        for (int i = 0; i < treeSnapshot.size(); i++) {
            Container item = treeSnapshot.get(i);
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

        boolean changed = false;
        for (ClipSnapshot snapshot : removedClips) {
            if (timelineModel.findTrackForClip(snapshot.clip) != null
                    || timelineModel.getAllTracks().stream().noneMatch(track -> track == snapshot.track)) {
                continue;
            }
            snapshot.clip.restoreStateFrom(snapshot.state);
            if (!snapshot.track.addClip(snapshot.clip)) {
                LOGGER.warn("[ClearAllCommand] 片段 {} 恢复被轨道拒绝", snapshot.clip.getLabel());
                continue;
            }
            changed = true;
            try {
                snapshot.audioRestore.run();
            } catch (Exception ex) {
                snapshot.track.removeClip(snapshot.clip);
                LOGGER.warn("[ClearAllCommand] 恢复片段 {} 的音频失败: {}",
                    snapshot.clip.getLabel(), ex.getMessage());
            }
        }
        if (changed) {
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
