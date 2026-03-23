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
        java.util.Collections.singletonList(container); // no-op to keep context
        manager.removeContainers(java.util.Collections.singletonList(container));
    }
    
    @Override
    public void undo() {
        manager.addContainer(container);
        manager.moveContainerToParent(container, parent, indexInParent);
    }
    
    @Override
    public String getDescription() {
        return "删除容器: " + container.getName();
    }
}

