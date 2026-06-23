package com.director.ui.panels;

import com.director.core.selection.block_filters.BlockFilter;
import com.director.core.selection.block_filters.FilterIds;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.PanelId;
import com.director.ui.PanelWindows;
import com.director.ui.commands.SelectionFilterCommands;
import com.director.ui.commands.UICommandFacade;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.aware.ICommandsAware;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SelectionFilterPanel v3.0
 * <p>
 * UI-only 重构版本：
 * - 面向普通用户的“人话过滤”
 * - 保留完整高级过滤能力
 * - 不直接接触 Pipeline / Manager
 * <p>
 * 三层语义（底层共用 BlockFilterPipeline）：
 * - 层 1「选择内容」：基础 Checkbox，人话（忽略空气/水/岩浆，仅固体/植物/可替换）
 * - 层 2「选择模式」：Preset 的 UI 语义（切换、保存为模式）
 * - 层 3「高级过滤器」：规则选择、当前规则列表、清空
 * <p>
 * Commands / Core 完全不变
 */
public class SelectionFilterPanel extends AbstractPanel implements ICommandsAware {
    private static final Comparator<String> PRESET_ID_COMPARATOR = Comparator
            .comparingInt(SelectionFilterPanel::presetSortOrder)
            .thenComparing(FilterIds::getPresetDisplayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Comparator.naturalOrder());
    private static final Comparator<String> FILTER_ID_COMPARATOR = Comparator
            .comparingInt(SelectionFilterPanel::filterSortOrder)
            .thenComparing(FilterIds::getFilterDisplayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Comparator.naturalOrder());

