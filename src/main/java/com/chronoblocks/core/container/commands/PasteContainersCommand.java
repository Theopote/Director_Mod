package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 将剪贴板中的容器粘贴到场景中的命令。
 */
public class PasteContainersCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final List<Container> templates = new ArrayList<>();
    private final Container targetParent;
    private final int insertIndex;
    private final List<Container> pasted = new ArrayList<>();

    public PasteContainersCommand(ContainerManager manager,
                                  Collection<Container> templates,
                                  Container targetParent,
                                  int insertIndex) {
        this.manager = manager;
        this.targetParent = targetParent != null && targetParent.getType() == ContainerType.GROUP
            ? targetParent
            : null;
        this.insertIndex = insertIndex;
        if (templates != null) {
            for (Container template : manager.getTopLevelSelection(templates)) {
                if (template != null) {
                    this.templates.add(copyTemplate(template));
                }
            }
        }
    }

    @Override
    public void execute() {
        if (templates.isEmpty()) {
            return;
        }

        pasted.clear();
        int nextIndex = insertIndex;
        boolean append = nextIndex < 0;

        for (Container template : templates) {
            Container clone = copyTemplate(template);
            clone.setName(manager.generateUniqueName(clone.getName()));
            manager.addContainer(clone);

            int appliedIndex = append ? -1 : nextIndex;
            manager.moveContainerToParent(clone, targetParent, appliedIndex);
            if (!append) {
                nextIndex++;
            }
            pasted.add(clone);
        }

        if (!pasted.isEmpty()) {
            manager.selectContainer(pasted.get(pasted.size() - 1));
        }
    }

    @Override
    public void undo() {
        for (int i = pasted.size() - 1; i >= 0; i--) {
            manager.removeContainer(pasted.get(i));
        }
        pasted.clear();
    }

    @Override
    public String getDescription() {
        return "粘贴容器 (" + templates.size() + ")";
    }

    private static Container copyTemplate(Container source) {
        Container clone = source.clone();
        restoreNames(clone, source);
        return clone;
    }

    private static void restoreNames(Container copy, Container source) {
        copy.setName(source.getName());
        List<Container> copyChildren = copy.getChildren();
        List<Container> sourceChildren = source.getChildren();
        int limit = Math.min(copyChildren.size(), sourceChildren.size());
        for (int i = 0; i < limit; i++) {
            restoreNames(copyChildren.get(i), sourceChildren.get(i));
        }
    }
}

