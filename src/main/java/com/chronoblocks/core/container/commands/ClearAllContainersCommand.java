package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 清空所有容器的命令，支持撤销恢复。
 */
public class ClearAllContainersCommand implements ContainerCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearAllContainersCommand.class);

    private final ContainerManager manager;
    private final List<Container> rootSnapshot = new ArrayList<>();
    private final List<Container> selectionSnapshot = new ArrayList<>();
    private Container primarySelection;
    private boolean captured;

    public ClearAllContainersCommand(ContainerManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute() {
        if (!captured) {
            captureSnapshot();
        }
        LOGGER.info("[ClearAllCommand] 清空容器 (共 {} 个)", rootSnapshot.size());
        manager.clear();
    }

    @Override
    public void undo() {
        if (!captured) {
            return;
        }
        LOGGER.info("[ClearAllCommand] 撤销清空，恢复 {} 个根容器", rootSnapshot.size());
        if (!manager.getAllContainers().isEmpty()) {
            manager.clear();
        }
        // 批量恢复整棵层级，提高性能与一致性
        manager.restoreHierarchyBulk(rootSnapshot);
        restoreSelection();
    }

    @Override
    public String getDescription() {
        return "清空所有容器";
    }

    private void captureSnapshot() {
        captured = true;
        rootSnapshot.clear();
        rootSnapshot.addAll(manager.getRootContainersView());

        selectionSnapshot.clear();
        primarySelection = manager.getSelectedContainer();
        if (primarySelection != null) {
            selectionSnapshot.add(primarySelection);
        }
        Collection<Container> currentSelection = manager.getSelectedContainers();
        for (Container container : currentSelection) {
            if (container != null && container != primarySelection) {
                selectionSnapshot.add(container);
            }
        }
    }

    // 层级恢复逻辑已下沉到 manager.restoreHierarchyBulk

    private void restoreSelection() {
        manager.clearSelection();
        boolean first = true;
        for (Container container : selectionSnapshot) {
            if (container == null) {
                continue;
            }
            manager.selectContainer(container, !first);
            first = false;
        }
        if (!selectionSnapshot.isEmpty() && primarySelection != null && manager.isSelected(primarySelection)) {
            manager.selectContainer(primarySelection, true);
        }
    }
}

