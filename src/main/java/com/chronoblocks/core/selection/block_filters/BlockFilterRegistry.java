package com.chronoblocks.core.selection.block_filters;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.TagKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🎯 方块过滤器注册表
 * 
 * 统一管理所有方块过滤器，提供：
 * - 过滤器注册和查找
 * - 预设过滤器
 * - 组合过滤器
 * 
 * @author ChronoBlocks Team
 * @version 2.0 (从 SelectionFilterSystem 迁移)
 */
public class BlockFilterRegistry {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockFilterRegistry.class);
    
    /** 过滤器注册表 */
    private static final Map<String, BlockFilter> registeredFilters = new HashMap<>();
    
    /** 反向查找表（用于序列化） */
    private static final Map<BlockFilter, String> reverseRegistry = new HashMap<>();
    
    static {
        initializeDefaultFilters();
    }
    
    /**
     * 初始化默认过滤器
     */
    private static void initializeDefaultFilters() {
        // 基础过滤器
        registerFilter("air", (world, pos, state) -> state.isAir());
        registerFilter("non_air", BlockFilters.nonAir());
        registerFilter("solid", BlockFilters.solid());
        registerFilter("transparent", BlockFilters.transparent());
        registerFilter("replaceable", BlockFilters.replaceable());
        
        // 发光过滤器
        registerFilter("luminous", BlockFilters.luminous(1));
        registerFilter("luminous_5", BlockFilters.luminous(5));
        registerFilter("luminous_10", BlockFilters.luminous(10));
        
        LOGGER.info("[BlockFilterRegistry] 已注册 {} 个默认过滤器", registeredFilters.size());
    }
    
    /**
     * 注册过滤器
     */
    public static void registerFilter(String id, BlockFilter filter) {
        if (registeredFilters.containsKey(id)) {
            LOGGER.warn("[BlockFilterRegistry] 过滤器 '{}' 已存在，将被覆盖", id);
        }
        registeredFilters.put(id, filter);
        reverseRegistry.put(filter, id);
        LOGGER.debug("[BlockFilterRegistry] 注册过滤器: {}", id);
    }
    
    /**
     * 获取过滤器
     */
    public static BlockFilter getFilter(String id) {
        BlockFilter filter = registeredFilters.get(id);
        if (filter == null) {
            LOGGER.warn("[BlockFilterRegistry] 未找到过滤器: {}", id);
        }
        return filter;
    }
    
    /**
     * 检查过滤器是否存在
     */
    public static boolean hasFilter(String id) {
        return registeredFilters.containsKey(id);
    }
    
    /**
     * 获取所有过滤器ID
     */
    public static Set<String> getAllFilterIds() {
        return Collections.unmodifiableSet(registeredFilters.keySet());
    }
    
    /**
     * 根据过滤器实例获取其ID（用于序列化）
     */
    public static String getFilterId(BlockFilter filter) {
        return reverseRegistry.get(filter);
    }
    
    /**
     * 清除所有过滤器（仅用于测试）
     */
    public static void clear() {
        registeredFilters.clear();
        reverseRegistry.clear();
        LOGGER.debug("[BlockFilterRegistry] 清除所有过滤器");
    }
    
    /**
     * 重新加载默认过滤器
     */
    public static void reload() {
        clear();
        initializeDefaultFilters();
        LOGGER.info("[BlockFilterRegistry] 重新加载过滤器");
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 创建方块类型过滤器
     */
    public static BlockFilter ofBlock(Block block) {
        return BlockFilters.ofBlock(block);
    }
    
    /**
     * 创建方块状态过滤器
     */
    public static BlockFilter ofBlockState(BlockState state) {
        return BlockFilters.ofBlockState(state);
    }
    
    /**
     * 创建标签过滤器
     */
    public static BlockFilter ofTag(TagKey<Block> tag) {
        return BlockFilters.hasTag(tag);
    }
    
    /**
     * 创建名称包含过滤器
     */
    public static BlockFilter nameContains(String keyword) {
        return BlockFilters.nameContains(keyword);
    }
    
    /**
     * 组合多个过滤器（AND）
     */
    public static BlockFilter and(BlockFilter... filters) {
        if (filters.length == 0) {
            return BlockFilters.all();
        }
        
        BlockFilter result = filters[0];
        for (int i = 1; i < filters.length; i++) {
            result = result.and(filters[i]);
        }
        return result;
    }
    
    /**
     * 组合多个过滤器（OR）
     */
    public static BlockFilter or(BlockFilter... filters) {
        if (filters.length == 0) {
            return BlockFilters.none();
        }
        
        BlockFilter result = filters[0];
        for (int i = 1; i < filters.length; i++) {
            result = result.or(filters[i]);
        }
        return result;
    }
}

