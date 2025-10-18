package com.chronoblocks.core.container.commands;

/**
 * 容器命令接口
 * 支持撤销/重做功能
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public interface ContainerCommand {
    /**
     * 执行命令
     */
    void execute();
    
    /**
     * 撤销命令
     */
    void undo();
    
    /**
     * 获取命令描述
     */
    String getDescription();
}

