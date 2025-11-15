package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 合并多个容器为新的组合容器的命令。
 */
public class MergeContainersCommand implements ContainerCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeContainersCommand.class);

    private final ContainerManager manager;
    private final List<Container> targets;
    private final String preferredName;

    private final List<Container> originalParents = new ArrayList<>();
    private final List<Integer> originalIndices = new ArrayList<>();
    private final List<List<Container>> originalChildren = new ArrayList<>();
    private final List<Container> removedContainers = new ArrayList<>();

    private Container mergedContainer;
    private Container mergedParent;
    private int mergedInsertIndex = -1;

    public MergeContainersCommand(ContainerManager manager, Collection<Container> selection, String preferredName) {
        this.manager = manager;
        this.targets = manager.getTopLevelSelection(selection);
        this.preferredName = preferredName;
    }

    @Override
    public void execute() {
        if (targets.size() < 2) {
            LOGGER.warn("[MergeCommand] 合并失败：需要至少两个容器");
            return;
        }

        clearSnapshots();

        for (Container container : targets) {
            Container parent = container.getParent();
            int index = parent != null
                ? parent.indexOfChild(container)
                : manager.getRootContainersView().indexOf(container);
            originalParents.add(parent);
            originalIndices.add(index);
            originalChildren.add(new ArrayList<>(container.getChildren()));
        }

        mergedParent = determineCommonParent();
        mergedInsertIndex = computeInsertIndex();
        if (mergedParent != null && mergedParent.getType() != ContainerType.GROUP) {
            LOGGER.warn("[MergeCommand] 原父级 {} 非组类型，合并结果将存放在顶层", mergedParent.getName());
            mergedParent = null;
            mergedInsertIndex = manager.getRootContainersView().size();
        }

        String baseName = (preferredName == null || preferredName.isBlank())
            ? targets.get(0).getName() + "_合并"
            : preferredName.trim();
        String uniqueName = manager.generateUniqueName(baseName);

        mergedContainer = manager.createContainer(uniqueName, ContainerType.GROUP);
        manager.moveContainerToParent(mergedContainer, mergedParent, mergedInsertIndex);

        for (Container container : targets) {
            if (container == mergedContainer) {
                continue;
            }

            mergedContainer.addBlocks(container.getBlocks());
            
            // v5.2: 如果合并后的容器是隐藏状态，可见性由ContainerVisibilityService管理

            List<Container> childrenSnapshot = new ArrayList<>(container.getChildren());
            for (Container child : childrenSnapshot) {
                manager.moveContainerToParent(child, mergedContainer, mergedContainer.getChildCount());
            }

            removedContainers.add(container);
            manager.removeContainer(container);
        }

        manager.selectContainer(mergedContainer);
    }

    @Override
    public void undo() {
        if (mergedContainer == null) {
            return;
        }

        // 按保存的顺序恢复容器
        for (int i = 0; i < removedContainers.size(); i++) {
            Container container = removedContainers.get(i);
            Container parent = originalParents.get(i);
            int index = originalIndices.get(i);
            List<Container> children = originalChildren.get(i);

            manager.addContainer(container);
            manager.moveContainerToParent(container, parent, index);

            int insertIndex = 0;
            for (Container child : children) {
                if (child.getParent() == mergedContainer) {
                    manager.moveContainerToParent(child, container, insertIndex++);
                }
            }
        }

        manager.removeContainer(mergedContainer);
    }

    @Override
    public String getDescription() {
        return "合并容器 (" + targets.size() + ")";
    }

    private void clearSnapshots() {
        originalParents.clear();
        originalIndices.clear();
        originalChildren.clear();
        removedContainers.clear();
    }

    private Container determineCommonParent() {
        Container firstParent = targets.get(0).getParent();
        boolean sameParent = targets.stream().allMatch(c -> c.getParent() == firstParent);
        return sameParent ? firstParent : null;
    }

    private int computeInsertIndex() {
        if (mergedParent == null) {
            return manager.getRootContainersView().size();
        }
        int minIndex = Integer.MAX_VALUE;
        for (Container container : targets) {
            if (container.getParent() == mergedParent) {
                int idx = mergedParent.indexOfChild(container);
                if (idx >= 0 && idx < minIndex) {
                    minIndex = idx;
                }
            }
        }
        return minIndex == Integer.MAX_VALUE ? mergedParent.getChildCount() : minIndex;
    }

    public Container getMergedContainer() {
        return mergedContainer;
    }
}

