package com.director.core.selection.tools;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.commands.RemoveBlocksFromContainerCommand;
import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import com.director.ui.container.ContainerCommandBridge;
import com.director.ui.theme.Icons;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * @author Director Team
 * @version 5.0
 */
public class ContainerCullTool implements SelectionTool {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerCullTool.class);
    
    // ========== 工具标识 ==========
    private static final String TOOL_ID = "container_cull";
    private static final String TOOL_ICON = Icons.CUT;
    
    // ========== 选择状态 ==========
    private final Set<BlockPos> selectedBlocks = new HashSet<>();
    private Container targetContainer = null;
    private BlockPos lastDragPos = null;
    private boolean multiSelectEnabled = false;
    
    // ========== 预览颜色 ==========
    private static final int PREVIEW_COLOR = 0xFFFF3333; // 红色半透明

    private final SelectionState previewState = new SelectionState(TOOL_ID);
    
    @Override
    public String getId() {
        return TOOL_ID;
    }
    
    @Override
    public String getIcon() {
        return TOOL_ICON;
    }
    
    @Override
    public void onActivate(World world) {
        refreshActiveContainer();
        selectedBlocks.clear();
        syncToSelectionManager();
    }
    
    @Override
    public void onDeactivate(World world) {
        selectedBlocks.clear();
        syncToSelectionManager();
        targetContainer = null;
        lastDragPos = null;
        LOGGER.info("[ContainerCullTool] 停用剔除工具");
    }
    
    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        refreshActiveContainer();
        if (targetContainer == null || pos == null) {
            LOGGER.warn("[ContainerCullTool] 没有可用容器（未激活且未选中）");
            return;
        }
        
        // 只能选择容器内的方块
        if (!targetContainer.containsBlock(pos)) {
            LOGGER.debug("[ContainerCullTool] 方块 {} 不在容器 {} 内", 
                pos, targetContainer.getName());
            return;
        }
        
        // 默认单选；多选由属性面板复选框控制，避免占用 Shift。
        if (!multiSelectEnabled) {
            selectedBlocks.clear();
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
        syncToSelectionManager();
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        refreshActiveContainer();
        lastDragPos = null;
        tryAddDuringDrag(start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        tryAddDuringDrag(current);
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        tryAddDuringDrag(end);
        lastDragPos = null;
    }
    
    @Override
    public void onRightClick(World world, BlockPos pos) {
        // 右键确认剔除
        confirmCull();
    }
    
    @Override
    public SelectionState getCurrentSelection() {
        previewState.clear();
        previewState.addBlocks(selectedBlocks);
        previewState.setHighlightColor(PREVIEW_COLOR);
        previewState.setName("待剔除方块");
        return previewState;
    }
    
    @Override
    public void clearSelection() {
        selectedBlocks.clear();
        syncToSelectionManager();
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !selectedBlocks.isEmpty();
    }
    
    /**
     * 确认剔除
     */
    public void confirmCull() {
        refreshActiveContainer();
        if (targetContainer == null) {
            LOGGER.warn("[ContainerCullTool] 没有可用容器（未激活且未选中）");
            return;
        }
        
        if (selectedBlocks.isEmpty()) {
            LOGGER.info("[ContainerCullTool] 没有选中要剔除的方块");
            return;
        }
        
        Set<BlockPos> validBlocks = new LinkedHashSet<>();
        for (BlockPos pos : selectedBlocks) {
            if (targetContainer.containsBlock(pos)) {
                validBlocks.add(pos);
            }
        }
        if (validBlocks.isEmpty()) {
            LOGGER.info("[ContainerCullTool] 选中的方块已不在容器中，取消剔除");
            selectedBlocks.clear();
            syncToSelectionManager();
            return;
        }

        // 创建命令并执行
        RemoveBlocksFromContainerCommand command = new RemoveBlocksFromContainerCommand(
            targetContainer, 
            validBlocks
        );
        
        // 通过统一历史入口执行；若历史未绑定则自动退化为直接执行。
        ContainerCommandBridge.execute(command);
        
        LOGGER.info("[ContainerCullTool] 从容器 {} 剔除 {} 个方块", 
            targetContainer.getName(), validBlocks.size());
        
        // 清空选择
        selectedBlocks.clear();
        syncToSelectionManager();
        
        // 可以选择性地切换回默认工具
        // SelectionManager.get().setCurrentTool(null);
    }
    
    /**
     * 取消剔除
     */
    public void cancelCull() {
        LOGGER.info("[ContainerCullTool] 取消剔除");
        selectedBlocks.clear();
        syncToSelectionManager();
    }

    public boolean canConfirmCull() {
        return targetContainer != null && !selectedBlocks.isEmpty();
    }

    public boolean isMultiSelectEnabled() {
        return multiSelectEnabled;
    }

    public void setMultiSelectEnabled(boolean enabled) {
        this.multiSelectEnabled = enabled;
    }

    public int importFromCurrentSelection() {
        refreshActiveContainer();
        if (targetContainer == null) {
            return 0;
        }
        var current = DirectorServices.get().selection().getSelectionSet().asUnmodifiableSet();
        selectedBlocks.clear();
        for (BlockPos pos : current) {
            if (targetContainer.containsBlock(pos)) {
                selectedBlocks.add(pos);
            }
        }
        syncToSelectionManager();
        return selectedBlocks.size();
    }
    
    /**
     * 更新选择状态
     * 
     * <p>注意：SelectionManager会自动通过getCurrentSelection()获取状态，
     * 因此这里只需要等待下一次渲染周期即可。
     */
    private void syncToSelectionManager() {
        var selectionManager = DirectorServices.get().selection();
        selectionManager.clearSelection();
        if (!selectedBlocks.isEmpty()) {
            selectionManager.addAllToSelection(selectedBlocks);
        }
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

    public String getActiveContainerName() {
        return targetContainer != null ? targetContainer.getName() : "";
    }

    public void refreshActiveContainer() {
        ContainerManager manager = DirectorServices.get().containers();
        Container active = null;
        if (manager != null) {
            active = manager.getActive();
            if (active == null) {
                // 容器剔除更偏编辑操作；当没有“实时接收激活容器”时，回退到当前选中容器。
                active = manager.getSelectedContainer();
            }
        }
        if (active != targetContainer) {
            targetContainer = active;
            if (targetContainer == null) {
                LOGGER.warn("[ContainerCullTool] 当前无可用容器（未激活且未选中）");
                selectedBlocks.clear();
                syncToSelectionManager();
            } else {
                LOGGER.info("[ContainerCullTool] 当前目标容器: {}", targetContainer.getName());
            }
        }
    }

    private void tryAddDuringDrag(BlockPos pos) {
        if (pos == null || targetContainer == null) {
            return;
        }
        if (pos.equals(lastDragPos)) {
            return;
        }
        lastDragPos = pos;
        if (targetContainer.containsBlock(pos)) {
            selectedBlocks.add(pos);
            syncToSelectionManager();
        }
    }
}

