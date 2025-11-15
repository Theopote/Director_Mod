package com.chronoblocks.core.selection;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import java.util.function.Consumer; // 暂时不使用

/**
 * 🌉 SelectionToContainerBridge - 选择工具与容器系统的桥梁
 * 
 * 负责将选择工具的选择结果转换为容器系统中的容器：
 * - 自动创建容器
 * - 批量添加方块到容器
 * - 管理选择状态与容器状态的同步
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionToContainerBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToContainerBridge.class);
    
    private final SelectionManager selectionManager;
    private final ContainerManager containerManager;
    
    public SelectionToContainerBridge() {
        this.selectionManager = SelectionManager.get();
        this.containerManager = ContainerManager.getInstance();
        
        // 注册选择变化监听器
        setupSelectionListener();
    }
    
    /**
     * 设置选择变化监听器
     */
    private void setupSelectionListener() {
        selectionManager.addSelectionChangeListener(this::onSelectionChanged);
        LOGGER.info("[SelectionToContainerBridge] 已注册选择变化监听器");
    }
    
    /**
     * 处理选择状态变化
     */
    private void onSelectionChanged(SelectionState selection) {
        if (selection == null || selection.isEmpty()) {
            LOGGER.debug("[SelectionToContainerBridge] 选择为空，跳过处理");
            return;
        }
        
        LOGGER.debug("[SelectionToContainerBridge] 选择状态变化，包含 {} 个方块", selection.size());
        
        // 这里可以添加自动创建容器的逻辑
        // 例如：当选择达到一定数量时自动创建容器
    }
    
    /**
     * 将当前选择创建为新容器
     */
    public Container createContainerFromSelection(String containerName, ContainerType type) {
        SelectionState selection = selectionManager.getCurrentSelection();
        
        if (selection.isEmpty()) {
            LOGGER.warn("[SelectionToContainerBridge] 当前选择为空，无法创建容器");
            return null;
        }
        
        // 创建新容器
        Container container = new Container(containerName, type);
        
        // 添加选择的方块到容器
        container.addBlocks(selection.getSelectedBlocks());
        
        // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
        
        // 设置容器的元数据
        container.setMetadata("sourceTool", selection.getSourceToolId());
        container.setMetadata("selectionTime", selection.getTimestamp());
        container.setMetadata("blockCount", selection.size());
        
        // 注册到容器管理器
        if (containerManager != null) {
            containerManager.addContainer(container);
            LOGGER.debug("[SelectionToContainerBridge] 成功创建容器 '{}'，包含 {} 个方块", 
                       containerName, selection.size());
        } else {
            LOGGER.warn("[SelectionToContainerBridge] 容器管理器未初始化");
        }
        
        return container;
    }
    
    /**
     * 将当前选择添加到指定容器
     */
    public boolean addSelectionToContainer(Container container) {
        if (container == null) {
            LOGGER.warn("[SelectionToContainerBridge] 目标容器为空");
            return false;
        }
        
        SelectionState selection = selectionManager.getCurrentSelection();
        if (selection.isEmpty()) {
            LOGGER.warn("[SelectionToContainerBridge] 当前选择为空，无法添加到容器");
            return false;
        }
        
        // 添加选择的方块到容器
        container.addBlocks(selection.getSelectedBlocks());
        
        // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
        LOGGER.debug("[SelectionToContainerBridge] 成功将 {} 个方块添加到容器 '{}'", 
                   selection.size(), container.getName());
        
        return true;
    }
    
    /**
     * 从容器中移除当前选择
     */
    public boolean removeSelectionFromContainer(Container container) {
        if (container == null) {
            LOGGER.warn("[SelectionToContainerBridge] 目标容器为空");
            return false;
        }
        
        SelectionState selection = selectionManager.getCurrentSelection();
        if (selection.isEmpty()) {
            LOGGER.warn("[SelectionToContainerBridge] 当前选择为空，无法从容器移除");
            return false;
        }
        
        // 从容器中移除选择的方块
        for (var pos : selection.getSelectedBlocks()) {
            container.removeBlock(pos);
        }
        
        LOGGER.debug("[SelectionToContainerBridge] 成功从容器 '{}' 中移除 {} 个方块", 
                   container.getName(), selection.size());
        
        return true;
    }
    
    /**
     * 选择指定容器中的所有方块
     */
    public void selectContainerBlocks(Container container) {
        if (container == null) {
            LOGGER.warn("[SelectionToContainerBridge] 容器为空，无法选择");
            return;
        }
        
        // 创建新的选择状态
        SelectionState containerSelection = new SelectionState("container");
        containerSelection.addBlocks(container.getBlocks());
        containerSelection.setName(container.getName());
        
        // 这里需要设置到选择管理器中
        // 由于SelectionManager没有直接设置选择的方法，我们通过工具来处理
        LOGGER.debug("[SelectionToContainerBridge] 选择容器 '{}' 中的 {} 个方块", 
                   container.getName(), container.getBlockCount());
    }
    
    /**
     * 获取当前选择的容器信息
     */
    public Container getCurrentSelectionContainer() {
        SelectionState selection = selectionManager.getCurrentSelection();
        if (selection.isEmpty()) {
            return null;
        }
        
        // 查找包含当前选择的容器
        if (containerManager != null) {
            for (Container container : containerManager.getAllContainers()) {
                // 检查容器是否包含选择中的任何方块
                boolean containsAny = false;
                for (var pos : selection.getSelectedBlocks()) {
                    if (container.containsBlock(pos)) {
                        containsAny = true;
                        break;
                    }
                }
                if (containsAny) {
                    return container;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 清除当前选择
     */
    public void clearCurrentSelection() {
        selectionManager.clearCurrentSelection();
        LOGGER.debug("[SelectionToContainerBridge] 清除当前选择");
    }
    
    /**
     * 获取选择统计信息
     */
    public SelectionStats getSelectionStats() {
        SelectionState selection = selectionManager.getCurrentSelection();
        return new SelectionStats(selection);
    }
    
    /**
     * 选择统计信息类
     */
    public static class SelectionStats {
        private final int blockCount;
        private final String sourceTool;
        private final long timestamp;
        private final boolean isEmpty;
        
        public SelectionStats(SelectionState selection) {
            this.blockCount = selection != null ? selection.size() : 0;
            this.sourceTool = selection != null ? selection.getSourceToolId() : "none";
            this.timestamp = selection != null ? selection.getTimestamp() : 0;
            this.isEmpty = selection == null || selection.isEmpty();
        }
        
        public int getBlockCount() { return blockCount; }
        public String getSourceTool() { return sourceTool; }
        public long getTimestamp() { return timestamp; }
        public boolean isEmpty() { return isEmpty; }
        
        @Override
        public String toString() {
            return String.format("SelectionStats{blocks=%d, tool=%s, empty=%s}", 
                               blockCount, sourceTool, isEmpty);
        }
    }
}
