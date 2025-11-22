package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

import java.util.UUID;

/**
 * 交换两个容器位置的命令。
 */
public class SwapContainersCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID firstId;
    private final UUID secondId;

    public SwapContainersCommand(ContainerManager manager, UUID firstId, UUID secondId) {
        this.manager = manager;
        this.firstId = firstId;
        this.secondId = secondId;
    }

    @Override
    public void execute() {
        Container first = manager.getContainer(firstId);
        Container second = manager.getContainer(secondId);
        if (first == null || second == null || first == second) {
            return;
        }
        manager.swapContainers(first, second);
    }

    @Override
    public void undo() {
        Container first = manager.getContainer(firstId);
        Container second = manager.getContainer(secondId);
        if (first == null || second == null || first == second) {
            return;
        }
        manager.swapContainers(first, second);
    }

    @Override
    public String getDescription() {
        return "交换容器位置";
    }
}

