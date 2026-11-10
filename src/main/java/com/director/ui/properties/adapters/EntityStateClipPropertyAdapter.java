package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.actor.ActorClipInspectorGesture;
import com.director.timeline.model.entity.EntityStateClip;
import imgui.ImGui;
import imgui.type.ImInt;

/** State inspector edits a detached draft; one slider gesture produces one command. */
public class EntityStateClipPropertyAdapter implements IPropertyAdapter<EntityStateClip> {
    private final ActorClipInspectorGesture gesture = new ActorClipInspectorGesture();
    @Override public Class<EntityStateClip> getTargetType() { return EntityStateClip.class; }
    @Override public boolean renderProperties(EntityStateClip source) {
        var draft = gesture.begin(source);
        if (!(draft instanceof EntityStateClip clip)) { gesture.end(); return false; }
        ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.ADAPTER_NAME));
        ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.range",clip.getStartTime(),clip.getEndTime()));
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.state_edit.runtime_hint"));
        ImGui.beginDisabled();
        PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.SNEAKING));
        ImGui.checkbox("##entity_state_clip_sneaking",clip.isSneaking());
        PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.SPRINTING));
        ImGui.checkbox("##entity_state_clip_sprinting",clip.isSprinting());
        PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.INVULNERABLE));
        ImGui.checkbox("##entity_state_clip_invulnerable",clip.isInvulnerable());
        ImGui.endDisabled();
        var glow = new ImInt(clip.getGlowingOverride() == null ? 0 : clip.isGlowing() ? 1 : 2);
        PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.GLOWING));
        boolean changed = ImGui.combo("##stateGlowing",
                glow, new String[]{ActorPropertyAdapterI18n.tr("gui.director.actor.state_edit.inherit"),
                        ActorPropertyAdapterI18n.tr("gui.director.actor.state_edit.on"),
                        ActorPropertyAdapterI18n.tr("gui.director.actor.state_edit.off")});
        if (changed) clip.setGlowing(glow.get() == 0 ? null : glow.get() == 1);
        gesture.observe("glowing",changed);
        boolean overridesVisibility = clip.getVisibilityOverride() != null;
        PropertyRow.label(ActorPropertyAdapterI18n.tr("gui.director.actor.state_edit.override_visibility"));
        changed = ImGui.checkbox("##entity_state_clip_override_visibility", overridesVisibility);
        if (changed) clip.setVisibilityOverride(overridesVisibility ? null : 1f);
        gesture.observe("override_visibility", changed);
        ImGui.beginDisabled(clip.getVisibilityOverride() == null);
        float[] visibility = {clip.getVisibility()};
        PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.VISIBILITY));
        changed = ImGui.sliderFloat("##stateVisibility",visibility,0,1);
        if (changed) clip.setVisibility(visibility[0]);
        gesture.observe("visibility",changed);
        ImGui.endDisabled();
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.state_edit.visibility_hint"));
        gesture.end(); return false;
    }
}
