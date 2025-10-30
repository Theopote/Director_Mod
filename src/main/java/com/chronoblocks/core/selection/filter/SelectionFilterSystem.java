package com.chronoblocks.core.selection.filter;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;

/**
 * 🎯 选择过滤器系统
 * 
 * 提供强大的方块选择过滤功能，支持：
 * - 方块类型过滤
 * - 方块状态过滤
 * - 标签过滤
 * - 自定义条件过滤
 * - 组合过滤器
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionFilterSystem {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionFilterSystem.class);
    
    /** 过滤器注册表 */
    private static final Map<String, SelectionFilter> registeredFilters = new HashMap<>();
    
    /** 预设过滤器 */
    private static final Map<String, SelectionPipeline> presets = new HashMap<>();
    
    static {
        initializeDefaultFilters();
        initializePresets();
    }
    
    /**
     * 初始化默认过滤器
     */
    private static void initializeDefaultFilters() {
        // 基础过滤器
        registerFilter("air", (world, pos, state) -> state.isAir());
        registerFilter("non_air", SelectionFilter.nonAir());
        registerFilter("solid", (world, pos, state) -> state.isSolidBlock(null, pos));
        registerFilter("transparent", (world, pos, state) -> !state.isOpaque());
        
        // 方块类型过滤器
        registerFilter("stone", SelectionFilter.ofBlock(Registries.BLOCK.get(Identifier.of("minecraft:stone"))));
        registerFilter("dirt", SelectionFilter.ofBlock(Registries.BLOCK.get(Identifier.of("minecraft:dirt"))));
        registerFilter("grass", SelectionFilter.ofBlock(Registries.BLOCK.get(Identifier.of("minecraft:grass_block"))));
        registerFilter("wood", SelectionFilter.ofBlock(Registries.BLOCK.get(Identifier.of("minecraft:oak_log"))));
        
        // 标签过滤器
        registerFilter("logs", SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:logs"))));
        registerFilter("leaves", SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:leaves"))));
        registerFilter("stone_bricks", SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:stone_bricks"))));
        
        LOGGER.info("[SelectionFilterSystem] 初始化了 {} 个默认过滤器", registeredFilters.size());
    }
    
    /**
     * 初始化预设过滤器
     */
    private static void initializePresets() {
        // 建筑材料预设
        SelectionPipeline buildingMaterials = new SelectionPipeline()
            .add(SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:stone_bricks"))))
            .add(SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:logs"))))
            .add(SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:planks"))));
        presets.put("building_materials", buildingMaterials);
        
        // 自然方块预设
        SelectionPipeline naturalBlocks = new SelectionPipeline()
            .add(SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:leaves"))))
            .add(SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:logs"))))
            .add(SelectionFilter.ofBlock(Registries.BLOCK.get(Identifier.of("minecraft:grass_block"))));
        presets.put("natural_blocks", naturalBlocks);
        
        // 透明方块预设
        SelectionPipeline transparentBlocks = new SelectionPipeline()
            .add((world, pos, state) -> !state.isOpaque())
            .add(SelectionFilter.ofTag(TagKey.of(Registries.BLOCK.getKey(), Identifier.of("minecraft:glass"))));
        presets.put("transparent_blocks", transparentBlocks);
        
        LOGGER.info("[SelectionFilterSystem] 初始化了 {} 个预设过滤器", presets.size());
    }
    
    /**
     * 注册过滤器
     */
    public static void registerFilter(String id, SelectionFilter filter) {
        registeredFilters.put(id, filter);
        LOGGER.debug("[SelectionFilterSystem] 注册过滤器: {}", id);
    }
    
    /**
     * 获取过滤器
     */
    public static SelectionFilter getFilter(String id) {
        return registeredFilters.get(id);
    }
    
    /**
     * 获取所有注册的过滤器ID
     */
    public static Set<String> getRegisteredFilterIds() {
        return new HashSet<>(registeredFilters.keySet());
    }
    
    /**
     * 获取预设过滤器
     */
    public static SelectionPipeline getPreset(String presetId) {
        return presets.get(presetId);
    }
    
    /**
     * 获取所有预设ID
     */
    public static Set<String> getPresetIds() {
        return new HashSet<>(presets.keySet());
    }
    
    /**
     * 保存预设过滤器
     */
    public static void savePreset(String presetId, SelectionPipeline pipeline) {
        presets.put(presetId, pipeline);
        LOGGER.info("[SelectionFilterSystem] 保存预设过滤器: {}", presetId);
    }
    
    /**
     * 删除预设过滤器
     */
    public static void deletePreset(String presetId) {
        presets.remove(presetId);
        LOGGER.info("[SelectionFilterSystem] 删除预设过滤器: {}", presetId);
    }
    
    /**
     * 创建组合过滤器
     */
    public static SelectionPipeline createCombinedFilter(String... filterIds) {
        SelectionPipeline pipeline = new SelectionPipeline();
        for (String filterId : filterIds) {
            SelectionFilter filter = getFilter(filterId);
            if (filter != null) {
                pipeline.add(filter);
            } else {
                LOGGER.warn("[SelectionFilterSystem] 未找到过滤器: {}", filterId);
            }
        }
        return pipeline;
    }
    
    /**
     * 创建反向过滤器
     */
    public static SelectionFilter createInverseFilter(String filterId) {
        SelectionFilter originalFilter = getFilter(filterId);
        if (originalFilter != null) {
            return (world, pos, state) -> !originalFilter.test(world, pos, state);
        }
        return SelectionFilter.any();
    }
    
    /**
     * 创建自定义条件过滤器
     */
    public static SelectionFilter createCustomFilter(String name, Predicate<BlockState> condition) {
        return (world, pos, state) -> {
            if (world == null || pos == null) return false;
            return condition.test(state);
        };
    }
    
    /**
     * 创建高度范围过滤器
     */
    public static SelectionFilter createHeightRangeFilter(int minY, int maxY) {
        return (world, pos, state) -> pos.getY() >= minY && pos.getY() <= maxY;
    }
    
    /**
     * 创建距离过滤器
     */
    public static SelectionFilter createDistanceFilter(BlockPos center, double maxDistance) {
        return (world, pos, state) -> {
            double distance = Math.sqrt(
                Math.pow(pos.getX() - center.getX(), 2) +
                Math.pow(pos.getY() - center.getY(), 2) +
                Math.pow(pos.getZ() - center.getZ(), 2)
            );
            return distance <= maxDistance;
        };
    }
    
    /**
     * 创建方块硬度过滤器
     */
    public static SelectionFilter createHardnessFilter(float minHardness, float maxHardness) {
        return (world, pos, state) -> {
            if (world == null || pos == null) return false;
            float hardness = state.getBlock().getHardness();
            return hardness >= minHardness && hardness <= maxHardness;
        };
    }
    
    /**
     * 创建方块类型过滤器
     */
    public static SelectionFilter createBlockTypeFilter(Class<? extends Block> blockClass) {
        return (world, pos, state) -> {
            if (world == null || pos == null) return false;
            return blockClass.isInstance(state.getBlock());
        };
    }
    
    /**
     * 获取过滤器统计信息
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("registeredFilters", registeredFilters.size());
        stats.put("presets", presets.size());
        stats.put("filterIds", getRegisteredFilterIds());
        stats.put("presetIds", getPresetIds());
        return stats;
    }
    
    /**
     * 验证过滤器
     */
    public static boolean validateFilter(String filterId) {
        return registeredFilters.containsKey(filterId);
    }
    
    /**
     * 验证预设
     */
    public static boolean validatePreset(String presetId) {
        return presets.containsKey(presetId);
    }
    
    /**
     * 清空所有过滤器
     */
    public static void clearAllFilters() {
        registeredFilters.clear();
        presets.clear();
        LOGGER.info("[SelectionFilterSystem] 清空所有过滤器");
    }
    
    /**
     * 重新初始化系统
     */
    public static void reinitialize() {
        clearAllFilters();
        initializeDefaultFilters();
        initializePresets();
        LOGGER.info("[SelectionFilterSystem] 重新初始化完成");
    }
}
