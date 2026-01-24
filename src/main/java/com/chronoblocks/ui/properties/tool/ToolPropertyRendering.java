package com.chronoblocks.ui.properties.tool;

import com.chronoblocks.core.selection.SelectionOperation;
import com.chronoblocks.core.selection.SelectionOperationSupport;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import com.chronoblocks.core.selection.tools.ConnectedSelectTool;
import com.chronoblocks.core.selection.tools.boxselecttool.BoxSelectTool;
import com.chronoblocks.core.selection.tools.brushselecttool.BrushSelectTool;
import com.chronoblocks.core.selection.tools.faceselecttool.FaceSelectConfig;
import com.chronoblocks.core.selection.tools.faceselecttool.FaceSelectTool;
import com.chronoblocks.core.selection.tools.geometry.GeometryShape;
import com.chronoblocks.core.selection.tools.magicwandtool.MagicWandConfig;
import com.chronoblocks.core.selection.tools.magicwandtool.MagicWandTask;
import com.chronoblocks.core.selection.tools.magicwandtool.MagicWandTool;
import com.chronoblocks.core.selection.tools.lassoselecttool.LassoSelectTool;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具属性渲染逻辑 - 供 ToolPropertiesPanel 与 SelectionToolPropertyAdapter 复用
 * <p>
 * 集中承载 renderToolInfo、renderOperationControls、renderToolSpecificProperties
 * 及各 ToolPropertyRenderer 实现，通过 IToolPropertyRenderContext 获取缓冲区和回调。
 * </p>
 *
 * @author ChronoBlocks Team
 */
