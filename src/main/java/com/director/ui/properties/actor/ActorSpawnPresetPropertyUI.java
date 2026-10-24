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
    /** Called after the inspector draft commits, so duplication never races a pending property edit. */
    public static void renderIndependentAction(EntitySpawnClip source) {
        if(source.getPreset()==null) return;
        var library=AnimationAssetLibrary.getInstance();
        var asset=library.find(source.getPreset().animationId()).orElse(null);
        if(asset==null) return; // Built-in fixed Pose actions are read-only references, not editable rig assets.
        var model=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        var wrapper=source.getTimelineClip();var track=wrapper==null?null:wrapper.getParentTrack();
        boolean blocked=track==null || track.isLocked() || track.isMuted() || source.isMuted() || model.isPlaying()
                || model.isScrubbing() || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging()
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging();
        ImGui.beginDisabled(blocked);
        if(ImGui.button("制作预设独立副本##spawnPresetIndependent")) {
            try {
                com.director.ui.actor.pose.ActorAnimationAssetEditing.makePresetIndependent(model,library,track,source,
                        "project:preset_"+UUID.randomUUID(),asset.name()+" 副本");
            } catch(IllegalArgumentException failure) { ImGui.setTooltip(failure.getMessage()); }
        }
        ImGui.endDisabled();
        if(ImGui.isItemHovered()) ImGui.setTooltip("只替换当前片段的预设引用；手工关键帧及其他演员仍使用原资产。");
    }
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
                AnimationLibrary.getInstance().getAllAnimations().forEach(a->choices.putIfAbsent(a.getId(),a.purpose().label()+" · "+a.getName()+" · "+a.getId()));
            var binding=draft.getPreset();
            var ids=new ArrayList<String>();ids.add(null);ids.addAll(choices.keySet());
            if(binding!=null && !ids.contains(binding.animationId())) ids.add(binding.animationId());
            String[] labels=ids.stream().map(id->id==null?"无预设":choices.getOrDefault(id,"缺失或不兼容 · "+id)).toArray(String[]::new);
            var selected=new ImInt(binding==null?0:ids.indexOf(binding.animationId()));
            boolean changed=ImGui.combo("动作",selected,labels);
            if(changed) {
                String id=ids.get(selected.get());
                binding=ActorPresetBinding.select(id,binding);
                draft.setPreset(binding);
            }
            gesture.observe("presetId",changed);
            if(binding==null) { ImGui.textWrapped("绑定预设后可直接叠加手工骨骼关键帧，不会创建额外片段。");return; }
            var model=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
            var playback=payload==null?null:new com.director.timeline.playback.entity.EntityClipSampler(payload).sampleSnapshot(model.getCurrentTime());
            boolean distanceClock=playback!=null && com.director.actor.runtime.ActorPresetClock.followsDistance(binding.animationId(),playback);
            if(distanceClock) ImGui.textWrapped("此循环步态按路径行进距离推进，单次路径起止阶段自动淡化步态；缩短演员片段会加快位移和步频。循环与速度设置仅用于原地播放。");
            else if(com.director.actor.runtime.ActorPresetClock.purpose(binding.animationId())==com.director.actor.animation.AnimationData.Purpose.LOCOMOTION)
                ImGui.textWrapped("原地步态：只改变身体动作，不移动演员。沿路径走跑通常直接使用自动步态即可。");
            else ImGui.textWrapped("表演预设按片段本地时间播放；路径继续控制整体位移，未声明的骨骼通道保留底层动作。");
            if(playback!=null && playback.routeClip!=null
                    && com.director.actor.runtime.ActorPresetClock.purpose(binding.animationId())==com.director.actor.animation.AnimationData.Purpose.STATIC_POSE)
                ImGui.textWrapped("静态姿态可能覆盖腿部步态；需要边走边表演时，可在部位范围中排除双腿。");
            ImGui.beginDisabled(distanceClock);
            boolean loop=binding.loop();changed=ImGui.checkbox("循环",loop);
            if(changed) loop=!loop;gesture.observe("presetLoop",changed);
            float[] speed={binding.speed()};changed=ImGui.sliderFloat("速度",speed,.01f,4);gesture.observe("presetSpeed",changed);
            ImGui.endDisabled();
            var blend=binding.blend();float[] weight={blend.weight()};
            changed=ImGui.sliderFloat("预设权重",weight,0,1);gesture.observe("presetWeight",changed);
            var mode=new ImInt(blend.mode().ordinal());changed=ImGui.combo("预设混合",mode,new String[]{"覆盖","叠加（相对静止姿态）"});
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
            var rig=ActorRigCompatibility.resolveRig(actor).orElse(null);
            if(ImGui.beginCombo("部位快捷范围",ActorPresetScope.currentLabel(binding,rig))) {
                for(var scope:ActorPresetScope.values()) {
                    ImGui.beginDisabled(!scope.supported(rig));
                    if(ImGui.selectable(scope.label())) {
                        blend=scope.apply(new ActorPresetBinding(binding.animationId(),loop,speed[0],
                                new PoseBlendSettings(PoseLayer.BlendMode.values()[mode.get()],weight[0],channels,
                                        blend.allBones(),blend.boneIds(),fades[0],fades[1])),rig).blend();
                        gesture.requestCommit();
                    }
                    ImGui.endDisabled();
                }
                ImGui.endCombo();
            }
            ImGui.textWrapped("仅上半身保留双腿的底层步态；仅双腿不包含模型根，避免改变整体位移。范围只筛选已有动画通道。");
            boolean all=blend.allBones();changed=ImGui.checkbox("所有部位",all);
            if(changed) all=!all;gesture.observe("presetAllBones",changed);
            var bones=new LinkedHashSet<>(blend.boneIds());
            if(!all && rig!=null) for(var bone:rig.bones()) {
                changed=ImGui.checkbox(bone.displayName()+"##presetBone"+bone.id(),bones.contains(bone.id()));
                if(changed) { if(!bones.remove(bone.id())) bones.add(bone.id()); }
                gesture.observe("presetBone"+bone.id(),changed);
            }
            var next=new ActorPresetBinding(binding.animationId(),loop,speed[0],new PoseBlendSettings(
                    PoseLayer.BlendMode.values()[mode.get()],weight[0],channels,all,bones,fades[0],fades[1]));
            draft.setPreset(next);
            float time=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel().getCurrentTime();
            var feedback=ActorPresetFeedback.resolve(next,rig,time-draft.getStartTime(),draft.getLength(),playback,actor==null?null:actor.getRigProfile());
            boolean muted=source.isMuted() || wrapper!=null && wrapper.getParentTrack()!=null && wrapper.getParentTrack().isMuted();
            ImGui.text(String.format(Locale.ROOT,"当前预设层权重：%.1f%%",100*(muted?0:feedback.weight())));
            if(!feedback.available()) ImGui.textWrapped("预设缺失或骨架不兼容，当前不参与合成。");
            else if(feedback.bones().isEmpty()) ImGui.textWrapped("所选部位与通道没有对应动画，预设当前不产生影响。");
            else if(rig!=null) ImGui.textWrapped("影响部位："+feedback.bones().stream()
                    .map(id->rig.bone(rig.requireBoneIndex(id)).displayName()).collect(java.util.stream.Collectors.joining("、")));
            if(!muted && playback!=null && playback.routeClip!=null && feedback.available() && feedback.weight()>0
                    && feedback.rotationBones().stream().anyMatch(ActorPresetScope::isLeg)) {
                ImGui.textWrapped("此预设正在影响腿部旋转：玩家脚底接触会按动画权重释放。若只是边走边表演，可选择“仅上半身”；自制走跑动作则可以保留腿部。");
            }
            if(ImGui.treeNode("预设、关键帧与路径怎么配合？")) {
                ImGui.textWrapped("路径决定整体位置，基础步态或循环步态资产决定行走；表演预设可只影响上半身。");
                ImGui.textWrapped("普通部位编辑写入演员的手工修正层，不必先复制预设。只有修改预设资产本身且不想影响其他引用时，才制作独立副本。");
                ImGui.textWrapped("覆盖和叠加都受权重影响。叠加使用相对静止姿态的增量，适合单独制作的呼吸、颤抖；不要把完整基础动作再次当作增量叠加。");
                ImGui.textWrapped("排错时先把预设权重设为 0 验证底层动作，再逐层恢复；选中骨骼可在属性面板查看本帧实际控制来源。");
                ImGui.treePop();
            }
            ImGui.textWrapped("部位与通道只筛选预设已有数据；手工关键帧仍可覆盖同一通道。");
            ImGui.textWrapped(distanceClock?"步态相位跟随路径距离，淡入淡出跟随演员片段；清除预设会保留手工关键帧。":"时间跟随演员片段；先播放预设，再叠加已编辑的骨骼通道。清除预设会保留手工关键帧。");
        } finally { ImGui.popID(); }
    }
}
