package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;

import java.util.*;

/**
 * 批量设置容器锁定状态的命令（支持撤销/重做）
 */
public class BatchSetContainerLockedCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final List<UUID> containerIds;
    private final boolean newLocked;
    private final Map<UUID, Boolean> oldLockedStates = new HashMap<>();

    public BatchSetContainerLockedCommand(ContainerManager manager,
                                         Collection<Container> containers,
                                         boolean newLocked) {
        this.manager = manager;
        this.newLocked = newLocked;
        this.containerIds = new ArrayList<>();
        for (Container container : containers) {
            if (container != null) {
                containerIds.add(container.getId());
            }
        }
    }

    @Override
    public void execute() {
        oldLockedStates.clear();
        List<Container> selected = new ArrayList<>();
        for (UUID id : containerIds) {
            Container container = manager.getContainer(id);
            if (container != null) selected.add(container);
        }
        List<Container> roots = manager.getTopLevelSelection(selected);
        Set<Container> affected = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Container root : roots) collectAffected(root, affected);
        for (Container container : affected) oldLockedStates.put(container.getId(), container.isLocked());
        for (Container root : roots) root.setLocked(newLocked);
    }

    @Override
    public void undo() {
        for (Map.Entry<UUID, Boolean> entry : oldLockedStates.entrySet()) {
            UUID id = entry.getKey();
            Container container = manager.getContainer(id);
            if (container == null) continue;
            container.setLocked(entry.getValue(), false);
        }
    }

    @Override
    public String getDescription() {
        return (newLocked ? "批量锁定" : "批量解锁") + " " + containerIds.size() + " 个容器";
    }

    private static void collectAffected(Container container, Set<Container> affected) {
        if (!affected.add(container)) return;
        for (Container child : container.getChildrenView()) collectAffected(child, affected);
    }
}
