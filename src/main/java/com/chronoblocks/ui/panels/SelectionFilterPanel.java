package com.chronoblocks.ui.panels;

import com.chronoblocks.core.selection.block_filters.BlockFilter;
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
 * SelectionFilterPanel v2.1
 * <p>
 * 架构收口：<strong>只通过 SelectionFilterCommands 配置管道，不直接触碰 SelectionManager / BlockFilterPipeline。</strong>
 * <p>
 * 特性：
 * - 基于 BlockFilter 规则对象与 BlockFilterPipeline
 * - 快速过滤、过滤器选择、管道卡片、预设、性能统计 — 全部经 commands.selectionFilter() 完成
 * - 实现 ICommandsAware，由 UIPanelManager 注入 UICommandFacade
 *
 * @author ChronoBlocks Team
 * @version 2.1
 */
public class SelectionFilterPanel extends AbstractPanel implements ICommandsAware {

    public static final String PANEL_NAME = "SelectionFilter";
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionFilterPanel.class);

    private UICommandFacade commands;

    /** 当前选择的过滤器ID */
    private String selectedFilterId = "";

    /** 当前选择的预设ID */
    private String selectedPresetId = "";

    /** 自定义预设名称输入 */
    private final ImString presetNameInput = new ImString("", 64);

    public SelectionFilterPanel() {
        LOGGER.info("[SelectionFilterPanel] 初始化过滤器面板 v2.1");
    }

    @Override
    public void setCommands(UICommandFacade commands) {
        this.commands = commands;
    }

    private SelectionFilterCommands sf() {
        return commands != null ? commands.selectionFilter() : null;
    }

    @Override
    public String getPanelName() { return PANEL_NAME; }

    @Override
    public String getWindowName() { return Icons.FILTER + " 过滤器面板###SelectionFilterPanel"; }

    @Override
    public int getPriority() { return 25; }

    @Override
    protected int getWindowFlags() { return 0; }

    @Override
    protected void doRender() {
        SelectionFilterCommands sf = sf();
        if (sf == null) {
            ImGui.textDisabled("(等待命令门面)");
            return;
        }

        if (ImGui.collapsingHeader(Icons.TARGET + " 快速过滤", ImGuiTreeNodeFlags.DefaultOpen))
            renderQuickFilters(sf);

        if (ImGui.collapsingHeader(Icons.SEARCH + " 过滤器选择", ImGuiTreeNodeFlags.DefaultOpen))
            renderFilterSelection(sf);

        if (ImGui.collapsingHeader(Icons.LINK + " 当前过滤管道", ImGuiTreeNodeFlags.DefaultOpen))
            renderPipeline(sf);

        if (ImGui.collapsingHeader(Icons.BOOKMARK + " 预设管理"))
            renderPresets(sf);

        if (ImGui.collapsingHeader(Icons.PLAY + " 调试与统计"))
            renderTesting(sf);
    }

    // -------------------------------
    // 快速过滤
    // -------------------------------
    private void renderQuickFilters(SelectionFilterCommands sf) {
        ImGui.indent();
        if (ImGui.button("非空气")) { sf.applyQuickFilter("non_air"); LOGGER.info("[FilterPanel] 应用: non_air"); }
        ImGui.sameLine();
        if (ImGui.button("固体")) { sf.applyQuickFilter("solid"); LOGGER.info("[FilterPanel] 应用: solid"); }
        if (ImGui.button("透明")) { sf.applyQuickFilter("transparent"); LOGGER.info("[FilterPanel] 应用: transparent"); }
        ImGui.sameLine();
        if (ImGui.button("可替换")) { sf.applyQuickFilter("replaceable"); LOGGER.info("[FilterPanel] 应用: replaceable"); }
        ImGui.unindent();
    }

    // -------------------------------
    // 过滤器选择 + 动态参数配置
    // -------------------------------
    private void renderFilterSelection(SelectionFilterCommands sf) {
        ImGui.indent();

        Set<String> ids = sf.getFilterIds();
        String currentName = selectedFilterId.isEmpty() ? "选择过滤器..." : selectedFilterId;

        if (ImGui.beginCombo("过滤器类型##filterType", currentName)) {
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
                ImGui.spacing();
                UITheme.label("过滤器: " + filter.getClass().getSimpleName(), UITheme.DarkTheme.TEXT_DISABLED);
                if (ImGui.button("添加到管道##addFilter")) {
                    sf.appendFilter(filter);
                    LOGGER.info("[FilterPanel] 添加过滤器: {}", selectedFilterId);
                }
            }
        }

        ImGui.unindent();
    }

    // -------------------------------
    // 当前过滤管道（卡片化显示）
    // -------------------------------
    private void renderPipeline(SelectionFilterCommands sf) {
        ImGui.indent();

        List<BlockFilter> filters = new ArrayList<>(sf.getFilters());
        if (filters.isEmpty()) {
            UITheme.label("当前管道为空", UITheme.DarkTheme.TEXT_DISABLED);
            ImGui.unindent();
            return;
        }

        for (int i = 0; i < filters.size(); i++) {
            BlockFilter filter = filters.get(i);
            String filterName = sf.getFilterId(filter);
            if (filterName == null) filterName = filter.getClass().getSimpleName();

            boolean open = ImGui.treeNodeEx("[" + (i + 1) + "] " + filterName + "##card" + i, ImGuiTreeNodeFlags.DefaultOpen);
            ImGui.sameLine(ImGui.getContentRegionMaxX() - 20);
            if (ImGui.button(Icons.DELETE + "##remove" + i, 0, 0)) {
                sf.removeFilterAt(i);
                LOGGER.info("[FilterPanel] 移除过滤器: {}", filterName);
                if (open) ImGui.treePop();
                break;
            }

            if (open) {
                ImGui.indent();
                UITheme.label("类型: " + filter.getClass().getName(), UITheme.DarkTheme.TEXT_DISABLED);
                ImGui.unindent();
                ImGui.treePop();
            }
        }

        ImGui.spacing();
        if (ImGui.button("清空管道##clearPipeline")) {
            sf.clear();
            LOGGER.info("[FilterPanel] 清空管道");
        }

        ImGui.unindent();
    }

    // -------------------------------
    // 预设管理
    // -------------------------------
    private void renderPresets(SelectionFilterCommands sf) {
        ImGui.indent();

        Set<String> presetIds = sf.getPresetIds();
        String currentPresetName = selectedPresetId.isEmpty() ? "选择预设..." : selectedPresetId;

        if (ImGui.beginCombo("预设##presetSelect", currentPresetName)) {
            for (String presetId : presetIds) {
                boolean selected = presetId.equals(selectedPresetId);
                if (ImGui.selectable(presetId, selected)) selectedPresetId = presetId;
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }

        if (!selectedPresetId.isEmpty()) {
            ImGui.sameLine();
            if (ImGui.button("加载##loadPreset")) {
                sf.loadPreset(selectedPresetId);
                LOGGER.info("[FilterPanel] 加载预设: {}", selectedPresetId);
            }
            ImGui.sameLine();
            if (ImGui.button("删除##deletePreset")) {
                sf.deletePreset(selectedPresetId);
                selectedPresetId = "";
                LOGGER.info("[FilterPanel] 删除预设");
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        UITheme.label("保存当前管道为预设:", UITheme.DarkTheme.TEXT);
        ImGui.inputText("##presetName", presetNameInput);
        ImGui.sameLine();
        if (ImGui.button("保存##savePreset")) {
            String name = presetNameInput.get().trim();
            if (!name.isEmpty() && !sf.isEmpty()) {
                sf.savePreset(name);
                LOGGER.info("[FilterPanel] 保存预设: {}", name);
                presetNameInput.set("");
            }
        }

        ImGui.unindent();
    }

    // -------------------------------
    // 测试与应用
    // -------------------------------
    private void renderTesting(SelectionFilterCommands sf) {
        ImGui.indent();

        UITheme.label("管道统计", UITheme.DarkTheme.TEXT);
        ImGui.text("过滤器: " + sf.size() + "  最近: " + (sf.getLastNanos() / 1000.0) + " μs  总次数: " + sf.getTotalChecked());

        ImGui.spacing();
        if (ImGui.button("启用性能分析##enableProfiling")) { sf.enableProfiling(true); LOGGER.info("[FilterPanel] 启用性能分析"); }
        ImGui.sameLine();
        if (ImGui.button("禁用性能分析##disableProfiling")) { sf.enableProfiling(false); LOGGER.info("[FilterPanel] 禁用性能分析"); }
        ImGui.spacing();
        if (ImGui.button("重置统计##resetStats")) { sf.resetStats(); LOGGER.info("[FilterPanel] 重置统计"); }

        ImGui.unindent();
    }
}
