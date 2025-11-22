package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

import java.util.UUID;

/**
 * 设置容器锁定状态的命令。
 */
public class SetContainerLockedCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;
    private final boolean newLocked;
    private boolean oldLocked;

    public SetContainerLockedCommand(ContainerManager manager, Container container, boolean newLocked) {
        this.manager = manager;
        this.containerId = container.getId();
        this.newLocked = newLocked;
        this.oldLocked = container.isLocked();
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            container.setLocked(newLocked);
        }
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            container.setLocked(oldLocked);
        }
    }

    @Override
    public String getDescription() {
        return (newLocked ? "锁定" : "解锁") + "容器";
    }
}

