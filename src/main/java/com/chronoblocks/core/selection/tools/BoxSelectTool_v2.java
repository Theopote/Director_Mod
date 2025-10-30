package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.*;
import com.chronoblocks.core.selection.algorithms.RegionSelectors;
import com.chronoblocks.core.selection.filter.SelectionFilter;
import com.chronoblocks.core.selection.filter.SelectionPipeline;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 📦 框选工具 v2
 * 
 * 支持 Add/Sub/Intersect + 过滤器的升级版框选工具
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class BoxSelectTool_v2 implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoxSelectTool_v2.class);
    
    private BlockPos start;
    @SuppressWarnings("unused")
    private BlockPos end;
    private boolean isDragging = false;
    private SelectionOperation op = SelectionOperation.ADD;
    private final SelectionPipeline pipeline = new SelectionPipeline().add(SelectionFilter.nonAir());

    @Override 
    public String getId() { 
        return "box_v2"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "框选工具 v2"; 
    }
    
    @Override
    public String getDescription() {
        return "支持加选/减选/交集操作的框选工具，内置过滤器支持。";
    }
    
    @Override
    public String getIcon() {
        return "\uE910"; // Icons.BOX
    }

    public void setOperation(SelectionOperation op) { 
        this.op = op; 
        LOGGER.debug("[BoxSelectTool_v2] 设置操作模式: {}", op.getDisplayName());
    }
    
    public SelectionOperation getOperation() {
        return op;
    }
    
    public SelectionPipeline getPipeline() {
        return pipeline;
    }

    @Override
    public void onDragStart(World w, BlockPos start) { 
        this.start = start;
        this.isDragging = true;
        LOGGER.debug("[BoxSelectTool_v2] 拖拽开始: {}", start);
    }

    @Override
    public void onDragUpdate(World w, BlockPos current) {
        if (isDragging) {
            this.end = current;
        }
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        // 框选工具v2主要通过拖拽操作，单击可以选择单个方块
        if (world == null || pos == null) return;
        
        SelectionSet set = SelectionManager.get().getSelectionSet();
        switch (op) {
            case ADD -> set.add(pos);
            case SUBTRACT -> set.remove(pos);
            case INTERSECT -> {
                if (set.contains(pos)) {
                    set.clear();
                    set.add(pos);
                } else {
                    set.clear();
                }
            }
        }
    }

    @Override
    public void onDragEnd(World w, BlockPos end) {
        if (start == null) return;
        
        this.end = end;
        this.isDragging = false;
        
        // 创建选择框 - 使用正确的构造函数
        double minX = Math.min(start.getX(), end.getX());
        double minY = Math.min(start.getY(), end.getY());
        double minZ = Math.min(start.getZ(), end.getZ());
        double maxX = Math.max(start.getX(), end.getX()) + 1;
        double maxY = Math.max(start.getY(), end.getY()) + 1;
        double maxZ = Math.max(start.getZ(), end.getZ()) + 1;
        Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        
        Set<BlockPos> found = RegionSelectors.selectInBox(w, box, pipeline::test);

        LOGGER.debug("[BoxSelectTool_v2] 框选完成，找到 {} 个方块", found.size());

        SelectionSet set = SelectionManager.get().getSelectionSet();
        int beforeSize = set.size();
        
        switch (op) {
            case ADD -> {
                set.addAll(found);
                LOGGER.info("[BoxSelectTool_v2] 加选操作，添加 {} 个方块", set.size() - beforeSize);
            }
            case SUBTRACT -> {
                int removed = 0;
                for (BlockPos pos : found) {
                    if (set.remove(pos)) removed++;
                }
                LOGGER.info("[BoxSelectTool_v2] 减选操作，移除 {} 个方块", removed);
            }
            case INTERSECT -> {
                var current = set.asUnmodifiableSet();
                set.clear();
                for (BlockPos p : found) {
                    if (current.contains(p)) set.add(p);
                }
                LOGGER.info("[BoxSelectTool_v2] 交集操作，保留 {} 个方块", set.size());
            }
        }
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
        LOGGER.debug("[BoxSelectTool_v2] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !SelectionManager.get().getSelectionSet().isEmpty() || isDragging;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[BoxSelectTool_v2] 工具激活，操作模式: {}", op.getDisplayName());
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[BoxSelectTool_v2] 工具停用");
        isDragging = false;
        start = null;
        end = null;
    }
}
