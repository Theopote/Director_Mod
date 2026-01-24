package com.chronoblocks.ui.panels;

import com.chronoblocks.core.selection.block_filters.BlockFilter;
import com.chronoblocks.core.selection.block_filters.FilterIds;
import com.chronoblocks.ui.commands.SelectionFilterCommands;
import com.chronoblocks.ui.commands.UICommandFacade;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.aware.ICommandsAware;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public static final String PANEL_NAME = "SelectionFilter";
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
        return Icons.FILTER + " 选择过滤###SelectionFilterPanel";
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
            ImGui.textDisabled("(等待命令门面)");
            return;
        }

        // ===============================
        // 🟢 层 1：基础内容过滤（普通用户）
        // ===============================
        if (ImGui.collapsingHeader(Icons.TARGET + " 选择内容", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderBasicFilters(sf);
        }

        // ===============================
        // 🟣 层 2：选择模式（预设）
        // ===============================
        if (ImGui.collapsingHeader(Icons.BOOKMARK + " 选择模式")) {
            renderModes(sf);
        }

        // ===============================
        // 🔵 层 3：高级过滤（专家）
        // ===============================
        if (ImGui.collapsingHeader(Icons.TOOLS + " 高级过滤器 ▶")) {
            renderAdvancedFilters(sf);
        }
    }

    // -------------------------------------------------
    // 🟢 基础内容过滤（Checkbox，人话；filterId 见 FilterIds / docs/selection-filter-ids.md）
    // -------------------------------------------------
    private void renderBasicFilters(SelectionFilterCommands sf) {
        ImGui.indent();

        checkboxFilter(sf, "忽略空气", FilterIds.EXCLUDE_AIR);
        checkboxFilter(sf, "忽略水", FilterIds.EXCLUDE_WATER);
        checkboxFilter(sf, "忽略岩浆", FilterIds.EXCLUDE_LAVA);

        ImGui.separator();

        checkboxFilter(sf, "仅选择固体方块", FilterIds.SOLID_ONLY);
        checkboxFilter(sf, "仅选择植物", FilterIds.PLANT_ONLY);
        checkboxFilter(sf, "仅选择可替换方块（草 / 花 / 雪）", FilterIds.REPLACEABLE_ONLY);
        checkboxFilter(sf, "仅选择透明方块", FilterIds.TRANSPARENT_ONLY);

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
        if (sf.isFilterEnabled(FilterIds.PLANT_ONLY)) with.add("仅选择植物");
        if (sf.isFilterEnabled(FilterIds.REPLACEABLE_ONLY)) with.add("仅选择可替换方块");
        if (sf.isFilterEnabled(FilterIds.TRANSPARENT_ONLY)) with.add("仅选择透明方块");
        if (with.isEmpty()) return null;
        return "当前组合可能导致选不到任何方块：仅选择固体 + " + String.join("、", with);
    }

    private void checkboxFilter(SelectionFilterCommands sf, String label, String filterId) {
        boolean enabled = sf.isFilterEnabled(filterId);
        if (ImGui.checkbox(label + "##" + filterId, enabled)) {
            sf.setFilterEnabled(filterId, !enabled);
            LOGGER.info("[SelectionFilter] {} -> {}", filterId, !enabled);
        }
    }

    // -------------------------------------------------
    // 🟣 选择模式（Preset 的 UI 语义；显示名见 FilterIds.getPresetDisplayName）
    // -------------------------------------------------
    private void renderModes(SelectionFilterCommands sf) {
        ImGui.indent();

        Set<String> presetIds = sf.getPresetIds();
        String current = selectedPresetId.isEmpty() ? "通用编辑（默认）" : FilterIds.getPresetDisplayName(selectedPresetId);

        if (ImGui.beginCombo("当前模式##modeSelect", current)) {
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

        UITheme.label("保存当前过滤为新模式:", UITheme.DarkTheme.TEXT);
        ImGui.inputText("##newModeName", presetNameInput);
        ImGui.sameLine();
        if (ImGui.button("保存模式")) {
            String name = presetNameInput.get().trim();
            if (!name.isEmpty() && !sf.isEmpty()) {
                sf.savePreset(name);
                presetNameInput.set("");
                LOGGER.info("[SelectionFilter] 保存新模式: {}", name);
            }
        }

        ImGui.unindent();
    }

    // -------------------------------------------------
    // 🔵 高级过滤（完整系统，语义：规则 / 当前规则列表）
    // -------------------------------------------------
    private void renderAdvancedFilters(SelectionFilterCommands sf) {
        ImGui.indent();

        // --- 添加规则 ---
        Set<String> ids = sf.getFilterIds();
        String currentName = selectedFilterId.isEmpty() ? "选择规则..." : selectedFilterId;

        if (ImGui.beginCombo("添加规则##filterType", currentName)) {
            for (String id : ids) {
                boolean selected = id.equals(selectedFilterId);
                if (ImGui.selectable(id, selected)) selectedFilterId = id;
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }

        if (!selectedFilterId.isEmpty()) {
            BlockFilter filter = sf.getFilter(selectedFilterId);
            if (filter != null) {
                ImGui.sameLine();
                if (ImGui.button("添加到规则列表")) {
                    sf.appendFilter(filter);
                    LOGGER.info("[SelectionFilter] 添加高级规则: {}", selectedFilterId);
                }
            }
        }

        ImGui.separator();

        // --- 当前规则列表 ---
        List<BlockFilter> filters = new ArrayList<>(sf.getFilters());
        if (filters.isEmpty()) {
            UITheme.label("当前没有启用任何高级规则", UITheme.DarkTheme.TEXT_DISABLED);
        } else {
            for (int i = 0; i < filters.size(); i++) {
                BlockFilter filter = filters.get(i);
                String name = sf.getFilterId(filter);
                if (name == null) name = filter.getClass().getSimpleName();

                boolean open = ImGui.treeNodeEx(
                        "[" + (i + 1) + "] " + name + "##card" + i,
                        ImGuiTreeNodeFlags.DefaultOpen
                );

                ImGui.sameLine(ImGui.getContentRegionMaxX() - 24);
                if (ImGui.button(Icons.DELETE + "##rm" + i)) {
                    sf.removeFilterAt(i);
                    LOGGER.info("[SelectionFilter] 移除规则: {}", name);
                    if (open) ImGui.treePop();
                    break;
                }

                if (open) {
                    ImGui.indent();
                    UITheme.label(filter.getClass().getName(), UITheme.DarkTheme.TEXT_DISABLED);
                    ImGui.unindent();
                    ImGui.treePop();
                }
            }
        }

        ImGui.spacing();
        if (ImGui.button("清空高级规则")) {
            sf.clear();
            LOGGER.info("[SelectionFilter] 清空高级规则");
        }

        ImGui.unindent();
    }
}
