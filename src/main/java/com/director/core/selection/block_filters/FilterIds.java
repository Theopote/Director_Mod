package com.director.core.selection.block_filters;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;

import java.util.Set;

/**
 * Selection Filter ID 常量表（官方）
 * <p>
 * 所有 filterId、presetId 必须来自此类或 {@code docs/selection-filter-ids.md}，禁止 magic string。
 * UI 文案可本地化，见文档；此处提供预设与过滤器的显示名映射入口。
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

    private static final Set<String> BUILTIN_PRESET_IDS = Set.of(
            PRESET_DEFAULT, PRESET_TERRAIN_EDIT, PRESET_PLANT_EDIT, PRESET_FLUID_EDIT, PRESET_REPLACEABLE_EDIT
    );

    /** 是否为内置模式 id，保存时禁止使用以避免覆盖。 */
    public static boolean isBuiltinPresetId(String id) {
        return id != null && BUILTIN_PRESET_IDS.contains(id);
    }

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
        return switch (presetId) {
            case PRESET_DEFAULT -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.PRESET_DEFAULT);
            case PRESET_TERRAIN_EDIT -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.PRESET_TERRAIN_EDIT);
            case PRESET_PLANT_EDIT -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.PRESET_PLANT_EDIT);
            case PRESET_FLUID_EDIT -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.PRESET_FLUID_EDIT);
            case PRESET_REPLACEABLE_EDIT -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.PRESET_REPLACEABLE_EDIT);
            default -> presetId;
        };
    }

    /**
     * 过滤器规则的 UI 显示名（可本地化替换）
     */
    public static String getFilterDisplayName(String filterId) {
        if (filterId == null) return "";
        String canonical = switch (filterId) {
            case "non_air" -> EXCLUDE_AIR;
            case "not_water" -> EXCLUDE_WATER;
            case "not_lava" -> EXCLUDE_LAVA;
            case "solid" -> SOLID_ONLY;
            case "plant" -> PLANT_ONLY;
            case "replaceable" -> REPLACEABLE_ONLY;
            case "transparent" -> TRANSPARENT_ONLY;
            default -> filterId;
        };
        return switch (canonical) {
            case EXCLUDE_AIR -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_EXCLUDE_AIR);
            case EXCLUDE_WATER -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_EXCLUDE_WATER);
            case EXCLUDE_LAVA -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_EXCLUDE_LAVA);
            case SOLID_ONLY -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_SOLID_ONLY);
            case PLANT_ONLY -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_PLANT_ONLY);
            case REPLACEABLE_ONLY -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_REPLACEABLE_ONLY);
            case TRANSPARENT_ONLY -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_TRANSPARENT_ONLY);
            case SAME_BLOCK_TYPE -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_SAME_BLOCK_TYPE);
            case SURFACE_ONLY -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_SURFACE_ONLY);
            case VISIBLE_ONLY -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_VISIBLE_ONLY);
            case CONNECTED_ONLY -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_CONNECTED_ONLY);
            case MAX_DEPTH_LIMIT -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_MAX_DEPTH_LIMIT);
            case WITHIN_BBOX -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_WITHIN_BBOX);
            case WITHIN_CHUNK -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_WITHIN_CHUNK);
            case WITHIN_RADIUS -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_WITHIN_RADIUS);
            case MAX_BLOCK_COUNT -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_MAX_BLOCK_COUNT);
            case DEBUG_LOG_REJECT -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_DEBUG_LOG_REJECT);
            case PROFILING_ONLY -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_PROFILING_ONLY);
            case DRY_RUN -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_DRY_RUN);
            case ALL -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_ALL);
            case NONE -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_NONE);
            case LUMINOUS -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_LUMINOUS);
            case LUMINOUS_5 -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_LUMINOUS_5);
            case LUMINOUS_10 -> DirectorI18n.translate(DirectorLang.SelectionFilterPanel.FILTER_LUMINOUS_10);
            default -> canonical;
        };
    }
}
