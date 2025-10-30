package com.chronoblocks.core.selection.history;

import com.chronoblocks.core.selection.SelectionState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🎯 选择操作历史管理器
 * 
 * 提供选择操作的撤销/重做功能，包括：
 * - 操作历史记录
 * - 撤销/重做操作
 * - 历史状态管理
 * - 操作合并优化
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionHistoryManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionHistoryManager.class);
    
    /** 单例实例 */
    private static volatile SelectionHistoryManager INSTANCE;
    
    /** 历史记录列表 */
    private final List<SelectionHistoryEntry> history = new ArrayList<>();
    
    /** 当前历史位置 */
    private int currentIndex = -1;
    
    /** 最大历史记录数量 */
    private int maxHistorySize = 50;
    
    /** 是否启用历史记录 */
    private boolean enabled = true;
    
    /** 是否启用操作合并 */
    private boolean enableOperationMerging = true;
    
    /** 操作合并时间窗口（毫秒） */
    private long mergeTimeWindow = 1000;
    
    private SelectionHistoryManager() {
        LOGGER.info("[SelectionHistoryManager] 初始化选择操作历史管理器");
    }
    
    /**
     * 获取单例实例
     */
    public static SelectionHistoryManager getInstance() {
        if (INSTANCE == null) {
            synchronized (SelectionHistoryManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SelectionHistoryManager();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 记录选择操作
     */
    public void recordOperation(SelectionState beforeState, SelectionState afterState, String operation, String toolId) {
        if (!enabled) return;
        
        try {
            SelectionHistoryEntry entry = new SelectionHistoryEntry(
                beforeState, afterState, operation, toolId, System.currentTimeMillis()
            );
            
            // 检查是否可以合并操作
            if (enableOperationMerging && canMergeWithLastEntry(entry)) {
                mergeWithLastEntry(entry);
            } else {
                addNewEntry(entry);
            }
            
            LOGGER.debug("[SelectionHistoryManager] 记录操作: {} (工具: {})", operation, toolId);
            
        } catch (Exception e) {
            LOGGER.error("[SelectionHistoryManager] 记录操作失败", e);
        }
    }
    
    /**
     * 撤销操作
     */
    public SelectionState undo() {
        if (!canUndo()) {
            LOGGER.warn("[SelectionHistoryManager] 无法撤销操作");
            return null;
        }
        
        try {
            SelectionHistoryEntry entry = history.get(currentIndex);
            currentIndex--;
            
            LOGGER.info("[SelectionHistoryManager] 撤销操作: {} (工具: {})", entry.getOperation(), entry.getToolId());
            
            return entry.getBeforeState();
            
        } catch (Exception e) {
            LOGGER.error("[SelectionHistoryManager] 撤销操作失败", e);
            return null;
        }
    }
    
    /**
     * 重做操作
     */
    public SelectionState redo() {
        if (!canRedo()) {
            LOGGER.warn("[SelectionHistoryManager] 无法重做操作");
            return null;
        }
        
        try {
            currentIndex++;
            SelectionHistoryEntry entry = history.get(currentIndex);
            
            LOGGER.info("[SelectionHistoryManager] 重做操作: {} (工具: {})", entry.getOperation(), entry.getToolId());
            
            return entry.getAfterState();
            
        } catch (Exception e) {
            LOGGER.error("[SelectionHistoryManager] 重做操作失败", e);
            return null;
        }
    }
    
    /**
     * 清空历史记录
     */
    public void clearHistory() {
        history.clear();
        currentIndex = -1;
        LOGGER.info("[SelectionHistoryManager] 清空历史记录");
    }
    
    /**
     * 检查是否可以撤销
     */
    public boolean canUndo() {
        return enabled && currentIndex >= 0 && !history.isEmpty();
    }
    
    /**
     * 检查是否可以重做
     */
    public boolean canRedo() {
        return enabled && currentIndex < history.size() - 1 && !history.isEmpty();
    }
    
    /**
     * 获取当前历史位置
     */
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    /**
     * 获取历史记录总数
     */
    public int getHistorySize() {
        return history.size();
    }
    
    /**
     * 获取历史记录信息
     */
    public List<SelectionHistoryInfo> getHistoryInfo() {
        List<SelectionHistoryInfo> infoList = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            SelectionHistoryEntry entry = history.get(i);
            infoList.add(new SelectionHistoryInfo(
                i, entry.getOperation(), entry.getToolId(), entry.getTimestamp(), i == currentIndex
            ));
        }
        return infoList;
    }
    
    /**
     * 跳转到指定历史位置
     */
    public SelectionState jumpToHistory(int index) {
        if (index < 0 || index >= history.size()) {
            LOGGER.warn("[SelectionHistoryManager] 无效的历史位置: {}", index);
            return null;
        }
        
        try {
            currentIndex = index;
            SelectionHistoryEntry entry = history.get(currentIndex);
            
            LOGGER.info("[SelectionHistoryManager] 跳转到历史位置: {} (操作: {})", index, entry.getOperation());
            
            return entry.getAfterState();
            
        } catch (Exception e) {
            LOGGER.error("[SelectionHistoryManager] 跳转历史位置失败", e);
            return null;
        }
    }
    
    /**
     * 添加新的历史条目
     */
    private void addNewEntry(SelectionHistoryEntry entry) {
        // 移除当前位置之后的所有条目（如果有重做操作）
        if (currentIndex < history.size() - 1) {
            history.subList(currentIndex + 1, history.size()).clear();
        }
        
        // 添加新条目
        history.add(entry);
        currentIndex = history.size() - 1;
        
        // 限制历史记录大小
        if (history.size() > maxHistorySize) {
            history.remove(0);
            currentIndex--;
        }
    }
    
    /**
     * 检查是否可以与最后一个条目合并
     */
    private boolean canMergeWithLastEntry(SelectionHistoryEntry newEntry) {
        if (history.isEmpty() || currentIndex < 0) {
            return false;
        }
        
        SelectionHistoryEntry lastEntry = history.get(currentIndex);
        
        // 检查操作类型是否相同
        if (!lastEntry.getOperation().equals(newEntry.getOperation())) {
            return false;
        }
        
        // 检查工具是否相同
        if (!lastEntry.getToolId().equals(newEntry.getToolId())) {
            return false;
        }
        
        // 检查时间窗口
        long timeDiff = newEntry.getTimestamp() - lastEntry.getTimestamp();
        if (timeDiff > mergeTimeWindow) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 与最后一个条目合并
     */
    private void mergeWithLastEntry(SelectionHistoryEntry newEntry) {
        SelectionHistoryEntry lastEntry = history.get(currentIndex);
        
        // 创建合并后的条目
        SelectionHistoryEntry mergedEntry = new SelectionHistoryEntry(
            lastEntry.getBeforeState(),
            newEntry.getAfterState(),
            lastEntry.getOperation(),
            lastEntry.getToolId(),
            newEntry.getTimestamp()
        );
        
        // 替换最后一个条目
        history.set(currentIndex, mergedEntry);
        
        LOGGER.debug("[SelectionHistoryManager] 合并操作: {}", mergedEntry.getOperation());
    }
    
    // Getters and Setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[SelectionHistoryManager] 历史记录{}", enabled ? "启用" : "禁用");
    }
    
    public int getMaxHistorySize() {
        return maxHistorySize;
    }
    
    public void setMaxHistorySize(int maxHistorySize) {
        this.maxHistorySize = Math.max(1, maxHistorySize);
        LOGGER.info("[SelectionHistoryManager] 最大历史记录数量设置为: {}", this.maxHistorySize);
    }
    
    public boolean isEnableOperationMerging() {
        return enableOperationMerging;
    }
    
    public void setEnableOperationMerging(boolean enableOperationMerging) {
        this.enableOperationMerging = enableOperationMerging;
        LOGGER.info("[SelectionHistoryManager] 操作合并{}", enableOperationMerging ? "启用" : "禁用");
    }
    
    public long getMergeTimeWindow() {
        return mergeTimeWindow;
    }
    
    public void setMergeTimeWindow(long mergeTimeWindow) {
        this.mergeTimeWindow = Math.max(0, mergeTimeWindow);
        LOGGER.info("[SelectionHistoryManager] 操作合并时间窗口设置为: {}ms", this.mergeTimeWindow);
    }
    
    /**
     * 选择历史条目
     */
    public static class SelectionHistoryEntry {
        private final SelectionState beforeState;
        private final SelectionState afterState;
        private final String operation;
        private final String toolId;
        private final long timestamp;
        
        public SelectionHistoryEntry(SelectionState beforeState, SelectionState afterState, 
                                   String operation, String toolId, long timestamp) {
            this.beforeState = beforeState;
            this.afterState = afterState;
            this.operation = operation;
            this.toolId = toolId;
            this.timestamp = timestamp;
        }
        
        public SelectionState getBeforeState() {
            return beforeState;
        }
        
        public SelectionState getAfterState() {
            return afterState;
        }
        
        public String getOperation() {
            return operation;
        }
        
        public String getToolId() {
            return toolId;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return "SelectionHistoryEntry{" +
                   "operation='" + operation + '\'' +
                   ", toolId='" + toolId + '\'' +
                   ", timestamp=" + timestamp +
                   '}';
        }
    }
    
    /**
     * 选择历史信息
     */
    public static class SelectionHistoryInfo {
        private final int index;
        private final String operation;
        private final String toolId;
        private final long timestamp;
        private final boolean isCurrent;
        
        public SelectionHistoryInfo(int index, String operation, String toolId, long timestamp, boolean isCurrent) {
            this.index = index;
            this.operation = operation;
            this.toolId = toolId;
            this.timestamp = timestamp;
            this.isCurrent = isCurrent;
        }
        
        public int getIndex() {
            return index;
        }
        
        public String getOperation() {
            return operation;
        }
        
        public String getToolId() {
            return toolId;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public boolean isCurrent() {
            return isCurrent;
        }
        
        @Override
        public String toString() {
            return "SelectionHistoryInfo{" +
                   "index=" + index +
                   ", operation='" + operation + '\'' +
                   ", toolId='" + toolId + '\'' +
                   ", isCurrent=" + isCurrent +
                   '}';
        }
    }
}
