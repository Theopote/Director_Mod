package com.director.ui.properties.adapters;
import com.director.lighting.PointLight;
import com.director.ui.panels.LightsPanel;
import com.director.ui.properties.PropertyRow;
import imgui.ImGui;
import imgui.type.ImBoolean;
/** Shared controls for scene defaults and animated light values. */
final class LightParameterControls {
    private PointLight source;
    private final float[] position = new float[3], brightness = new float[1], range = new float[1], falloff = new float[1];
    private final ImBoolean enabled = new ImBoolean();
    private final float[] width=new float[1],height=new float[1],yaw=new float[1],pitch=new float[1];
    public void render(PointLight current,boolean locked,java.util.function.Consumer<PointLight> preview,Runnable finish) {
        if (!current.equals(source)) {
            finish.run();
            source = current;
            position[0] = (float) current.x(); position[1] = (float) current.y(); position[2] = (float) current.z();
            brightness[0] = current.brightness(); range[0] = current.range(); falloff[0] = current.falloff();
            enabled.set(current.enabled());width[0]=current.width();height[0]=current.height();yaw[0]=current.yaw();pitch[0]=current.pitch();
        }
        if (locked) finish.run();
        ImGui.beginDisabled(locked);
        PropertyRow.label(LightsPanel.tr("enabled"));
        boolean changed = ImGui.checkbox("##light_enabled", enabled);
        boolean active = ImGui.isItemActive();
        boolean finished = ImGui.isItemDeactivatedAfterEdit();
        PropertyRow.field(LightsPanel.tr("position"));
        changed |= ImGui.inputFloat3("##light_position", position);
        active |= ImGui.isItemActive(); finished |= ImGui.isItemDeactivatedAfterEdit();
        PropertyRow.field(LightsPanel.tr("brightness"));
        changed |= ImGui.sliderFloat("##light_brightness", brightness, 0, 15);
        active |= ImGui.isItemActive(); finished |= ImGui.isItemDeactivatedAfterEdit();
        PropertyRow.field(LightsPanel.tr("range"));
        changed |= ImGui.sliderFloat("##light_range", range, 1, 32);
        active |= ImGui.isItemActive(); finished |= ImGui.isItemDeactivatedAfterEdit();
        PropertyRow.field(LightsPanel.tr("falloff"));
        changed |= ImGui.sliderFloat("##light_falloff", falloff, .25f, 4);
        active |= ImGui.isItemActive(); finished |= ImGui.isItemDeactivatedAfterEdit();
        if(current.type()==com.director.lighting.LightType.AREA) {
            PropertyRow.field(LightsPanel.tr("width"));
            changed |= ImGui.sliderFloat("##light_width",width,.1f,64);
            active |= ImGui.isItemActive();
            finished |= ImGui.isItemDeactivatedAfterEdit();
            PropertyRow.field(LightsPanel.tr("height"));
            changed |= ImGui.sliderFloat("##light_height",height,.1f,64);
            active |= ImGui.isItemActive();
            finished |= ImGui.isItemDeactivatedAfterEdit();
            PropertyRow.field(LightsPanel.tr("yaw"));
            changed |= ImGui.sliderFloat("##light_yaw",yaw,-180,180);
            active |= ImGui.isItemActive();
            finished |= ImGui.isItemDeactivatedAfterEdit();
            PropertyRow.field(LightsPanel.tr("pitch"));
            changed |= ImGui.sliderFloat("##light_pitch",pitch,-90,90);
            active |= ImGui.isItemActive();
            finished |= ImGui.isItemDeactivatedAfterEdit();
        }
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
            if(current.type()==com.director.lighting.LightType.AREA)
                next=next.area(bounded(width[0],.1f,64,current.width()),bounded(height[0],.1f,64,current.height()),
                    bounded(yaw[0],-180,180,current.yaw()),bounded(pitch[0],-90,90,current.pitch()));
            preview.accept(next);
            source = next;
        }
        if (finished || !active) finish.run();
    }
    private static float bounded(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