public final class ToolPropertyRendering {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolPropertyRendering.class);

    private static final Map<Class<? extends SelectionTool>, ToolPropertyRenderer<?>> RENDERERS = new HashMap<>();

    static {
        registerRenderer(new BrushToolRenderer());
        registerRenderer(new BoxV3ToolRenderer());
        registerRenderer(new FloodFillToolRenderer());
        registerRenderer(new FaceSelectToolRenderer());
        registerRenderer(new LassoToolRenderer());
        LOGGER.info("[ToolPropertyRendering] 已注册 {} 个工具属性渲染器", RENDERERS.size());
    }

    public interface ToolPropertyRenderer<T extends SelectionTool> {
        void render(T tool, IToolPropertyRenderContext ctx);
        Class<T> getToolClass();
    }

    private static void registerRenderer(ToolPropertyRenderer<?> r) {
        RENDERERS.put(r.getToolClass(), r);
    }

    private static ToolPropertyRenderer<?> findRenderer(SelectionTool tool) {
        ToolPropertyRenderer<?> exact = RENDERERS.get(tool.getClass());
        if (exact != null) return exact;
        for (Map.Entry<Class<? extends SelectionTool>, ToolPropertyRenderer<?>> e : RENDERERS.entrySet()) {
            if (e.getKey().isInstance(tool)) return e.getValue();
        }
        return null;
    }

    /**
     * 渲染工具属性内容（不包含「无工具」分支，由调用方处理）
     */
    @SuppressWarnings("unchecked")
    public static void render(SelectionTool tool, IToolPropertyRenderContext ctx) {
        renderToolInfo(tool, ctx.getSelectionManager());
        renderOperationControls(tool);
        ImGui.separator();
        ToolPropertyRenderer<?> renderer = findRenderer(tool);
        if (renderer != null) {
            try {
                ((ToolPropertyRenderer<SelectionTool>) renderer).render(tool, ctx);
            } catch (Exception e) {
                LOGGER.error("[ToolPropertyRendering] 渲染工具属性失败: {}", tool.getId(), e);
                renderGenericProperties(tool);
            }
        } else {
            renderGenericProperties(tool);
        }
    }

    static void renderToolInfo(SelectionTool tool, com.chronoblocks.core.selection.SelectionManager sm) {
        int n = sm != null ? sm.getCurrentSelection().size() : 0;
        int color = n > 0 ? UITheme.DarkTheme.TEXT_SELECTED : UITheme.DarkTheme.TEXT;
        UITheme.label(String.format("%s %s (%d)", tool.getIcon(), tool.getDisplayName(), n), color);
        String d = tool.getDescription();
        if (d != null && !d.isEmpty()) ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, d);
        ImGui.spacing();
    }

    static void renderOperationControls(SelectionTool tool) {
        if (!(tool instanceof SelectionOperationSupport s)) return;
        ImGui.spacing();
        UITheme.label(Icons.TARGET + " 操作模式", UITheme.DarkTheme.TEXT_SELECTED);
        SelectionOperation cur = s.getOperation();
        SelectionOperation[] all = SelectionOperation.values();
        for (int i = 0; i < all.length; i++) {
            SelectionOperation op = all[i];
            boolean sel = op == cur;
            if (sel) {
                float[] c = UITheme.toFloatArrayPublic(UITheme.DarkTheme.TAB_ACTIVE);
                ImGui.pushStyleColor(ImGuiCol.Button, c[0], c[1], c[2], c[3]);
            }
            String lbl = op.getIcon() + " " + op.getDisplayName() + "##opMode_" + op.name();
            if (ImGui.button(lbl, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                s.setOperation(op);
                LOGGER.info("[ToolPropertyRendering] 操作模式: {}", op.getDisplayName());
            }
            if (sel) ImGui.popStyleColor();
            if (ImGui.isItemHovered()) ImGui.setTooltip(op.getDescription());
            if (i < all.length - 1) ImGui.sameLine();
        }
        ImGui.spacing();
    }

    static void renderGenericProperties(SelectionTool tool) {
        if (!ImGui.collapsingHeader("工具信息")) return;
        SelectionState state = tool.getCurrentSelection();
        if (state != null && !state.isEmpty()) {
            UITheme.label("已选择: " + state.size() + " 个方块", UITheme.DarkTheme.TEXT_SELECTED);
            if (ImGui.button("清除选择##clearSelection")) {
                tool.clearSelection();
                LOGGER.info("[ToolPropertyRendering] 清除选择");
            }
        } else {
            UITheme.label("未选择任何方块", UITheme.DarkTheme.TEXT_DISABLED);
        }
    }

    // ---------- 各工具渲染器 ----------

    private static final class FaceSelectToolRenderer implements ToolPropertyRenderer<FaceSelectTool> {
        @Override
        public void render(FaceSelectTool tool, IToolPropertyRenderContext ctx) {
            // 1) 传播设置（与魔棒「连通性与传播」同级）
            if (ImGui.collapsingHeader(Icons.AREA + " 传播设置", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                int idx = switch (tool.getPlaneMode()) {
                    case AUTO -> 0; case XY -> 1; case XZ -> 2; case YZ -> 3;
                };
                ImInt pi = new ImInt(idx);
                String[] items = { "自动（跟随鼠标命中面）", "XY 平面（法线 Z）", "XZ 平面（法线 Y）", "YZ 平面（法线 X）" };
                if (ImGui.combo("平面模式##facePlaneMode", pi, items)) {
                    FaceSelectConfig.PlaneMode m = switch (pi.get()) {
                        case 1 -> FaceSelectConfig.PlaneMode.XY;
                        case 2 -> FaceSelectConfig.PlaneMode.XZ;
                        case 3 -> FaceSelectConfig.PlaneMode.YZ;
                        default -> FaceSelectConfig.PlaneMode.AUTO;
                    };
                    tool.setPlaneMode(m);
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("面选择在指定平面内扩散。自动模式由鼠标命中面决定。");
                ctx.getIntBuffer1()[0] = tool.getMaxSelectionSize();
                if (ImGui.sliderInt("最大方块数##faceMaxBlocks", ctx.getIntBuffer1(), 100, 500_000))
                    tool.setMaxSelectionSize(ctx.getIntBuffer1()[0]);
                ctx.getIntBuffer2()[0] = tool.getStepSize();
                if (ImGui.sliderInt("每帧步数##faceStepSize", ctx.getIntBuffer2(), 128, 16_384))
                    tool.setStepSize(ctx.getIntBuffer2()[0]);
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "步数越大扩散越快，但可能带来瞬时卡顿。");
                ImGui.unindent();
            }
            // 2) 匹配规则
            if (ImGui.collapsingHeader(Icons.FILTER + " 匹配规则", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                if (ImGui.checkbox("忽略透明方块##faceIgnoreTransparent", tool.isIgnoreTransparent())) tool.setIgnoreTransparent(!tool.isIgnoreTransparent());
                if (ImGui.isItemHovered()) ImGui.setTooltip("跳过玻璃、空气等透明方块。");
                if (ImGui.checkbox("按材质传播##faceByMaterial", tool.isPropagateByMaterial())) tool.setPropagateByMaterial(!tool.isPropagateByMaterial());
                if (ImGui.isItemHovered()) ImGui.setTooltip("按方块材质 (MapColor) 判断连通。");
                if (ImGui.checkbox("严格匹配方块状态##faceStrictState", tool.isStrictBlockState())) tool.setStrictBlockState(!tool.isStrictBlockState());
                if (ImGui.isItemHovered()) ImGui.setTooltip("仅传播到完全相同 BlockState 的方块。");
                ImGui.unindent();
            }
            // 3) 底部：选中预览（与魔棒一致）
            ImGui.spacing();
            ImGui.separator();
            int cnt = ctx.getSelectionManager().getSelectionSet().size();
            if (cnt > 0) {
                ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, Icons.STATS + " 当前选中: " + cnt + " 个方块");
            } else {
                ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, Icons.STATS + " 点击方块面以选择并传播");
            }
        }
        @Override
        public Class<FaceSelectTool> getToolClass() { return FaceSelectTool.class; }
    }

    private static final class LassoToolRenderer implements ToolPropertyRenderer<LassoSelectTool> {
        @Override
        public void render(LassoSelectTool tool, IToolPropertyRenderContext ctx) {
            // 1) 套索设置（与魔棒「匹配规则」同级）
            if (ImGui.collapsingHeader(Icons.LASSO + " 套索设置", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                if (ImGui.checkbox("包含非实体方块##lassoIncludeNonSolid", tool.isIncludeNonSolid())) tool.setIncludeNonSolid(!tool.isIncludeNonSolid());
                if (ImGui.isItemHovered()) ImGui.setTooltip("草、花等非实体方块也会加入选区。");
                ImGui.spacing();
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "当前采样点: " + tool.getPathSampleCount());
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "绘制状态: " + (tool.isDrawing() ? "进行中" : "已结束/空闲"));
                ImGui.unindent();
            }
            // 2) 底部：选中预览（与魔棒一致）
            ImGui.spacing();
            ImGui.separator();
            int cnt = ctx.getSelectionManager().getSelectionSet().size();
            if (cnt > 0) {
                ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, Icons.STATS + " 当前选中: " + cnt + " 个方块");
            } else {
                ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, Icons.STATS + " 绘制闭合路径以选择");
            }
        }
        @Override
        public Class<LassoSelectTool> getToolClass() { return LassoSelectTool.class; }
    }

    private static final class BrushToolRenderer implements ToolPropertyRenderer<BrushSelectTool> {
        @Override
        public void render(BrushSelectTool tool, IToolPropertyRenderContext ctx) {
            // 1) 几何形状（与魔棒「匹配规则」同级，进退关系）
            if (ImGui.collapsingHeader(Icons.BOX + " 几何形状", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                GeometryShape s = tool.getShape();
                if (ImGui.beginCombo("形状##brushShape", s.getDisplayName())) {
                    for (GeometryShape sh : GeometryShape.values()) {
                        if (ImGui.selectable(sh.getDisplayName(), sh == s)) tool.setShape(sh);
                        if (sh == s) ImGui.setItemDefaultFocus();
                    }
                    ImGui.endCombo();
                }
                ImGui.spacing();
                ctx.getFloatBuffer()[0] = (float) tool.getRadius();
                if (ImGui.sliderFloat("半径 (方块)##brushRadius", ctx.getFloatBuffer(), 0.5f, 64.0f, "%.1f"))
                    tool.setRadius(ctx.getFloatBuffer()[0]);
                if (s != GeometryShape.SPHERE) {
                    ctx.getFloatBuffer()[0] = (float) tool.getHeight();
                    if (ImGui.sliderFloat("高度 (方块)##brushHeight", ctx.getFloatBuffer(), 1.0f, 128.0f, "%.1f"))
                        tool.setHeight(ctx.getFloatBuffer()[0]);
                }
                if (ImGui.checkbox("空心结构##brushHollow", tool.isHollow())) tool.setHollow(!tool.isHollow());
                ImGui.spacing();
                UITheme.label("提示: 生成后可通过操作模式执行叠加/相减/交集等操作。", UITheme.DarkTheme.TEXT_DISABLED);
                ImGui.unindent();
            }
            // 2) 过滤器设置
            if (ImGui.collapsingHeader(Icons.FILTER + " 过滤器设置", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                ImGui.text(String.format("过滤管线: %d 个过滤器", tool.getPipeline().size()));
                Runnable r = ctx.getOnRequestShowFilterPanel();
                if (r != null && ImGui.button("编辑过滤器##brushFilters")) r.run();
                ImGui.unindent();
            }
            // 3) 底部：选中预览（与魔棒一致）
            ImGui.spacing();
            ImGui.separator();
            int cnt = ctx.getSelectionManager().getSelectionSet().size();
            if (cnt > 0) {
                ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, Icons.STATS + " 当前选中: " + cnt + " 个方块");
            } else {
                ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, Icons.STATS + " 拖拽绘制笔刷范围以选择");
            }
        }
        @Override
        public Class<BrushSelectTool> getToolClass() { return BrushSelectTool.class; }
    }

    private static final class FloodFillToolRenderer implements ToolPropertyRenderer<SelectionTool> {
        @Override
        public void render(SelectionTool tool, IToolPropertyRenderContext ctx) {
            if (tool instanceof MagicWandTool wt) renderMagicWand(wt, ctx);
            else if (tool instanceof ConnectedSelectTool ct) renderConnected(ct, ctx);
        }
        private void renderMagicWand(MagicWandTool tool, IToolPropertyRenderContext ctx) {
            MagicWandConfig c = tool.getConfig();
            if (ImGui.collapsingHeader(Icons.FILTER + " 匹配规则", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                MagicWandConfig.CompareMode m = c.getCompareMode();
                if (ImGui.beginCombo("比较模式##wandCompareMode", m.getDisplayName())) {
                    for (MagicWandConfig.CompareMode o : MagicWandConfig.CompareMode.values()) {
                        if (ImGui.selectable(o.getDisplayName(), o == m)) { c.setCompareMode(o); tool.requestPreviewRefresh(); }
                        if (ImGui.isItemHovered()) ImGui.setTooltip(o.getDescription());
                        if (o == m) ImGui.setItemDefaultFocus();
                    }
                    ImGui.endCombo();
                }
                ImGui.spacing();
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, m.getDescription());
                ImGui.unindent();
            }
            if (ImGui.collapsingHeader(Icons.LINK + " 连通性与传播", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                MagicWandConfig.Connectivity conn = c.getConnectivity();
                if (ImGui.beginCombo("连通性##wandConnectivity", conn.getDisplayName() + " (" + conn.getCount() + "邻居)")) {
                    for (MagicWandConfig.Connectivity o : MagicWandConfig.Connectivity.values()) {
                        if (ImGui.selectable(o.getDisplayName() + " (" + o.getCount() + ")", o == conn)) { c.setConnectivity(o); tool.requestPreviewRefresh(); }
                        if (ImGui.isItemHovered()) ImGui.setTooltip(o.getDescription());
                        if (o == conn) ImGui.setItemDefaultFocus();
                    }
                    ImGui.endCombo();
                }
                ImGui.spacing();
                if (ImGui.checkbox("向上传播##wandPropUp", c.isPropagateUp())) { c.setPropagateUp(!c.isPropagateUp()); tool.requestPreviewRefresh(); }
                ImGui.sameLine();
                if (ImGui.checkbox("向下传播##wandPropDown", c.isPropagateDown())) { c.setPropagateDown(!c.isPropagateDown()); tool.requestPreviewRefresh(); }
                if (ImGui.checkbox("水平传播##wandPropHoriz", c.isPropagateHorizontal())) { c.setPropagateHorizontal(!c.isPropagateHorizontal()); tool.requestPreviewRefresh(); }
                if (conn != MagicWandConfig.Connectivity.NEIGHBOR_6) {
                    ImGui.sameLine();
                    if (ImGui.checkbox("对角传播##wandPropCorners", c.isPropagateCorners())) { c.setPropagateCorners(!c.isPropagateCorners()); tool.requestPreviewRefresh(); }
                }
                ImGui.unindent();
            }
            if (ImGui.collapsingHeader(Icons.SETTINGS + " 限制与性能", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                ctx.getIntBuffer1()[0] = c.getMaxBlocks();
                if (ImGui.sliderInt("最大方块数##wandMaxBlocks", ctx.getIntBuffer1(), 100, 500_000)) { c.setMaxBlocks(ctx.getIntBuffer1()[0]); tool.requestPreviewRefresh(); }
                if (ImGui.isItemHovered()) ImGui.setTooltip("防止选择过多方块影响性能");
                int r = c.getRange();
                ctx.getIntBuffer2()[0] = r < 0 ? 100 : r;
                if (ImGui.sliderInt("扩散范围##wandRange", ctx.getIntBuffer2(), 1, 200)) { c.setRange(ctx.getIntBuffer2()[0]); tool.requestPreviewRefresh(); }
                if (ImGui.isItemHovered()) ImGui.setTooltip("从种子点向外扩散的最大步数（-1=无限制）");
                ImGui.sameLine();
                if (ImGui.button("无限##wandRangeUnlim")) { c.setRange(-1); tool.requestPreviewRefresh(); }
                ImGui.spacing();
                if (ImGui.checkbox("仅表层##wandSurfaceOnly", c.isSurfaceOnly())) { c.setSurfaceOnly(!c.isSurfaceOnly()); tool.requestPreviewRefresh(); }
                if (ImGui.isItemHovered()) ImGui.setTooltip("只选择至少有一面临空气的方块");
                if (ImGui.checkbox("限定在当前选区##wandLimitSel", c.isLimitToCurrentSelection())) { c.setLimitToCurrentSelection(!c.isLimitToCurrentSelection()); tool.requestPreviewRefresh(); }
                if (ImGui.isItemHovered()) ImGui.setTooltip("只在已有选区内部进行泛洪填充");
                ImGui.unindent();
            }
            ImGui.spacing();
            ImGui.separator();
            int pc = tool.getLastPreviewCount();
            if (pc > 0) {
                ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, Icons.STATS + " 预览: " + pc + " 个方块");
                MagicWandTask.MagicWandTaskResult pr = tool.getPreviewResult();
                if (pr != null) {
                    if (pr.reachedLimit()) { ImGui.sameLine(); ImGui.textColored(1.0f, 1.0f, 0.0f, 1.0f, "(达到上限)"); }
                    if (pr.cancelled()) { ImGui.sameLine(); ImGui.textColored(1.0f, 0.5f, 0.5f, 1.0f, "(已取消)"); }
                }
            } else {
                ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, "悬停方块以显示预览");
            }
        }
        private void renderConnected(ConnectedSelectTool tool, IToolPropertyRenderContext ctx) {
            // 1) 洪填充设置（与魔棒「连通性与传播」同级）
            if (ImGui.collapsingHeader(Icons.LINK + " 洪填充设置", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                UITheme.label("最大选择数量: " + tool.getMaxSelectionSize(), UITheme.DarkTheme.TEXT);
                if (ImGui.checkbox("包含对角线连接##connectedDiagonal", tool.isIncludeDiagonal())) tool.setIncludeDiagonal(!tool.isIncludeDiagonal());
                ImGui.unindent();
            }
            // 2) 底部：选中预览（与魔棒一致）
            ImGui.spacing();
            ImGui.separator();
            int cnt = ctx.getSelectionManager().getSelectionSet().size();
            if (cnt > 0) {
                ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, Icons.STATS + " 当前选中: " + cnt + " 个方块");
            } else {
                ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, Icons.STATS + " 点击相连方块以选择");
            }
        }
        @Override
        public Class<SelectionTool> getToolClass() { return SelectionTool.class; }
    }

    private static final class BoxV3ToolRenderer implements ToolPropertyRenderer<BoxSelectTool> {
        @Override
        public void render(BoxSelectTool tool, IToolPropertyRenderContext ctx) {
            // 1) 框选设置（与魔棒结构一致，暂无扩展参数）
            if (ImGui.collapsingHeader(Icons.BOX + " 框选设置", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "拖拽绘制立方体区域，支持 Gizmo 调整。");
                ImGui.unindent();
            }
            // 2) 底部：选中预览（与魔棒一致）
            ImGui.spacing();
            ImGui.separator();
            int cnt = ctx.getSelectionManager().getSelectionSet().size();
            if (cnt > 0) {
                ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, Icons.STATS + " 当前选中: " + cnt + " 个方块");
            } else {
                ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, Icons.STATS + " 拖拽绘制框选区域");
            }
        }
        @Override
        public Class<BoxSelectTool> getToolClass() { return BoxSelectTool.class; }
    }
}
