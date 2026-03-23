package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 将一组容器分组到新的父容器的命令。
 */
public class GroupContainersCommand implements ContainerCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupContainersCommand.class);

    private final ContainerManager manager;
    private final List<Container> targets;
    private final String preferredName;

    private final List<Container> originalParents = new ArrayList<>();
    private final List<Integer> originalIndices = new ArrayList<>();
    private Container groupContainer;
    private Container groupParent;
    private int groupInsertIndex = -1;

    public GroupContainersCommand(ContainerManager manager, Collection<Container> selection, String preferredName) {
        this.manager = manager;
        this.targets = manager.getTopLevelSelection(selection);
        this.preferredName = preferredName;
    }

    @Override
    public void execute() {
        if (targets.isEmpty()) {
            LOGGER.warn("[GroupCommand] 无可分组的目标容器");
            return;
        }

        originalParents.clear();
        originalIndices.clear();

        // 记录原父级和顺序（一次获取根快照，避免循环内多次复制/加锁）
        java.util.List<Container> rootView = manager.getRootContainersView();
        for (Container container : targets) {
            Container parent = container.getParent();
            int index = parent != null
                ? parent.indexOfChild(container)
                : rootView.indexOf(container);
            originalParents.add(parent);
            originalIndices.add(index);
        }

        groupParent = determineCommonParent();
        groupInsertIndex = computeInsertIndex();
        if (groupParent != null && groupParent.getType() != ContainerType.GROUP) {
            LOGGER.warn("[GroupCommand] 原父级 {} 非组类型，分组将创建在顶层", groupParent.getName());
            groupParent = null;
            groupInsertIndex = manager.getRootContainersView().size();
        }

        String baseName = (preferredName == null || preferredName.isBlank())
            ? "组"
            : preferredName.trim();
        String uniqueName = manager.generateUniqueName(baseName);

        groupContainer = manager.createContainer(uniqueName, ContainerType.GROUP);
        manager.moveContainerToParent(groupContainer, groupParent, groupInsertIndex);

        // 批量移动到组内，单次加锁
        java.util.ArrayList<Container> toMove = new java.util.ArrayList<>();
        for (Container c : targets) {
            if (c != groupContainer) toMove.add(c);
        }
        manager.moveContainersToParent(toMove, groupContainer, 0);

        manager.selectContainer(groupContainer);
    }

    @Override
    public void undo() {
        if (groupContainer == null) {
            return;
        }

        // 将子容器还原到原位置
        for (int i = targets.size() - 1; i >= 0; i--) {
            Container container = targets.get(i);
            if (container == groupContainer) {
                continue;
            }
            Container originalParent = originalParents.get(i);
            int originalIndex = originalIndices.get(i);
            manager.moveContainerToParent(container, originalParent, originalIndex);
        }

        // 移除新建的组容器
        manager.removeContainer(groupContainer);
    }

    @Override
    public String getDescription() {
        return "分组容器 (" + targets.size() + ")";
    }

    private Container determineCommonParent() {
        Container firstParent = targets.getFirst().getParent();
        boolean sameParent = targets.stream().allMatch(c -> c.getParent() == firstParent);
        return sameParent ? firstParent : null;
    }

    private int computeInsertIndex() {
        if (groupParent == null) {
            return manager.getRootContainersView().size();
        }
        int minIndex = Integer.MAX_VALUE;
        for (Container container : targets) {
            if (container.getParent() == groupParent) {
                int idx = groupParent.indexOfChild(container);
                if (idx >= 0 && idx < minIndex) {
                    minIndex = idx;
                }
            }
        }
        return minIndex == Integer.MAX_VALUE ? groupParent.getChildCount() : minIndex;
    }

    public Container getGroupContainer() {
        return groupContainer;
    }
}

