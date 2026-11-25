package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerState;

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
            oldStates.putIfAbsent(container, container.getState());
        }
        Map<Container, ContainerState> newStates = new LinkedHashMap<>();
        for (Container container : oldStates.keySet()) newStates.put(container, newState);
        applyStates(newStates);
    }

    @Override
    public void undo() {
        applyStates(oldStates);
    }

    private static void applyStates(Map<Container, ContainerState> states) {
        Set<Container> affectedLeaves = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Container container : states.keySet()) collectLeaves(container, affectedLeaves);

        Map<Container, Boolean> oldVisibility = new IdentityHashMap<>();
        for (Container leaf : affectedLeaves) oldVisibility.put(leaf, leaf.isEffectivelyVisible());
        for (Map.Entry<Container, ContainerState> entry : states.entrySet()) {
            entry.getKey().setState(entry.getValue(), false);
        }
        for (Container leaf : affectedLeaves) leaf.refreshEffectiveVisibility(oldVisibility.get(leaf));
    }

    private static void collectLeaves(Container container, Set<Container> leaves) {
        if (container == null) return;
        if (!container.isGroup()) {
            leaves.add(container);
            return;
        }
        for (Container child : container.getChildrenView()) collectLeaves(child, leaves);
    }

    @Override
    public String getDescription() {
        return "批量设置 " + containers.size() + " 个容器为" + newState.getDisplayName();
    }
}
