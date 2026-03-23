package com.director.core.selection.tools;

import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
// import net.minecraft.block.Blocks; // 暂时不使用
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🏷️ 按类型选择工具
 * 
 * 在选区内，一键选择所有同种方块（如所有橡木原木）。
 * 支持材质过滤器和状态过滤器。
 * 
 * @author Director Team
 * @version 1.0
 */
public class TypeFilterTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeFilterTool.class);
    
    private SelectionState currentSelection = SelectionState.empty();
    private Block targetBlockType = null; // 目标方块类型
    private final Set<String> materialFilters = new HashSet<>(); // 材质过滤器
    private final Set<String> stateFilters = new HashSet<>(); // 状态过滤器
    private BlockPos selectionCenter = null;
    private int selectionRadius = 20; // 选择半径

    @Override 
    public String getId() { 
        return "typefilter"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "按类型选择"; 
    }
    
    @Override
    public String getDescription() {
        return "在选区内一键选择所有同种方块。支持材质和状态过滤器。";
    }
    
    @Override
    public String getIcon() {
        return "\uE91F"; // Icons.COLOR (类型)
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        // 获取点击的方块类型
        BlockState targetState = world.getBlockState(pos);
        targetBlockType = targetState.getBlock();
        selectionCenter = pos;
        
        LOGGER.debug("[TypeFilterTool] 按类型选择，方块类型: {}，中心: {}", targetBlockType, pos);
        
        // 执行按类型选择
        Set<BlockPos> typeBlocks = selectByType(world, selectionCenter, selectionRadius, targetBlockType);
        
        if (typeBlocks.isEmpty()) {
            LOGGER.debug("[TypeFilterTool] 未找到相同类型的方块");
            return;
        }
        
        if (isShiftDown) {
            // Shift+点击：添加到现有选择
            currentSelection.addBlocks(typeBlocks);
        } else {
            // 普通点击：替换当前选择
            currentSelection.clear();
            currentSelection.addBlocks(typeBlocks);
        }
        
        LOGGER.info("[TypeFilterTool] 按类型选择完成，选择 {} 个 {} 方块", typeBlocks.size(), targetBlockType);
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        selectionCenter = start;
        LOGGER.debug("[TypeFilterTool] 开始调整选择区域，中心: {}", start);
    }

    @Override
    public void onDragUpdate(World world, BlockPos current) {
        if (selectionCenter != null) {
            // 根据拖拽距离调整半径
            int distance = (int) Math.sqrt(selectionCenter.getSquaredDistance(current));
            selectionRadius = Math.max(1, Math.min(100, distance));
            LOGGER.debug("[TypeFilterTool] 调整半径: {}", selectionRadius);
        }
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        if (selectionCenter != null && targetBlockType != null) {
            // 最终确定半径
            int distance = (int) Math.sqrt(selectionCenter.getSquaredDistance(end));
            selectionRadius = Math.max(1, Math.min(100, distance));
            
            // 重新执行选择
            Set<BlockPos> typeBlocks = selectByType(world, selectionCenter, selectionRadius, targetBlockType);
            
            currentSelection.clear();
            currentSelection.addBlocks(typeBlocks);
            
            LOGGER.info("[TypeFilterTool] 按类型选择完成，半径: {}，选择 {} 个 {} 方块", 
                       selectionRadius, typeBlocks.size(), targetBlockType);
        }
    }

    /**
     * 按类型选择算法
     */
    private Set<BlockPos> selectByType(World world, BlockPos center, int radius, Block targetType) {
        Set<BlockPos> result = new HashSet<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    
                    // 检查是否在半径内
                    if (center.getSquaredDistance(pos) <= radius * radius) {
                        BlockState state = world.getBlockState(pos);
                        
                        if (matchesFilters(state, targetType)) {
                            result.add(pos);
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 检查方块是否匹配过滤器
     */
    private boolean matchesFilters(BlockState state, Block targetType) {
        // 基础类型匹配
        if (!state.getBlock().equals(targetType)) {
            return false;
        }
        
        // 材质过滤器
        if (!materialFilters.isEmpty()) {
            String material = state.getBlock().toString().toLowerCase();
            boolean matchesMaterial = false;
            for (String filter : materialFilters) {
                if (material.contains(filter.toLowerCase())) {
                    matchesMaterial = true;
                    break;
                }
            }
            if (!matchesMaterial) {
                return false;
            }
        }
        
        // 状态过滤器
        if (!stateFilters.isEmpty()) {
            String stateString = state.toString().toLowerCase();
            boolean matchesState = false;
            for (String filter : stateFilters) {
                if (stateString.contains(filter.toLowerCase())) {
                    matchesState = true;
                    break;
                }
            }
            if (!matchesState) {
                return false;
            }
        }
        
        return true;
    }

    @Override
    public SelectionState getCurrentSelection() {
        return currentSelection;
    }

    @Override
    public void clearSelection() {
        currentSelection.clear();
        targetBlockType = null;
        selectionCenter = null;
        LOGGER.debug("[TypeFilterTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty() || selectionCenter != null;
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[TypeFilterTool] 工具激活，半径: {}", selectionRadius);
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[TypeFilterTool] 工具停用");
        targetBlockType = null;
        selectionCenter = null;
    }
    
    /**
     * 添加材质过滤器
     */
    public void addMaterialFilter(String material) {
        materialFilters.add(material);
        LOGGER.debug("[TypeFilterTool] 添加材质过滤器: {}", material);
    }
    
    /**
     * 移除材质过滤器
     */
    public void removeMaterialFilter(String material) {
        materialFilters.remove(material);
        LOGGER.debug("[TypeFilterTool] 移除材质过滤器: {}", material);
    }
    
    /**
     * 添加状态过滤器
     */
    public void addStateFilter(String state) {
        stateFilters.add(state);
        LOGGER.debug("[TypeFilterTool] 添加状态过滤器: {}", state);
    }
    
    /**
     * 移除状态过滤器
     */
    public void removeStateFilter(String state) {
        stateFilters.remove(state);
        LOGGER.debug("[TypeFilterTool] 移除状态过滤器: {}", state);
    }
    
    /**
     * 清除所有过滤器
     */
    public void clearFilters() {
        materialFilters.clear();
        stateFilters.clear();
        LOGGER.debug("[TypeFilterTool] 清除所有过滤器");
    }
    
    /**
     * 设置选择半径
     */
    public void setSelectionRadius(int radius) {
        this.selectionRadius = Math.max(1, Math.min(100, radius));
    }
    
    /**
     * 获取选择半径
     */
    public int getSelectionRadius() {
        return selectionRadius;
    }
    
    /**
     * 获取目标方块类型
     */
    public Block getTargetBlockType() {
        return targetBlockType;
    }
    
    /**
     * 获取材质过滤器
     */
    public Set<String> getMaterialFilters() {
        return new HashSet<>(materialFilters);
    }
    
    /**
     * 获取状态过滤器
     */
    public Set<String> getStateFilters() {
        return new HashSet<>(stateFilters);
    }
}
