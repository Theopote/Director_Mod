package com.chronoblocks.ui.panels;

import com.chronoblocks.client.settings.RenderSettings;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RenderSettingsPanel（全局渲染设置面板）
 * <p>
 * 统一管理与渲染相关的可视化设置，包括：
 * - 选区渲染（Selection）
 * - 工具预览 / Gizmo（Tool Preview）
 * - 容器可视化（Container Highlight）
 * - 性能与贪婪合并（Mesh、距离、剔除）
 * <p>
 * 注意：本面板设置实时生效；交互元素由 SelectionOverlayRenderer 独立负责。
 */
public class RenderSettingsPanel extends AbstractPanel {
    
    // ==================== 样式数据结构 ====================
    
    /**
     * 边框样式预设
     */
    public record BorderStyle(String name, float[] color, float width) {}
    
    /**
     * 填充样式预设
     */
    public record FillStyle(String name, float[] color, float intensity) {}
    
    /** 面板名称常量（对外展示为渲染设置） */
    public static final String PANEL_NAME = "RenderSettings";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderSettingsPanel.class);
    
    // ==================== 默认值常量 ====================
    
    // 默认常量已移除，直接读写 RenderSettings
    
    // ==================== 成员变量 ====================
    
    /**
     * 构造函数 - 默认不可见
     */
    public RenderSettingsPanel() {
        super();
        setVisible(false);
    }
    
    // 不再维护本地渲染模式
    
    // ==================== 模块化预设系统 ====================
    
    /** 边框样式预设列表 */
    private final BorderStyle[] borderPresets = {
        new BorderStyle("蓝色辉光", new float[]{0.1f, 0.7f, 1.0f, 1.0f}, 2.5f),
        new BorderStyle("危险红", new float[]{1.0f, 0.2f, 0.2f, 1.0f}, 2.0f),
        new BorderStyle("科技绿", new float[]{0.1f, 1.0f, 0.3f, 1.0f}, 1.5f),
        new BorderStyle("默认", new float[]{0.1f, 0.8f, 1.0f, 0.95f}, 2.0f)
    };
    
    /** 填充样式预设列表 */
    private final FillStyle[] fillPresets = {
        new FillStyle("柔光蓝", new float[]{0.3f, 0.7f, 1.0f, 0.25f}, 1.0f),
        new FillStyle("警告黄", new float[]{1.0f, 0.8f, 0.0f, 0.30f}, 1.2f),
        new FillStyle("幽灵绿", new float[]{0.1f, 1.0f, 0.3f, 0.2f}, 0.8f),
        new FillStyle("默认", new float[]{1.0f, 1.0f, 1.0f, 0.35f}, 1.0f)
    };
    
    /** 当前选择的边框预设索引 */
    private int selectedBorderPreset = 3; // 默认选择"默认"预设
    
    /** 当前选择的填充预设索引 */
    private int selectedFillPreset = 3; // 默认选择"默认"预设
    
    // 直接操作 RenderSettings，不再维护本地副本
    private final RenderSettings rs = RenderSettings.get();
    
    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }
    
    @Override
    public String getWindowName() {
        return Icons.COLOR + " 渲染预览设置###RenderSettingsPanel";
    }
    
    @Override
    public int getPriority() {
        return 30;
    }
    
    @Override
    protected int getWindowFlags() {
        return super.getWindowFlags();
    }
    
    @Override
    protected void doRender() {
        // ===== 1. 选区预览 =====
        if (ImGui.collapsingHeader(Icons.SELECT + " 选区预览（Selection）")) {
            renderSelectionToggles();
            ImGui.separator();
            renderSelectionStyles();
            ImGui.separator();
            renderSelectionPerformance();
            ImGui.separator();
        }

        // ===== 2. 工具预览 =====
        if (ImGui.collapsingHeader(Icons.TOOLS + " 工具预览（Tool Preview）")) {
            renderToolPreviewSettings();
            ImGui.separator();
        }

        // ===== 3. 容器预览 =====
        if (ImGui.collapsingHeader(Icons.BOX + " 容器预览（Container Highlight）")) {
            renderContainerSettings();
            ImGui.separator();
        }

        // ===== 4. 全局性能 / Mesh 设置 =====
        if (ImGui.collapsingHeader(Icons.SETTINGS + " 全局性能 / Mesh 设置")) {
            renderGlobalMeshSettings();
            ImGui.separator();
        }

        renderActionButtons();
    }

    // ============================================================
    // 全局 Mesh / 性能设置（可选）
    // ============================================================

    private void renderGlobalMeshSettings() {
        UITheme.label("Mesh / 缓存 / 降级：", UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.indent();

        {
            boolean cur = rs.useSharedMeshCache;
            if (ImGui.checkbox("启用共享 BlockOutlineMeshCache##globalMeshCache", cur)) {
                rs.useSharedMeshCache = !cur;
            }
        }

        {
            boolean cur = rs.fallbackHugeArea;
            if (ImGui.checkbox("对超大区域使用降级渲染##globalFallbackHuge", cur)) {
                rs.fallbackHugeArea = !cur;
            }
        }

        int[] huge = {rs.hugeAreaThreshold};
        if (ImGui.sliderInt("巨大区域阈值（方块数）##globalHugeThreshold", huge, 1000, 100000)) {
            rs.hugeAreaThreshold = Math.max(1000, huge[0]);
        }

        ImGui.unindent();
    }

    // 预设应用
    private void applyBorderPreset(BorderStyle style) {
        System.arraycopy(style.color(), 0, rs.selection_outlineColor, 0, 4);
        rs.selection_outlineWidth = style.width();
        LOGGER.info("[RenderSettingsPanel] 应用选区边框预设: {}", style.name());
    }

    private void applyFillPreset(FillStyle style) {
        System.arraycopy(style.color(), 0, rs.selection_fillColor, 0, 4);
        rs.selection_highlightIntensity = style.intensity();
        LOGGER.info("[RenderSettingsPanel] 应用选区填充预设: {}", style.name());
    }
    
    // ============================================================
    // 选区预览：开关 / 样式 / 性能
    // ============================================================

    private void renderSelectionToggles() {
        UITheme.label("显示控制：", UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.indent();

        // 显示填充
        {
            boolean cur = rs.selection_showFill;
            if (ImGui.checkbox("显示填充##selectionFill", cur)) {
                rs.selection_showFill = !cur;
            }
        }

        // 显示轮廓线
        {
            boolean cur = rs.selection_showOutline;
            if (ImGui.checkbox("显示线框##selectionOutline", cur)) {
                rs.selection_showOutline = !cur;
            }
        }

        // 显示包围盒（只对选区/容器开放）
        {
            boolean cur = rs.selection_showBoundingBox;
            if (ImGui.checkbox("显示包围盒##selectionBBox", cur)) {
                rs.selection_showBoundingBox = !cur;
            }
        }

        // 贪婪合并（面+线一起控制）
        {
            boolean cur = rs.selection_enableGreedy;
            if (ImGui.checkbox("启用贪婪合并（面+线）##selectionGreedy", cur)) {
                rs.selection_enableGreedy = !cur;
            }
        }

        ImGui.unindent();
    }

    private void renderSelectionStyles() {
        UITheme.label("选区样式：", UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.indent();

        // 颜色 / 线宽基础控制
        if (ImGui.colorEdit4("填充颜色##selectionFillColor", rs.selection_fillColor)) {
            LOGGER.debug("[RenderSettingsPanel] 选区填充颜色更新");
        }

        if (ImGui.colorEdit4("线框颜色##selectionOutlineColor", rs.selection_outlineColor)) {
            LOGGER.debug("[RenderSettingsPanel] 选区线框颜色更新");
        }

        float[] width = {rs.selection_outlineWidth};
        if (ImGui.sliderFloat("线宽##selectionOutlineWidth", width, 0.5f, 5.0f, "%.1f")) {
            rs.selection_outlineWidth = width[0];
        }

        float[] intensity = {rs.selection_highlightIntensity};
        if (ImGui.sliderFloat("高亮强度##selectionIntensity", intensity, 0.1f, 2.0f, "%.1f")) {
            rs.selection_highlightIntensity = intensity[0];
        }

        ImGui.separator();

        // 预设（可选）
        UITheme.label("预设：", UITheme.DarkTheme.TEXT_SELECTED);

        // 边框预设
        String[] borderPresetNames = java.util.Arrays.stream(borderPresets)
                .map(BorderStyle::name)
                .toArray(String[]::new);
        ImInt borderIdx = new ImInt(selectedBorderPreset);
        if (ImGui.combo("边框预设##selectionBorderPreset", borderIdx, borderPresetNames)) {
            selectedBorderPreset = borderIdx.get();
            applyBorderPreset(borderPresets[selectedBorderPreset]);
        }

        // 填充预设
        String[] fillPresetNames = java.util.Arrays.stream(fillPresets)
                .map(FillStyle::name)
                .toArray(String[]::new);
        ImInt fillIdx = new ImInt(selectedFillPreset);
        if (ImGui.combo("填充预设##selectionFillPreset", fillIdx, fillPresetNames)) {
            selectedFillPreset = fillIdx.get();
            applyFillPreset(fillPresets[selectedFillPreset]);
        }

        ImGui.unindent();
    }

    private void renderSelectionPerformance() {
        UITheme.label("选区性能 / 剔除：", UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.indent();

        int[] dist = {rs.selection_maxRenderDistance};
        if (ImGui.sliderInt("最大渲染距离##selectionMaxDist", dist, 8, 256)) {
            rs.selection_maxRenderDistance = dist[0];
        }

        {
            boolean cur = rs.selection_enableFrustumCull;
            if (ImGui.checkbox("视锥剔除##selectionFrustum", cur)) {
                rs.selection_enableFrustumCull = !cur;
            }
        }

        {
            boolean cur = rs.selection_enableOcclusionCull;
            if (ImGui.checkbox("遮挡剔除##selectionOcclusion", cur)) {
                rs.selection_enableOcclusionCull = !cur;
            }
        }

        // 贪婪阈值
        int[] th = {rs.selection_greedyThreshold};
        if (ImGui.sliderInt("贪婪合并阈值（方块数）##selectionGreedyThreshold", th, 1, 5000)) {
            rs.selection_greedyThreshold = Math.max(1, th[0]);
        }

        ImGui.unindent();
    }

    // ============================================================
    // 工具预览设置
    // ============================================================

    private void renderToolPreviewSettings() {
        UITheme.label("工具预览样式：", UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.indent();

        {
            boolean cur = rs.tool_showFill;
            if (ImGui.checkbox("显示填充##toolFill", cur)) {
                rs.tool_showFill = !cur;
            }
        }
        {
            boolean cur = rs.tool_showOutline;
            if (ImGui.checkbox("显示线框##toolOutline", cur)) {
                rs.tool_showOutline = !cur;
            }
        }
        // 工具不需要 BoundingBox（按设计）

        {
            boolean cur = rs.tool_enableGreedy;
            if (ImGui.checkbox("启用贪婪合并（面+线）##toolGreedy", cur)) {
                rs.tool_enableGreedy = !cur;
            }
        }

        ImGui.separator();

        ImGui.colorEdit4("轮廓颜色##toolOutlineColor", rs.tool_outlineColor);
        ImGui.colorEdit4("填充颜色##toolFillColor", rs.tool_fillColor);

        float[] width = {rs.tool_outlineWidth};
        if (ImGui.sliderFloat("线宽##toolOutlineWidth", width, 0.5f, 5.0f, "%.1f")) {
            rs.tool_outlineWidth = width[0];
        }

        ImGui.unindent();
    }

    // ============================================================
    // 容器预览设置
    // ============================================================

    private void renderContainerSettings() {
        UITheme.label("容器预览样式：", UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.indent();

        {
            boolean cur = rs.container_showFill;
            if (ImGui.checkbox("显示填充##containerFill", cur)) {
                rs.container_showFill = !cur;
            }
        }
        {
            boolean cur = rs.container_showOutline;
            if (ImGui.checkbox("显示线框##containerOutline", cur)) {
                rs.container_showOutline = !cur;
            }
        }
        {
            boolean cur = rs.container_showBoundingBox;
            if (ImGui.checkbox("显示包围盒##containerBBox", cur)) {
                rs.container_showBoundingBox = !cur;
            }
        }
        {
            boolean cur = rs.container_enableGreedy;
            if (ImGui.checkbox("启用贪婪合并（面+线）##containerGreedy", cur)) {
                rs.container_enableGreedy = !cur;
            }
        }

        ImGui.separator();

        ImGui.colorEdit4("轮廓颜色##containerOutlineColor", rs.container_outlineColor);
        ImGui.colorEdit4("填充颜色##containerFillColor", rs.container_fillColor);

        float[] width = {rs.container_outlineWidth};
        if (ImGui.sliderFloat("线宽##containerOutlineWidth", width, 0.5f, 5.0f, "%.1f")) {
            rs.container_outlineWidth = width[0];
        }

        ImGui.unindent();
    }
    
    // ============================================================
    // 操作按钮 & 重置
    // ============================================================

    private void renderActionButtons() {
        UITheme.label("操作：", UITheme.DarkTheme.TEXT_SELECTED);
        if (ImGui.button("重置为默认值##resetRenderSettings")) {
            resetToDefaults();
        }
    }

    private void resetToDefaults() {
        // === 选区 ===
        rs.selection_showFill         = true;
        rs.selection_showOutline      = true;
        rs.selection_showBoundingBox  = false;
        rs.selection_enableGreedy     = true;
        rs.selection_greedyThreshold  = 1;
        rs.selection_maxRenderDistance = 64;
        rs.selection_enableFrustumCull = true;
        rs.selection_enableOcclusionCull = false;
        rs.selection_outlineColor     = new float[]{0.1f, 0.8f, 1.0f, 0.95f};
        rs.selection_fillColor        = new float[]{1.0f, 1.0f, 1.0f, 0.35f};
        rs.selection_outlineWidth     = 2.0f;
        rs.selection_highlightIntensity = 1.0f;

        // === 工具预览 ===
        rs.tool_showFill              = true;
        rs.tool_showOutline           = true;
        rs.tool_enableGreedy          = true;
        rs.tool_outlineColor          = new float[]{0.2f, 0.6f, 1.0f, 0.7f};
        rs.tool_fillColor             = new float[]{1.0f, 0.8f, 0.0f, 0.30f};
        rs.tool_outlineWidth          = 1.2f;

        // === 容器 ===
        rs.container_showFill         = false;
        rs.container_showOutline      = true;
        rs.container_showBoundingBox  = false;
        rs.container_enableGreedy     = true;
        rs.container_outlineColor     = new float[]{0.4f, 1.0f, 0.5f, 0.9f};
        rs.container_fillColor        = new float[]{0.4f, 1.0f, 0.5f, 0.2f};
        rs.container_outlineWidth     = 2.0f;

        // === 全局 Mesh / 性能 ===
        rs.useSharedMeshCache         = true;
        rs.fallbackHugeArea           = true;
        rs.hugeAreaThreshold          = 15000;

        LOGGER.info("[RenderSettingsPanel] 已重置渲染设置为默认值");
    }
}