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
    private static final com.director.ui.actor.pose.ActorPoseGallery gallery = new com.director.ui.actor.pose.ActorPoseGallery();
    private static final ActorPresetFocusRequest<EntitySpawnClip> focusRequest=new ActorPresetFocusRequest<>();
    public static void requestFocus(EntitySpawnClip source) {focusRequest.request(source);}
    private static final com.director.ui.actor.pose.ActorAnimationCatalog.Cache catalogCache=new com.director.ui.actor.pose.ActorAnimationCatalog.Cache();
    private static final com.director.ui.actor.pose.ActorInspectorPlayback playbackQuery=new com.director.ui.actor.pose.ActorInspectorPlayback();
    /** Called after the inspector draft commits, so duplication never races a pending property edit. */
    public static void renderIndependentAction(EntitySpawnClip source) {
        var galleryWrapper=source.getTimelineClip();
        var galleryTrack=galleryWrapper==null?null:galleryWrapper.getParentTrack();
        var galleryPayload=galleryTrack==null?null:galleryTrack.getActorTrackPayload();
        if(galleryPayload!=null && galleryPayload.getActorRef()!=null)
            gallery.render(com.director.core.DirectorServices.get().containerTimeline().getTimelineModel(),
                    galleryPayload.getActorRef().getActorId(),source);
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
    private static ActorPresetBinding renderScopeButtons(ActorPresetBinding binding,RigDefinition rig,ActorClipInspectorGesture gesture) {
        var result=binding;
        float available=Math.max(1,ImGui.getContentRegionAvailX());
        float rowEnd=ImGui.getCursorScreenPosX()+available;
        boolean first=true;
        for(var scope:ActorPresetScope.values()) {
            float width=Math.min(available,ImGui.calcTextSize(scope.label()).x+ImGui.getStyle().getFramePaddingX()*2);
            if(!first && ImGui.getItemRectMaxX()+ImGui.getStyle().getItemSpacingX()+width<=rowEnd) ImGui.sameLine();
            first=false;
            boolean selected=scope.matches(result,rig);
            if(selected) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,ImGui.getColorU32(imgui.flag.ImGuiCol.ButtonActive));
            ImGui.beginDisabled(!scope.supported(rig));
            if(ImGui.button(scope.label()+"##presetScope"+scope.name(),width,0)) {
                result=scope.apply(result,rig);gesture.requestCommit();
            }
            ImGui.endDisabled();
            if(selected) ImGui.popStyleColor();
            if(!scope.supported(rig) && ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled))
                ImGui.setTooltip("此骨架没有标准人形分组，请逐个勾选部位。");
        }
        if(ActorPresetScope.currentLabel(result,rig).equals("自定义部位")) ImGui.textDisabled("自定义部位；点击范围按钮可恢复该分组。");
        return result;
    }

    public static void render(EntitySpawnClip source,EntitySpawnClip draft,ActorClipInspectorGesture gesture) {
        boolean focus=focusRequest.consume(source);
        if(focus) ImGui.setNextItemOpen(true,imgui.flag.ImGuiCond.Always);
        if(!ImGui.collapsingHeader("演员动作预设")) {playbackQuery.clear();return;}
        if(focus) ImGui.setScrollHereY(0);
        ImGui.pushID("spawnPreset");
        try {
            var wrapper=source.getTimelineClip();
            var payload=wrapper==null || wrapper.getParentTrack()==null?null:wrapper.getParentTrack().getActorTrackPayload();
            var actor=payload==null || payload.getActorRef()==null?null:
                    ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
            var catalog=catalogCache.entries(AnimationAssetLibrary.getInstance(),AnimationLibrary.getInstance(),actor);
            var binding=draft.getPreset();
            String current="无预设";
            if(binding!=null) {
                String id=binding.animationId();
                current=catalog.stream().filter(e->e.id().equals(id)).map(e->e.name()+" · "+e.purpose().label()+(e.compatible()?"":" · 不兼容")).findFirst().orElse("缺失引用 · "+id);
            }
            ImGui.textWrapped("当前动作："+current);
            boolean changed=false;
            ImGui.textDisabled("在下方“姿态与动作”中浏览和绑定动作；此处调整当前绑定。");
            if(binding!=null && ImGui.smallButton("移除动作绑定##clearPreset")) {
                binding=null;draft.setPreset(null);gesture.requestCommit();
            }
            if(binding==null) {playbackQuery.clear();return;}
            var model=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
            var playback=playbackQuery.sample(payload,model.getCurrentTime());
            boolean distanceClock=playback!=null && com.director.actor.runtime.ActorPresetClock.followsDistance(binding.animationId(),playback);
            if(distanceClock) ImGui.textWrapped("此循环步态按路径行进距离推进，单次路径起止阶段自动淡化步态；缩短演员片段会加快位移和步频。循环与速度设置仅用于原地播放。");
            else if(com.director.actor.runtime.ActorPresetClock.purpose(binding.animationId())==com.director.actor.animation.AnimationData.Purpose.LOCOMOTION)
                ImGui.textWrapped("原地步态：只改变身体动作，不移动演员。沿路径走跑通常直接使用自动步态即可。");
            else ImGui.textWrapped("表演预设按片段本地时间播放；路径继续控制整体位移，未声明的骨骼通道保留底层动作。");
            ImGui.beginDisabled(distanceClock);
            boolean loop=binding.loop();changed=ImGui.checkbox("循环",loop);
            if(changed) loop=!loop;gesture.observe("presetLoop",changed);
            float[] speed={binding.speed()};changed=ImGui.sliderFloat("速度",speed,.01f,4);gesture.observe("presetSpeed",changed);
            ImGui.endDisabled();
            var blend=binding.blend();float[] weight={blend.weight()};
            changed=ImGui.sliderFloat("预设权重",weight,0,1);gesture.observe("presetWeight",changed);
            var mode=new ImInt(blend.mode().ordinal());
            float[] fades={blend.fadeIn(),blend.fadeOut()};
            int channels=blend.channels();
            if(ImGui.treeNode("高级混合：叠加、淡入淡出与通道##presetAdvancedBlend")) {
                changed=ImGui.combo("预设混合",mode,new String[]{"覆盖","叠加（相对静止姿态）"});
                gesture.observe("presetMode",changed);
                changed=ImGui.inputFloat2("淡入 / 淡出（秒）",fades);
                if(!Float.isFinite(fades[0]) || !Float.isFinite(fades[1]) || fades[0]<0 || fades[1]<0) {
                    fades[0]=blend.fadeIn();fades[1]=blend.fadeOut();
                }
                gesture.observe("presetFades",changed);
                ImGui.textDisabled("淡入淡出跟随演员片段边界，不在每次动画循环时重新开始。");
                int[] bits={RigPose.TRANSLATION,RigPose.ROTATION,RigPose.SCALE};
                String[] labels={"位置通道","旋转通道","缩放通道"};
                for(int i=0;i<bits.length;i++) {
                    changed=ImGui.checkbox(labels[i],(channels&bits[i])!=0);
                    if(changed) channels^=bits[i];gesture.observe("presetChannel"+i,changed);
                }
                ImGui.treePop();
            }
            var rig=ActorRigCompatibility.resolveRig(actor).orElse(null);
            ImGui.text("部位范围");
            var scopeInput=new ActorPresetBinding(binding.animationId(),loop,speed[0],
                    new PoseBlendSettings(PoseLayer.BlendMode.values()[mode.get()],weight[0],channels,
                            blend.allBones(),blend.boneIds(),fades[0],fades[1]));
            blend=renderScopeButtons(scopeInput,rig,gesture).blend();
            ImGui.textDisabled("范围说明 (?)");
            if(ImGui.isItemHovered()) ImGui.setTooltip("仅上半身保留双腿底层动作；仅双腿不含模型根。范围只筛选资产已有通道。");
            boolean all=blend.allBones();
            var bones=new LinkedHashSet<>(blend.boneIds());
            if(ImGui.treeNode("自定义部位##presetCustomBones")) {
                changed=ImGui.checkbox("所有部位",all);
                if(changed) all=!all;gesture.observe("presetAllBones",changed);
                if(!all && rig!=null) for(var bone:rig.bones()) {
                    changed=ImGui.checkbox(bone.displayName()+"##presetBone"+bone.id(),bones.contains(bone.id()));
                    if(changed) {if(!bones.remove(bone.id())) bones.add(bone.id());}
                    gesture.observe("presetBone"+bone.id(),changed);
                }
                ImGui.treePop();
            }
            var next=new ActorPresetBinding(binding.animationId(),loop,speed[0],new PoseBlendSettings(
                    PoseLayer.BlendMode.values()[mode.get()],weight[0],channels,all,bones,fades[0],fades[1]));
            draft.setPreset(next);
            float time=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel().getCurrentTime();
            var feedback=ActorPresetFeedback.resolve(next,rig,time-draft.getStartTime(),draft.getLength(),playback,actor==null?null:actor.getRigProfile());
            boolean muted=source.isMuted() || wrapper!=null && wrapper.getParentTrack()!=null && wrapper.getParentTrack().isMuted();
            ImGui.text(String.format(Locale.ROOT,"当前预设层权重：%.1f%%",100*(muted?0:feedback.weight())));
            if(!feedback.available()) ImGui.textWrapped("预设缺失或骨架不兼容，当前不参与合成。");
            else if(!feedback.authoredData()) ImGui.textWrapped("此资产尚无可用的骨骼关键帧；请先制作动画，调整范围不会生成动作。");
            else if(feedback.bones().isEmpty()) {
                ImGui.textWrapped("当前部位或通道筛选排除了已有动画。");
                if(ImGui.button("恢复全部部位与通道##restorePresetMask")) {
                    draft.setPreset(ActorPresetScope.restoreChannelsAndBones(next));gesture.requestCommit();
                }
            }
            else if(rig!=null) ImGui.textWrapped("影响部位："+feedback.bones().stream()
                    .map(id->rig.bone(rig.requireBoneIndex(id)).displayName()).collect(java.util.stream.Collectors.joining("、")));
            var routeFeedback=ActorPresetRouteFeedback.resolve(feedback,playback,muted);
            if(routeFeedback==ActorPresetRouteFeedback.LOCAL_CLOCK) {
                ImGui.textWrapped("此预设正在影响腿部旋转，但当前未使用路径距离时钟，可能与行进节奏不一致。");
                ImGui.beginDisabled(!ActorPresetScope.UPPER.supported(rig));
                if(ImGui.button("当前片段改为仅上半身##fixPresetLegScope")) {
                    draft.setPreset(ActorPresetScope.UPPER.apply(next,rig));
                    gesture.requestCommit();
                }
                ImGui.endDisabled();
                ImGui.textWrapped("若这是自制走跑动作，请在动画资产库检查“动作用途”。修改用途会影响所有引用该资产的演员，不会在这里自动修改。");
            } else if(routeFeedback==ActorPresetRouteFeedback.DISTANCE_CLOCK) {
                ImGui.textWrapped("腿部预设使用路径距离时钟；玩家脚底接触按腿部动画权重释放。这不代表已保证完全无滑步。");
            }
            if(ImGui.treeNode("预设、关键帧与路径怎么配合？")) {
                ImGui.textWrapped("路径决定整体位置，基础步态或循环步态资产决定行走；表演预设可只影响上半身。");
                ImGui.textWrapped("普通部位编辑写入演员的手工修正层，不必先复制预设。只有修改预设资产本身且不想影响其他引用时，才制作独立副本。");
                ImGui.textWrapped("覆盖和叠加都受权重影响。叠加使用相对静止姿态的增量，适合单独制作的呼吸、颤抖；不要把完整基础动作再次当作增量叠加。");
                ImGui.textWrapped("排错时先把预设权重设为 0 验证底层动作，再逐层恢复；选中骨骼可在属性面板查看本帧实际控制来源。");
                ImGui.treePop();
            }


        } finally { ImGui.popID(); }
    }
}
