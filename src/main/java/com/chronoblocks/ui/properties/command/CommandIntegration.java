package com.chronoblocks.ui.properties.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * 🔗 命令集成管理器 v1.0 - ChronoBlocks Properties System
 * <p>
 * 管理属性修改命令的撤销/重做栈。
 * 提供统一的命令执行、撤销、重做接口。
 * </p>
 * 
 * <h3>核心功能</h3>
 * <ul>
 *   <li>执行命令并记录到历史</li>
 *   <li>撤销最近的命令</li>
 *   <li>重做已撤销的命令</li>
 *   <li>命令合并（连续修改同一属性）</li>
 *   <li>命令历史查看</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 执行命令
 * PropertyModifyCommand cmd = new PropertyModifyCommand(kf, "time", 0.5f, 1.0f);
 * CommandIntegration.execute(cmd);
 * 
 * // 撤销
 * CommandIntegration.undo();
 * 
 * // 重做
 * CommandIntegration.redo();
 * 
 * // 清空历史
 * CommandIntegration.clear();
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2025-10-16
 */
public class CommandIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandIntegration.class);
    
    // ==================== 命令栈 ====================
    
    /** 撤销栈（已执行的命令） */
    private static final Stack<PropertyModifyCommand> undoStack = new Stack<>();
    
    /** 重做栈（已撤销的命令） */
    private static final Stack<PropertyModifyCommand> redoStack = new Stack<>();
    
    /** 最大历史记录数 */
    private static int MAX_HISTORY_SIZE = 100;
    
    /** 命令合并时间窗口（毫秒） */
    private static long MERGE_WINDOW_MS = 1000;
    
    /** 上次命令执行时间 */
    private static long lastCommandTime = 0;
    
    /** 是否启用命令合并 */
    private static boolean mergeEnabled = true;
    
    // ==================== 命令执行 ====================
    
    /**
     * 执行命令
     * 
     * @param command 要执行的命令
     */
    public static synchronized void execute(PropertyModifyCommand command) {
        if (command == null) {
            LOGGER.warn("[CommandIntegration] 命令为 null，跳过执行");
            return;
        }
        
        try {
            // 执行命令
            command.execute();
            
            // 尝试合并命令
            long currentTime = System.currentTimeMillis();
            boolean merged = false;
            
            if (mergeEnabled && !undoStack.isEmpty()) {
                PropertyModifyCommand lastCommand = undoStack.peek();
                
                // 检查是否可以合并
                if (lastCommand.canMergeWith(command) && 
                    (currentTime - lastCommandTime) < MERGE_WINDOW_MS) {
                    
                    // 合并命令
                    PropertyModifyCommand mergedCommand = lastCommand.mergeWith(command);
                    undoStack.pop();
                    undoStack.push(mergedCommand);
                    merged = true;
                    
                    LOGGER.debug("[CommandIntegration] 命令已合并: {}", mergedCommand.getDescription());
                }
            }
            
            // 如果没有合并，添加新命令
            if (!merged) {
                undoStack.push(command);
                LOGGER.debug("[CommandIntegration] 命令已执行: {}", command.getDescription());
            }
            
            // 清空重做栈
            redoStack.clear();
            
            // 限制历史大小
            if (undoStack.size() > MAX_HISTORY_SIZE) {
                undoStack.remove(0);
                LOGGER.debug("[CommandIntegration] 移除最旧的命令");
            }
            
            lastCommandTime = currentTime;
            
        } catch (Exception e) {
            LOGGER.error("[CommandIntegration] 执行命令失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 撤销最近的命令
     * 
     * @return 如果撤销成功则返回 true
     */
    public static synchronized boolean undo() {
        if (undoStack.isEmpty()) {
            LOGGER.debug("[CommandIntegration] 撤销栈为空，无法撤销");
            return false;
        }
        
        try {
            PropertyModifyCommand command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            
            LOGGER.info("[CommandIntegration] 已撤销: {}", command.getDescription());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("[CommandIntegration] 撤销失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 重做最近撤销的命令
     * 
     * @return 如果重做成功则返回 true
     */
    public static synchronized boolean redo() {
        if (redoStack.isEmpty()) {
            LOGGER.debug("[CommandIntegration] 重做栈为空，无法重做");
            return false;
        }
        
        try {
            PropertyModifyCommand command = redoStack.pop();
            command.redo();
            undoStack.push(command);
            
            LOGGER.info("[CommandIntegration] 已重做: {}", command.getDescription());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("[CommandIntegration] 重做失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 清空所有历史
     */
    public static synchronized void clear() {
        int undoCount = undoStack.size();
        int redoCount = redoStack.size();
        
        undoStack.clear();
        redoStack.clear();
        lastCommandTime = 0;
        
        LOGGER.info("[CommandIntegration] 已清空历史: {} 个撤销命令, {} 个重做命令", 
            undoCount, redoCount);
    }
    
    // ==================== 查询方法 ====================
    
    /**
     * 检查是否可以撤销
     */
    public static boolean canUndo() {
        return !undoStack.isEmpty();
    }
    
    /**
     * 检查是否可以重做
     */
    public static boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    /**
     * 获取撤销栈大小
     */
    public static int getUndoCount() {
        return undoStack.size();
    }
    
    /**
     * 获取重做栈大小
     */
    public static int getRedoCount() {
        return redoStack.size();
    }
    
    /**
     * 获取下一个要撤销的命令描述
     */
    public static String getNextUndoDescription() {
        if (undoStack.isEmpty()) {
            return null;
        }
        return undoStack.peek().getDescription();
    }
    
    /**
     * 获取下一个要重做的命令描述
     */
    public static String getNextRedoDescription() {
        if (redoStack.isEmpty()) {
            return null;
        }
        return redoStack.peek().getDescription();
    }
    
    /**
     * 获取撤销历史
     */
    public static List<String> getUndoHistory() {
        List<String> history = new ArrayList<>();
        for (PropertyModifyCommand cmd : undoStack) {
            history.add(cmd.getDescription());
        }
        return history;
    }
    
    /**
     * 获取重做历史
     */
    public static List<String> getRedoHistory() {
        List<String> history = new ArrayList<>();
        for (PropertyModifyCommand cmd : redoStack) {
            history.add(cmd.getDescription());
        }
        return history;
    }
    
    // ==================== 配置方法 ====================
    
    /**
     * 设置最大历史记录数
     */
    public static void setMaxHistorySize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("最大历史记录数必须大于0");
        }
        MAX_HISTORY_SIZE = size;
        LOGGER.info("[CommandIntegration] 最大历史记录数已设置为: {}", size);
    }
    
    /**
     * 设置命令合并时间窗口
     */
    public static void setMergeWindow(long milliseconds) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("合并时间窗口必须大于等于0");
        }
        MERGE_WINDOW_MS = milliseconds;
        LOGGER.info("[CommandIntegration] 命令合并时间窗口已设置为: {} ms", milliseconds);
    }
    
    /**
     * 启用/禁用命令合并
     */
    public static void setMergeEnabled(boolean enabled) {
        mergeEnabled = enabled;
        LOGGER.info("[CommandIntegration] 命令合并已{}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 获取调试信息
     */
    public static String getDebugInfo() {
        return String.format(
            "CommandIntegration [撤销栈: %d, 重做栈: %d, 合并启用: %s, 合并窗口: %d ms]",
            undoStack.size(),
            redoStack.size(),
            mergeEnabled,
            MERGE_WINDOW_MS
        );
    }
}

