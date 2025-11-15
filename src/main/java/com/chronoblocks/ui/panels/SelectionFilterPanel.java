package com.chronoblocks.ui.panels;

import com.chronoblocks.core.selection.block_filters.*;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * SelectionFilterPanel v2.0
 * <p>
 * 重构版：使用新的 block_filters 系统
 * ------------------------------------------------
 * 特性：
 * - 基于 BlockFilter 和 BlockFilterPipeline
 * - 动态注册：通过 BlockFilterRegistry 自动加载过滤器
 * - 卡片化管道：TreeNode 样式，支持启用/禁用、编辑、删除
 * - 预设支持（加载 / 保存）via BlockFilterPresetManager
 * - 简洁的UI逻辑
 * <p>
 * 作者：ChronoBlocks Team
 * 版本：2.0
 */
public class SelectionFilterPanel extends AbstractPanel {
    
    public static final String PANEL_NAME = "SelectionFilter";
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionFilterPanel.class);
    
    /** 获取当前管道 */
    private BlockFilterPipeline getPipeline() {
        return com.chronoblocks.core.selection.SelectionManager.get().getPipeline();
    }
    
    /** 当前选择的过滤器ID */
    private String selectedFilterId = "";
    
    /** 当前选择的预设ID */
    private String selectedPresetId = "";
    
    /** 自定义预设名称输入 */
    private final ImString presetNameInput = new ImString("", 64);
    
    public SelectionFilterPanel() {
        LOGGER.info("[SelectionFilterPanel] 初始化过滤器面板 v2.0");
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
        UITheme.title(Icons.FILTER + " 选择过滤器");
        ImGui.spacing();
        
        if (ImGui.collapsingHeader(Icons.TARGET + " 快速过滤", ImGuiTreeNodeFlags.DefaultOpen))
            renderQuickFilters();
        
        if (ImGui.collapsingHeader(Icons.SEARCH + " 过滤器选择", ImGuiTreeNodeFlags.DefaultOpen))
            renderFilterSelection();
        
        if (ImGui.collapsingHeader(Icons.LINK + " 当前过滤管道", ImGuiTreeNodeFlags.DefaultOpen))
            renderPipeline();
        
        if (ImGui.collapsingHeader(Icons.BOOKMARK + " 预设管理"))
            renderPresets();
        
        if (ImGui.collapsingHeader(Icons.PLAY + " 快速应用"))
            renderTesting();
    }
    
    // -------------------------------
    // 快速过滤
    // -------------------------------
    private void renderQuickFilters() {
        ImGui.indent();
        if (ImGui.button("非空气")) applyQuickFilter("non_air");
        ImGui.sameLine();
        if (ImGui.button("固体")) applyQuickFilter("solid");
        ImGui.sameLine();
        if (ImGui.button("透明")) applyQuickFilter("transparent");
        ImGui.sameLine();
        if (ImGui.button("可替换")) applyQuickFilter("replaceable");
        ImGui.unindent();
    }
    
    private void applyQuickFilter(String id) {
        BlockFilter filter = BlockFilterRegistry.getFilter(id);
        if (filter != null) {
            getPipeline().clear();
            getPipeline().add(filter);
            LOGGER.info("[FilterPanel] 应用快速过滤器: {}", id);
        } else {
            // 尝试预设
            BlockFilterPipeline preset = BlockFilterPresetManager.getPreset(id);
            if (preset != null) {
                getPipeline().clear();
                getPipeline().addAll(preset.getFilters());
                LOGGER.info("[FilterPanel] 应用快速预设: {}", id);
            }
        }
    }
    
    // -------------------------------
    // 过滤器选择 + 动态参数配置
    // -------------------------------
    private void renderFilterSelection() {
        ImGui.indent();
        
        Set<String> ids = BlockFilterRegistry.getAllFilterIds();
        String currentName = selectedFilterId.isEmpty() ? "选择过滤器..." : selectedFilterId;
        
        if (ImGui.beginCombo("过滤器类型##filterType", currentName)) {
            for (String id : ids) {
                boolean selected = id.equals(selectedFilterId);
                if (ImGui.selectable(id, selected)) {
                    selectedFilterId = id;
                }
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }
        
        if (!selectedFilterId.isEmpty()) {
            BlockFilter filter = BlockFilterRegistry.getFilter(selectedFilterId);
            if (filter != null) {
                ImGui.spacing();
                UITheme.label("过滤器: " + filter.getClass().getSimpleName(), UITheme.DarkTheme.TEXT_DISABLED);
                
                if (ImGui.button("添加到管道##addFilter")) {
                    getPipeline().add(filter);
                    LOGGER.info("[FilterPanel] 添加过滤器: {}", selectedFilterId);
                }
            }
        }
        
        ImGui.unindent();
    }
    
    // -------------------------------
    // 当前过滤管道（卡片化显示）
    // -------------------------------
    private void renderPipeline() {
        ImGui.indent();
        
        List<BlockFilter> filters = new ArrayList<>(getPipeline().getFilters());
        if (filters.isEmpty()) {
            UITheme.label("当前管道为空", UITheme.DarkTheme.TEXT_DISABLED);
            ImGui.unindent();
            return;
        }
        
        for (int i = 0; i < filters.size(); i++) {
            BlockFilter filter = filters.get(i);
            String filterName = BlockFilterRegistry.getFilterId(filter);
            if (filterName == null) {
                filterName = filter.getClass().getSimpleName();
            }
            
            boolean open = ImGui.treeNodeEx("[" + (i + 1) + "] " + filterName + "##card" + i, ImGuiTreeNodeFlags.DefaultOpen);
            
            // 删除按钮
            ImGui.sameLine(ImGui.getContentRegionMaxX() - 20);
            if (ImGui.button(Icons.DELETE + "##remove" + i, 0, 0)) {
                filters.remove(i);
                // 重建管道
                getPipeline().clear();
                getPipeline().addAll(filters);
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
            getPipeline().clear();
            LOGGER.info("[FilterPanel] 清空管道");
        }
        
        ImGui.unindent();
    }
    
    // -------------------------------
    // 预设管理
    // -------------------------------
    private void renderPresets() {
        ImGui.indent();
        
        Set<String> presetIds = BlockFilterPresetManager.getPresetIds();
        String currentPresetName = selectedPresetId.isEmpty() ? "选择预设..." : selectedPresetId;
        
        if (ImGui.beginCombo("预设##presetSelect", currentPresetName)) {
            for (String presetId : presetIds) {
                boolean selected = presetId.equals(selectedPresetId);
                if (ImGui.selectable(presetId, selected)) {
                    selectedPresetId = presetId;
                }
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }
        
        if (!selectedPresetId.isEmpty()) {
            ImGui.sameLine();
            if (ImGui.button("加载##loadPreset")) {
                BlockFilterPipeline preset = BlockFilterPresetManager.getPreset(selectedPresetId);
                if (preset != null) {
                    getPipeline().clear();
                    getPipeline().addAll(preset.getFilters());
                    LOGGER.info("[FilterPanel] 加载预设: {}", selectedPresetId);
                }
            }
            
            ImGui.sameLine();
            if (ImGui.button("删除##deletePreset")) {
                BlockFilterPresetManager.deletePreset(selectedPresetId);
                selectedPresetId = "";
                LOGGER.info("[FilterPanel] 删除预设: {}", selectedPresetId);
            }
        }
        
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        
        UITheme.label("保存当前管道为预设:", UITheme.DarkTheme.TEXT);
        ImGui.inputText("##presetName", presetNameInput);
        ImGui.sameLine();
        if (ImGui.button("保存##savePreset")) {
            String presetName = presetNameInput.get().trim();
            if (!presetName.isEmpty() && !getPipeline().isEmpty()) {
                BlockFilterPresetManager.savePreset(presetName, getPipeline());
                LOGGER.info("[FilterPanel] 保存预设: {}", presetName);
                presetNameInput.set("");
            }
        }
        
        ImGui.unindent();
    }
    
    // -------------------------------
    // 测试与应用
    // -------------------------------
    private void renderTesting() {
        ImGui.indent();
        
        UITheme.label("当前管道统计:", UITheme.DarkTheme.TEXT);
        ImGui.text("过滤器数量: " + getPipeline().size());
        ImGui.text("性能分析: " + (getPipeline().getLastNanos() / 1000.0) + " μs");
        ImGui.text("总测试次数: " + getPipeline().getTotalChecked());
        
        ImGui.spacing();
        if (ImGui.button("启用性能分析##enableProfiling")) {
            getPipeline().enableProfiling(true);
            LOGGER.info("[FilterPanel] 启用性能分析");
        }
        ImGui.sameLine();
        if (ImGui.button("禁用性能分析##disableProfiling")) {
            getPipeline().enableProfiling(false);
            LOGGER.info("[FilterPanel] 禁用性能分析");
        }
        
        ImGui.spacing();
        if (ImGui.button("重置统计##resetStats")) {
            getPipeline().resetStats();
            LOGGER.info("[FilterPanel] 重置统计");
        }
        
        ImGui.unindent();
    }
}
