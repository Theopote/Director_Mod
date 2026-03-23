package com.director.ui.properties.actor;

import com.director.actor.rig.RigProfileStyle;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * RigProfileStylePropertyUI v1.0
 *
 * <p>RigProfileStyle 的风格滑条 UI 组件。
 *
 * <p>这是一个纯 UI Helper，用于在 Actor 面板中编辑表演风格参数。
 *
 * <p>设计原则：
 * <ul>
 *   <li>只负责 UI 渲染，不参与 Selection 逻辑</li>
 *   <li>使用反射访问字段（UI 层可以接受）</li>
 *   <li>参数范围经过精心设计，保证视觉合理性</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class RigProfileStylePropertyUI {

    /**
     * 渲染风格滑条 UI。
     *
     * @param style RigProfileStyle，如果为 null 则显示提示信息
     */
    public static void render(RigProfileStyle style) {
        if (style == null) {
            ImGui.textDisabled("No Rig Profile Style.");
            return;
        }

        // 使用 collapsingHeader 显示风格设置
        if (ImGui.collapsingHeader("🎭 Performance Style", ImGuiTreeNodeFlags.DefaultOpen)) {

        // Motion Scale：整体动作幅度
        slider("Motion Scale", style, "motionScale", 0.5f, 1.5f, "整体动作幅度（0.5 保守 → 1.5 夸张）");

        // Tempo：动作节奏
        slider("Tempo", style, "tempo", 0.7f, 1.3f, "动作节奏（0.7 慢 → 1.3 快）");

        // Lean Bias：身体前倾偏好
        slider("Lean Bias", style, "leanBias", 0.5f, 1.5f, "身体前倾偏好（0.5 不倾向 → 1.5 更前倾）");

        // Arm Expressiveness：手臂表现欲
        slider("Arm Expressiveness", style, "armExpressiveness", 0.3f, 1.5f, "手臂表现欲（0.3 内敛 → 1.5 夸张）");

        // Leg Expressiveness：腿部表现欲
        slider("Leg Expressiveness", style, "legExpressiveness", 0.3f, 1.5f, "腿部表现欲（0.3 内敛 → 1.5 夸张）");

        ImGui.spacing();
        ImGui.textDisabled("These settings affect all actions globally.");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("这些设置会影响该演员的所有动作，不是 Clip 级的时间变化。");
        }
        }
    }

    /**
     * 渲染单个滑条。
     *
     * @param label 标签
     * @param style RigProfileStyle 对象
     * @param field 字段名
     * @param min 最小值
     * @param max 最大值
     * @param tooltip 工具提示（可选）
     */
    private static void slider(
            String label,
            RigProfileStyle style,
            String field,
            float min,
            float max,
            String tooltip
    ) {
        try {
            var f = RigProfileStyle.class.getField(field);
            float value = f.getFloat(style);
            float[] arr = new float[]{value};

            if (ImGui.sliderFloat(label, arr, min, max, "%.2f")) {
                f.setFloat(style, arr[0]);
            }

            if (tooltip != null && ImGui.isItemHovered()) {
                ImGui.setTooltip(tooltip);
            }
        } catch (Exception e) {
            // 反射失败，静默处理（UI 层可以接受）
        }
    }
}
