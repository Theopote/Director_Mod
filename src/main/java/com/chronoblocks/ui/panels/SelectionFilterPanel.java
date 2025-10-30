package com.chronoblocks.ui.panels;

import com.chronoblocks.core.selection.filter.SelectionFilter;
import com.chronoblocks.core.selection.filter.SelectionFilterSystem;
import com.chronoblocks.core.selection.filter.SelectionPipeline;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 🎯 选择过滤器面板
 * 
 * 提供可视化的过滤器编辑和管理功能，包括：
 * - 过滤器选择和配置
 * - 预设过滤器管理
 * - 自定义过滤器创建
 * - 过滤器组合和测试
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionFilterPanel extends AbstractPanel {
    
    /** 面板名称常量 */
    public static final String PANEL_NAME = "SelectionFilter";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionFilterPanel.class);
    
    /** 当前活动的过滤器管道 */
    private SelectionPipeline currentPipeline = new SelectionPipeline();
    
    /** 可用的过滤器ID列表 */
    private final List<String> availableFilters = new ArrayList<>();
    
    /** 可用的预设ID列表 */
    private final List<String> availablePresets = new ArrayList<>();
    
    /** 选中的过滤器ID */
    private String selectedFilterId = "";
    
    /** 选中的预设ID */
    private String selectedPresetId = "";
    
    /** 自定义过滤器名称 */
    private final ImString customFilterName = new ImString("", 64);
    
    /** 自定义过滤器条件 */
    private final ImString customFilterCondition = new ImString("", 256);
    
    /** 高度范围设置 */
    private int minHeight = 0;
    private int maxHeight = 256;
    
    /** 距离过滤器设置 */
    private double maxDistance = 10.0;
    
    /** 硬度范围设置 */
    private float minHardness = 0.0f;
    private float maxHardness = 10.0f;
    
    /** 是否显示高级选项 */
    private boolean showAdvancedOptions = false;
    
    /** 过滤器测试结果 */
    private int testResultCount = 0;
    
    public SelectionFilterPanel() {
        updateAvailableFilters();
        updateAvailablePresets();
        LOGGER.info("[SelectionFilterPanel] 初始化选择过滤器面板");
    }
    
    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }
    
    @Override
    public String getWindowName() {
        return Icons.FILTER + " 选择过滤器###SelectionFilterPanel";
    }
    
    @Override
    public int getPriority() {
        return 25; // 中等优先级
    }
    
    @Override
    protected int getWindowFlags() {
        // 独立面板，允许拖拽、调整大小、折叠
        return imgui.flag.ImGuiWindowFlags.None;
    }
    
    @Override
    public boolean shouldReserveDockSpaceWhenHidden() {
        return super.shouldReserveDockSpaceWhenHidden();
    }
    
    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }
    
    @Override
    protected void doRender() {
        UITheme.title(Icons.FILTER + " 选择过滤器");
        
        // 渲染快速操作栏
        renderQuickActionBar();
        ImGui.separator();
        
        // 渲染过滤器选择区域
        renderFilterSelection();
        ImGui.separator();
        
        // 渲染当前过滤器管道
        renderCurrentPipeline();
        ImGui.separator();
        
        // 渲染预设管理器
        renderPresetManager();
        ImGui.separator();
        
        // 渲染自定义过滤器
        renderCustomFilters();
        ImGui.separator();
        
        // 渲染过滤器测试
        renderFilterTest();
        ImGui.separator();
        
        // 渲染操作按钮
        renderActionButtons();
    }
    
    /**
     * 渲染快速操作栏
     */
    private void renderQuickActionBar() {
        UITheme.label("快速操作:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.button("非空气##quickNonAir")) {
            applyQuickFilter("nonAir");
        }
        ImGui.sameLine();
        
        if (ImGui.button("固体##quickSolid")) {
            applyQuickFilter("solid");
        }
        ImGui.sameLine();
        
        if (ImGui.button("透明##quickTransparent")) {
            applyQuickFilter("transparent");
        }
        ImGui.sameLine();
        
        if (ImGui.button("建筑材料##quickBuilding")) {
            applyQuickFilter("buildingMaterials");
        }
    }
    
    /**
     * 应用快速过滤器
     */
    private void applyQuickFilter(String filterType) {
        SelectionFilter filter = null;
        switch (filterType) {
            case "nonAir":
                filter = SelectionFilter.nonAir();
                break;
            case "solid":
                filter = SelectionFilter.nonAir(); // 使用非空气作为固体过滤器的简化实现
                break;
            case "transparent":
                filter = SelectionFilter.nonAir(); // 使用非空气作为透明过滤器的简化实现
                break;
            case "buildingMaterials":
                // 使用预设的建筑材料过滤器
                SelectionPipeline preset = SelectionFilterSystem.getPreset("buildingMaterials");
                if (preset != null && !preset.getFilters().isEmpty()) {
                    filter = preset.getFilters().getFirst();
                }
                break;
        }
        
        if (filter != null) {
            currentPipeline.clear(); // 清空现有管道
            currentPipeline.add(filter);
            LOGGER.info("[SelectionFilterPanel] 应用快速过滤器: {}", filterType);
        }
    }
    
    /**
     * 渲染过滤器选择区域
     */
    private void renderFilterSelection() {
        UITheme.label("过滤器选择:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 过滤器类型选择
        if (ImGui.beginCombo("选择过滤器类型##filterType", selectedFilterId.isEmpty() ? "选择过滤器..." : selectedFilterId)) {
            for (String filterId : availableFilters) {
                boolean isSelected = filterId.equals(selectedFilterId);
                if (ImGui.selectable(filterId, isSelected)) {
                    selectedFilterId = filterId;
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        
        // 添加选中的过滤器
        if (ImGui.button("添加过滤器##addFilter") && !selectedFilterId.isEmpty()) {
            addSelectedFilter();
        }
        
        ImGui.sameLine();
        
        // 显示过滤器描述
        if (!selectedFilterId.isEmpty()) {
            String description = getFilterDescription(selectedFilterId);
            if (!description.isEmpty()) {
                UITheme.label("描述: " + description, UITheme.DarkTheme.TEXT_DISABLED);
            }
        }
    }
    
    /**
     * 渲染当前过滤器管道
     */
    private void renderCurrentPipeline() {
        UITheme.label("当前过滤器管道:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (currentPipeline.getFilters().isEmpty()) {
            UITheme.label("无过滤器", UITheme.DarkTheme.TEXT_DISABLED);
        } else {
            for (int i = 0; i < currentPipeline.getFilters().size(); i++) {
                SelectionFilter filter = currentPipeline.getFilters().get(i);
                ImGui.text(String.format("%d. %s", i + 1, filter.toString()));
                
                ImGui.sameLine();
                if (ImGui.button("移除##remove" + i)) {
                    currentPipeline.getFilters().remove(i);
                    i--; // 调整索引
                }
            }
        }
        
        // 清空管道按钮
        if (!currentPipeline.getFilters().isEmpty()) {
            if (ImGui.button("清空管道##clearPipeline")) {
                currentPipeline.clear();
            }
        }
    }
    
    /**
     * 渲染预设管理器
     */
    private void renderPresetManager() {
        UITheme.label("预设管理器:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 预设选择
        if (ImGui.beginCombo("选择预设##presetSelect", selectedPresetId.isEmpty() ? "选择预设..." : selectedPresetId)) {
            for (String presetId : availablePresets) {
                boolean isSelected = presetId.equals(selectedPresetId);
                if (ImGui.selectable(presetId, isSelected)) {
                    selectedPresetId = presetId;
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        
        // 预设操作按钮
        if (ImGui.button("加载预设##loadPreset") && !selectedPresetId.isEmpty()) {
            loadPreset();
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("保存为预设##savePreset")) {
            saveCurrentAsPreset();
        }
    }
    
    /**
     * 渲染自定义过滤器
     */
    private void renderCustomFilters() {
        UITheme.label("自定义过滤器:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 自定义过滤器名称
        ImGui.inputText("过滤器名称##customName", customFilterName);
        
        // 自定义过滤器条件
        ImGui.inputTextMultiline("过滤器条件##customCondition", customFilterCondition, 200, 100);
        
        // 创建自定义过滤器按钮
        if (ImGui.button("创建自定义过滤器##createCustom")) {
            createCustomFilter();
        }
        
        // 高级选项
        if (ImGui.checkbox("显示高级选项##showAdvanced", showAdvancedOptions)) {
            showAdvancedOptions = !showAdvancedOptions;
        }
        
        if (showAdvancedOptions) {
            renderAdvancedOptions();
        }
    }
    
    /**
     * 渲染高级选项
     */
    private void renderAdvancedOptions() {
        ImGui.indent();
        
        // 高度范围
        UITheme.label("高度范围:", UITheme.DarkTheme.TEXT);
        int[] heightRange = {minHeight, maxHeight};
        if (ImGui.sliderInt2("高度##heightRange", heightRange, 0, 256)) {
            minHeight = heightRange[0];
            maxHeight = heightRange[1];
        }
        
        // 距离过滤器
        UITheme.label("距离过滤器:", UITheme.DarkTheme.TEXT);
        float[] distanceArray = {(float) maxDistance};
        if (ImGui.sliderFloat("最大距离##maxDistance", distanceArray, 1.0f, 50.0f, "%.1f")) {
            maxDistance = distanceArray[0];
        }
        
        // 硬度范围
        UITheme.label("硬度范围:", UITheme.DarkTheme.TEXT);
        float[] hardnessRange = {minHardness, maxHardness};
        if (ImGui.sliderFloat2("硬度##hardnessRange", hardnessRange, 0.0f, 10.0f, "%.1f")) {
            minHardness = hardnessRange[0];
            maxHardness = hardnessRange[1];
        }
        
        ImGui.unindent();
    }
    
    /**
     * 渲染过滤器测试
     */
    private void renderFilterTest() {
        UITheme.label("过滤器测试:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.button("测试当前过滤器##testFilters")) {
            testCurrentFilters();
        }
        
        if (testResultCount > 0) {
            UITheme.label(String.format("测试结果: %d 个方块匹配", testResultCount), UITheme.DarkTheme.TEXT_SELECTED);
        }
    }
    
    /**
     * 渲染操作按钮
     */
    private void renderActionButtons() {
        UITheme.label("操作:", UITheme.DarkTheme.TEXT_SELECTED);
        
        if (ImGui.button("应用过滤器##applyFilters")) {
            applyCurrentFilters();
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("重置过滤器##resetFilters")) {
            resetFilters();
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("导出过滤器##exportFilters")) {
            exportFilters();
        }
    }
    
    /**
     * 添加选中的过滤器
     */
    private void addSelectedFilter() {
        try {
            SelectionFilter filter = SelectionFilterSystem.getFilter(selectedFilterId);
            if (filter != null) {
                currentPipeline.add(filter);
                LOGGER.info("[SelectionFilterPanel] 添加过滤器: {}", selectedFilterId);
            } else {
                LOGGER.warn("[SelectionFilterPanel] 未找到过滤器: {}", selectedFilterId);
            }
        } catch (Exception e) {
            LOGGER.error("[SelectionFilterPanel] 添加过滤器失败", e);
        }
    }
    
    /**
     * 加载预设
     */
    private void loadPreset() {
        try {
            SelectionPipeline preset = SelectionFilterSystem.getPreset(selectedPresetId);
            if (preset != null) {
                currentPipeline = new SelectionPipeline();
                currentPipeline.getFilters().addAll(preset.getFilters());
                LOGGER.info("[SelectionFilterPanel] 加载预设: {}", selectedPresetId);
            } else {
                LOGGER.warn("[SelectionFilterPanel] 未找到预设: {}", selectedPresetId);
            }
        } catch (Exception e) {
            LOGGER.error("[SelectionFilterPanel] 加载预设失败", e);
        }
    }
    
    /**
     * 保存当前过滤器为预设
     */
    private void saveCurrentAsPreset() {
        try {
            String presetName = "自定义预设_" + System.currentTimeMillis();
            SelectionFilterSystem.savePreset(presetName, currentPipeline);
            updateAvailablePresets();
            LOGGER.info("[SelectionFilterPanel] 保存预设: {}", presetName);
        } catch (Exception e) {
            LOGGER.error("[SelectionFilterPanel] 保存预设失败", e);
        }
    }
    
    /**
     * 创建自定义过滤器
     */
    private void createCustomFilter() {
        try {
            String name = customFilterName.get();
            String condition = customFilterCondition.get();
            
            if (!name.isEmpty() && !condition.isEmpty()) {
                // 这里应该实现自定义过滤器的创建逻辑
                LOGGER.info("[SelectionFilterPanel] 创建自定义过滤器: {} - {}", name, condition);
                
                // 清空输入
                customFilterName.set("");
                customFilterCondition.set("");
            }
        } catch (Exception e) {
            LOGGER.error("[SelectionFilterPanel] 创建自定义过滤器失败", e);
        }
    }
    
    
    /**
     * 测试当前过滤器
     */
    private void testCurrentFilters() {
        try {
            // 这里应该实现实际的过滤器测试逻辑
            testResultCount = (int) (Math.random() * 100); // 模拟测试结果
            LOGGER.info("[SelectionFilterPanel] 测试过滤器，结果: {}", testResultCount);
        } catch (Exception e) {
            LOGGER.error("[SelectionFilterPanel] 测试过滤器失败", e);
        }
    }
    
    /**
     * 应用当前过滤器
     */
    private void applyCurrentFilters() {
        try {
            // 这里应该实现实际的过滤器应用逻辑
            LOGGER.info("[SelectionFilterPanel] 应用过滤器");
        } catch (Exception e) {
            LOGGER.error("[SelectionFilterPanel] 应用过滤器失败", e);
        }
    }
    
    /**
     * 重置过滤器
     */
    private void resetFilters() {
        currentPipeline.clear();
        selectedFilterId = "";
        selectedPresetId = "";
        customFilterName.set("");
        customFilterCondition.set("");
        testResultCount = 0;
        LOGGER.info("[SelectionFilterPanel] 重置过滤器");
    }
    
    /**
     * 导出过滤器
     */
    private void exportFilters() {
        try {
            // 这里应该实现过滤器导出逻辑
            LOGGER.info("[SelectionFilterPanel] 导出过滤器");
        } catch (Exception e) {
            LOGGER.error("[SelectionFilterPanel] 导出过滤器失败", e);
        }
    }
    
    /**
     * 获取过滤器描述
     */
    private String getFilterDescription(String filterId) {
        // 这里应该返回过滤器的描述信息
        return "过滤器描述: " + filterId;
    }
    
    /**
     * 更新可用过滤器列表
     */
    private void updateAvailableFilters() {
        availableFilters.clear();
        availableFilters.add("nonAir");
        availableFilters.add("solid");
        availableFilters.add("transparent");
        availableFilters.add("buildingMaterials");
        availableFilters.add("naturalBlocks");
    }
    
    /**
     * 更新可用预设列表
     */
    private void updateAvailablePresets() {
        availablePresets.clear();
        availablePresets.add("buildingMaterials");
        availablePresets.add("naturalBlocks");
        availablePresets.add("transparentBlocks");
    }
    
    /**
     * 获取当前过滤器管道
     */
    public SelectionPipeline getCurrentPipeline() {
        return currentPipeline;
    }
    
    /**
     * 设置当前过滤器管道
     */
    public void setCurrentPipeline(SelectionPipeline pipeline) {
        this.currentPipeline = pipeline != null ? pipeline : new SelectionPipeline();
    }
}
