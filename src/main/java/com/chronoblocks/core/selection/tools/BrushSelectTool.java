package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.*;
import com.chronoblocks.core.selection.algorithms.RegionSelectors;
import com.chronoblocks.core.selection.filter.SelectionPipeline;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 🖌️ 刷选工具 (Brush)
 * 
 * 圆形区域笔刷选择工具，支持加选/减选/交集操作
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class BrushSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrushSelectTool.class);
    
    private double radius = 3.0;
    private SelectionOperation op = SelectionOperation.ADD;
    private final SelectionPipeline pipeline = new SelectionPipeline();
    private boolean isPainting = false;

    @Override 
    public String getId() { 
        return "brush"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "刷选工具"; 
    }
    
    @Override
    public String getDescription() {
        return "圆形区域笔刷选择工具，支持加选/减选/交集操作。";
    }
    
    @Override
    public String getIcon() {
        return "\uE911"; // Icons.BRUSH
    }

    public void setRadius(double r) { 
        this.radius = Math.max(0.5, Math.min(20.0, r)); 
        LOGGER.debug("[BrushSelectTool] 设置半径: {}", radius);
    }
    
    public double getRadius() {
        return radius;
    }
    
    public void setOperation(SelectionOperation op) { 
        this.op = op; 
        LOGGER.debug("[BrushSelectTool] 设置操作模式: {}", op.getDisplayName());
    }
    
    public SelectionOperation getOperation() {
        return op;
    }
    
    public SelectionPipeline getPipeline() {
        return pipeline;
    }

    @Override
    public void onClick(World w, BlockPos pos, boolean shift) {
        if (w == null || pos == null) return;
        
        LOGGER.debug("[BrushSelectTool] 刷选点击: {}，半径: {}", pos, radius);
        
        Set<BlockPos> found = RegionSelectors.selectSphere(w, pos, radius, pipeline::test);
        
        LOGGER.debug("[BrushSelectTool] 刷选完成，找到 {} 个方块", found.size());

        SelectionSet set = SelectionManager.get().getSelectionSet();
        int beforeSize = set.size();
        
        switch (op) {
            case ADD -> {
                set.addAll(found);
                LOGGER.info("[BrushSelectTool] 加选操作，添加 {} 个方块", set.size() - beforeSize);
            }
            case SUBTRACT -> {
                int removed = 0;
                for (BlockPos p : found) {
                    if (set.remove(p)) removed++;
                }
                LOGGER.info("[BrushSelectTool] 减选操作，移除 {} 个方块", removed);
            }
            case INTERSECT -> {
                var current = set.asUnmodifiableSet();
                set.clear();
                for (BlockPos p : found) {
                    if (current.contains(p)) set.add(p);
                }
                LOGGER.info("[BrushSelectTool] 交集操作，保留 {} 个方块", set.size());
            }
        }
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        isPainting = true;
        LOGGER.debug("[BrushSelectTool] 开始刷选拖拽");
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (isPainting && current != null) {
            // 连续刷选
            onClick(world, current, false);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        isPainting = false;
        LOGGER.debug("[BrushSelectTool] 刷选拖拽结束");
    }

    @Override
    public SelectionState getCurrentSelection() {
        SelectionManager manager = SelectionManager.get();
        return SelectionState.of(manager.getSelectionSet().asUnmodifiableSet());
    }

    @Override
    public void clearSelection() {
        SelectionManager manager = SelectionManager.get();
        manager.clearSelection();
        LOGGER.debug("[BrushSelectTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !SelectionManager.get().getSelectionSet().isEmpty() || isPainting;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[BrushSelectTool] 工具激活，半径: {}，操作模式: {}", radius, op.getDisplayName());
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[BrushSelectTool] 工具停用");
        isPainting = false;
    }
}