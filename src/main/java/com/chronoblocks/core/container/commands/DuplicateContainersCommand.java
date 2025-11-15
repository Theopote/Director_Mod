package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 复制容器的命令。
 */
public class DuplicateContainersCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final List<Container> sources;
    private final List<Container> duplicates = new ArrayList<>();

    public DuplicateContainersCommand(ContainerManager manager, Collection<Container> sources) {
        this.manager = manager;
        this.sources = manager.getTopLevelSelection(sources);
    }

    @Override
    public void execute() {
        if (sources.isEmpty()) {
            return;
        }
        duplicates.clear();

        for (Container source : sources) {
            Container clone = source.clone();
            clone.setName(manager.generateUniqueName(clone.getName()));
            manager.addContainer(clone);
            Container parent = source.getParent();
            int insertIndex = parent != null
                ? parent.indexOfChild(source) + 1
                : manager.getRootContainersView().indexOf(source) + 1;
            manager.moveContainerToParent(clone, parent, insertIndex);
            duplicates.add(clone);
        }

        if (!duplicates.isEmpty()) {
            manager.selectContainer(duplicates.get(duplicates.size() - 1));
        }
    }

    @Override
    public void undo() {
        for (Container duplicate : duplicates) {
            manager.removeContainer(duplicate);
        }
    }

    @Override
    public String getDescription() {
        return "复制容器 (" + sources.size() + ")";
    }
}

