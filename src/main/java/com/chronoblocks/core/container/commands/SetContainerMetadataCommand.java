package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

import java.util.UUID;

/**
 * 设置容器元数据的命令。
 */
public class SetContainerMetadataCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;
    private final String key;
    private final Object newValue;
    private Object oldValue;

    public SetContainerMetadataCommand(ContainerManager manager, Container container, String key, Object newValue) {
        this.manager = manager;
        this.containerId = container.getId();
        this.key = key;
        this.newValue = newValue;
        this.oldValue = container.getMetadata(key, null);
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            oldValue = container.getMetadata(key, null);
            container.setMetadata(key, newValue);
        }
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            if (oldValue == null) {
                container.removeMetadata(key);
            } else {
                container.setMetadata(key, oldValue);
            }
        }
    }

    @Override
    public String getDescription() {
        return String.format("设置容器元数据: %s", key);
    }
}

