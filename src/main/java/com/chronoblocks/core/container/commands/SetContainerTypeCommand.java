package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerType;

import java.util.UUID;

/**
 * 设置容器类型的命令。
 */
public class SetContainerTypeCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;
    private final ContainerType newType;
    private ContainerType oldType;

    public SetContainerTypeCommand(ContainerManager manager, Container container, ContainerType newType) {
        this.manager = manager;
        this.containerId = container.getId();
        this.newType = newType;
        this.oldType = container.getType();
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container != null && newType != null) {
            // 不允许将类型改为 GROUP
            if (newType == ContainerType.GROUP) {
                return;
            }
            oldType = container.getType();
            container.setType(newType);
        }
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container != null && oldType != null) {
            container.setType(oldType);
        }
    }

    @Override
    public String getDescription() {
        return "设置容器类型为 " + (newType != null ? newType.getDisplayName() : "未知");
    }
}

