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

        for (Container container : targets) {
            Container parent = container.getParent();
            int index = parent != null
                ? parent.indexOfChild(container)
                : manager.getRootContainersView().indexOf(container);
            parents.add(parent);
            indices.add(index);
        }

        for (Container container : targets) {
            manager.removeContainer(container);
        }
    }

    @Override
    public void undo() {
        for (int i = targets.size() - 1; i >= 0; i--) {
            Container container = targets.get(i);
            Container parent = parents.get(i);
            int index = indices.get(i);
            manager.addContainer(container);
            manager.moveContainerToParent(container, parent, index);
        }
    }

    @Override
    public String getDescription() {
        return "删除容器 (" + targets.size() + ")";
    }
}

