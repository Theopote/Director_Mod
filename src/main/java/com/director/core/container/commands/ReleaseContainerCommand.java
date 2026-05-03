package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerState;
import com.director.core.container.ContainerManager;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 释放容器命令，支持撤销/重做。
 */
public class ReleaseContainerCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;

    private final List<Container> releasedTree = new ArrayList<>();
    private final List<ContainerStateSnapshot> stateSnapshots = new ArrayList<>();
    private final List<ClipSnapshot> removedClips = new ArrayList<>();

    private Container container;
    private Container parent;
    private int indexInParent = -1;
    private UUID previousActiveId;
    private boolean captured;

    public ReleaseContainerCommand(ContainerManager manager, Container container) {
        this.manager = manager;
        this.container = container;
        this.containerId = container.getId();
    }

    @Override
    public void execute() {
        if (!captured) {
            captureSnapshot();
        } else {
            container = manager.getContainer(containerId);
        }
        if (container == null) {
            return;
        }

        if (containsContainer(previousActiveId)) {
            manager.setActive((UUID) null);
        }

        removeTimelineClips();
        manager.removeContainer(container);
    }

    @Override
    public void undo() {
        if (!captured || container == null) {
            return;
        }

        manager.restoreHierarchyBulk(Collections.singletonList(container));
        manager.moveContainerToParent(container, parent, indexInParent);
        restoreContainerStates();
        restoreTimelineClips();

        if (previousActiveId != null && containsContainer(previousActiveId)) {
            manager.setActive(previousActiveId);
        }
    }

    @Override
    public String getDescription() {
        return "释放容器: " + (container != null ? container.getName() : containerId);
    }

    private void captureSnapshot() {
        captured = true;
        container = manager.getContainer(containerId);
        if (container == null) {
            return;
        }

        parent = container.getParent();
        List<Container> rootView = manager.getRootContainersView();
        indexInParent = parent != null ? parent.indexOfChild(container) : rootView.indexOf(container);

        Container active = manager.getActive();
        previousActiveId = active != null ? active.getId() : null;

        releasedTree.clear();
        stateSnapshots.clear();
        removedClips.clear();
        collectTree(container);
        captureTimelineClips();
    }

    private void collectTree(Container current) {
        if (current == null) {
            return;
        }
        releasedTree.add(current);
        stateSnapshots.add(new ContainerStateSnapshot(current, current.getState()));
        for (Container child : current.getChildren()) {
            collectTree(child);
        }
    }

    private void captureTimelineClips() {
        ContainerTimelineService timelineService = ContainerTimelineService.getInstance();
        TimelineModel timelineModel = timelineService.getTimelineModel();
        if (timelineModel == null) {
            return;
        }

        for (Container released : releasedTree) {
            List<TimelineClip> clips = timelineService.findClipsForContainer(released.getId());
            for (TimelineClip clip : clips) {
                TimelineTrack track = timelineModel.findTrackForClip(clip);
                if (track != null) {
                    removedClips.add(new ClipSnapshot(track, clip));
                }
            }
        }
    }

    private void removeTimelineClips() {
        ContainerTimelineService timelineService = ContainerTimelineService.getInstance();
        for (Container released : releasedTree) {
            timelineService.removeClipsForContainer(released.getId());
        }
    }

    private void restoreContainerStates() {
        for (ContainerStateSnapshot snapshot : stateSnapshots) {
            snapshot.container.setState(snapshot.state);
        }
    }

    private void restoreTimelineClips() {
        ContainerTimelineService timelineService = ContainerTimelineService.getInstance();
        TimelineModel timelineModel = timelineService.getTimelineModel();
        if (timelineModel == null) {
            return;
        }

        for (ClipSnapshot snapshot : removedClips) {
            timelineModel.addClip(snapshot.track, snapshot.clip);
        }
        if (!removedClips.isEmpty()) {
            timelineModel.markDirty();
        }
    }

    private boolean containsContainer(UUID id) {
        if (id == null) {
            return false;
        }
        for (Container released : releasedTree) {
            if (id.equals(released.getId())) {
                return true;
            }
        }
        return false;
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

        private ClipSnapshot(TimelineTrack track, TimelineClip clip) {
            this.track = track;
            this.clip = clip;
        }
    }
}
