package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;

/**
 * 删除容器命令
 * 
 * @author Director Team
 * @version 4.0
 */
public class DeleteContainerCommand implements ContainerCommand {
    private final ContainerManager manager;
    private final Container container;
    private final Container parent;
    @SuppressWarnings("unused") // 保留用于未来恢复位置功能
    private final int indexInParent;
    private boolean executed;
    
    public DeleteContainerCommand(ContainerManager manager, Container container) {
        this.manager = manager;
        this.container = container;
        this.parent = container.getParent();
        
        // 记录在父容器中的位置
        this.indexInParent = parent != null
            ? parent.indexOfChild(container)
            : manager.getRootContainersView().indexOf(container);
    }
    
    @Override
    public void execute() {
        if (!manager.requireEditableTree(container, "删除")) {
            return;
        }
        manager.removeContainers(java.util.Collections.singletonList(container));
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed || container.isLocked() || manager.getContainer(container.getId()) != null
                || (parent != null && !manager.isEditable(parent))) {
            return;
        }
        manager.addContainer(container);
        manager.moveContainerToParent(container, parent, indexInParent);
        executed = false;
    }
    
    @Override
    public String getDescription() {
        return "删除容器: " + container.getName();
    }
}

