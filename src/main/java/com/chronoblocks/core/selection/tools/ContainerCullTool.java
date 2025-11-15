package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.commands.RemoveBlocksFromContainerCommand;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 容器剔除工具 (v5.0)
 * <p>从当前激活容器中移除方块的工具
 * 
 * <p>功能特性：
 * <ul>
 *   <li>只能选择激活容器内的方块</li>
 *   <li>红色半透明预览</li>
 *   <li>右键确认剔除</li>
 *   <li>ESC取消</li>
 *   <li>支持Undo/Redo</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 5.0
 */
public class ContainerCullTool implements SelectionTool {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerCullTool.class);
    
    // ========== 工具标识 ==========
    private static final String TOOL_ID = "container_cull";
    private static final String TOOL_NAME = "容器剔除工具";
    private static final String TOOL_ICON = "✂️";
    
    // ========== 选择状态 ==========
    private final Set<BlockPos> selectedBlocks = new HashSet<>();
    private Container targetContainer = null;
    
    // ========== 预览颜色 ==========
    private static final int PREVIEW_COLOR = 0xFFFF3333; // 红色半透明
    
    @Override
    public String getId() {
        return TOOL_ID;
    }
    
    @Override
    public String getDisplayName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "从容器中剔除不需要的方块";
    }
    
    @Override
    public String getIcon() {
        return TOOL_ICON;
    }
    
    @Override
    public void onActivate(World world) {
        // 获取激活容器
        ContainerManager manager = ContainerManager.getInstance();
        targetContainer = manager != null ? manager.getActive() : null;
        
        if (targetContainer == null) {
            LOGGER.warn("[ContainerCullTool] 没有激活的容器，无法使用剔除工具");
        } else {
            LOGGER.info("[ContainerCullTool] 激活剔除工具，目标容器: {}", 
                targetContainer.getName());
        }
        
        selectedBlocks.clear();
    }
    
    @Override
    public void onDeactivate(World world) {
        selectedBlocks.clear();
        targetContainer = null;
        LOGGER.info("[ContainerCullTool] 停用剔除工具");
    }
    
    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (targetContainer == null) {
            LOGGER.warn("[ContainerCullTool] 没有激活的容器");
            return;
        }
        
        // 只能选择容器内的方块
        if (!targetContainer.containsBlock(pos)) {
            LOGGER.debug("[ContainerCullTool] 方块 {} 不在容器 {} 内", 
                pos, targetContainer.getName());
            return;
        }
        
        // 切换选择
        if (selectedBlocks.contains(pos)) {
            selectedBlocks.remove(pos);
            LOGGER.debug("[ContainerCullTool] 取消选择方块: {}", pos);
        } else {
            selectedBlocks.add(pos);
            LOGGER.debug("[ContainerCullTool] 选择方块: {}", pos);
        }
        
        // 更新选择状态
        updateSelection();
    }
    
    @Override
    public void onRightClick(World world, BlockPos pos) {
        // 右键确认剔除
        confirmCull();
    }
    
    @Override
    public SelectionState getCurrentSelection() {
        SelectionState state = new SelectionState(TOOL_ID);
        state.addBlocks(selectedBlocks);
        state.setHighlightColor(PREVIEW_COLOR);
        state.setName("待剔除方块");
        return state;
    }
    
    @Override
    public void clearSelection() {
        selectedBlocks.clear();
        updateSelection();
    }
    
    /**
     * 确认剔除
     */
    private void confirmCull() {
        if (targetContainer == null) {
            LOGGER.warn("[ContainerCullTool] 没有激活的容器");
            return;
        }
        
        if (selectedBlocks.isEmpty()) {
            LOGGER.info("[ContainerCullTool] 没有选中要剔除的方块");
            return;
        }
        
        // 创建命令并执行
        RemoveBlocksFromContainerCommand command = new RemoveBlocksFromContainerCommand(
            targetContainer, 
            selectedBlocks
        );
        
        // 执行命令
        command.execute();
        
        LOGGER.info("[ContainerCullTool] 从容器 {} 剔除 {} 个方块", 
            targetContainer.getName(), selectedBlocks.size());
        
        // 清空选择
        selectedBlocks.clear();
        updateSelection();
        
        // 可以选择性地切换回默认工具
        // SelectionManager.get().setCurrentTool(null);
    }
    
    /**
     * 取消剔除
     */
    public void cancelCull() {
        LOGGER.info("[ContainerCullTool] 取消剔除");
        selectedBlocks.clear();
        updateSelection();
    }
    
    /**
     * 更新选择状态
     * 
     * <p>注意：SelectionManager会自动通过getCurrentSelection()获取状态，
     * 因此这里只需要等待下一次渲染周期即可。
     */
    private void updateSelection() {
        // SelectionManager会在下一次渲染时自动获取getCurrentSelection()的结果
        // 不需要手动触发通知
    }
    
    /**
     * 获取当前选中的方块数量
     */
    public int getSelectedBlockCount() {
        return selectedBlocks.size();
    }
    
    /**
     * 获取目标容器
     */
    public Container getTargetContainer() {
        return targetContainer;
    }
    
    /**
     * 检查是否有激活的容器
     */
    public boolean hasActiveContainer() {
        return targetContainer != null;
    }
}

