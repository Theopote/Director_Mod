package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerAnimationPreset;
import com.director.core.container.ContainerManager;

import java.util.UUID;

/**
 * 设置容器默认动画预设的命令。
 */
public class SetContainerPresetCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;
    private final ContainerAnimationPreset newPreset;
    private ContainerAnimationPreset oldPreset;

    public SetContainerPresetCommand(ContainerManager manager, Container container, ContainerAnimationPreset newPreset) {
        this.manager = manager;
        this.containerId = container.getId();
        this.newPreset = newPreset != null ? newPreset.copy() : ContainerAnimationPreset.defaultPreset();
        this.oldPreset = container.getDefaultAnimationPreset().copy();
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            container.setDefaultAnimationPreset(newPreset);
        }
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            container.setDefaultAnimationPreset(oldPreset);
        }
    }

    @Override
    public String getDescription() {
        return "更新容器默认动画预设";
    }
}

