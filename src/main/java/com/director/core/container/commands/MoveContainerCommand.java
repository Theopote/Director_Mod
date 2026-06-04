package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;

import java.util.UUID;

/**
 * 容器移动命令，支持撤销/重做。
 */
public class MoveContainerCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;

    private Container oldParent;
    private int oldIndex;
    private boolean oldLocked;
    private ContainerState oldState;

    private final Container targetParent;
    private final int targetIndex;

    public MoveContainerCommand(ContainerManager manager,
                                Container container,
                                Container targetParent,
                                int targetIndex) {
        this.manager = manager;
        this.containerId = container.getId();
        this.targetParent = targetParent;
        this.targetIndex = targetIndex;
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container == null) {
            return;
        }

        oldParent = container.getParent();
        java.util.List<Container> rootView = manager.getRootContainersView();
        oldIndex = oldParent != null
            ? oldParent.indexOfChild(container)
            : rootView.indexOf(container);
        oldLocked = container.isLocked();
        oldState = container.getState();

        manager.moveContainerToParent(container, targetParent, targetIndex);
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container == null) {
            return;
        }
        manager.moveContainerToParent(container, oldParent, oldIndex);
        container.setLocked(oldLocked);
        container.setState(oldState);
    }

    @Override
    public String getDescription() {
        return "移动容器";
    }
}

