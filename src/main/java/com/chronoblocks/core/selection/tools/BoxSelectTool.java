package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 📦 框选工具
 * 
 * 支持拖拽一个立方体区域选择方块。
 * - 拖拽：创建框选区域
 * - Shift+拖拽：添加到现有选择
 * - 支持实时预览
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class BoxSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoxSelectTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private BlockPos dragStart = null;
    private BlockPos dragCurrent = null;
    private boolean isDragging = false;

    @Override 
    public String getId() { 
        return "box"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "框选工具"; 
    }
    
    @Override
    public String getDescription() {
        return "拖拽创建立方体选择区域。Shift+拖拽可添加到现有选择。";
    }
    
    @Override
    public String getIcon() {
        return "\uE910"; // Icons.BOX
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        LOGGER.debug("[BoxSelectTool] 点击方块: {} (Shift: {})", pos, isShiftDown);
        
        // 框选工具的点击可以清除选择或开始新的框选
        if (!isShiftDown) {
            currentSelection.clear();
        }
        
        dragStart = pos;
        dragCurrent = pos;
        isDragging = true;
        
        LOGGER.debug("[BoxSelectTool] 开始框选，起点: {}", pos);
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        if (world == null || start == null) {
            return;
        }
        
        dragStart = start;
        dragCurrent = start;
        isDragging = true;
        
        LOGGER.debug("[BoxSelectTool] 拖拽开始: {}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (isDragging && current != null) {
            dragCurrent = current;
            LOGGER.debug("[BoxSelectTool] 拖拽更新: {}", current);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (isDragging && dragStart != null && end != null) {
            LOGGER.debug("[BoxSelectTool] 拖拽结束: {} -> {}", dragStart, end);
            
            // 创建框选区域
            SelectionState boxSelection = SelectionState.box(dragStart, end, getId());
            
            // 添加到当前选择或替换
            if (currentSelection.isEmpty()) {
                currentSelection = boxSelection;
            } else {
                // 合并选择
                currentSelection.addBlocks(boxSelection.getSelectedBlocks());
            }
            
            LOGGER.info("[BoxSelectTool] 框选完成，选择 {} 个方块", currentSelection.size());
        }
        
        // 重置拖拽状态
        isDragging = false;
        dragStart = null;
        dragCurrent = null;
    }

    @Override
    public void tick(World world) {
        // 可以在这里更新实时预览
        if (isDragging && dragStart != null && dragCurrent != null) {
            // 实时更新预览区域
            LOGGER.debug("[BoxSelectTool] 实时预览: {} -> {}", dragStart, dragCurrent);
        }
    }

    @Override
    public SelectionState getCurrentSelection() {
        return currentSelection;
    }

    @Override
    public void clearSelection() {
        currentSelection.clear();
        isDragging = false;
        dragStart = null;
        dragCurrent = null;
        LOGGER.debug("[BoxSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty() || isDragging;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[BoxSelectTool] 工具激活");
        isDragging = false;
        dragStart = null;
        dragCurrent = null;
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[BoxSelectTool] 工具停用");
        isDragging = false;
        dragStart = null;
        dragCurrent = null;
    }
    
    /**
     * 获取当前拖拽状态
     */
    public boolean isDragging() {
        return isDragging;
    }
    
    /**
     * 获取拖拽起点
     */
    public BlockPos getDragStart() {
        return dragStart;
    }
    
    /**
     * 获取当前拖拽位置
     */
    public BlockPos getDragCurrent() {
        return dragCurrent;
    }
}
