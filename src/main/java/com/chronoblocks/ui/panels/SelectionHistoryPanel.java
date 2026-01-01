package com.chronoblocks.ui.panels;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.history.SelectionHistoryManager;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTableBgTarget;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * SelectionHistoryPanel v2.0
 * <p>
 * 模块化架构 + 增强的时间线视图 + 完整的状态应用 + 精美的交互
 * ------------------------------------------------
 * 特性：
 * - 模块化Section架构：Toolbar/Timeline/Table/Settings组件化
 * - 时间线可视化：节点式历史视图，带连线、呼吸动画和水平滚动
 * - 操作类型颜色：枚举化颜色映射系统
 * - 快捷键支持：Ctrl+Z / Ctrl+Y
 * - 表格视图：虚拟化渲染，支持点击跳转和高亮
 * - 完整的 applyState：健壮的状态应用逻辑
 * - 管理器集成：使用 SelectionHistoryManager
 * - 设置联动：分组管理，统一配置
 * <p>
 * 作者：ChronoBlocks Team
 * 版本：2.0
 */
public class SelectionHistoryPanel extends AbstractPanel {
    
    public static final String PANEL_NAME = "SelectionHistory";
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionHistoryPanel.class);
    
    // ========== 依赖 ==========
    private final SelectionHistoryManager historyManager;
    private final SelectionManager selectionManager;
    
    // ========== UI组件 ==========
    private final ToolbarSection toolbar;
    private final TimelineSection timeline;
    private final TableSection table;
    private final SettingsSection settings;
    
    // ========== 时间格式化 ==========
    private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss");
    
    // ========== 构造函数 ==========
    public SelectionHistoryPanel() {
        this.historyManager = SelectionHistoryManager.getInstance();
        this.selectionManager = SelectionManager.get();
        
        // 初始化UI组件
        this.toolbar = new ToolbarSection();
        this.timeline = new TimelineSection();
        this.table = new TableSection();
        this.settings = new SettingsSection();
        
        LOGGER.info("[SelectionHistoryPanel] 初始化选择历史面板 v2.0");
    }
    
    // ========== 面板接口 ==========
    @Override
    public String getPanelName() { return PANEL_NAME; }
    
    @Override
    public String getWindowName() { return Icons.UNDO + " 选择历史###SelectionHistoryPanel"; }
    
    @Override
    public int getPriority() { return 40; }
    
    @Override
    protected int getWindowFlags() { return 0; }
    
    @Override
    protected void doRender() {
        UITheme.title(Icons.UNDO + " 选择历史");
        ImGui.spacing();
        
        // 快捷键处理
        handleKeyboardShortcuts();
        
        if (ImGui.collapsingHeader(Icons.TARGET + " 工具栏", ImGuiTreeNodeFlags.DefaultOpen))
            toolbar.render();
        
        if (ImGui.collapsingHeader(Icons.LINK + " 时间线视图", ImGuiTreeNodeFlags.DefaultOpen))
            timeline.render();
        
        if (ImGui.collapsingHeader(Icons.LAYERS + " 历史列表", ImGuiTreeNodeFlags.DefaultOpen))
            table.render();
        
        if (ImGui.collapsingHeader(Icons.SETTINGS + " 设置"))
            settings.render();
    }
    
    // ========== 快捷键处理 ==========
    private void handleKeyboardShortcuts() {
        boolean ctrl = ImGui.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
        
        if (ctrl && ImGui.isKeyPressed(GLFW.GLFW_KEY_Z)) {
            toolbar.performUndo();
        }
        
        if (ctrl && ImGui.isKeyPressed(GLFW.GLFW_KEY_Y)) {
            toolbar.performRedo();
        }
    }
    
    // ========== UI Section: 工具栏 ==========
    private class ToolbarSection {
        void render() {
            ImGui.indent();
            
            boolean canUndo = historyManager.canUndo();
            boolean canRedo = historyManager.canRedo();
            
            if (!canUndo) ImGui.beginDisabled();
            if (ImGui.button(Icons.UNDO + " 撤销")) performUndo();
            if (!canUndo) ImGui.endDisabled();
            
            ImGui.sameLine();
            if (!canRedo) ImGui.beginDisabled();
            if (ImGui.button(Icons.REDO + " 重做")) performRedo();
            if (!canRedo) ImGui.endDisabled();
            
            ImGui.sameLine();
            if (ImGui.button("跳转到开始")) {
                if (historyManager.getHistorySize() > 0) navigateToHistory(0);
            }
            
            ImGui.sameLine();
            if (ImGui.button("跳转到结束")) {
                int size = historyManager.getHistorySize();
                if (size > 0) navigateToHistory(size - 1);
            }
            
            ImGui.sameLine();
            if (ImGui.button("清空历史")) {
                historyManager.clearHistory();
                LOGGER.info("[SelectionHistoryPanel] 清空历史");
            }
            
            ImGui.unindent();
        }
        
        void performUndo() {
            if (!historyManager.canUndo()) {
                LOGGER.warn("[SelectionHistoryPanel] 无法撤销操作");
                return;
            }
            SelectionState state = historyManager.undo();
            if (state != null) applyState(state);
        }
        
        void performRedo() {
            if (!historyManager.canRedo()) {
                LOGGER.warn("[SelectionHistoryPanel] 无法重做操作");
                return;
            }
            SelectionState state = historyManager.redo();
            if (state != null) applyState(state);
        }
        
        void navigateToHistory(int index) {
            index = Math.max(0, Math.min(index, historyManager.getHistorySize() - 1));
            SelectionState state = historyManager.jumpToHistory(index);
            if (state != null) applyState(state);
        }
    }
    
    // ========== UI Section: 时间线视图 ==========
    private class TimelineSection {
        private static final float NODE_STEP_MIN = 35f;
        private static final float NODE_RADIUS_MIN = 4f;
        private static final float NODE_RADIUS_CURRENT = 6f;
        
        void render() {
            ImGui.indent();
            
            List<SelectionHistoryManager.SelectionHistoryInfo> entries = historyManager.getHistoryInfo();
            if (entries.isEmpty()) {
                UITheme.label("无历史记录", UITheme.DarkTheme.TEXT_DISABLED);
                ImGui.unindent();
                return;
            }
            
            // 创建时间线容器，支持水平滚动
            if (ImGui.beginChild("timeline", 0, 80, true, ImGuiWindowFlags.HorizontalScrollbar)) {
                var drawList = ImGui.getWindowDrawList();
                float startX = ImGui.getCursorScreenPosX() + 20;
                float y = ImGui.getCursorScreenPosY() + 40;
                
                // 动态计算步长
                float availWidth = ImGui.getContentRegionAvailX() - 40;
                float step = Math.max(NODE_STEP_MIN, availWidth / Math.max(1, entries.size() - 1));
                
                // 先绘制所有连线
                for (int i = 1; i < entries.size(); i++) {
                    float cx = startX + i * step;
                    float prevCx = startX + (i - 1) * step;
                    drawList.addLine(prevCx, y, cx, y, 0xFF606060, 1.5f);
                }
                
                // 再绘制所有节点
                for (int i = 0; i < entries.size(); i++) {
                    var info = entries.get(i);
                    boolean isCurrent = info.isCurrent();
                    
                    int color = OperationType.getColor(info.getOperation());
                    float cx = startX + i * step;
                    
                    // 呼吸效果：当前节点动态半径（仅在设置启用时）
                    float baseRadius = isCurrent ? NODE_RADIUS_CURRENT : NODE_RADIUS_MIN;
                    float radius = baseRadius;
                    if (isCurrent && settings.isBreathingAnimationEnabled()) {
                        float breathingFactor = (float) (Math.sin(ImGui.getTime() * 5.0) * 0.5 + 0.5);
                        radius = baseRadius + breathingFactor * 1.5f;
                    }
                    
                    drawList.addCircleFilled(cx, y, radius, color);
                    
                    if (isCurrent && settings.isBreathingAnimationEnabled()) {
                        float breathingAlpha = (float) (Math.sin(ImGui.getTime() * 5.0) * 0.3 + 0.7);
                        int outlineColor = adjustColorAlpha(breathingAlpha);
                        drawList.addCircle(cx, y, radius + 2, outlineColor, 0, 2f);
                    } else if (isCurrent) {
                        drawList.addCircle(cx, y, radius + 2, 0xFFFFFFFF, 0, 2f);
                    }
                    
                    ImGui.setCursorScreenPos(cx - 8, y - 8);
                    ImGui.invisibleButton("node_" + i, 16, 16);
                    
                    if (ImGui.isItemHovered()) {
                        ImGui.beginTooltip();
                        UITheme.label("操作: " + info.getOperation(), UITheme.DarkTheme.TEXT_SELECTED);
                        UITheme.label("工具: " + info.getToolId(), UITheme.DarkTheme.TEXT_DISABLED);
                        UITheme.label("时间: " + formatTime(info.getTimestamp()), UITheme.DarkTheme.TEXT_DISABLED);
                        ImGui.endTooltip();
                    }
                    
                    if (ImGui.isItemClicked()) {
                        toolbar.navigateToHistory(i);
                    }
                }
            }
            ImGui.endChild();
            
            ImGui.unindent();
        }
    }
    
    // ========== UI Section: 表格列表 ==========
    private class TableSection {
        void render() {
            ImGui.indent();
            
            List<SelectionHistoryManager.SelectionHistoryInfo> entries = historyManager.getHistoryInfo();
            if (entries.isEmpty()) {
                UITheme.label("无历史记录", UITheme.DarkTheme.TEXT_DISABLED);
                ImGui.unindent();
                return;
            }
            
            if (ImGui.beginTable("historyTable", 3, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable)) {
                // 修复：指定宽度时必须使用 WidthFixed 标志
                ImGui.tableSetupColumn("#", ImGuiTableColumnFlags.WidthFixed, 40);
                ImGui.tableSetupColumn("描述");
                ImGui.tableSetupColumn("时间", ImGuiTableColumnFlags.WidthFixed, 150);
                ImGui.tableHeadersRow();
                
                for (SelectionHistoryManager.SelectionHistoryInfo info : entries) {
                    ImGui.tableNextRow();
                    
                    if (info.isCurrent()) {
                        ImGui.tableSetBgColor(ImGuiTableBgTarget.RowBg0, ImGui.getColorU32(0.25f, 0.35f, 0.55f, 0.65f));
                    }
                    
                    ImGui.tableSetColumnIndex(0);
                    String indexText = info.isCurrent() ? "▶ " + (info.getIndex() + 1) : "  " + (info.getIndex() + 1);
                    UITheme.label(indexText, info.isCurrent() ? UITheme.DarkTheme.TEXT_SELECTED : UITheme.DarkTheme.TEXT);
                    
                    ImGui.tableSetColumnIndex(1);
                    String descText = Icons.CLOCK + " " + info.getOperation();
                    UITheme.label(descText, UITheme.DarkTheme.TEXT);
                    
                    ImGui.tableSetColumnIndex(2);
                    if (settings.isTimestampsEnabled()) {
                        UITheme.label(formatTime(info.getTimestamp()), UITheme.DarkTheme.TEXT_DISABLED);
                    }
                    
                    if (ImGui.isItemHovered() && ImGui.isMouseClicked(0)) {
                        toolbar.navigateToHistory(info.getIndex());
                    }
                }
                
                ImGui.endTable();
            }
            
            ImGui.unindent();
        }
    }
    
    // ========== UI Section: 设置 ==========
    private class SettingsSection {
        private int maxHistorySize = 50;
        private boolean autoSaveHistory = true;
        private boolean showTimestamps = true;
        private boolean breathingAnimation = true;
        
        void render() {
            ImGui.indent();
            
            if (ImGui.checkbox("自动保存历史", autoSaveHistory)) {
                autoSaveHistory = !autoSaveHistory;
                historyManager.setEnabled(autoSaveHistory);
                LOGGER.debug("[SelectionHistoryPanel] 自动保存历史: {}", autoSaveHistory);
            }
            
            int[] maxSizeArray = {maxHistorySize};
            if (ImGui.sliderInt("最大历史记录数", maxSizeArray, 10, 500)) {
                maxHistorySize = maxSizeArray[0];
                historyManager.setMaxHistorySize(maxHistorySize);
                LOGGER.debug("[SelectionHistoryPanel] 最大历史记录数: {}", maxHistorySize);
            }
            
            if (ImGui.checkbox("显示时间戳", showTimestamps)) {
                showTimestamps = !showTimestamps;
                LOGGER.debug("[SelectionHistoryPanel] 显示时间戳: {}", showTimestamps);
            }
            
            if (ImGui.checkbox("呼吸动画效果", breathingAnimation)) {
                breathingAnimation = !breathingAnimation;
                LOGGER.debug("[SelectionHistoryPanel] 呼吸动画效果: {}", breathingAnimation);
            }
            
            ImGui.unindent();
        }
        
        boolean isTimestampsEnabled() { return showTimestamps; }
        boolean isBreathingAnimationEnabled() { return breathingAnimation; }
    }
    
    // ========== 操作类型枚举 ==========
    private enum OperationType {
        ADD("add", 0xFF78DC78),
        REMOVE("remove|subtract", 0xFFDC7878),
        FILTER("filter", 0xFFDCC878),
        MODIFY("modify", 0xFF788CDC),
        OTHER("", 0xFF969696);
        
        private final String pattern;
        private final int color;
        
        OperationType(String pattern, int color) {
            this.pattern = pattern;
            this.color = color;
        }
        
        static int getColor(String operation) {
            if (operation == null) return OTHER.color;
            String lower = operation.toLowerCase();
            for (OperationType type : values()) {
                if (type == OTHER) continue;
                String[] patterns = type.pattern.split("\\|");
                for (String p : patterns) {
                    if (lower.contains(p)) return type.color;
                }
            }
            return OTHER.color;
        }
        
    }
    
    // ========== 核心方法 ==========
    /**
     * 应用选择状态到当前管理器
     * 完整实现：将历史状态中的方块应用到当前选择集
     * 
     * @param state 要应用的历史状态
     */
    private void applyState(SelectionState state) {
        if (state == null) {
            LOGGER.warn("[SelectionHistoryPanel] 尝试应用一个空的历史状态");
            return;
        }
        
        if (selectionManager == null) {
            LOGGER.error("[SelectionHistoryPanel] SelectionManager 未初始化，无法应用状态");
            return;
        }
        
        try {
            // 防御性检查：确保状态包含有效的方块数据
            Set<BlockPos> blocks = state.getSelectedBlocks();
            if (blocks == null) {
                LOGGER.warn("[SelectionHistoryPanel] 历史状态缺少方块数据");
                return;
            }
            
            // 核心逻辑：用历史状态替换当前的选择状态
            selectionManager.clearSelection();
            selectionManager.addAllToSelection(blocks);
            
            LOGGER.info("[SelectionHistoryPanel] 成功应用历史状态，包含 {} 个方块", state.size());
            
        } catch (Exception e) {
            LOGGER.error("[SelectionHistoryPanel] 应用选择状态时发生严重错误", e);
        }
    }
    
    private String formatTime(long timestamp) {
        return TIME_FORMATTER.format(new Date(timestamp));
    }
    
    private int adjustColorAlpha(float alpha) {
        int r = (-1 >> 16) & 0xFF;
        int g = (-1 >> 8) & 0xFF;
        int b = 0xFF;
        int a = (int) (alpha * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}