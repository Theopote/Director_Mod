package com.director.ui.properties.actor;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.AnimationLibrary;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import imgui.ImGui;
import imgui.type.ImInt;
import java.util.*;

/** Edits the Spawn inspector draft; the existing gesture commits one undoable change. */
public final class ActorSpawnPresetPropertyUI {
    private ActorSpawnPresetPropertyUI() {}
    public static void render(EntitySpawnClip source,EntitySpawnClip draft,ActorClipInspectorGesture gesture) {
        if(!ImGui.collapsingHeader("演员动作预设")) return;
        ImGui.pushID("spawnPreset");
        try {
            var wrapper=source.getTimelineClip();
            var payload=wrapper==null || wrapper.getParentTrack()==null?null:wrapper.getParentTrack().getActorTrackPayload();
            var actor=payload==null || payload.getActorRef()==null?null:
                    ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
            var choices=new LinkedHashMap<String,String>();
            AnimationAssetLibrary.getInstance().all().stream()
                    .filter(a->ActorRigCompatibility.accepts(actor,a.rigId())
                            && ActorRigCompatibility.resolveRig(actor).map(r->RigPoseComposer.sameLayout(r,a.rig())).orElse(false)
                            && !a.id().equals(draft.getBoneAnimationId()))
                    .forEach(a->choices.put(a.id(),a.name()+" · "+a.id()));
            if(ActorRigCompatibility.resolveRig(actor).map(HumanoidTopology::matches).orElse(false))
                AnimationLibrary.getInstance().getAllAnimations().forEach(a->choices.putIfAbsent(a.getId(),a.getName()+" · "+a.getId()));
            var binding=draft.getPreset();
            var ids=new ArrayList<String>();ids.add(null);ids.addAll(choices.keySet());
            if(binding!=null && !ids.contains(binding.animationId())) ids.add(binding.animationId());
            String[] labels=ids.stream().map(id->id==null?"无预设":choices.getOrDefault(id,"缺失或不兼容 · "+id)).toArray(String[]::new);
            var selected=new ImInt(binding==null?0:ids.indexOf(binding.animationId()));
            boolean changed=ImGui.combo("动作",selected,labels);
            if(changed) {
                String id=ids.get(selected.get());
                binding=id==null?null:new ActorPresetBinding(id,binding!=null && binding.loop(),
                        binding==null?1:binding.speed(),binding==null?PoseBlendSettings.DEFAULT:binding.blend());
                draft.setPreset(binding);
            }
            gesture.observe("presetId",changed);
            if(binding==null) { ImGui.textWrapped("绑定预设后可直接叠加手工骨骼关键帧，不会创建额外片段。");return; }
            boolean loop=binding.loop();changed=ImGui.checkbox("循环",loop);
            if(changed) loop=!loop;gesture.observe("presetLoop",changed);
            float[] speed={binding.speed()};changed=ImGui.sliderFloat("速度",speed,.01f,4);gesture.observe("presetSpeed",changed);
            var blend=binding.blend();float[] weight={blend.weight()};
            changed=ImGui.sliderFloat("预设权重",weight,0,1);gesture.observe("presetWeight",changed);
            var mode=new ImInt(blend.mode().ordinal());changed=ImGui.combo("预设混合",mode,new String[]{"覆盖","叠加（相对绑定姿态）"});
            gesture.observe("presetMode",changed);
            float[] fades={blend.fadeIn(),blend.fadeOut()};changed=ImGui.inputFloat2("淡入 / 淡出（秒）",fades);
            if(!Float.isFinite(fades[0]) || !Float.isFinite(fades[1]) || fades[0]<0 || fades[1]<0) {
                fades[0]=blend.fadeIn();fades[1]=blend.fadeOut();
            }
            gesture.observe("presetFades",changed);
            int channels=blend.channels();
            int[] bits={RigPose.TRANSLATION,RigPose.ROTATION,RigPose.SCALE};
            String[] channelLabels={"位置通道","旋转通道","缩放通道"};
            for(int i=0;i<bits.length;i++) {
                changed=ImGui.checkbox(channelLabels[i],(channels&bits[i])!=0);
                if(changed) channels^=bits[i];gesture.observe("presetChannel"+i,changed);
            }
            boolean all=blend.allBones();changed=ImGui.checkbox("所有部位",all);
            if(changed) all=!all;gesture.observe("presetAllBones",changed);
            var bones=new LinkedHashSet<>(blend.boneIds());
            var rig=ActorRigCompatibility.resolveRig(actor).orElse(null);
            if(!all && rig!=null) for(var bone:rig.bones()) {
                changed=ImGui.checkbox(bone.displayName()+"##presetBone"+bone.id(),bones.contains(bone.id()));
                if(changed) { if(!bones.remove(bone.id())) bones.add(bone.id()); }
                gesture.observe("presetBone"+bone.id(),changed);
            }
            var next=new ActorPresetBinding(binding.animationId(),loop,speed[0],new PoseBlendSettings(
                    PoseLayer.BlendMode.values()[mode.get()],weight[0],channels,all,bones,fades[0],fades[1]));
            draft.setPreset(next);
            float time=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel().getCurrentTime();
            var feedback=ActorPresetFeedback.resolve(next,rig,time-draft.getStartTime(),draft.getLength());
            boolean muted=source.isMuted() || wrapper!=null && wrapper.getParentTrack()!=null && wrapper.getParentTrack().isMuted();
            ImGui.text(String.format(Locale.ROOT,"当前预设层权重：%.1f%%",100*(muted?0:feedback.weight())));
            if(!feedback.available()) ImGui.textWrapped("预设缺失或骨架不兼容，当前不参与合成。");
            else if(feedback.bones().isEmpty()) ImGui.textWrapped("所选部位与通道没有对应动画，预设当前不产生影响。");
            else if(rig!=null) ImGui.textWrapped("影响部位："+feedback.bones().stream()
                    .map(id->rig.bone(rig.requireBoneIndex(id)).displayName()).collect(java.util.stream.Collectors.joining("、")));
            ImGui.textWrapped("部位与通道只筛选预设已有数据；手工关键帧仍可覆盖同一通道。");
            ImGui.textWrapped("时间跟随演员片段；先播放预设，再叠加已编辑的骨骼通道。清除预设会保留手工关键帧。");
        } finally { ImGui.popID(); }
    }
}