    public static final String PANEL_NAME = PanelId.SELECTION_FILTER;
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionFilterPanel.class);

    private UICommandFacade commands;

    public SelectionFilterPanel() {
        super();
        setVisible(false);  // 默认布局不打开，需从菜单「选择过滤」打开
    }

    /** 当前选择的过滤器ID（高级区） */
    private String selectedFilterId = "";

    /** 当前选择的模式 / 预设 */
    private String selectedPresetId = "";

    /** 新建模式名称 */
    private final ImString presetNameInput = new ImString("", 64);

    /** 保存模式被拒绝时的提示（如使用内置名），成功保存后清空 */
    private String saveBlockedReason = null;

    @Override
    public void setCommands(UICommandFacade commands) {
        this.commands = commands;
    }

    private SelectionFilterCommands sf() {
        return commands != null ? commands.selectionFilter() : null;
    }

    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }

    @Override
    public String getWindowName() {
        return PanelWindows.selectionFilter();
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    protected int getWindowFlags() {
        return 0;
    }

    @Override
    protected void doRender() {
        SelectionFilterCommands sf = sf();
        if (sf == null) {
            ImGui.textDisabled(t(DirectorLang.SelectionFilterPanel.WAITING_COMMANDS));
            return;
        }

        // ===============================
        // 🟢 层 1：基础内容过滤（普通用户）
        // ===============================
        if (ImGui.collapsingHeader(Icons.TARGET + " " + t(DirectorLang.SelectionFilterPanel.SECTION_BASIC), ImGuiTreeNodeFlags.DefaultOpen)) {
            renderBasicFilters(sf);
        }

        // ===============================
        // 🟣 层 2：选择模式（预设）
        // ===============================
        if (ImGui.collapsingHeader(Icons.BOOKMARK + " " + t(DirectorLang.SelectionFilterPanel.SECTION_MODES))) {
            renderModes(sf);
        }

        // ===============================
        // 🔵 层 3：高级过滤（专家）
        // ===============================
        if (ImGui.collapsingHeader(Icons.TOOLS + " " + t(DirectorLang.SelectionFilterPanel.SECTION_ADVANCED))) {
            renderAdvancedFilters(sf);
        }
    }

    // -------------------------------------------------
    // 🟢 基础内容过滤（Checkbox，人话；filterId 见 FilterIds / docs/selection-filter-ids.md）
    // -------------------------------------------------
    private void renderBasicFilters(SelectionFilterCommands sf) {
        ImGui.indent();

        checkboxFilter(sf, t(DirectorLang.SelectionFilterPanel.BASIC_EXCLUDE_AIR), FilterIds.EXCLUDE_AIR);
        checkboxFilter(sf, t(DirectorLang.SelectionFilterPanel.BASIC_EXCLUDE_WATER), FilterIds.EXCLUDE_WATER);
        checkboxFilter(sf, t(DirectorLang.SelectionFilterPanel.BASIC_EXCLUDE_LAVA), FilterIds.EXCLUDE_LAVA);

        ImGui.separator();

        checkboxFilter(sf, t(DirectorLang.SelectionFilterPanel.BASIC_SOLID_ONLY), FilterIds.SOLID_ONLY);
        checkboxFilter(sf, t(DirectorLang.SelectionFilterPanel.BASIC_PLANT_ONLY), FilterIds.PLANT_ONLY);
        checkboxFilter(sf, t(DirectorLang.SelectionFilterPanel.BASIC_REPLACEABLE_ONLY), FilterIds.REPLACEABLE_ONLY);
        checkboxFilter(sf, t(DirectorLang.SelectionFilterPanel.BASIC_TRANSPARENT_ONLY), FilterIds.TRANSPARENT_ONLY);

        String conflict = getBasicConflictHint(sf);
        if (conflict != null) {
            ImGui.spacing();
            UITheme.label(conflict, UITheme.DarkTheme.TEXT_WARNING);
        }

        ImGui.unindent();
    }

    /** 检测基础层互斥组合：solid_only 与 plant/replaceable/transparent 同时开易导致选不到。仅提示，不自动取消。 */
    private String getBasicConflictHint(SelectionFilterCommands sf) {
        if (!sf.isFilterEnabled(FilterIds.SOLID_ONLY)) return null;
        List<String> with = new ArrayList<>();
        if (sf.isFilterEnabled(FilterIds.PLANT_ONLY)) with.add(t(DirectorLang.SelectionFilterPanel.BASIC_PLANT_ONLY));
        if (sf.isFilterEnabled(FilterIds.REPLACEABLE_ONLY)) with.add(t(DirectorLang.SelectionFilterPanel.CONFLICT_REPLACEABLE_SHORT));
        if (sf.isFilterEnabled(FilterIds.TRANSPARENT_ONLY)) with.add(t(DirectorLang.SelectionFilterPanel.BASIC_TRANSPARENT_ONLY));
        if (with.isEmpty()) return null;
        return t(DirectorLang.SelectionFilterPanel.CONFLICT_HINT, String.join(t(DirectorLang.SelectionFilterPanel.CONFLICT_DELIMITER), with));
    }

    private void checkboxFilter(SelectionFilterCommands sf, String label, String filterId) {
        boolean enabled = sf.isFilterEnabled(filterId);
        if (ImGui.checkbox(label + "##" + filterId, enabled)) {
            sf.setFilterEnabled(filterId, !enabled);
            selectedPresetId = "";
            LOGGER.info("[SelectionFilter] {} -> {}", filterId, !enabled);
        }
    }

    // -------------------------------------------------
    // 🟣 选择模式（Preset 的 UI 语义；显示名见 FilterIds.getPresetDisplayName）
    // -------------------------------------------------
    private void renderModes(SelectionFilterCommands sf) {
        ImGui.indent();

        List<String> presetIds = new ArrayList<>(sf.getPresetIds());
        presetIds.sort(PRESET_ID_COMPARATOR);
        String current = selectedPresetId.isEmpty() ? t(DirectorLang.SelectionFilterPanel.MODE_CUSTOM) : FilterIds.getPresetDisplayName(selectedPresetId);

        if (ImGui.beginCombo(t(DirectorLang.SelectionFilterPanel.MODE_CURRENT_LABEL) + "##modeSelect", current)) {
            for (String id : presetIds) {
                boolean selected = id.equals(selectedPresetId);
                String label = FilterIds.getPresetDisplayName(id);
                if (ImGui.selectable(label + "##" + id, selected)) {
                    selectedPresetId = id;
                    sf.loadPreset(id);
                    LOGGER.info("[SelectionFilter] 切换模式: {}", id);
                }
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        UITheme.label(t(DirectorLang.SelectionFilterPanel.MODE_SAVE_LABEL), UITheme.DarkTheme.TEXT);
        ImGui.inputText("##newModeName", presetNameInput);
        ImGui.sameLine();
        if (ImGui.button(t(DirectorLang.SelectionFilterPanel.MODE_SAVE_BUTTON))) {
            String name = presetNameInput.get().trim();
            saveBlockedReason = null;
            if (name.isEmpty()) {
                // 不保存
            } else if (FilterIds.isBuiltinPresetId(name)) {
                saveBlockedReason = t(DirectorLang.SelectionFilterPanel.MODE_SAVE_BUILTIN_BLOCKED);
            } else if (sf.isEmpty()) {
                // 不保存空管道
            } else {
                sf.savePreset(name);
                presetNameInput.set("");
                LOGGER.info("[SelectionFilter] 保存新模式: {}", name);
            }
        }
        if (saveBlockedReason != null) {
            UITheme.label(saveBlockedReason, UITheme.DarkTheme.TEXT_WARNING);
        }

        ImGui.unindent();
    }

    // -------------------------------------------------
    // 🔵 高级过滤（完整系统，语义：规则 / 当前规则列表）
    // -------------------------------------------------
    private void renderAdvancedFilters(SelectionFilterCommands sf) {
        ImGui.indent();

        // --- 添加规则 ---
        List<String> ids = new ArrayList<>(sf.getFilterIds());
        ids.sort(FILTER_ID_COMPARATOR);
        String currentName = selectedFilterId.isEmpty()
                ? t(DirectorLang.SelectionFilterPanel.ADV_RULE_PLACEHOLDER)
                : FilterIds.getFilterDisplayName(selectedFilterId);

        if (ImGui.beginCombo(t(DirectorLang.SelectionFilterPanel.ADV_ADD_RULE_LABEL) + "##filterType", currentName)) {
            for (String id : ids) {
                boolean selected = id.equals(selectedFilterId);
                String label = FilterIds.getFilterDisplayName(id);
                if (ImGui.selectable(label + "##" + id, selected)) selectedFilterId = id;
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }

        if (!selectedFilterId.isEmpty()) {
            BlockFilter filter = sf.getFilter(selectedFilterId);
            if (filter != null) {
                ImGui.sameLine();
                if (ImGui.button(t(DirectorLang.SelectionFilterPanel.ADV_ADD_RULE_BUTTON))) {
                    sf.appendFilter(filter);
                    selectedPresetId = "";
                    LOGGER.info("[SelectionFilter] 添加高级规则: {}", selectedFilterId);
                }
            }
        }

        ImGui.separator();

        // --- 当前规则列表 ---
        List<BlockFilter> filters = new ArrayList<>(sf.getFilters());
        if (filters.isEmpty()) {
            UITheme.label(t(DirectorLang.SelectionFilterPanel.ADV_EMPTY), UITheme.DarkTheme.TEXT_DISABLED);
        } else {
            for (int i = 0; i < filters.size(); i++) {
                BlockFilter filter = filters.get(i);
                String filterId = sf.getFilterId(filter);
                String name = filterId != null
                        ? FilterIds.getFilterDisplayName(filterId)
                        : t(DirectorLang.SelectionFilterPanel.ADV_UNREGISTERED_FILTER);

                boolean open = ImGui.treeNodeEx(
                        "[" + (i + 1) + "] " + name + "##card" + i,
                        ImGuiTreeNodeFlags.DefaultOpen
                );

                ImGui.sameLine(ImGui.getContentRegionMaxX() - 24);
                if (ImGui.button(Icons.DELETE + "##rm" + i)) {
                    sf.removeFilterAt(i);
                    selectedPresetId = "";
                    LOGGER.info("[SelectionFilter] 移除规则: {}", name);
                    if (open) ImGui.treePop();
                    break;
                }

                if (open) {
                    ImGui.indent();
                    if (filterId != null && !filterId.isBlank()) {
                        UITheme.label(filterId, UITheme.DarkTheme.TEXT_DISABLED);
                    } else {
                        UITheme.label(t(DirectorLang.SelectionFilterPanel.ADV_UNREGISTERED_FILTER), UITheme.DarkTheme.TEXT_DISABLED);
                    }
                    ImGui.unindent();
                    ImGui.treePop();
                }
            }
        }

        ImGui.spacing();
        if (ImGui.button(t(DirectorLang.SelectionFilterPanel.ADV_CLEAR_ALL))) {
            sf.clear();
            selectedPresetId = "";
            LOGGER.info("[SelectionFilter] 清空所有规则");
        }

        ImGui.unindent();
    }

    private static String t(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    private static int presetSortOrder(String presetId) {
        if (presetId == null) return Integer.MAX_VALUE;
        return switch (presetId) {
            case FilterIds.PRESET_DEFAULT -> 0;
            case FilterIds.PRESET_TERRAIN_EDIT -> 1;
            case FilterIds.PRESET_PLANT_EDIT -> 2;
            case FilterIds.PRESET_FLUID_EDIT -> 3;
            case FilterIds.PRESET_REPLACEABLE_EDIT -> 4;
            default -> 1000;
        };
    }

    private static int filterSortOrder(String filterId) {
        if (filterId == null) return Integer.MAX_VALUE;
        return switch (filterId) {
            case FilterIds.EXCLUDE_AIR -> 0;
            case FilterIds.EXCLUDE_WATER -> 1;
            case FilterIds.EXCLUDE_LAVA -> 2;
            case FilterIds.SOLID_ONLY -> 3;
            case FilterIds.PLANT_ONLY -> 4;
            case FilterIds.REPLACEABLE_ONLY -> 5;
            case FilterIds.TRANSPARENT_ONLY -> 6;

            case FilterIds.SAME_BLOCK_TYPE -> 100;
            case FilterIds.SURFACE_ONLY -> 101;
            case FilterIds.VISIBLE_ONLY -> 102;
            case FilterIds.CONNECTED_ONLY -> 103;
            case FilterIds.MAX_DEPTH_LIMIT -> 104;

            case FilterIds.WITHIN_BBOX -> 200;
            case FilterIds.WITHIN_CHUNK -> 201;
            case FilterIds.WITHIN_RADIUS -> 202;
            case FilterIds.MAX_BLOCK_COUNT -> 203;

            case FilterIds.LUMINOUS -> 300;
            case FilterIds.LUMINOUS_5 -> 301;
            case FilterIds.LUMINOUS_10 -> 302;
            default -> 1000;
        };
    }
}
