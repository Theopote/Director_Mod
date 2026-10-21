package com.director.ui.properties.actor;

import com.director.actor.ActorRegistryManager;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.EntityClip;
import imgui.ImGui;
import imgui.type.ImInt;
import java.util.*;
import java.util.function.Consumer;

/** Controls edit the inspector draft; the gesture owns commit and undo. */
public final class ActorAnimationBlendPropertyUI {
    private ActorAnimationBlendPropertyUI() {}
    public static void render(EntityClip source,PoseBlendSettings initial,Consumer<PoseBlendSettings> save,
                              ActorClipInspectorGesture gesture) {
        if(!ImGui.collapsingHeader("骨骼动作混合")) return;
        if(source instanceof com.director.timeline.model.entity.EntityAnimationClip animation
                && com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().find(animation.getAnimationId()).isEmpty()) {
            ImGui.textWrapped("此设置用于逐骨骼动画资产；当前动画没有逐骨骼通道，不能使用局部混合。");
            return;
        }
        if(source instanceof com.director.timeline.model.entity.EntitySpawnClip spawn && spawn.getBoneAnimationId()==null)
            ImGui.textWrapped("尚未创建骨骼关键帧；以下设置将在创建后生效。");
        var mode=new ImInt(initial.mode().ordinal());
        boolean changed=ImGui.combo("混合方式",mode,new String[]{"覆盖（替换选中通道）","叠加（相对绑定姿态）"});
        gesture.observe("blendMode",changed);
        float[] weight={initial.weight()};
        changed=ImGui.sliderFloat("动作权重",weight,0,1); gesture.observe("blendWeight",changed);
        float fadeIn=durationInput("淡入时长（秒）","blendFadeIn",initial.fadeIn(),gesture);
        float fadeOut=durationInput("淡出时长（秒）","blendFadeOut",initial.fadeOut(),gesture);
        ImGui.textWrapped("过渡按片段时间平滑变化；总时长超过片段长度时按比例压缩。0 表示立即切换，仅改变骨骼动作权重。");
        int channels=initial.channels();
        int[] bits={RigPose.TRANSLATION,RigPose.ROTATION,RigPose.SCALE};
        String[] labels={"位置通道","旋转通道","缩放通道"};
        for(int i=0;i<bits.length;i++) {
            changed=ImGui.checkbox(labels[i],(channels&bits[i])!=0);
            if(changed) channels^=bits[i]; gesture.observe("blendChannel"+i,changed);
        }
        boolean all=initial.allBones();
        changed=ImGui.checkbox("所有部位",all); if(changed) all=!all;
        gesture.observe("blendAllBones",changed);
        Set<String> bones=new LinkedHashSet<>(initial.boneIds());
        var wrapper=source.getTimelineClip();
        var payload=wrapper==null || wrapper.getParentTrack()==null?null:wrapper.getParentTrack().getActorTrackPayload();
        var actor=payload==null || payload.getActorRef()==null?null:
                ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
        if(!all) {
            var rig=ActorRigCompatibility.resolveRig(actor).orElse(null);
            if(rig!=null) for(var bone:rig.bones()) {
                changed=ImGui.checkbox(bone.displayName()+"##blendBone"+bone.id(),bones.contains(bone.id()));
                if(changed) { if(!bones.remove(bone.id())) bones.add(bone.id()); }
                gesture.observe("blendBone"+bone.id(),changed);
            }
            else ImGui.textWrapped("当前演员没有可编辑骨架。");
            if(bones.isEmpty()) ImGui.textWrapped("未选择部位：此动作层不会影响演员。");
        }
        ImGui.textWrapped("仅影响已有关键帧的通道；不修改路径和整体变换。叠加以绑定姿态为基准。");
        save.accept(new PoseBlendSettings(PoseLayer.BlendMode.values()[mode.get()],weight[0],channels,all,bones,fadeIn,fadeOut));
    }
    private static float durationInput(String label,String id,float value,ActorClipInspectorGesture gesture) {
        var input=new imgui.type.ImFloat(value);
        boolean changed=ImGui.inputFloat(label,input,.05f,.2f,"%.3f");
        if(changed && (!Float.isFinite(input.get()) || input.get()<0)) {
            gesture.report("过渡时长必须是大于或等于 0 的有限数值。");
            gesture.observe(id,false);
            return value;
        }
        gesture.observe(id,changed);
        return input.get();
    }
}
