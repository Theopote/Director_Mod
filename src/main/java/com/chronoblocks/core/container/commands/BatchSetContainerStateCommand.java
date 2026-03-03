package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerState;

import java.util.*;

/**
 * 批量设置容器可见性状态的命令（支持撤销/重做）
 */
public class BatchSetContainerStateCommand implements ContainerCommand {

    private final List<Container> containers;
    private final ContainerState newState;
    private final Map<Container, ContainerState> oldStates = new LinkedHashMap<>();

    public BatchSetContainerStateCommand(Collection<Container> containers, ContainerState newState) {
        this.containers = new ArrayList<>(containers);
        this.newState = newState;
    }

    @Override
    public void execute() {
        oldStates.clear();
        for (Container container : containers) {
            if (container == null) continue;
            oldStates.put(container, container.getState());
            container.setState(newState);
        }
    }

    @Override
    public void undo() {
        for (Map.Entry<Container, ContainerState> entry : oldStates.entrySet()) {
            entry.getKey().setState(entry.getValue());
        }
    }

    @Override
    public String getDescription() {
        return "批量设置 " + containers.size() + " 个容器为" + newState.getDisplayName();
    }
}
