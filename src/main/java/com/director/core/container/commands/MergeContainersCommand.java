package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;
import com.director.core.container.ContainerType;
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

        String baseName = (preferredName == null || preferredName.isBlank())
            ? targets.getFirst().getName() + "_合并"
            : preferredName.trim();
        String uniqueName = manager.generateUniqueName(baseName);

        // 合并后的容器类型：保持非组类型（如果首个为组则退回为 GENERAL）
        ContainerType resultType = targets.getFirst().getType();
        if (resultType == ContainerType.GROUP) {
            resultType = ContainerType.GENERAL;
        }
        mergedContainer = manager.createContainer(uniqueName, resultType);
        manager.moveContainerToParent(mergedContainer, mergedParent, mergedInsertIndex);

        // 合并结果默认隐藏：先设置为 HIDDEN，再追加方块，确保可见性由服务统一管理
        mergedContainer.setState(ContainerState.HIDDEN);

        for (Container container : targets) {
            if (container == mergedContainer) {
                continue;
            }

            // 并入方块集合
            mergedContainer.addBlocks(container.getBlocks());

            // 同步方块快照与世界归属，使可见性服务能在显示时正确还原方块
            if (mergedContainer.getLevelKey() == null && container.getLevelKey() != null) {
                mergedContainer.setLevelKey(container.getLevelKey());
            }
            // 拷贝或补全方块快照：优先复用源容器快照，若缺失则尝试从当前世界读取补齐
            net.minecraft.client.MinecraftClient mc;
            net.minecraft.world.World world = null;
            try {
                mc = net.minecraft.client.MinecraftClient.getInstance();
                world = mc != null ? mc.world : null;
            } catch (Throwable ignored) {}
            for (net.minecraft.util.math.BlockPos pos : container.getBlocks()) {
                var entry = container.getBlockStore().get(pos);
                if (entry != null) {
                    mergedContainer.getBlockStore().add(pos, entry);
                } else if (world != null) {
                    // 回退方案：从世界捕获该方块的快照，避免合并后显示只剩线框
                    mergedContainer.getBlockStore().addAllFromWorld(java.util.Collections.singleton(pos), world);
                }
            }
            
            // 仅当结果为组时才迁移子容器；否则合并仅包含方块
            if (mergedContainer.getType() == ContainerType.GROUP) {
                List<Container> childrenSnapshot = new ArrayList<>(container.getChildren());
                for (Container child : childrenSnapshot) {
                    manager.moveContainerToParent(child, mergedContainer, mergedContainer.getChildCount());
                }
            }

            removedContainers.add(container);
        }

        // ★ 功能：容器合并时，删除被合并容器在时间线上的片段
        var timelineService = com.director.ui.timeline.container.ContainerTimelineService.getInstance();
        for (Container container : removedContainers) {
            int removedCount = timelineService.removeClipsForContainer(container.getId());
            if (removedCount > 0) {
                LOGGER.info("[MergeCommand] 容器 {} 在时间线上的 {} 个片段已删除（合并操作）", 
                    container.getName(), removedCount);
            }
        }

        // 统一批量删除所有被合并的原容器（一次加锁、一次事件触发），删除前已在上方补齐快照
        manager.removeContainers(removedContainers);

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
        Container firstParent = targets.getFirst().getParent();
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

