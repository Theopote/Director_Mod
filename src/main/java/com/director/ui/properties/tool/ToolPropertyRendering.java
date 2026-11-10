package com.director.ui.properties.tool;

import com.director.core.selection.SelectionOperation;
import com.director.core.selection.SelectionOperationSupport;
import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import com.director.core.selection.tools.ClickSelectTool;
import com.director.core.selection.tools.ContainerCullTool;
import com.director.core.selection.tools.boxselecttool.BoxSelectTool;
import com.director.core.selection.tools.brushselecttool.BrushSelectTool;
import com.director.core.selection.tools.faceselecttool.FaceSelectConfig;
import com.director.core.selection.tools.faceselecttool.FaceSelectTool;
import com.director.core.selection.tools.geometry.GeometryShape;
import com.director.core.selection.tools.magicwandtool.MagicWandConfig;
import com.director.core.selection.tools.magicwandtool.MagicWandTask;
import com.director.core.selection.tools.magicwandtool.MagicWandTool;
import com.director.core.selection.tools.lassoselecttool.LassoSelectTool;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.Section;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
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
 * @author Director Team
 */
public final class ToolPropertyRendering {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolPropertyRendering.class);

    private static final Map<Class<? extends SelectionTool>, ToolPropertyRenderer<?>> RENDERERS = new HashMap<>();

    static {
        registerRenderer(new BrushToolRenderer());
        registerRenderer(new BoxV3ToolRenderer());
        registerRenderer(new ClickToolRenderer());
        registerRenderer(new ContainerCullToolRenderer());
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
        // 打开选择过滤（全局管道，所有选择工具共用；入口统一放在面板顶部）
        Runnable r = ctx.getOnRequestShowFilterPanel();
        if (r != null) {
            if (ImGui.button(Icons.FILTER + " 选择过滤##toolFilterTop")) r.run();
            if (ImGui.isItemHovered()) ImGui.setTooltip("打开选择过滤面板，配置方块选择规则（全局，对所有选择工具生效）");
            ImGui.spacing();
        }
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

    static void renderToolInfo(SelectionTool tool, com.director.core.selection.SelectionManager sm) {
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
        if (!Section.header("selection_tool.info", "工具信息", false)) return;
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
            if (Section.header("selection_tool.face.spread", "传播设置")) {
                ImGui.indent();
                int idx = switch (tool.getPlaneMode()) {
                    case AUTO -> 0; case XY -> 1; case XZ -> 2; case YZ -> 3;
                };
                ImInt pi = new ImInt(idx);
                String[] items = { "自动（跟随鼠标命中面）", "XY 平面（法线 Z）", "XZ 平面（法线 Y）", "YZ 平面（法线 X）" };
                PropertyRow.field("平面模式");
                if (ImGui.combo("##facePlaneMode", pi, items)) {
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
                PropertyRow.field("最大方块数");
                if (ImGui.sliderInt("##faceMaxBlocks", ctx.getIntBuffer1(), 100, 500_000))
                    tool.setMaxSelectionSize(ctx.getIntBuffer1()[0]);
                ctx.getIntBuffer2()[0] = tool.getStepSize();
                PropertyRow.field("每帧步数");
                if (ImGui.sliderInt("##faceStepSize", ctx.getIntBuffer2(), 128, 16_384))
                    tool.setStepSize(ctx.getIntBuffer2()[0]);
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "步数越大扩散越快，但可能带来瞬时卡顿。");
                ImGui.unindent();
            }
            // 2) 匹配规则
            if (Section.header("selection_tool.face.match", "匹配规则")) {
                ImGui.indent();
                PropertyRow.label("忽略透明方块");
                if (ImGui.checkbox("##faceIgnoreTransparent", tool.isIgnoreTransparent())) tool.setIgnoreTransparent(!tool.isIgnoreTransparent());
                if (ImGui.isItemHovered()) ImGui.setTooltip("跳过玻璃、空气等透明方块。");
                PropertyRow.label("按材质传播");
                if (ImGui.checkbox("##faceByMaterial", tool.isPropagateByMaterial())) tool.setPropagateByMaterial(!tool.isPropagateByMaterial());
                if (ImGui.isItemHovered()) ImGui.setTooltip("按方块材质 (MapColor) 判断连通。");
                PropertyRow.label("严格匹配方块状态");
                if (ImGui.checkbox("##faceStrictState", tool.isStrictBlockState())) tool.setStrictBlockState(!tool.isStrictBlockState());
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
            if (Section.header("selection_tool.lasso", "套索设置")) {
                ImGui.indent();
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
            if (Section.header("selection_tool.brush.geometry", "几何形状")) {
                ImGui.indent();
                GeometryShape s = tool.getShape();
                PropertyRow.field("形状");
                if (ImGui.beginCombo("##brushShape", s.getDisplayName())) {
                    for (GeometryShape sh : GeometryShape.values()) {
                        if (ImGui.selectable(sh.getDisplayName(), sh == s)) tool.setShape(sh);
                        if (sh == s) ImGui.setItemDefaultFocus();
                    }
                    ImGui.endCombo();
                }
                ImGui.spacing();
                ctx.getFloatBuffer()[0] = (float) tool.getRadius();
                PropertyRow.field("半径 (方块)");
                if (ImGui.sliderFloat("##brushRadius", ctx.getFloatBuffer(), 0.5f, 64.0f, "%.1f"))
                    tool.setRadius(ctx.getFloatBuffer()[0]);
                if (s != GeometryShape.SPHERE) {
                    ctx.getFloatBuffer()[0] = (float) tool.getHeight();
                    PropertyRow.field("高度 (方块)");
                    if (ImGui.sliderFloat("##brushHeight", ctx.getFloatBuffer(), 1.0f, 128.0f, "%.1f"))
                        tool.setHeight(ctx.getFloatBuffer()[0]);
                }
                PropertyRow.label("空心结构");
                if (ImGui.checkbox("##brushHollow", tool.isHollow())) tool.setHollow(!tool.isHollow());
                ImGui.spacing();
                UITheme.label("提示: 生成后可通过操作模式执行叠加/相减/交集等操作。", UITheme.DarkTheme.TEXT_DISABLED);
                ImGui.unindent();
            }
            // 2) 底部：选中预览（与魔棒一致）
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

    private static final class ClickToolRenderer implements ToolPropertyRenderer<ClickSelectTool> {
        @Override
        public void render(ClickSelectTool tool, IToolPropertyRenderContext ctx) {
            if (Section.header("selection_tool.click.expansion", "点选扩展")) {
                ImGui.indent();
                boolean enabled = tool.isDoubleClickExpandEnabled();
                PropertyRow.label("启用双击扩选");
                if (ImGui.checkbox("##clickDoubleEnabled", enabled)) {
                    tool.setDoubleClickExpandEnabled(!enabled);
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("双击同一方块时执行扩展选择。关闭后保持原有单点行为。");

                ClickSelectTool.DoubleClickPreset preset = tool.getDoubleClickPreset();
                ClickSelectTool.DoubleClickPreset[] presets = ClickSelectTool.DoubleClickPreset.values();
                int presetCurrent = 0;
                for (int i = 0; i < presets.length; i++) {
                    if (presets[i] == preset) {
                        presetCurrent = i;
                        break;
                    }
                }
                ImInt presetIdx = new ImInt(presetCurrent);
                String[] presetItems = new String[presets.length];
                for (int i = 0; i < presets.length; i++) {
                    presetItems[i] = presets[i].getDisplayName();
                }
                PropertyRow.field("双击预设");
                if (ImGui.combo("##clickPreset", presetIdx, presetItems)) {
                    tool.setDoubleClickPreset(presets[presetIdx.get()]);
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("快速/精确/大范围为推荐参数组合；自定义表示你已手动调整参数。");

                ClickSelectTool.DoubleClickMode mode = tool.getDoubleClickMode();
                ClickSelectTool.DoubleClickMode[] modes = ClickSelectTool.DoubleClickMode.values();
                int current = 0;
                for (int i = 0; i < modes.length; i++) {
                    if (modes[i] == mode) {
                        current = i;
                        break;
                    }
                }
                ImInt modeIdx = new ImInt(current);
                String[] modeItems = new String[modes.length];
                for (int i = 0; i < modes.length; i++) {
                    modeItems[i] = modes[i].getDisplayName();
                }
                PropertyRow.field("双击模式");
                if (ImGui.combo("##clickDoubleMode", modeIdx, modeItems)) {
                    tool.setDoubleClickMode(modes[modeIdx.get()]);
                }

                boolean visibleOnly = tool.isVisibleOnlySelection();
                PropertyRow.label("仅可见方块");
                if (ImGui.checkbox("##clickVisibleOnly", visibleOnly)) {
                    tool.setVisibleOnlySelection(!visibleOnly);
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("只选择至少有一个外露面的方块，避免扩选到内部被遮挡方块。");

                ctx.getIntBuffer1()[0] = tool.getExpansionRange();
                PropertyRow.field("扩展范围");
                if (ImGui.sliderInt("##clickExpandRange", ctx.getIntBuffer1(), 1, 64)) {
                    tool.setExpansionRange(ctx.getIntBuffer1()[0]);
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("连通模式表示最大扩散步数；全局同类模式表示立方体半径。");

                ctx.getIntBuffer2()[0] = tool.getMaxSelectionCount();
                PropertyRow.field("最大选择数");
                if (ImGui.sliderInt("##clickMaxCount", ctx.getIntBuffer2(), 128, 200_000)) {
                    tool.setMaxSelectionCount(ctx.getIntBuffer2()[0]);
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("限制单次双击扩选数量，避免大范围卡顿。");

                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "提示: Shift+双击会强制加选，与其他选择工具一致。");
                ImGui.unindent();
            }

            ImGui.spacing();
            ImGui.separator();
            int cnt = ctx.getSelectionManager().getSelectionSet().size();
            if (cnt > 0) {
                ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, Icons.STATS + " 当前选中: " + cnt + " 个方块");
            } else {
                ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, Icons.STATS + " 单击选择，双击可按配置扩展");
            }
        }

        @Override
        public Class<ClickSelectTool> getToolClass() {
            return ClickSelectTool.class;
        }
    }

    private static final class ContainerCullToolRenderer implements ToolPropertyRenderer<ContainerCullTool> {
        @Override
        public void render(ContainerCullTool tool, IToolPropertyRenderContext ctx) {
            tool.refreshActiveContainer();

            if (Section.header("selection_tool.container_cull", "容器剔除")) {
                ImGui.indent();
                if (tool.hasActiveContainer()) {
                    UITheme.label("目标容器: " + tool.getActiveContainerName(), UITheme.DarkTheme.TEXT);
                    UITheme.label("待剔除方块: " + tool.getSelectedBlockCount(), UITheme.DarkTheme.TEXT_SELECTED);
                    PropertyRow.label("多选模式");
                    if (ImGui.checkbox("##containerCullMulti", tool.isMultiSelectEnabled())) {
                        tool.setMultiSelectEnabled(!tool.isMultiSelectEnabled());
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("开启后左键点击不会清空之前选择，用于累积多选。");
                    if (ImGui.button(Icons.IMPORT + " 导入当前选区##containerCullImport", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                        int imported = tool.importFromCurrentSelection();
                        LOGGER.info("[ToolPropertyRendering] 容器剔除导入当前选区: {} 个方块", imported);
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("可先用其他选择工具选区，再导入为剔除候选。");
                    ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "左键选取，拖拽连续框取；右键或按钮确认剔除。");
                } else {
                    UITheme.label("未检测到可用容器", UITheme.DarkTheme.TEXT_DISABLED);
                    ImGui.textColored(0.8f, 0.6f, 0.2f, 1.0f, "请先在容器面板选中一个容器（或启用实时接收激活容器）。");
                }
                ImGui.unindent();
            }

            ImGui.spacing();
            boolean canConfirm = tool.canConfirmCull();
            if (!canConfirm) ImGui.beginDisabled();
            if (ImGui.button(Icons.CHECK + " 确认剔除##containerCullConfirm", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                tool.confirmCull();
            }
            if (!canConfirm) ImGui.endDisabled();
            ImGui.sameLine();
            if (ImGui.button(Icons.CLOSE + " 取消选择##containerCullCancel", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                tool.cancelCull();
            }
        }

        @Override
        public Class<ContainerCullTool> getToolClass() {
            return ContainerCullTool.class;
        }
    }

    private static final class FloodFillToolRenderer implements ToolPropertyRenderer<SelectionTool> {
        @Override
        public void render(SelectionTool tool, IToolPropertyRenderContext ctx) {
            if (tool instanceof MagicWandTool wt) renderMagicWand(wt, ctx);
        }
        private void renderMagicWand(MagicWandTool tool, IToolPropertyRenderContext ctx) {
            MagicWandConfig c = tool.getConfig();
            if (Section.header("selection_tool.wand.spread", "选择方式")) {
                ImGui.indent();
                MagicWandConfig.SpreadMode spread = c.getSpreadMode();
                PropertyRow.field("扩散方式");
                if (ImGui.beginCombo("##wandSpreadMode", spread.getDisplayName())) {
                    for (MagicWandConfig.SpreadMode o : MagicWandConfig.SpreadMode.values()) {
                        if (ImGui.selectable(o.getDisplayName(), o == spread)) {
                            c.setSpreadMode(o);
                            tool.requestPreviewRefresh();
                        }
                        if (ImGui.isItemHovered()) ImGui.setTooltip(o.getDescription());
                        if (o == spread) ImGui.setItemDefaultFocus();
                    }
                    ImGui.endCombo();
                }
                ImGui.spacing();
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, spread.getDescription());
                if (spread == MagicWandConfig.SpreadMode.SPHERE_REGION) {
                    ImGui.spacing();
                    ctx.getIntBuffer1()[0] = c.getSphereRadius();
                    PropertyRow.field("扫描半径");
                    if (ImGui.sliderInt("##wandSphereRadius", ctx.getIntBuffer1(), 1, 100)) {
                        c.setSphereRadius(ctx.getIntBuffer1()[0]);
                        tool.requestPreviewRefresh();
                    }
                    if (ImGui.isItemHovered()) ImGui.setTooltip("球形区域内选择所有匹配方块；拖拽也可调整半径");
                }
                ImGui.unindent();
            }
            if (Section.header("selection_tool.wand.match", "匹配规则")) {
                ImGui.indent();
                MagicWandConfig.CompareMode m = c.getCompareMode();
                PropertyRow.field("比较模式");
                if (ImGui.beginCombo("##wandCompareMode", m.getDisplayName())) {
                    for (MagicWandConfig.CompareMode o : MagicWandConfig.CompareMode.values()) {
                        if (ImGui.selectable(o.getDisplayName(), o == m)) { c.setCompareMode(o); tool.requestPreviewRefresh(); }
                        if (ImGui.isItemHovered()) ImGui.setTooltip(o.getDescription());
                        if (o == m) ImGui.setItemDefaultFocus();
                    }
                    ImGui.endCombo();
                }
                ImGui.spacing();
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, m.getDescription());
                
                // 动态显示标签/过滤器输入框
                if (m == MagicWandConfig.CompareMode.BY_TAG) {
                    ImGui.spacing();
                    String tag = c.getTagFilter();
                    imgui.type.ImString tagStr = new imgui.type.ImString(tag, 128);
                    PropertyRow.field("标签 (如 #logs)");
                    if (ImGui.inputText("##wandTag", tagStr)) {
                        c.setTagFilter(tagStr.get());
                        tool.requestPreviewRefresh();
                    }
                } else if (m == MagicWandConfig.CompareMode.BY_FILTER) {
                    ImGui.spacing();
                    String fid = c.getCustomFilterId();
                    imgui.type.ImString fidStr = new imgui.type.ImString(fid, 128);
                    PropertyRow.field("过滤器ID");
                    if (ImGui.inputText("##wandFilterId", fidStr)) {
                        c.setCustomFilterId(fidStr.get());
                        tool.requestPreviewRefresh();
                    }
                }
                ImGui.unindent();
            }
            if (c.getSpreadMode() == MagicWandConfig.SpreadMode.CONNECTED_FLOOD
                && Section.header("selection_tool.wand.connectivity", "连通性与传播")) {
                ImGui.indent();
                MagicWandConfig.Connectivity conn = c.getConnectivity();
                PropertyRow.field("连通性");
                if (ImGui.beginCombo("##wandConnectivity", conn.getDisplayName() + " (" + conn.getCount() + "邻居)")) {
                    for (MagicWandConfig.Connectivity o : MagicWandConfig.Connectivity.values()) {
                        if (ImGui.selectable(o.getDisplayName() + " (" + o.getCount() + ")", o == conn)) { c.setConnectivity(o); tool.requestPreviewRefresh(); }
                        if (ImGui.isItemHovered()) ImGui.setTooltip(o.getDescription());
                        if (o == conn) ImGui.setItemDefaultFocus();
                    }
                    ImGui.endCombo();
                }
                ImGui.spacing();
                PropertyRow.label("向上传播");
                if (ImGui.checkbox("##wandPropUp", c.isPropagateUp())) { c.setPropagateUp(!c.isPropagateUp()); tool.requestPreviewRefresh(); }
                PropertyRow.label("向下传播");
                if (ImGui.checkbox("##wandPropDown", c.isPropagateDown())) { c.setPropagateDown(!c.isPropagateDown()); tool.requestPreviewRefresh(); }
                PropertyRow.label("水平传播");
                if (ImGui.checkbox("##wandPropHoriz", c.isPropagateHorizontal())) { c.setPropagateHorizontal(!c.isPropagateHorizontal()); tool.requestPreviewRefresh(); }
                // 对角传播 checkbox 已移除，融合进 Connectivity 逻辑中
                ImGui.unindent();
            }
            if (Section.header("selection_tool.wand.limits", "限制与性能")) {
                ImGui.indent();
                ctx.getIntBuffer1()[0] = c.getMaxBlocks();
                PropertyRow.field("最大方块数");
                if (ImGui.sliderInt("##wandMaxBlocks", ctx.getIntBuffer1(), 100, 500_000)) { c.setMaxBlocks(ctx.getIntBuffer1()[0]); tool.requestPreviewRefresh(); }
                if (ImGui.isItemHovered()) ImGui.setTooltip("防止选择过多方块影响性能");
                
                if (c.getSpreadMode() == MagicWandConfig.SpreadMode.CONNECTED_FLOOD) {
                    int r = c.getRange();
                    boolean useRange = r != -1;
                    PropertyRow.label("限制扩散范围");
                    if (ImGui.checkbox("##wandUseRange", useRange)) {
                        c.setRange(useRange ? -1 : 64);
                        tool.requestPreviewRefresh();
                    }
                    if (c.getRange() != -1) {
                        ctx.getIntBuffer2()[0] = c.getRange();
                        PropertyRow.field("范围 (最大步数)");
                        if (ImGui.sliderInt("##wandRange", ctx.getIntBuffer2(), 1, 200)) {
                            c.setRange(ctx.getIntBuffer2()[0]);
                            tool.requestPreviewRefresh();
                        }
                    }
                    ImGui.spacing();
                }
                PropertyRow.label("限定在当前选区");
                if (ImGui.checkbox("##wandLimitSel", c.isLimitToCurrentSelection())) { c.setLimitToCurrentSelection(!c.isLimitToCurrentSelection()); tool.requestPreviewRefresh(); }
                if (ImGui.isItemHovered()) ImGui.setTooltip("只在已有选区内部进行泛洪填充");
                ImGui.unindent();
            }
            if (Section.header("selection_tool.wand.advanced", "高级设置", false)) {
                ImGui.indent();
                PropertyRow.label("多种子模式");
                if (ImGui.checkbox("##wandMultiSeed", c.isMultiSeedMode())) {
                     c.setMultiSeedMode(!c.isMultiSeedMode());
                     // 多种子模式通常不需要刷新预览，或者逻辑比较复杂，视情况而定
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("允许同时从多个位置开始泛洪（实验性）");
                
                PropertyRow.label("权重模式 (距离衰减)");
                if (ImGui.checkbox("##wandWeighted", c.isWeightedMode())) {
                    c.setWeightedMode(!c.isWeightedMode());
                    tool.requestPreviewRefresh();
                }
                if (c.isWeightedMode()) {
                    ctx.getIntBuffer1()[0] = c.getMaxWeightSteps();
                    PropertyRow.field("最大权重步数");
                    if (ImGui.sliderInt("##wandWeightSteps", ctx.getIntBuffer1(), 1, 100)) {
                        c.setMaxWeightSteps(ctx.getIntBuffer1()[0]);
                        tool.requestPreviewRefresh();
                    }
                }
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
        @Override
        public Class<SelectionTool> getToolClass() { return SelectionTool.class; }
    }

    private static final class BoxV3ToolRenderer implements ToolPropertyRenderer<BoxSelectTool> {
        @Override
        public void render(BoxSelectTool tool, IToolPropertyRenderContext ctx) {
            // 1) 框选设置（与魔棒结构一致，暂无扩展参数）
            if (Section.header("selection_tool.box", "框选设置")) {
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
