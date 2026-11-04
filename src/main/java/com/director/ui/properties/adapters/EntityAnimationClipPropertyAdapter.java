package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.actor.*;
import com.director.timeline.model.entity.EntityAnimationClip;
import imgui.ImGui;
import imgui.type.*;

public class EntityAnimationClipPropertyAdapter implements IPropertyAdapter<EntityAnimationClip> {
    private final com.director.ui.actor.pose.ActorAnimationEventPanel events=new com.director.ui.actor.pose.ActorAnimationEventPanel();
    private final ActorClipInspectorGesture gesture=new ActorClipInspectorGesture();
    @Override public Class<EntityAnimationClip> getTargetType() { return EntityAnimationClip.class; }
    @Override public boolean renderProperties(EntityAnimationClip source) {
        var draft=gesture.begin(source);
        if(!(draft instanceof EntityAnimationClip clip)) { gesture.end(); return false; }
        ImGui.text("骨骼动画片段");
        ImGui.text(String.format("时间范围：%.2f – %.2f 秒",clip.getStartTime(),clip.getEndTime()));
        var owner=source.getTimelineClip().getParentTrack();
        var actor=com.director.actor.ActorRegistryManager.getInstance().getActor(owner.getActorTrackPayload().getActorRef().getActorId()).orElse(null);
        var entries=com.director.ui.actor.pose.ActorAnimationCatalog.entries(
                com.director.actor.animation.asset.AnimationAssetLibrary.getInstance(),
                com.director.actor.animation.AnimationLibrary.getInstance().getAllAnimations(),actor);
        var current=entries.stream().filter(e->e.id().equals(clip.getAnimationId())).findFirst().orElse(null);
        boolean changed=false;
        if(ImGui.beginCombo("动画",current==null?clip.getAnimationId():current.name())) {
            for(var entry:entries) {
                if (entry.isPose() && !entry.id().equals(clip.getAnimationId())) continue;
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
        ImGui.textDisabled(clip.getAnimationId());
        if(current==null || !current.compatible()) ImGui.textWrapped("当前动画缺失或不兼容；原引用已保留，请选择有效动画修复。");
        changed=ImGui.checkbox("循环播放",clip.isLoop()); if(changed) clip.setLoop(!clip.isLoop()); gesture.observe("loop",changed);
        float[] speed={clip.getSpeed()}; changed=ImGui.sliderFloat("播放速度",speed,.01f,4);
        if(changed) clip.setSpeed(speed[0]); gesture.observe("speed",changed);
        ActorAnimationBlendPropertyUI.render(source,clip.getBlendSettings(),clip::setBlendSettings,gesture);
        gesture.end(); events.render(com.director.core.DirectorServices.get().containerTimeline().getTimelineModel(),source); return false;
    }
}
