package com.chronoblocks.core.container.commands;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 切换容器状态命令 (v5.0)
 * <p>支持撤销/重做</p>
 * 
 * @author ChronoBlocks Team
 * @version 5.0
 */
public class ToggleContainerStateCommand implements ContainerCommand {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleContainerStateCommand.class);
    
    private final Container container;
    private final ContainerState oldState;
    private final ContainerState newState;
    
    public ToggleContainerStateCommand(Container container) {
        this.container = container;
        this.oldState = container.getState();
        this.newState = oldState.toggle();
    }
    
    public ToggleContainerStateCommand(Container container, ContainerState newState) {
        this.container = container;
        this.oldState = container.getState();
        this.newState = newState;
    }
    
    @Override
    public void execute() {
        // v5.2: setState() 会自动调用 ContainerVisibilityService
        container.setState(newState);
        
        LOGGER.debug("[ToggleContainerStateCommand] 容器 {} 状态: {} -> {}", 
            container.getName(), oldState.getDisplayName(), newState.getDisplayName());
    }
    
    @Override
    public void undo() {
        // v5.2: setState() 会自动调用 ContainerVisibilityService
        container.setState(oldState);
        
        LOGGER.debug("[ToggleContainerStateCommand] 撤销：容器 {} 状态: {} -> {}", 
            container.getName(), newState.getDisplayName(), oldState.getDisplayName());
    }
    
    @Override
    public String getDescription() {
        return String.format("切换容器 \"%s\" 状态: %s -> %s", 
            container.getName(), 
            oldState.getDisplayName(), 
            newState.getDisplayName());
    }
}

