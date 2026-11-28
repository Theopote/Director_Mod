package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;

import java.util.*;

/**
 * 批量设置容器可见性状态的命令（支持撤销/重做）
 */
public class BatchSetContainerStateCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final List<UUID> containerIds = new ArrayList<>();
    private final ContainerState newState;
    private final Map<UUID, ContainerState> oldStates = new LinkedHashMap<>();

    public BatchSetContainerStateCommand(ContainerManager manager,
                                         Collection<Container> containers,
                                         ContainerState newState) {
        this.manager = manager;
        this.newState = newState;
        if (containers != null) {
            for (Container container : containers) {
                if (container != null) {
                    containerIds.add(container.getId());
                }
            }
        }
    }

    @Override
    public void execute() {
        List<Container> resolved = resolveEditableTargets("批量切换可见性");
        if (resolved == null || newState == null) {
            return;
        }
        oldStates.clear();
        Map<UUID, ContainerState> newStates = new LinkedHashMap<>();
        for (Container container : resolved) {
            oldStates.put(container.getId(), container.getState());
            newStates.put(container.getId(), newState);
        }
        applyStates(newStates, "批量切换可见性");
    }

    @Override
    public void undo() {
        applyStates(oldStates, "撤销批量可见性修改");
    }

    private List<Container> resolveEditableTargets(String action) {
        List<Container> resolved = new ArrayList<>();
        for (UUID containerId : containerIds) {
            Container container = manager.getContainer(containerId);
            if (container != null) {
                resolved.add(container);
            }
        }
        return manager.requireAllEditable(resolved, action) ? resolved : null;
    }

    private void applyStates(Map<UUID, ContainerState> states, String action) {
        if (states.isEmpty()) {
            return;
        }
        List<Container> targets = new ArrayList<>();
        for (UUID id : states.keySet()) {
            Container container = manager.getContainer(id);
            if (container != null) {
                targets.add(container);
            }
        }
        if (!manager.requireAllEditable(targets, action)) {
            return;
        }

        Set<Container> affectedLeaves = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Container container : targets) collectLeaves(container, affectedLeaves);

        Map<Container, Boolean> oldVisibility = new IdentityHashMap<>();
        for (Container leaf : affectedLeaves) oldVisibility.put(leaf, leaf.isEffectivelyVisible());
        for (Container container : targets) {
            container.setState(states.get(container.getId()), false);
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
        return "批量设置 " + containerIds.size() + " 个容器为" + newState.getDisplayName();
    }
}
