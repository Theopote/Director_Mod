package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.actor.ActorClipInspectorGesture;
import com.director.timeline.model.entity.EntityDespawnClip;
import imgui.ImGui;
import imgui.type.ImInt;

public class EntityDespawnClipPropertyAdapter implements IPropertyAdapter<EntityDespawnClip> {
    private final ActorClipInspectorGesture gesture = new ActorClipInspectorGesture();
    @Override public Class<EntityDespawnClip> getTargetType() { return EntityDespawnClip.class; }
    private String tr(String key) { return ActorPropertyAdapterI18n.tr("gui.director.actor.despawn_edit."+key); }
    @Override public boolean renderProperties(EntityDespawnClip source) {
        var draft = gesture.begin(source);
        if (!(draft instanceof EntityDespawnClip clip)) { gesture.end(); return false; }
        ImGui.text(tr("title"));
        ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.range",clip.getStartTime(),clip.getEndTime()));
        var modes = EntityDespawnClip.DespawnMode.values();
        var labels = new String[modes.length]; int selected = 0;
        for (int i=0;i<modes.length;i++) { labels[i]=tr(modes[i].name()); if (modes[i]==clip.getMode()) selected=i; }
        var index = new ImInt(selected);
        boolean changed = ImGui.combo(tr("mode")+"##despawnMode",index,labels);
        if (changed) clip.setMode(modes[index.get()]);
        gesture.observe("mode",changed);
        ImGui.textWrapped(tr("hint"));
        gesture.end(); return false;
    }
}
