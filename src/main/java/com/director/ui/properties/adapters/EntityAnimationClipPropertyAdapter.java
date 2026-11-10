package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.actor.*;
import com.director.timeline.model.entity.EntityAnimationClip;
import imgui.ImGui;
import imgui.type.*;

public class EntityAnimationClipPropertyAdapter implements IPropertyAdapter<EntityAnimationClip> {
    private final com.director.ui.actor.pose.ActorAnimationEventPanel events=new com.director.ui.actor.pose.ActorAnimationEventPanel();
    private final ActorClipInspectorGesture gesture=new ActorClipInspectorGesture();
    private static String tr(String key, Object... args) {
        return com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.animation_inspector." + key, args);
    }
    @Override public Class<EntityAnimationClip> getTargetType() { return EntityAnimationClip.class; }
    @Override public boolean renderProperties(EntityAnimationClip source) {
        var draft=gesture.begin(source);
        if(!(draft instanceof EntityAnimationClip clip)) { gesture.end(); return false; }
        ImGui.text(tr("title"));
        ImGui.text(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.range", clip.getStartTime(), clip.getEndTime()));
        var wrapper = source.getTimelineClip();
        var owner = wrapper == null ? null : wrapper.getParentTrack();
        var payload = owner == null ? null : owner.getActorTrackPayload();
        var actor = payload == null || payload.getActorRef() == null ? null
                : com.director.actor.ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
        var entries=com.director.ui.actor.pose.ActorAnimationCatalog.entries(
                com.director.actor.animation.asset.AnimationAssetLibrary.getInstance(),
                com.director.actor.animation.AnimationLibrary.getInstance().getAllAnimations(),actor);
        var current=entries.stream().filter(e->e.id().equals(clip.getAnimationId())).findFirst().orElse(null);
        boolean changed=false;
        String label = !clip.hasAnimation() ? tr("select") : current == null ? clip.getAnimationId() : current.name();
        if(ImGui.beginCombo(tr("animation"), label)) {
            for(var entry:entries) {
                if (entry.isPose()) continue;
                ImGui.beginDisabled(!entry.compatible());
                if(ImGui.selectable(entry.name()+" · "+entry.rigName()+"##"+entry.id(),entry.id().equals(clip.getAnimationId()))) {
                    clip.setAnimationId(entry.id());
                    clip.setSource(entry.source()==com.director.ui.actor.pose.ActorAnimationCatalog.Source.PROJECT
                            ?EntityAnimationClip.AnimationSource.CUSTOM:EntityAnimationClip.AnimationSource.BUILT_IN);
                    changed=true;
                }
                ImGui.endDisabled();
            }
            ImGui.endCombo();
        }
        gesture.observe("animation",changed);
        if (clip.hasAnimation()) ImGui.textDisabled(clip.getAnimationId());
        if (!clip.hasAnimation()) ImGui.textWrapped(tr("unbound"));
        else if(current==null || !current.compatible() || current.isPose()) ImGui.textWrapped(tr("compatible"));
        changed=ImGui.checkbox(tr("loop"),clip.isLoop()); if(changed) clip.setLoop(!clip.isLoop()); gesture.observe("loop",changed);
        float[] speed={clip.getSpeed()}; changed=ImGui.sliderFloat(tr("speed"),speed,.01f,4);
        if(changed) clip.setSpeed(speed[0]); gesture.observe("speed",changed);
        ActorAnimationBlendPropertyUI.render(source,clip.getBlendSettings(),clip::setBlendSettings,gesture);
        gesture.end(); events.render(com.director.core.DirectorServices.get().containerTimeline().getTimelineModel(),source); return false;
    }
}
