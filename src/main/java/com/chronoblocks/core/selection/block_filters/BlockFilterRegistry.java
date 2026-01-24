package com.chronoblocks.core.selection.block_filters;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.TagKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🎯 方块过滤器注册表
 * <p>
 * 统一管理所有方块过滤器；filterId 以 {@link FilterIds} 与 {@code docs/selection-filter-ids.md} 为唯一合法来源。
 * - 正式注册仅用规范 id（exclude_air、solid_only 等）；旧 id 通过 LEGACY_ALIASES 在 getFilter/hasFilter 中解析，不写入注册表。
 * - 禁止将编辑器概念（容器/锁定/面板）纳入；带参数规则用 {@link #createFilter(String)} 取得新实例。
 *
 * @author ChronoBlocks Team
 * @see FilterIds
 * @see <a href="../../../../../docs/selection-filter-ids.md">selection-filter-ids.md</a>
 */
public class BlockFilterRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockFilterRegistry.class);

    /** 过滤器注册表 */
    private static final Map<String, BlockFilter> registeredFilters = new HashMap<>();

    /** 反向查找表（用于序列化） */
    private static final Map<BlockFilter, String> reverseRegistry = new HashMap<>();

    /** 旧 id → 规范表 id，仅用于 getFilter / hasFilter 解析，不写入 reverseRegistry */
    private static final Map<String, String> LEGACY_ALIASES = Map.of(
            "non_air", FilterIds.EXCLUDE_AIR,
            "not_water", FilterIds.EXCLUDE_WATER,
            "not_lava", FilterIds.EXCLUDE_LAVA,
            "solid", FilterIds.SOLID_ONLY,
            "plant", FilterIds.PLANT_ONLY,
            "replaceable", FilterIds.REPLACEABLE_ONLY,
            "transparent", FilterIds.TRANSPARENT_ONLY
    );

    static {
        initializeDefaultFilters();
    }

    /**
     * 初始化默认过滤器（仅使用 FilterIds 与 BlockFilters）
     */
    private static void initializeDefaultFilters() {
        // 一、基础内容过滤（Layer 1）
        registerFilter(FilterIds.EXCLUDE_AIR, BlockFilters.nonAir());
        registerFilter(FilterIds.EXCLUDE_WATER, BlockFilters.water().negate());
        registerFilter(FilterIds.EXCLUDE_LAVA, BlockFilters.lava().negate());
        registerFilter(FilterIds.SOLID_ONLY, BlockFilters.solid());
        registerFilter(FilterIds.PLANT_ONLY, BlockFilters.plant());
        registerFilter(FilterIds.REPLACEABLE_ONLY, BlockFilters.replaceable());
        registerFilter(FilterIds.TRANSPARENT_ONLY, BlockFilters.transparent());

        // 内部用
        registerFilter(FilterIds.ALL, BlockFilters.all());
        registerFilter(FilterIds.NONE, BlockFilters.none());

        // 扩展（高级区）
        registerFilter(FilterIds.LUMINOUS, BlockFilters.luminous(1));
        registerFilter(FilterIds.LUMINOUS_5, BlockFilters.luminous(5));
        registerFilter(FilterIds.LUMINOUS_10, BlockFilters.luminous(10));

        LOGGER.info("[BlockFilterRegistry] 已注册 {} 个默认过滤器", registeredFilters.size());
    }

    private static String resolveId(String id) {
        if (id == null) return null;
        return LEGACY_ALIASES.getOrDefault(id, id);
    }

    /** 解析为规范 id（legacy 映射），供 Commands 等做 pipeline 查找/删除。 */
    public static String resolveFilterId(String id) {
        return resolveId(id);
    }

    /**
     * 注册过滤器。id 须非空、非空白；建议来自 FilterIds。
     */
    public static void registerFilter(String id, BlockFilter filter) {
        if (id == null || id.isBlank()) {
            LOGGER.warn("[BlockFilterRegistry] 拒绝注册空 id");
            return;
        }
        if (!FilterIds.isKnownFilterId(id)) {
            LOGGER.debug("[BlockFilterRegistry] 注册非规范表 id: {}（扩展/插件）", id);
        }
        if (registeredFilters.containsKey(id)) {
            LOGGER.warn("[BlockFilterRegistry] 过滤器 '{}' 已存在，将被覆盖", id);
        }
        registeredFilters.put(id, filter);
        reverseRegistry.put(filter, id);
        LOGGER.debug("[BlockFilterRegistry] 注册过滤器: {}", id);
    }

    /**
     * 获取过滤器；旧 id（如 non_air、not_water）会解析为规范 id 再查找。
     * 无状态规则返回共享实例；带参数的规则应使用 {@link #createFilter(String)} 获得独立实例。
     */
    public static BlockFilter getFilter(String id) {
        String canonical = resolveId(id);
        if (canonical == null) return null;
        BlockFilter filter = registeredFilters.get(canonical);
        if (filter == null) {
            LOGGER.warn("[BlockFilterRegistry] 未找到过滤器: {} (resolved: {})", id, canonical);
        }
        return filter;
    }

    /**
     * 创建过滤器实例。无参数规则目前与 getFilter 相同（共享实例）；
     * 带参数规则（如 max_depth_limit、within_radius）必须在此返回新实例，避免管道间参数污染。
     * 参见 docs/selection-filter-ids.md 中「参数化」标注的 filterId。
     */
    public static BlockFilter createFilter(String id) {
        if (isParameterizedId(resolveId(id))) {
            // 预留：参数化规则在此 factory 或子类中 new 实例
            LOGGER.debug("[BlockFilterRegistry] createFilter 参数化 id 尚未实现，回退 getFilter: {}", id);
        }
        return getFilter(id);
    }

    private static boolean isParameterizedId(String c) {
        return (FilterIds.MAX_DEPTH_LIMIT.equals(c) || FilterIds.WITHIN_RADIUS.equals(c)
                || FilterIds.WITHIN_BBOX.equals(c) || FilterIds.MAX_BLOCK_COUNT.equals(c));
    }

    /**
     * 检查过滤器是否存在；旧 id 会解析为规范 id。
     */
    public static boolean hasFilter(String id) {
        String canonical = resolveId(id);
        return canonical != null && registeredFilters.containsKey(canonical);
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

