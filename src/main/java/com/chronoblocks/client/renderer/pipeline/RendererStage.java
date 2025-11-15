package com.chronoblocks.client.renderer.pipeline;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 🧱 RendererStage v2.0
 * -----------------------------------------------------
 * ChronoBlocks 渲染阶段配置表（类似 Unity RenderPipelineConfig）
 *
 * 每个阶段：
 *  - 定义逻辑顺序 (order)
 *  - 定义默认层名称 (layerName)
 *  - 可选颜色标识 (debugColor)
 *  - 可选渲染标志 (enabled)
 *
 * 提供：
 *  - getByLayer()
 *  - getOrderedStages()
 *  - toggleEnabled()
 *  - prettyPrint() 调试输出
 */
public enum RendererStage {

    // === 🟦 选区渲染 ===
    SELECTION_BASE(
        "selection_base",
        "SelectionRenderer",
        100,
        0x4FA3FF,
        true,
        WorldRenderPhase.AFTER_ENTITIES
    ),

    // === 🟨 选区叠加（锚点/面中心） ===
    SELECTION_OVERLAY(
        "selection_overlay",
        "SelectionOverlayRenderer",
        200,
        0xFFD93D,
        true,
        WorldRenderPhase.AFTER_TRANSLUCENT
    ),

    // === 🟩 工具预览（几何/套索） ===
    TOOL_PREVIEW(
        "tool_preview",
        "ToolPreviewManager",
        300,
        0x47D97C,
        true,
        WorldRenderPhase.AFTER_TRANSLUCENT
    ),

    // === 🟧 交互提示（Hover 高亮 + Hint 文本） ===
    INTERACTION_HINT(
        "interaction_hint",
        "HoverHighlightRenderer + InteractionHintRenderer",
        400,
        0xF6A23D,
        true,
        WorldRenderPhase.AFTER_ENTITIES
    ),

    // === 🟥 Gizmo 操作控件 ===
    GIZMO(
        "gizmo",
        "GizmoManager",
        500,
        0xE74C3C,
        true,
        WorldRenderPhase.AFTER_TRANSLUCENT
    ),

    // === ⚙️ 调试层（边界线/Overlay 信息） ===
    DEBUG_OVERLAY(
        "debug_overlay",
        "OverlayRenderHelper",
        1000,
        0xAAAAAA,
        false,
        WorldRenderPhase.AFTER_TRANSLUCENT
    );

    public enum WorldRenderPhase {
        BEFORE_ENTITIES,
        AFTER_ENTITIES,
        AFTER_TRANSLUCENT
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RendererStage.class);

    private final String id;
    private final String defaultLayerName;
    private final int order;
    private final int debugColor;
    private boolean enabled;
    private final WorldRenderPhase worldPhase;

    RendererStage(String id, String defaultLayerName, int order, int debugColor, boolean enabled, WorldRenderPhase worldPhase) {
        this.id = id;
        this.defaultLayerName = defaultLayerName;
        this.order = order;
        this.debugColor = debugColor;
        this.enabled = enabled;
        this.worldPhase = worldPhase;
    }

    // -----------------------------------------------------------
    // 基础属性访问
    // -----------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getDefaultLayerName() {
        return defaultLayerName;
    }

    public int getOrder() {
        return order;
    }

    public int getDebugColor() {
        return debugColor;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public WorldRenderPhase getWorldPhase() {
        return worldPhase;
    }

    public void toggleEnabled() {
        this.enabled = !this.enabled;
        LOGGER.info("[RendererStage] {} 阶段已 {}", id, enabled ? "启用" : "禁用");
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    // -----------------------------------------------------------
    // 工具方法
    // -----------------------------------------------------------

    /**
     * 获取排序后的阶段列表（按 order 升序）
     */
    public static List<RendererStage> getOrderedStages() {
        List<RendererStage> list = new ArrayList<>(Arrays.asList(values()));
        list.sort(Comparator.comparingInt(RendererStage::getOrder));
        return list;
    }

    /**
     * 根据层名获取阶段
     */
    public static Optional<RendererStage> getByLayer(String layerName) {
        return Arrays.stream(values())
            .filter(stage -> stage.defaultLayerName.equalsIgnoreCase(layerName))
            .findFirst();
    }

    /**
     * 渲染阶段调试输出（用于 UI 展示）
     */
    public static String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("🎨 ChronoBlocks RendererStage Config:\n");
        for (RendererStage stage : getOrderedStages()) {
            sb.append(String.format(" - [%s] %-30s | enabled=%-5s | order=%-4d | color=#%06X%n",
                stage.name(),
                stage.defaultLayerName,
                stage.enabled,
                stage.order,
                stage.debugColor));
        }
        return sb.toString();
    }

    /**
     * 生成 Identifier，用于与 RenderLayer / Shader 绑定
     */
    public Identifier toLayerId() {
        return Identifier.of("chronoblocks", this.id);
    }
}