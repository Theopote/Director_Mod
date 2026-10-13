package com.director.ui.panels;
import com.director.i18n.DirectorI18n;
import com.director.ui.PanelId;
import com.director.ui.PanelWindows;
import com.director.ui.lighting.PointLightController;
import com.director.ui.lighting.PointLightEditor;
import imgui.ImGui;
public final class LightsPanel extends AbstractPanel {
    public LightsPanel() { setVisible(false); }
    @Override public String getPanelName() { return PanelId.LIGHTS; }
    @Override public String getWindowName() { return PanelWindows.lights(); }
    @Override public int getPriority() { return 146; }
    @Override protected void onContentUnavailable() { PointLightController.get().stop(); }
    @Override protected void doRender() {
        var model=PointLightEditor.model(); if(model==null) return;
        var controller=PointLightController.get();
        ImGui.beginDisabled(com.director.export.video.VideoExportManager.getInstance().isExporting());
        if(ImGui.button(tr("place"))) controller.startPlacement(com.director.lighting.LightType.POINT);
        if(ImGui.button(tr("place_line"))) controller.startPlacement(com.director.lighting.LightType.LINE);
        ImGui.sameLine();if(ImGui.button(tr("place_area"))) controller.startPlacement(com.director.lighting.LightType.AREA);
        if(controller.isActive()) {
            ImGui.textWrapped(tr(controller.isPlacing()?(controller.placementType()==com.director.lighting.LightType.LINE?"line_hint":"placement_hint"):"selection_hint"));
        }
        ImGui.textDisabled(tr("prototype")); ImGui.separator();
        var selected=PointLightEditor.selected();
        for(var light:model.getPointLights()) {
            ImGui.pushID(light.id().toString());
            if(ImGui.selectable(tr(light.type().name().toLowerCase())+" · "+light.name(),selected!=null && selected.id().equals(light.id()))) controller.select(light);
            ImGui.popID();
        }
        if(selected!=null && ImGui.button(tr("delete"))) {
            controller.cancelDrag();PointLightEditor.change(model,model.getPointLight(selected.id()),null);
        }
        ImGui.endDisabled();
    }
    public static String tr(String key) { return DirectorI18n.translate("gui.director.light."+key); }
}
