package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;

/**
 * 创建容器命令
 * 
 * @author Director Team
 * @version 4.0
 */
public class CreateContainerCommand implements ContainerCommand {
    private final ContainerManager manager;
    private final String name;
    private final ContainerType type;
    private final Container parent;
    private Container createdContainer;
    
    public CreateContainerCommand(ContainerManager manager, String name) {
        this(manager, name, ContainerType.GENERAL, null);
    }
    
    public CreateContainerCommand(ContainerManager manager, String name, ContainerType type, Container parent) {
        this.manager = manager;
        this.name = name;
        this.type = type;
        this.parent = parent;
    }
    
    @Override
    public void execute() {
        createdContainer = manager.createContainer(name, type);
        Container safeParent = parent != null && parent.getType() == ContainerType.GROUP ? parent : null;
        if (safeParent != null) {
            manager.moveContainerToParent(createdContainer, safeParent);
        }
    }
    
    @Override
    public void undo() {
        if (createdContainer != null) {
            manager.removeContainer(createdContainer);
        }
    }
    
    @Override
    public String getDescription() {
        return "创建容器: " + name;
    }
    
    public Container getCreatedContainer() {
        return createdContainer;
    }
}

