package com.chronoblocks.core.timeline;

/**
 * Timeline 命令模式接口（Command Pattern）
 * 用于实现撤销/重做功能和右键菜单操作
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface TimelineCommand {
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
    
    /**
     * 是否可以撤销
     */
    default boolean canUndo() {
        return true;
    }
}

