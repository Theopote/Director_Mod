package com.director.ui.properties.actor;

import com.director.actor.rig.RigProfileStyle;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

public final class RigProfileStylePropertyUI {

    public static boolean render(RigProfileStyle style) {
        if (style == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.NO_STYLE));
            return false;
        }

        boolean changed = false;
        if (ImGui.collapsingHeader(Icons.SMILE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.SECTION),
                ImGuiTreeNodeFlags.DefaultOpen)) {

            changed |= slider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.MOTION_SCALE),
                    style, "motionScale", 0.5f, 1.5f,
                    ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.MOTION_SCALE_TOOLTIP));

            changed |= slider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.TEMPO),
                    style, "tempo", 0.7f, 1.3f,
                    ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.TEMPO_TOOLTIP));

            changed |= slider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.LEAN_BIAS),
                    style, "leanBias", 0.5f, 1.5f,
                    ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.LEAN_BIAS_TOOLTIP));

            changed |= slider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.ARM_EXPRESSIVENESS),
                    style, "armExpressiveness", 0.3f, 1.5f,
                    ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.ARM_EXPRESSIVENESS_TOOLTIP));

            changed |= slider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.LEG_EXPRESSIVENESS),
                    style, "legExpressiveness", 0.3f, 1.5f,
                    ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.LEG_EXPRESSIVENESS_TOOLTIP));

            ImGui.spacing();
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.GLOBAL_HINT));
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.RigStyle.GLOBAL_TOOLTIP));
            }
        }
        return changed;
    }

    private static boolean slider(String label, RigProfileStyle style, String field, float min, float max, String tooltip) {
        boolean changed = false;
        try {
            var f = RigProfileStyle.class.getField(field);
            float value = f.getFloat(style);
            float[] arr = new float[]{value};

            if (ImGui.sliderFloat(label, arr, min, max, "%.2f")) {
                f.setFloat(style, arr[0]);
                changed = true;
            }

            if (tooltip != null && ImGui.isItemHovered()) {
                ImGui.setTooltip(tooltip);
            }
        } catch (Exception ignored) {
        }
        return changed;
    }
}
