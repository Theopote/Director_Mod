package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 切换实时接收命令。
 */
public class ToggleIntakeCommand implements ContainerCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleIntakeCommand.class);

    private final ContainerManager manager;
    private final Container container;
    private final boolean oldState;
    private final boolean newState;
    private final UUID oldActiveId;
    private final UUID newActiveId;

    public ToggleIntakeCommand(ContainerManager manager, Container container) {
        this(manager, container, !container.isIntakeEnabled());
    }

    public ToggleIntakeCommand(ContainerManager manager, Container container, boolean newState) {
        this.manager = manager;
        this.container = container;
        this.oldState = container.isIntakeEnabled();
        this.newState = newState;

        Container oldActive = manager.getActive();
        this.oldActiveId = oldActive != null ? oldActive.getId() : null;

        if (newState) {
            this.newActiveId = container.getId();
        } else if (container.getId().equals(oldActiveId)) {
            this.newActiveId = null;
        } else {
            this.newActiveId = oldActiveId;
        }
    }

    @Override
    public void execute() {
        container.setIntakeEnabled(newState);
        manager.setActive(newActiveId);

        LOGGER.info("[ToggleIntakeCommand] 容器 {} 实时接收: {} -> {}",
            container.getName(), oldState, newState);
    }

    @Override
    public void undo() {
        container.setIntakeEnabled(oldState);
        manager.setActive(oldActiveId);

        LOGGER.info("[ToggleIntakeCommand] 撤销容器 {} 实时接收: {} -> {}",
            container.getName(), newState, oldState);
    }

    @Override
    public String getDescription() {
        return String.format("切换容器 \"%s\" 实时接收: %s",
            container.getName(),
            newState ? "开启" : "关闭");
    }
}
