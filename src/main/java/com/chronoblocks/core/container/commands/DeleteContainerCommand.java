package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;

/**
 * 删除容器命令
 * 
 * @author ChronoBlocks Team
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
        if (parent != null) {
            this.indexInParent = parent.getChildren().indexOf(container);
        } else {
            this.indexInParent = manager.getRootContainers().indexOf(container);
        }
    }
    
    @Override
    public void execute() {
        manager.removeContainer(container);
    }
    
    @Override
    public void undo() {
        manager.addContainer(container);
        if (parent != null) {
            manager.moveContainerToParent(container, parent);
            // TODO: 恢复到原来的位置（需要在ContainerManager中添加支持）
        }
    }
    
    @Override
    public String getDescription() {
        return "删除容器: " + container.getName();
    }
}

