package com.director.ui.properties.adapters;
import com.director.lighting.PointLight;
import com.director.ui.lighting.*;
import com.director.ui.panels.LightsPanel;
import com.director.ui.properties.IPropertyAdapter;
public final class PointLightPropertyAdapter implements IPropertyAdapter<PointLight> {
    private final LightParameterControls controls=new LightParameterControls();
    @Override public Class<PointLight> getTargetType() { return PointLight.class; }
    @Override public String getAdapterName() { return LightsPanel.tr("point"); }
    @Override public boolean renderProperties(PointLight target) {
        var model=PointLightEditor.model();if(model==null) return false;
        var current=model.getPointLight(target.id());if(current==null) {PointLightParameterEditing.finish();return false;}
        boolean locked=com.director.client.gizmo.GizmoManager.get().isDraggingHandle()
            || com.director.export.video.VideoExportManager.getInstance().isExporting();
        controls.render(current,locked,value -> PointLightParameterEditing.preview(model,value),PointLightParameterEditing::finish);
        return false;
    }
}
