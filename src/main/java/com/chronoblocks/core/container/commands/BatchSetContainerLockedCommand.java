package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

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
        for (UUID id : containerIds) {
            Container container = manager.getContainer(id);
            if (container == null) continue;
            oldLockedStates.put(id, container.isLocked());
            container.setLocked(newLocked);
        }
    }

    @Override
    public void undo() {
        for (UUID id : containerIds) {
            Container container = manager.getContainer(id);
            if (container == null) continue;
            Boolean old = oldLockedStates.get(id);
            if (old != null) {
                container.setLocked(old);
            }
        }
    }

    @Override
    public String getDescription() {
        return (newLocked ? "批量锁定" : "批量解锁") + " " + containerIds.size() + " 个容器";
    }
}
