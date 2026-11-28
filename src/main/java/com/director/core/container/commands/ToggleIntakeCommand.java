package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
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
    private final UUID containerId;
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
        this.containerId = container.getId();
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
        Container current = resolveRegisteredContainer();
        if (current == null || !canSetIntake(current, newState)) {
            return;
        }
        current.setIntakeEnabled(newState);
        if (newState) {
            manager.setActive(current);
        } else if (manager.getActive() == current) {
            manager.setActive((UUID) null);
        }

        LOGGER.info("[ToggleIntakeCommand] 容器 {} 实时接收: {} -> {}",
            current.getName(), oldState, newState);
    }

    @Override
    public void undo() {
        Container current = resolveRegisteredContainer();
        if (current == null || !canSetIntake(current, oldState)) {
            return;
        }
        current.setIntakeEnabled(oldState);
        if (oldState) {
            if (manager.getActive() == null || manager.getActive() == current) {
                manager.setActive(current);
            }
        } else if (manager.getActive() == current) {
            manager.setActive((UUID) null);
        }

        LOGGER.info("[ToggleIntakeCommand] 撤销容器 {} 实时接收: {} -> {}",
            current.getName(), newState, oldState);
    }

    private Container resolveRegisteredContainer() {
        Container current = manager.getContainer(containerId);
        return current != null && manager.isRegistered(container) && current == container ? current : null;
    }

    private boolean canSetIntake(Container current, boolean enabled) {
        if (enabled && (current.isLocked() || current.getType() == ContainerType.GROUP)) {
            LOGGER.warn("[ToggleIntakeCommand] 容器 {} 已锁定或为组，无法开启实时接收", current.getName());
            return false;
        }
        return true;
    }

    @Override
    public String getDescription() {
        return String.format("切换容器 \"%s\" 实时接收: %s",
            container.getName(),
            newState ? "开启" : "关闭");
    }
}
