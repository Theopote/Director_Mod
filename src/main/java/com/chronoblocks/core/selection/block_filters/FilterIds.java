package com.chronoblocks.core.selection.block_filters;

import java.util.Set;

/**
 * Selection Filter ID 常量表（官方）
 * <p>
 * 所有 filterId、presetId 必须来自此类或 {@code docs/selection-filter-ids.md}，禁止 magic string。
 * UI 文案可本地化，见文档；此处仅提供预设的显示名映射。
 *
 * @see <a href="../../../../../docs/selection-filter-ids.md">selection-filter-ids.md</a>
 */
public final class FilterIds {

    private FilterIds() {}

    // ---------- 一、基础内容过滤（Layer 1） ----------
    public static final String EXCLUDE_AIR = "exclude_air";
    public static final String EXCLUDE_WATER = "exclude_water";
    public static final String EXCLUDE_LAVA = "exclude_lava";
    public static final String SOLID_ONLY = "solid_only";
    public static final String PLANT_ONLY = "plant_only";
    public static final String REPLACEABLE_ONLY = "replaceable_only";
    public static final String TRANSPARENT_ONLY = "transparent_only";

    // ---------- 二、选择行为过滤（Layer 2） ----------
    public static final String SAME_BLOCK_TYPE = "same_block_type";
    public static final String SURFACE_ONLY = "surface_only";
    public static final String VISIBLE_ONLY = "visible_only";
    public static final String CONNECTED_ONLY = "connected_only";
    public static final String MAX_DEPTH_LIMIT = "max_depth_limit";

    // ---------- 三、空间/范围过滤（Layer 3） ----------
    public static final String WITHIN_BBOX = "within_bbox";
    public static final String WITHIN_CHUNK = "within_chunk";
    public static final String WITHIN_RADIUS = "within_radius";
    public static final String MAX_BLOCK_COUNT = "max_block_count";

    // ---------- 四、调试/系统（隐藏） ----------
    public static final String DEBUG_LOG_REJECT = "debug_log_reject";
    public static final String PROFILING_ONLY = "profiling_only";
    public static final String DRY_RUN = "dry_run";

    // ---------- 内部用（Pipeline 空时 fallback 等） ----------
    public static final String ALL = "all";
    public static final String NONE = "none";

    // ---------- 扩展（高级区可选，未入规范表主表） ----------
    public static final String LUMINOUS = "luminous";
    public static final String LUMINOUS_5 = "luminous_5";
    public static final String LUMINOUS_10 = "luminous_10";

    // ---------- 内置 Preset / Mode ID ----------
    public static final String PRESET_DEFAULT = "default";
    public static final String PRESET_TERRAIN_EDIT = "terrain_edit";
    public static final String PRESET_PLANT_EDIT = "plant_edit";
    public static final String PRESET_FLUID_EDIT = "fluid_edit";
    public static final String PRESET_REPLACEABLE_EDIT = "replaceable_edit";

    /** 规范表已收录的 filterId（含内部 all/none、扩展 luminous*），用于校验/提示 */
    private static final Set<String> KNOWN_FILTER_IDS = Set.of(
            EXCLUDE_AIR, EXCLUDE_WATER, EXCLUDE_LAVA,
            SOLID_ONLY, PLANT_ONLY, REPLACEABLE_ONLY, TRANSPARENT_ONLY,
            SAME_BLOCK_TYPE, SURFACE_ONLY, VISIBLE_ONLY, CONNECTED_ONLY, MAX_DEPTH_LIMIT,
            WITHIN_BBOX, WITHIN_CHUNK, WITHIN_RADIUS, MAX_BLOCK_COUNT,
            DEBUG_LOG_REJECT, PROFILING_ONLY, DRY_RUN,
            ALL, NONE,
            LUMINOUS, LUMINOUS_5, LUMINOUS_10
    );

    public static boolean isKnownFilterId(String id) {
        return id != null && KNOWN_FILTER_IDS.contains(id);
    }

    /**
     * 内置模式的 UI 显示名（可本地化替换）
     */
    public static String getPresetDisplayName(String presetId) {
        if (presetId == null) return "";
        switch (presetId) {
            case "default": return "通用编辑";
            case "terrain_edit": return "地形编辑";
            case "plant_edit": return "植物编辑";
            case "fluid_edit": return "流体编辑";
            case "replaceable_edit": return "可替换方块";
            default: return presetId;
        }
    }
}
