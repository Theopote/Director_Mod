package com.director.core.container.commands;

import com.director.core.container.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 切换实时接收命令 (v5.0)
 * <p>支持撤销/重做</p>
 * 
 * @author Director Team
 * @version 5.0
 */
public class ToggleIntakeCommand implements ContainerCommand {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleIntakeCommand.class);
    
    private final Container container;
    private final boolean oldState;
    private final boolean newState;
    
    public ToggleIntakeCommand(Container container) {
        this.container = container;
        this.oldState = container.isIntakeEnabled();
        this.newState = !oldState;
    }
    
    public ToggleIntakeCommand(Container container, boolean newState) {
        this.container = container;
        this.oldState = container.isIntakeEnabled();
        this.newState = newState;
    }
    
    @Override
    public void execute() {
        container.setIntakeEnabled(newState);
        
        LOGGER.info("[ToggleIntakeCommand] 容器 {} 实时接收: {} -> {}", 
            container.getName(), oldState, newState);
    }
    
    @Override
    public void undo() {
        container.setIntakeEnabled(oldState);
        
        LOGGER.info("[ToggleIntakeCommand] 撤销：容器 {} 实时接收: {} -> {}", 
            container.getName(), newState, oldState);
    }
    
    @Override
    public String getDescription() {
        return String.format("切换容器 \"%s\" 实时接收: %s", 
            container.getName(), 
            newState ? "开启" : "关闭");
    }
}

