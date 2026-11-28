package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 切换容器状态命令 (v5.0)
 * <p>支持撤销/重做
 *
 * @author Director Team
 * @version 5.0
 */
public class ToggleContainerStateCommand implements ContainerCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleContainerStateCommand.class);

    private final ContainerManager manager;
    private final UUID containerId;
    private final String containerName;
    private final ContainerState oldState;
    private final ContainerState newState;

    public ToggleContainerStateCommand(ContainerManager manager, Container container) {
        this(manager, container, container.getState().toggle());
    }

    public ToggleContainerStateCommand(ContainerManager manager, Container container, ContainerState newState) {
        this.manager = manager;
        this.containerId = container.getId();
        this.containerName = container.getName();
        this.oldState = container.getState();
        this.newState = newState;
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (!manager.requireEditable(container, "切换可见性") || newState == null) {
            return;
        }
        // v5.2: setState() 会自动调用 ContainerVisibilityService
        container.setState(newState);

        LOGGER.debug("[ToggleContainerStateCommand] 容器 {} 状态: {} -> {}",
            containerName, oldState.getDisplayName(), newState.getDisplayName());
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (!manager.requireEditable(container, "撤销可见性修改") || oldState == null) {
            return;
        }
        // v5.2: setState() 会自动调用 ContainerVisibilityService
        container.setState(oldState);

        LOGGER.debug("[ToggleContainerStateCommand] 撤销：容器 {} 状态: {} -> {}",
            containerName, newState.getDisplayName(), oldState.getDisplayName());
    }

    @Override
    public String getDescription() {
        return String.format("切换容器 \"%s\" 状态: %s -> %s",
            containerName,
            oldState.getDisplayName(),
            newState.getDisplayName());
    }
}
