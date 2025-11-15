package com.chronoblocks.ui.panels;

import com.chronoblocks.core.selection.SelectionOperation;
import com.chronoblocks.core.selection.SelectionOperationSupport;
import com.chronoblocks.core.selection.SelectionTool;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.tools.*;
import com.chronoblocks.core.selection.tools.boxselecttool.BoxSelectTool;
import com.chronoblocks.core.selection.tools.brushselecttool.BrushSelectTool;
import com.chronoblocks.core.selection.tools.faceselecttool.FaceSelectTool;
import com.chronoblocks.core.selection.tools.geometryselecttool.GeometrySelectTool;
import com.chronoblocks.core.selection.tools.geometryselecttool.GeometryConfig;
import com.chronoblocks.core.selection.tools.geometryselecttool.GeometryShape;
import com.chronoblocks.core.selection.tools.magicwandtool.MagicWandConfig;
import com.chronoblocks.core.selection.tools.magicwandtool.MagicWandTask;
import com.chronoblocks.core.selection.tools.magicwandtool.MagicWandTool;
import com.chronoblocks.core.selection.tools.lassoselecttool.LassoSelectTool;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 🛠️ 动态工具属性面板 v3.1
 * 
 * 根据当前激活的选择工具动态显示对应的属性配置。
 * 支持所有13种选择工具的自动适配和属性编辑。
 * 
 * 特性：
 * - ✅ 模块化渲染器架构（ToolPropertyRenderer）
 * - ✅ 折叠分组（CollapsingHeader）提升可读性
 * - ✅ 统一UITheme风格
 * - ✅ 性能优化（缓冲区复用）
 * - ✅ 细化错误处理
 * - ✅ 自动检测当前工具并显示对应属性
 * - ✅ 无需手动切换工具模式
 * - ✅ 支持工具特定属性的配置
 * - ✅ 可扩展架构支持未来新增工具
 * 
 * @author ChronoBlocks Team
 * @version 3.1
 */
public class ToolPropertiesPanel extends AbstractPanel {
    
