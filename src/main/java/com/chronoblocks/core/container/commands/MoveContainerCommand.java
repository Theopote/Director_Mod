package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

import java.util.UUID;

/**
 * 容器移动命令，支持撤销/重做。
 */
public class MoveContainerCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;

    private Container oldParent;
    private int oldIndex;

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
        oldIndex = oldParent != null
            ? oldParent.indexOfChild(container)
            : manager.getRootContainersView().indexOf(container);

        manager.moveContainerToParent(container, targetParent, targetIndex);
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container == null) {
            return;
        }
        manager.moveContainerToParent(container, oldParent, oldIndex);
    }

    @Override
    public String getDescription() {
        return "移动容器";
    }
}

