package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

import java.util.UUID;

/**
 * 设置容器不透明度的命令。
 */
public class SetContainerOpacityCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;
    private final float newOpacity;
    private float oldOpacity;

    public SetContainerOpacityCommand(ContainerManager manager, Container container, float newOpacity) {
        this.manager = manager;
        this.containerId = container.getId();
        this.newOpacity = Math.max(0.0f, Math.min(1.0f, newOpacity));
        this.oldOpacity = container.getOpacity();
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            container.setOpacity(newOpacity);
        }
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            container.setOpacity(oldOpacity);
        }
    }

    @Override
    public String getDescription() {
        return String.format("设置容器不透明度: %.2f", newOpacity);
    }
}

