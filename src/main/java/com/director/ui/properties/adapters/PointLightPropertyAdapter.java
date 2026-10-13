package com.director.ui.properties.adapters;

import com.director.lighting.PointLight;
import com.director.ui.lighting.PointLightEditor;
import com.director.ui.lighting.PointLightParameterEditing;
import com.director.ui.panels.LightsPanel;
import com.director.ui.properties.IPropertyAdapter;
import imgui.ImGui;
import imgui.type.ImBoolean;

/** Property controls preview immediately and automatically commit on release/blur. */
public final class PointLightPropertyAdapter implements IPropertyAdapter<PointLight> {
    private PointLight source;
    private final float[] position = new float[3], brightness = new float[1], range = new float[1], falloff = new float[1];
    private final ImBoolean enabled = new ImBoolean();
    @Override public Class<PointLight> getTargetType() { return PointLight.class; }
    @Override public String getAdapterName() { return LightsPanel.tr("point"); }

    @Override public boolean renderProperties(PointLight target) {
        var model = PointLightEditor.model();
        if (model == null) return false;
        PointLight current = model.getPointLight(target.id());
        if (current == null) { PointLightParameterEditing.finish(); return false; }
        if (!current.equals(source)) {
            PointLightParameterEditing.finish();
            source = current;
            position[0] = (float) current.x(); position[1] = (float) current.y(); position[2] = (float) current.z();
            brightness[0] = current.brightness(); range[0] = current.range(); falloff[0] = current.falloff();
            enabled.set(current.enabled());
        }
        boolean locked = com.director.client.gizmo.GizmoManager.get().isDraggingHandle()
                || com.director.export.video.VideoExportManager.getInstance().isExporting();
        if (locked) PointLightParameterEditing.finish();
        ImGui.beginDisabled(locked);
        boolean changed = ImGui.checkbox(LightsPanel.tr("enabled"), enabled);
        boolean active = ImGui.isItemActive();
        boolean finished = ImGui.isItemDeactivatedAfterEdit();
        changed |= ImGui.inputFloat3(LightsPanel.tr("position"), position);
        active |= ImGui.isItemActive(); finished |= ImGui.isItemDeactivatedAfterEdit();
        changed |= ImGui.sliderFloat(LightsPanel.tr("brightness"), brightness, 0, 15);
        active |= ImGui.isItemActive(); finished |= ImGui.isItemDeactivatedAfterEdit();
        changed |= ImGui.sliderFloat(LightsPanel.tr("range"), range, 1, 32);
        active |= ImGui.isItemActive(); finished |= ImGui.isItemDeactivatedAfterEdit();
        changed |= ImGui.sliderFloat(LightsPanel.tr("falloff"), falloff, .25f, 4);
        active |= ImGui.isItemActive(); finished |= ImGui.isItemDeactivatedAfterEdit();
        ImGui.endDisabled();
        ImGui.textDisabled(LightsPanel.tr("white"));

        if (changed && !locked) {
            boolean validPosition = Float.isFinite(position[0]) && Float.isFinite(position[1]) && Float.isFinite(position[2])
                    && Math.abs(position[0]) <= 30_000_000 && Math.abs(position[1]) <= 30_000_000 && Math.abs(position[2]) <= 30_000_000;
            PointLight next = validPosition ? current.at(
                    position[0] == (float) current.x() ? current.x() : position[0],
                    position[1] == (float) current.y() ? current.y() : position[1],
                    position[2] == (float) current.z() ? current.z() : position[2]) : current;
            brightness[0] = bounded(brightness[0], 0, 15, current.brightness());
            range[0] = bounded(range[0], 1, 32, current.range());
            falloff[0] = bounded(falloff[0], .25f, 4, current.falloff());
            next = next.settings(brightness[0], range[0], falloff[0], enabled.get());
            PointLightParameterEditing.preview(model, next);
            source = next;
        }
        if (finished || !active) PointLightParameterEditing.finish();
        return false; // The completed gesture command persists the change.
    }
    private static float bounded(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
