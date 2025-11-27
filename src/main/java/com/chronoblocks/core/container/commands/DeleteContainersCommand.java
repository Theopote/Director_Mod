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

        // 注意：删除片段的逻辑现在由 ContainerManager.removeContainers 自动处理
        // 这样可以确保无论从哪里删除容器，片段都会被自动删除

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