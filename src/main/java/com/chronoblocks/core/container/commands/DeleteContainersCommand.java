package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 批量删除容器的命令。
 */
public class DeleteContainersCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final List<Container> targets;

    private final List<Container> parents = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();

    public DeleteContainersCommand(ContainerManager manager, Collection<Container> containers) {
        this.manager = manager;
        this.targets = manager.getTopLevelSelection(containers);
    }

    @Override
    public void execute() {
        if (targets.isEmpty()) {
            return;
        }

        parents.clear();
        indices.clear();

        // ★ 功能：删除容器前，先删除时间线上的对应片段
        var timelineService = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance();
        for (Container container : targets) {
            int removedCount = timelineService.removeClipsForContainer(container.getId());
            if (removedCount > 0) {
                org.slf4j.LoggerFactory.getLogger(DeleteContainersCommand.class)
                    .info("[DeleteContainersCommand] 容器 {} 在时间线上的 {} 个片段已删除", 
                        container.getName(), removedCount);
            }
        }

        // 在循环外获取一次根列表快照，避免多次复制/加锁
        java.util.List<Container> rootView = manager.getRootContainersView();
        for (Container container : targets) {
            Container parent = container.getParent();
            int index = parent != null
                ? parent.indexOfChild(container)
                : rootView.indexOf(container);
            parents.add(parent);
            indices.add(index);
        }

        // 一次性批量删除，降低加锁开销
        manager.removeContainers(targets);
    }

    @Override
    public void undo() {
        // 批量恢复到原位置（一次加锁）
        manager.restoreContainersAtPositions(targets, parents, indices);
    }

    @Override
    public String getDescription() {
        return "删除容器 (" + targets.size() + ")";
    }
}