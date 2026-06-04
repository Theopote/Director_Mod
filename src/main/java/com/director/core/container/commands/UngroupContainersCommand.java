package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;
import com.director.core.container.ContainerType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 解散组容器：移出子容器并删除空组，支持撤销。
 */
public class UngroupContainersCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final List<Container> groups = new ArrayList<>();
    private final List<GroupSnapshot> snapshots = new ArrayList<>();

    public UngroupContainersCommand(ContainerManager manager, Collection<Container> selection) {
        this.manager = manager;
        for (Container container : manager.getTopLevelSelection(selection)) {
            if (container != null && container.getType() == ContainerType.GROUP && container.hasChildren()) {
                groups.add(container);
            }
        }
    }

    @Override
    public void execute() {
        snapshots.clear();
        if (groups.isEmpty()) {
            return;
        }

        for (Container group : groups) {
            Container parent = group.getParent();
            int index = parent != null ? parent.indexOfChild(group) : manager.getRootContainersView().indexOf(group);
            List<Container> children = new ArrayList<>(group.getChildren());
            if (children.isEmpty()) {
                continue;
            }

            GroupSnapshot snapshot = new GroupSnapshot(group, parent, index, children);
            snapshots.add(snapshot);

            manager.moveContainersToParent(children, parent, index);
            manager.removeContainer(group);
        }
    }

    @Override
    public void undo() {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            GroupSnapshot snapshot = snapshots.get(i);
            manager.addContainer(snapshot.group);
            manager.moveContainerToParent(snapshot.group, snapshot.parent, snapshot.index);
            snapshot.group.setLocked(snapshot.groupLocked);
            snapshot.group.setState(snapshot.groupState);

            manager.moveContainersToParent(snapshot.children, snapshot.group, 0);
            for (int j = 0; j < snapshot.children.size(); j++) {
                Container child = snapshot.children.get(j);
                child.setLocked(snapshot.childLocked.get(j));
                child.setState(snapshot.childStates.get(j));
            }
        }
    }

    @Override
    public String getDescription() {
        return groups.size() <= 1 ? "解散组" : ("解散 " + groups.size() + " 个组");
    }

    public boolean hasGroups() {
        return !groups.isEmpty();
    }

    private static final class GroupSnapshot {
        final Container group;
        final Container parent;
        final int index;
        final boolean groupLocked;
        final ContainerState groupState;
        final List<Container> children;
        final List<Boolean> childLocked = new ArrayList<>();
        final List<ContainerState> childStates = new ArrayList<>();

        GroupSnapshot(Container group, Container parent, int index, List<Container> children) {
            this.group = group;
            this.parent = parent;
            this.index = index;
            this.groupLocked = group.isLocked();
            this.groupState = group.getState();
            this.children = new ArrayList<>(children);
            for (Container child : children) {
                childLocked.add(child.isLocked());
                childStates.add(child.getState());
            }
        }
    }
}
