package com.chronoblocks.core.timeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Timeline 命令历史管理器
 * 实现撤销/重做功能栈
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineCommandHistory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCommandHistory.class);
    
    private final List<TimelineCommand> undoStack = new ArrayList<>();
    private final List<TimelineCommand> redoStack = new ArrayList<>();
    private int maxHistorySize = 100;
    
    /**
     * 执行命令并加入历史栈
     */
    public void executeCommand(TimelineCommand command) {
        command.execute();
        
        if (command.canUndo()) {
            undoStack.add(command);
            redoStack.clear(); // 执行新命令后清空重做栈
            
            // 限制历史栈大小
            if (undoStack.size() > maxHistorySize) {
                undoStack.remove(0);
            }
            
            LOGGER.debug("[Timeline] 执行命令: {}", command.getDescription());
        }
    }
    
    /**
     * 撤销上一个命令
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            LOGGER.debug("[Timeline] 无可撤销的命令");
            return false;
        }
        
        TimelineCommand command = undoStack.remove(undoStack.size() - 1);
        command.undo();
        redoStack.add(command);
        
        LOGGER.info("[Timeline] 撤销命令: {}", command.getDescription());
        return true;
    }
    
    /**
     * 重做上一个撤销的命令
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            LOGGER.debug("[Timeline] 无可重做的命令");
            return false;
        }
        
        TimelineCommand command = redoStack.remove(redoStack.size() - 1);
        command.execute();
        undoStack.add(command);
        
        LOGGER.info("[Timeline] 重做命令: {}", command.getDescription());
        return true;
    }
    
    /**
     * 清空所有历史
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        LOGGER.debug("[Timeline] 清空命令历史");
    }
    
    /**
     * 获取可撤销的命令数量
     */
    public int getUndoSize() {
        return undoStack.size();
    }
    
    /**
     * 获取可重做的命令数量
     */
    public int getRedoSize() {
        return redoStack.size();
    }
    
    /**
     * 是否可以撤销
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }
    
    /**
     * 是否可以重做
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    /**
     * 设置最大历史大小
     */
    public void setMaxHistorySize(int size) {
        this.maxHistorySize = Math.max(10, size);
    }
}