    /** 面板名称常量 */
    public static final String PANEL_NAME = "ToolProperties";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolPropertiesPanel.class);
    
    /** 选择管理器 */
    private final com.chronoblocks.core.selection.SelectionManager selectionManager;
    
    /** 渲染器注册表 */
    private static final Map<Class<? extends SelectionTool>, ToolPropertyRenderer<?>> RENDERERS = new HashMap<>();
    
    /** 性能优化：类级复用缓冲区 */
    private final float[] floatBuffer = new float[1];
    private final int[] intBuffer1 = new int[1];
    private final int[] intBuffer2 = new int[1];
    
    /** 静态初始化：注册所有渲染器 */
    static {
        registerRenderer(new BrushToolRenderer());
        registerRenderer(new GeometryToolRenderer());
        registerRenderer(new BoxV3ToolRenderer());
        registerRenderer(new FloodFillToolRenderer());
        registerRenderer(new FaceSelectToolRenderer());
        registerRenderer(new LassoToolRenderer());
        LOGGER.info("[ToolPropertiesPanel] 已注册 {} 个工具属性渲染器", RENDERERS.size());
    }
    
    /** 构造函数 */
    public ToolPropertiesPanel() {
        this.selectionManager = com.chronoblocks.core.selection.SelectionManager.get();
        LOGGER.info("[ToolPropertiesPanel] 初始化动态工具属性面板 v3.1");
    }

    /**
     * 面选择工具属性渲染器
     */
    private static class FaceSelectToolRenderer implements ToolPropertyRenderer<FaceSelectTool> {
        @Override
        public void render(FaceSelectTool tool, ToolPropertiesPanel panel) {
            if (ImGui.collapsingHeader("传播设置", ImGuiTreeNodeFlags.DefaultOpen)) {
                panel.intBuffer1[0] = tool.getMaxSelectionSize();
                if (ImGui.sliderInt("最大方块数##faceMaxBlocks", panel.intBuffer1, 100, 500_000)) {
                    tool.setMaxSelectionSize(panel.intBuffer1[0]);
                }

                panel.intBuffer2[0] = tool.getStepSize();
                if (ImGui.sliderInt("每帧步数##faceStepSize", panel.intBuffer2, 128, 16_384)) {
                    tool.setStepSize(panel.intBuffer2[0]);
                }

                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f,
                    "步数越大扩散越快，但可能带来瞬时卡顿。");
            }

            if (ImGui.collapsingHeader("匹配规则")) {
                boolean ignoreTransparent = tool.isIgnoreTransparent();
                if (ImGui.checkbox("忽略透明方块##faceIgnoreTransparent", ignoreTransparent)) {
                    tool.setIgnoreTransparent(!ignoreTransparent);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("勾选后将跳过玻璃、空气等透明方块。");
                }

                boolean byMaterial = tool.isPropagateByMaterial();
                if (ImGui.checkbox("按材质传播##faceByMaterial", byMaterial)) {
                    tool.setPropagateByMaterial(!byMaterial);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("勾选后使用方块材质 (MapColor) 判断是否连通。");
                }

                boolean strictState = tool.isStrictBlockState();
                if (ImGui.checkbox("严格匹配方块状态##faceStrictState", strictState)) {
                    tool.setStrictBlockState(!strictState);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("勾选后仅传播到完全相同 BlockState 的方块。");
                }
            }

            ImGui.spacing();
            int selectedCount = SelectionManager.get().getSelectionSet().size();
            UITheme.label("当前选中: " + selectedCount + " 个方块", UITheme.DarkTheme.TEXT);
        }

        @Override
        public Class<FaceSelectTool> getToolClass() {
            return FaceSelectTool.class;
        }
    }

    /**
     * 套索工具属性渲染器
     */
    private static class LassoToolRenderer implements ToolPropertyRenderer<LassoSelectTool> {
        @Override
        public void render(LassoSelectTool tool, ToolPropertiesPanel panel) {
            ImGui.separator();
            ImGui.text(Icons.LASSO + " 套索工具设置");

            boolean includeNonSolid = tool.isIncludeNonSolid();
            if (ImGui.checkbox("包含非实体方块##lassoIncludeNonSolid", includeNonSolid)) {
                tool.setIncludeNonSolid(!includeNonSolid);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("勾选后，草、花等非实体方块也会被加入选区。");
            }

            int pathSize = tool.getPathSampleCount();
            ImGui.spacing();
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f,
                String.format("当前采样点: %d", pathSize));
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f,
                String.format("绘制状态: %s", tool.isDrawing() ? "进行中" : "已结束/空闲"));
        }

        @Override
        public Class<LassoSelectTool> getToolClass() {
            return LassoSelectTool.class;
        }
    }
    
    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }
    
    @Override
    public String getWindowName() {
        // 动态显示当前工具名称
        String toolName = getCurrentToolDisplayName();
        return Icons.TOOLS + " " + toolName + "###ToolPropertiesPanel";
    }
    
    @Override
    public int getPriority() {
        return 35;
    }
    
    @Override
    protected int getWindowFlags() {
        return super.getWindowFlags();
    }
    
    @Override
    protected void doRender() {
        SelectionTool currentTool = selectionManager.getCurrentTool();
        
        if (currentTool == null) {
            renderNoToolMessage();
            return;
        }
        
        // 显示工具基本信息
        renderToolInfo(currentTool);
        renderOperationControls(currentTool);
        ImGui.separator();
        
        // 根据工具类型渲染特定属性
        renderToolSpecificProperties(currentTool);
    }
    
    /**
     * 渲染无工具消息
     */
    private void renderNoToolMessage() {
        ImGui.textColored(1.0f, 1.0f, 0.0f, 1.0f, Icons.WARNING + " 未选择工具");
        UITheme.label("请在工具栏中选择一个工具", UITheme.DarkTheme.TEXT);
    }
    
    /**
     * 渲染工具基本信息
     */
    private void renderToolInfo(SelectionTool tool) {
        int selectionCount = selectionManager != null ? selectionManager.getCurrentSelection().size() : 0;
        int titleColor = selectionCount > 0 ? UITheme.DarkTheme.TEXT_SELECTED : UITheme.DarkTheme.TEXT;
        UITheme.label(
            String.format("%s %s (%d)", tool.getIcon(), tool.getDisplayName(), selectionCount),
            titleColor
        );
        
        String description = tool.getDescription();
        if (description != null && !description.isEmpty()) {
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, description);
        }
        
        // 显示工具ID
        ImGui.spacing();
        UITheme.label("工具ID: " + tool.getId(), UITheme.DarkTheme.TEXT_DISABLED);
    }
    
    /**
     * 渲染通用操作模式控制
     */
    private void renderOperationControls(SelectionTool tool) {
        if (!(tool instanceof SelectionOperationSupport operationSupport)) {
            return;
        }

        ImGui.spacing();
        UITheme.label(Icons.TARGET + " 操作模式", UITheme.DarkTheme.TEXT_SELECTED);

        SelectionOperation currentOp = operationSupport.getOperation();
        SelectionOperation[] operations = SelectionOperation.values();

        for (int i = 0; i < operations.length; i++) {
            SelectionOperation operation = operations[i];
            boolean selected = operation == currentOp;
            if (selected) {
                float[] activeColor = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Button, activeColor[0], activeColor[1], activeColor[2], activeColor[3]);
            }

            String label = operation.getIcon() + " " + operation.getDisplayName() + "##opMode_" + operation.name();
            if (ImGui.button(label, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                operationSupport.setOperation(operation);
                LOGGER.info("[ToolPropertiesPanel] 操作模式切换为: {}", operation.getDisplayName());
            }

            if (selected) {
                ImGui.popStyleColor();
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(operation.getDescription());
            }

            if (i < operations.length - 1) {
                ImGui.sameLine();
            }
        }

        ImGui.spacing();
    }

    /**
     * 根据工具类型渲染特定属性
     */
    @SuppressWarnings("unchecked")
    private void renderToolSpecificProperties(SelectionTool tool) {
        // 使用模块化渲染器架构
        ToolPropertyRenderer<?> renderer = findRenderer(tool);
        if (renderer != null) {
            try {
                // 安全的类型转换：渲染器已经匹配工具类型
                ((ToolPropertyRenderer<SelectionTool>) renderer).render(tool, this);
            } catch (Exception e) {
                LOGGER.error("[ToolPropertiesPanel] 渲染工具属性失败: {}", tool.getId(), e);
                renderGenericProperties(tool);
            }
        } else {
            renderGenericProperties(tool);
        }
    }
    
    /**
     * 查找工具对应的渲染器
     */
    private ToolPropertyRenderer<?> findRenderer(SelectionTool tool) {
        // 精确匹配
        ToolPropertyRenderer<?> exact = RENDERERS.get(tool.getClass());
        if (exact != null) {
            return exact;
        }
        
        // 继承匹配
        for (Map.Entry<Class<? extends SelectionTool>, ToolPropertyRenderer<?>> entry : RENDERERS.entrySet()) {
            if (entry.getKey().isInstance(tool)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * 注册工具属性渲染器
     */
    private static void registerRenderer(ToolPropertyRenderer<?> renderer) {
        RENDERERS.put(renderer.getToolClass(), renderer);
        LOGGER.debug("[ToolPropertiesPanel] 注册工具渲染器: {}", renderer.getToolClass().getSimpleName());
    }
    
    /**
     * 通用工具属性（点击、框选等基础工具）
     */
    private void renderGenericProperties(SelectionTool tool) {
        if (ImGui.collapsingHeader("工具信息")) {
            // 显示当前选择状态
            com.chronoblocks.core.selection.SelectionState state = tool.getCurrentSelection();
            if (state != null && !state.isEmpty()) {
                int selectedCount = state.size();
                UITheme.label("已选择: " + selectedCount + " 个方块", 
                             UITheme.DarkTheme.TEXT_SELECTED);
                
                if (ImGui.button("清除选择##clearSelection")) {
                    tool.clearSelection();
                    LOGGER.info("[ToolPropertiesPanel] 清除选择");
                }
            } else {
                UITheme.label("未选择任何方块", UITheme.DarkTheme.TEXT_DISABLED);
            }
        }
    }
    
    /**
     * 获取当前工具显示名称
     */
    private String getCurrentToolDisplayName() {
        SelectionTool tool = selectionManager.getCurrentTool();
        if (tool != null) {
            return tool.getDisplayName();
        }
        return "工具属性";
    }
    
    // ==================== 渲染器接口 ====================
    
    /**
     * 工具属性渲染器接口
     */
    public interface ToolPropertyRenderer<T extends SelectionTool> {
        /**
         * 渲染工具属性
         * @param tool 工具实例
         * @param panel 面板实例（用于访问缓冲区等资源）
         */
        void render(T tool, ToolPropertiesPanel panel);
        
        /**
         * 获取工具类
         */
        Class<T> getToolClass();
    }
    
    // ==================== 具体渲染器实现 ====================
    
    /**
     * 刷选工具属性渲染器
     */
    private static class BrushToolRenderer implements ToolPropertyRenderer<BrushSelectTool> {
        @Override
        public void render(BrushSelectTool tool, ToolPropertiesPanel panel) {
            ImGui.separator();
            ImGui.text("🖌️ 刷选工具属性");

            panel.floatBuffer[0] = (float) tool.getRadius();
            if (ImGui.sliderFloat("笔刷半径##brushRadius", panel.floatBuffer, 0.5f, 20.0f, "%.1f")) {
                tool.setRadius(panel.floatBuffer[0]);
            }

            ImGui.spacing();
            ImGui.text(String.format("过滤管线: %d 个过滤器", tool.getPipeline().size()));
            if (ImGui.button("编辑过滤器##brushFilters")) {
                LOGGER.info("[ToolPropertiesPanel] TODO: 打开刷选过滤器编辑器");
            }
        }
        
        @Override
        public Class<BrushSelectTool> getToolClass() {
            return BrushSelectTool.class;
        }
    }
    
    /**
     * 洪填充工具属性渲染器（魔棒、关联选择）
     */
    private static class FloodFillToolRenderer implements ToolPropertyRenderer<SelectionTool> {
        @Override
        public void render(SelectionTool tool, ToolPropertiesPanel panel) {
            if (tool instanceof MagicWandTool wandTool) {
                renderEnhancedMagicWandSettings(wandTool, panel);
            } else if (tool instanceof ConnectedSelectTool connectedTool) {
                renderConnectedSelectSettings(connectedTool, panel);
            }
        }
        
        /**
         * 渲染增强版魔棒工具设置
         */
        private void renderEnhancedMagicWandSettings(MagicWandTool tool, ToolPropertiesPanel panel) {
            MagicWandConfig config = tool.getConfig();
            
            // 1. 匹配规则
            if (ImGui.collapsingHeader(Icons.FILTER + " 匹配规则", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                
                // 比较模式下拉框
                MagicWandConfig.CompareMode currentMode = config.getCompareMode();
                String modeLabel = currentMode.getDisplayName();
                if (ImGui.beginCombo("比较模式##wandCompareMode", modeLabel)) {
                    for (MagicWandConfig.CompareMode mode : MagicWandConfig.CompareMode.values()) {
                        boolean selected = mode == currentMode;
                        if (ImGui.selectable(mode.getDisplayName(), selected)) {
                            config.setCompareMode(mode);
                            tool.requestPreviewRefresh();
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(mode.getDescription());
                        }
                        if (selected) {
                            ImGui.setItemDefaultFocus();
                        }
                    }
                    ImGui.endCombo();
                }
                
                // 显示说明
                ImGui.spacing();
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, currentMode.getDescription());
                
                ImGui.unindent();
            }
            
            // 2. 连通性与传播
            if (ImGui.collapsingHeader(Icons.LINK + " 连通性与传播", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                
                // 连通性单选
                MagicWandConfig.Connectivity connectivity = config.getConnectivity();
                String connectivityLabel = connectivity.getDisplayName() + " (" + connectivity.getCount() + "邻居)";
                if (ImGui.beginCombo("连通性##wandConnectivity", connectivityLabel)) {
                    for (MagicWandConfig.Connectivity conn : MagicWandConfig.Connectivity.values()) {
                        boolean selected = conn == connectivity;
                        if (ImGui.selectable(conn.getDisplayName() + " (" + conn.getCount() + ")", selected)) {
                            config.setConnectivity(conn);
                            tool.requestPreviewRefresh();
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(conn.getDescription());
                        }
                        if (selected) {
                            ImGui.setItemDefaultFocus();
                        }
                    }
                    ImGui.endCombo();
                }
                
                ImGui.spacing();
                
                // 传播方向控制
                boolean propagateUp = config.isPropagateUp();
                if (ImGui.checkbox("向上传播##wandPropUp", propagateUp)) {
                    config.setPropagateUp(!propagateUp);
                    tool.requestPreviewRefresh();
                }
                ImGui.sameLine();
                
                boolean propagateDown = config.isPropagateDown();
                if (ImGui.checkbox("向下传播##wandPropDown", propagateDown)) {
                    config.setPropagateDown(!propagateDown);
                    tool.requestPreviewRefresh();
                }
                
                boolean propagateHorizontal = config.isPropagateHorizontal();
                if (ImGui.checkbox("水平传播##wandPropHoriz", propagateHorizontal)) {
                    config.setPropagateHorizontal(!propagateHorizontal);
                    tool.requestPreviewRefresh();
                }
                
                if (connectivity != MagicWandConfig.Connectivity.NEIGHBOR_6) {
                    ImGui.sameLine();
                    boolean propagateCorners = config.isPropagateCorners();
                    if (ImGui.checkbox("对角传播##wandPropCorners", propagateCorners)) {
                        config.setPropagateCorners(!propagateCorners);
                        tool.requestPreviewRefresh();
                    }
                }
                
                ImGui.unindent();
            }
            
            // 3. 限制与性能
            if (ImGui.collapsingHeader(Icons.SETTINGS + " 限制与性能", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                
                // 最大方块数滑块
                panel.intBuffer1[0] = config.getMaxBlocks();
                if (ImGui.sliderInt("最大方块数##wandMaxBlocks", panel.intBuffer1, 100, 500_000)) {
                    config.setMaxBlocks(panel.intBuffer1[0]);
                    tool.requestPreviewRefresh();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("防止选择过多方块影响性能");
                }
                
                // 范围限制
                int range = config.getRange();
                if (range < 0) {
                    range = 100; // 用于显示
                }
                panel.intBuffer2[0] = range;
                if (ImGui.sliderInt("扩散范围##wandRange", panel.intBuffer2, 1, 200)) {
                    config.setRange(panel.intBuffer2[0]);
                    tool.requestPreviewRefresh();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("限制从种子点向外扩散的最大步数（-1=无限制）");
                }
                ImGui.sameLine();
                if (ImGui.button("无限##wandRangeUnlim")) {
                    config.setRange(-1);
                    tool.requestPreviewRefresh();
                }
                
                ImGui.spacing();
                
                // 表层检测
                boolean surfaceOnly = config.isSurfaceOnly();
                if (ImGui.checkbox("仅表层##wandSurfaceOnly", surfaceOnly)) {
                    config.setSurfaceOnly(!surfaceOnly);
                    tool.requestPreviewRefresh();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("只选择至少有一面临空气的方块");
                }
                
                // 限定在当前选区内
                boolean limitToSel = config.isLimitToCurrentSelection();
                if (ImGui.checkbox("限定在当前选区##wandLimitSel", limitToSel)) {
                    config.setLimitToCurrentSelection(!limitToSel);
                    tool.requestPreviewRefresh();
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("只在已有选区内部进行泛洪填充（精修模式）");
                }
                
                ImGui.unindent();
            }
            
            // 4. 预览信息
            ImGui.spacing();
            ImGui.separator();
            
            int previewCount = tool.getLastPreviewCount();
            if (previewCount > 0) {
                ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, String.format("%s 预览: %d 个方块", Icons.STATS, previewCount));
                
                MagicWandTask.MagicWandTaskResult preview = tool.getPreviewResult();
                if (preview != null) {
                    if (preview.reachedLimit()) {
                        ImGui.sameLine();
                        ImGui.textColored(1.0f, 1.0f, 0.0f, 1.0f, "(达到上限)");
                    }
                    if (preview.cancelled()) {
                        ImGui.sameLine();
                        ImGui.textColored(1.0f, 0.5f, 0.5f, 1.0f, "(已取消)");
                    }
                }
            } else {
                ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, "悬停方块以显示预览");
            }
        }
        
        /**
         * 渲染关联选择工具设置（保留原有逻辑）
         */
        private void renderConnectedSelectSettings(ConnectedSelectTool tool, ToolPropertiesPanel panel) {
            if (ImGui.collapsingHeader("洪填充设置")) {
                int maxSize = tool.getMaxSelectionSize();
                UITheme.label("最大选择数量: " + maxSize, UITheme.DarkTheme.TEXT);
                
                // 对角线连接设置
                boolean includeDiagonal = tool.isIncludeDiagonal();
                if (ImGui.checkbox("包含对角线连接##connectedDiagonal", includeDiagonal)) {
                    tool.setIncludeDiagonal(!includeDiagonal);
                }
            }
        }
        
        @Override
        public Class<SelectionTool> getToolClass() {
            return SelectionTool.class;
        }
    }
    
    /**
     * 几何选择工具属性渲染器
     */
    private static class GeometryToolRenderer implements ToolPropertyRenderer<GeometrySelectTool> {
        @Override
        public void render(GeometrySelectTool tool, ToolPropertiesPanel panel) {
            GeometryShape currentShape = tool.getShape();
            GeometryConfig cfg = tool.getConfig();

            if (ImGui.collapsingHeader("几何形状")) {
                String currentLabel = currentShape.getDisplayName();
                if (ImGui.beginCombo("形状##geometryShape", currentLabel)) {
                    for (GeometryShape shape : GeometryShape.values()) {
                        boolean selected = shape == currentShape;
                        if (ImGui.selectable(shape.getDisplayName(), selected)) {
                            tool.setShape(shape);
                            tool.refreshPreview();
                            currentShape = shape;
                        }
                        if (selected) {
                            ImGui.setItemDefaultFocus();
                        }
                    }
                    ImGui.endCombo();
                }

                ImGui.spacing();
                panel.floatBuffer[0] = (float) cfg.getRadius();
                if (ImGui.sliderFloat("半径 (方块)##geometryRadius", panel.floatBuffer, 0.5f, 64.0f, "%.1f")) {
                    cfg.setRadius(panel.floatBuffer[0]);
                    tool.refreshPreview();
                }

                if (currentShape != GeometryShape.SPHERE) {
                    panel.floatBuffer[0] = (float) cfg.getHeight();
                    if (ImGui.sliderFloat("高度 (方块)##geometryHeight", panel.floatBuffer, 1.0f, 128.0f, "%.1f")) {
                        cfg.setHeight(panel.floatBuffer[0]);
                        tool.refreshPreview();
                    }
                }

                boolean hollow = cfg.isHollow();
                if (ImGui.checkbox("空心结构##geometryHollow", hollow)) {
                    cfg.setHollow(!hollow);
                    tool.refreshPreview();
                }

                boolean includeNonSolid = tool.isIncludeNonSolid();
                if (ImGui.checkbox("包含非实体方块##geometryIncludeNonSolid", includeNonSolid)) {
                    tool.setIncludeNonSolid(!includeNonSolid);
                }

                ImGui.spacing();
                UITheme.label("提示: 生成后可通过操作模式执行叠加/相减/交集等操作。", UITheme.DarkTheme.TEXT_DISABLED);
            }
        }

        @Override
        public Class<GeometrySelectTool> getToolClass() {
            return GeometrySelectTool.class;
        }
    }
    
    /**
     * 框选工具 v3 属性渲染器
     */
    private static class BoxV3ToolRenderer implements ToolPropertyRenderer<BoxSelectTool> {
        @Override
        public void render(BoxSelectTool tool, ToolPropertiesPanel panel) {
            if (ImGui.collapsingHeader("框选设置")) {
                // 保留扩展钩子，未来可以在此添加更多框选选项
            }
        }
        
        @Override
        public Class<BoxSelectTool> getToolClass() {
            return BoxSelectTool.class;
        }
    }
    
    // ==================== Getter/Setter ====================
    
    /**
     * 获取当前工具
     */
    public SelectionTool getCurrentTool() {
        return selectionManager.getCurrentTool();
    }
    
    /**
     * 获取当前工具ID
     */
    public String getCurrentToolId() {
        return selectionManager.getCurrentToolId();
    }
}