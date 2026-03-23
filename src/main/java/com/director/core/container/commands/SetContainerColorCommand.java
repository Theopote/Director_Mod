package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;

import java.util.UUID;

/**
 * 设置容器标记颜色的命令。
 */
public class SetContainerColorCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;
    private final int newColor;
    private int oldColor;

    public SetContainerColorCommand(ContainerManager manager, Container container, int newColor) {
        this.manager = manager;
        this.containerId = container.getId();
        this.newColor = newColor;
        this.oldColor = container.getColor();
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            oldColor = container.getColor();
            container.setColor(newColor);
        }
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            container.setColor(oldColor);
        }
    }

    @Override
    public String getDescription() {
        return String.format("设置容器颜色: #%08X", newColor);
    }
}

