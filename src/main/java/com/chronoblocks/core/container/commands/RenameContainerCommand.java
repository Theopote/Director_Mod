package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

import java.util.UUID;

/**
 * 重命名容器命令。
 */
public class RenameContainerCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;
    private final String requestedName;
    private String oldName;
    private String appliedName;

    public RenameContainerCommand(ContainerManager manager, Container container, String newName) {
        this.manager = manager;
        this.containerId = container.getId();
        this.requestedName = newName;
        this.oldName = container.getName();
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            oldName = container.getName();
            if (appliedName == null) {
                appliedName = resolveUniqueName(container, requestedName);
            }
            manager.renameContainer(container, appliedName);
        }
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            manager.renameContainer(container, oldName);
        }
    }

    @Override
    public String getDescription() {
        return "重命名容器";
    }

    private String resolveUniqueName(Container container, String desired) {
        if (desired == null || desired.isBlank()) {
            return oldName;
        }
        String trimmed = desired.trim();
        Container existing = manager.getContainer(trimmed);
        if (existing == null || existing.getId().equals(containerId)) {
            return trimmed;
        }
        return manager.generateUniqueName(trimmed);
    }
}

