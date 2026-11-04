package com.director.ui.actor.pose;

import com.director.actor.Actor;
import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import imgui.ImGui;
import imgui.type.*;
import java.util.*;

/** One discovery surface, with distinct static-pose and time-based action commands. */
public final class ActorPoseGallery {
    private final ImString query=new ImString(256),name=new ImString(128),rename=new ImString(128);
    private final ImInt category=new ImInt(),poseSource=new ImInt(),actionSource=new ImInt(),purpose=new ImInt();
    private final ActorAnimationCatalog.Cache catalog=new ActorAnimationCatalog.Cache();
    private final Map<Object,List<ActorPoseThumbnail.Segment>> thumbnails=new LinkedHashMap<>();
    private List<ActorSavedPoseLibrary.Saved> observedSavedPoses;
    private UUID actorId;
    private Actor actorIdentity;
    private TimelineModel currentModel;
    private RigDefinition rig;
    private LimbBendMode appearance;
    private boolean selectedOnly;
    private String message="";
    private ActorSavedPoseLibrary.Saved editing;
    private boolean deleting;

    public void render(TimelineModel model,UUID id) { render(model,id,null); }
    public void render(TimelineModel model,UUID id,EntitySpawnClip expectedSpawn) {
        if(model==null || id==null) return;
        var actor=ActorRegistryManager.getInstance().getActor(id).orElse(null);
        var nextRig=actor==null?null:ActorRigCompatibility.resolveRig(actor).orElse(null);
        if(nextRig==null) return;
        if(!id.equals(actorId) || nextRig!=rig || actor!=actorIdentity || model!=currentModel) {
            // Search/category are browsing preferences; selection and drafts belong to the actor.
            actorId=id;rig=nextRig;thumbnails.clear();name.set("");editing=null;message="";selectedOnly=false;
            actorIdentity=actor;currentModel=model;
        }
        if(appearance!=actor.getLimbBendMode()) {appearance=actor.getLimbBendMode();thumbnails.clear();}
        if(!ImGui.collapsingHeader("姿态与动作##actorPoseGallery",imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) return;
        ImGui.pushID("poseGallery");
        try {
            TimelineTrack owner;
            try { owner=ActorPoseGalleryEditing.owner(model,id); }
            catch(IllegalArgumentException failure) {ImGui.textWrapped(failure.getMessage());return;}
            var spawn=ActorEmbeddedAnimation.target(model,owner);
            boolean wrongTarget=expectedSpawn!=null && spawn!=expectedSpawn;
            var context=ActorAnimationEditContext.resolve(model,id);
            boolean blocked=model.isPlaying() || model.isScrubbing() || owner.isLocked() || owner.isMuted() || wrongTarget
                    || ActorBoneGizmoController.getInstance().hasPendingDraft() || ActorBoneGizmoController.getInstance().isDragging()
                    || ActorBonePropertiesPanel.hasNumericDraft() || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                    || com.director.export.video.VideoExportManager.getInstance().isExporting();
            boolean poseReady=!blocked && (context!=null?context.isCurrent():spawn!=null && spawn.getBoneAnimationId()==null);
            ImGui.textWrapped("编辑演员："+actor.getName()+" · 当前时间 "+String.format(Locale.ROOT,"%.2fs",model.getCurrentTime()));
            ImGui.textWrapped("应用姿态：当前时间的旋转关键帧；绑定动作：当前演员片段。位置与路径继续由原设置控制。");
            if(wrongTarget) ImGui.textWrapped("播放头不在此演员片段内，请先移动到该片段。");
            else if(blocked) ImGui.textWrapped("请先暂停播放、解除轨道锁定，或应用／取消正在编辑的草稿。");
            else if(!poseReady && spawn==null) ImGui.textWrapped("请把播放头移到可编辑的演员片段内。");
            if(context!=null) {
                ImGui.textWrapped("姿态写入："+context.asset().name());
                int refs=ActorAnimationAssetEditing.referenceCount(model,context.asset().id());
                if(refs>1) ImGui.textWrapped("共享动画：修改姿态会影响 "+refs+" 个引用；只改此片段时请先制作独立副本。");
            } else if(spawn!=null) ImGui.textDisabled("首次应用姿态会自动建立本片段的骨骼修正层，可一次撤销。");
            ImGui.beginDisabled(blocked);
            if(ImGui.button("微调身体部位##openPoseTool")) {
                com.director.ui.actor.selection.ActorSelectionHub.selectActor(id);
                ActorAnimationAuthoringSettings.getInstance().setEditTarget(ActorAnimationAuthoringSettings.EditTarget.ANIMATION);
                com.director.ui.tools.ToolRegistry.get().activate(com.director.ui.tools.ToolRegistry.ACTOR_POSE_TOOL_ID);
            }
            ImGui.endDisabled();
            if(spawn!=null && spawn.getBoneAnimationId()!=null) {
                ImGui.textWrapped("手工修正优先于动作预设；已有关键帧可能覆盖新绑定的动作。可旁路手工层检查预设，已有关键帧会保留。");
                ImGui.beginDisabled(blocked);
                if(ImGui.button(spawn.isBoneAnimationMuted()?"恢复手工修正##bypassManual":"旁路手工修正##bypassManual")) {
                    try {
                        var edit=new com.director.ui.actor.edit.ActorClipPropertyEditSession(model,spawn);
                        var draft=(EntitySpawnClip)edit.draft();
                        draft.setBoneAnimationMuted(!spawn.isBoneAnimationMuted());
                        edit.commit();refresh(id);
                    } catch(RuntimeException failure) {message=failure.getMessage();}
                }
                ImGui.endDisabled();
            }
            ImGui.inputTextWithHint("##search","搜索姿态或动作",query);
            ImGui.combo("预设类型##category",category,new String[]{"姿态","动作"});
            boolean poses = category.get() == 0;
            if (poses) {
                ImGui.combo("来源##poseSource", poseSource, new String[]{"全部来源","内置姿态","我的姿态","项目姿态"});
                if(ImGui.checkbox("姿态仅作用于所选部位##selectedOnly",selectedOnly)) selectedOnly=!selectedOnly;
            } else {
                ImGui.combo("来源##actionSource", actionSource, new String[]{"全部来源","内置动作","项目动作"});
                ImGui.combo("动作用途##purpose",purpose,new String[]{"全部动作","表演","循环步态"});
            }
            var selection=ActorBoneSelectionController.getInstance();
            var bones=selectedOnly?(id.equals(selection.getActorId())?selection.getSelectedBoneIds():List.<String>of()):
                    rig.bones().stream().map(BoneDefinition::id).toList();
            if(poses && selectedOnly && bones.isEmpty()) ImGui.textWrapped("尚未选择部位；请在姿态工具中选择，或关闭此选项以应用全身姿态。");
            boolean canApply=poseReady && !bones.isEmpty();
            if(ImGui.beginChild("galleryResults",0,240,true)) {
                int count=0;
                if(poses && (poseSource.get()==0 || poseSource.get()==1) && HumanoidTopology.matches(rig))
                    for(var preset:ActorPosePresets.Preset.values()) if(matches(preset.label())) {
                        count++;
                        thumbnail(()->cached(preset,()->ActorPosePresets.apply(new RigPose(rig),preset,rig.bones().stream().map(BoneDefinition::id).toList())));ImGui.sameLine();
                        ImGui.beginDisabled(!canApply);
                        if(ImGui.button("应用姿态 · "+preset.label()+"##"+preset.name())) apply(model,id,bones,p->ActorPosePresets.apply(p,preset,bones));
                        ImGui.endDisabled();ImGui.textDisabled("内置姿态 · 当前时间");
                    }
                if (poses && (poseSource.get()==0 || poseSource.get()==2)) try {
                    var savedPoses=ActorSavedPoseLibrary.current();
                    // Keep the cache bounded and remove obsolete saved poses after edits/world switches.
                    if(observedSavedPoses!=savedPoses) {
                        var currentPoses=new HashSet<>(savedPoses);
                        thumbnails.keySet().removeIf(k->k instanceof ActorSavedPoseLibrary.Saved && !currentPoses.contains(k));
                        observedSavedPoses=savedPoses;
                    }
                    for(var saved:savedPoses) {
                        if(!saved.rigId().equals(rig.id()) || !matches(saved.name())) continue;
                        count++;ImGui.pushID("saved:"+saved.name());
                        thumbnail(()->cached(saved,()->saved.apply(new RigPose(rig),saved.rotations().keySet())));ImGui.sameLine();
                        var scope=bones.stream().filter(saved.rotations()::containsKey).toList();
                        ImGui.beginDisabled(!canApply || scope.isEmpty());
                        if(ImGui.button("应用姿态 · "+saved.name())) apply(model,id,scope,p->saved.apply(p,scope));
                        ImGui.endDisabled();ImGui.textDisabled("我的姿态 · 当前存档");
                        ImGui.beginDisabled(blocked);
                        if(ImGui.smallButton("重命名")) {editing=saved;deleting=false;rename.set(saved.name());}
                        ImGui.sameLine();
                        if(ImGui.smallButton("删除")) {editing=saved;deleting=true;}
                        ImGui.endDisabled();ImGui.popID();
                    }
                } catch(java.io.IOException failure) {ImGui.textWrapped(failure.getMessage());}
                var entries=catalog.entries(AnimationAssetLibrary.getInstance(),AnimationLibrary.getInstance(),actor);
                var usage = poses ? AnimationData.Purpose.STATIC_POSE : switch (purpose.get()) {
                    case 1 -> AnimationData.Purpose.PERFORMANCE;
                    case 2 -> AnimationData.Purpose.LOCOMOTION;
                    default -> null;
                };
                int sourceFilter = poses ? poseSource.get() : actionSource.get();
                var source = sourceFilter == 1 ? ActorAnimationCatalog.Source.BUILTIN
                        : sourceFilter == (poses ? 3 : 2) ? ActorAnimationCatalog.Source.PROJECT : null;
                for(var entry:catalog.filter(entries,query.get(),source,usage,true,spawn==null?null:spawn.getBoneAnimationId())) {
                    if (entry.isPose() != poses || poses && poseSource.get() == 2) continue;
                    if (poses) {
                        count++;
                        ImGui.beginDisabled(!canApply);
                        if (ImGui.button("应用姿态 · " + entry.name() + "##assetPose:" + entry.id())) {
                            try {
                                boolean applied = ActorPoseGalleryEditing.applyAssetPose(model, id, bones, entry.id());
                                if (applied) ActorAnimationAuthoringSettings.getInstance().setEditTarget(ActorAnimationAuthoringSettings.EditTarget.ANIMATION);
                                message = applied ? "姿态已写入当前帧，可撤销。" : "姿态没有可应用的所选旋转，或目标当前不可编辑。";
                                if (applied) refresh(id);
                            } catch (IllegalArgumentException failure) { message = failure.getMessage(); }
                        }
                        ImGui.endDisabled();
                        ImGui.textDisabled(entry.source() == ActorAnimationCatalog.Source.BUILTIN ? "内置姿态 · 当前帧" : "项目姿态 · 当前帧");
                        continue;
                    }
                    count++;ImGui.pushID("action:"+entry.id());
                    ImGui.beginDisabled(blocked || spawn==null);
                    if(ImGui.button("绑定动作 · "+entry.name())) {
                        try {
                            message=ActorPoseGalleryEditing.bind(model,id,spawn,entry.id())?"动作已绑定，可撤销。点击时间线播放查看；原有手工关键帧会保留。":"动作未变化或当前目标不可编辑。";
                            refresh(id);
                        } catch(IllegalArgumentException failure) {message=failure.getMessage();}
                    }
                    ImGui.endDisabled();
                    ImGui.textDisabled((entry.source()==ActorAnimationCatalog.Source.BUILTIN?"内置动作":"项目动画")+" · "+entry.purpose().label());
                    if(spawn!=null && spawn.getPreset()!=null && entry.id().equals(spawn.getPreset().animationId())) ImGui.textDisabled("当前已绑定");
                    if(entry.purpose()==AnimationData.Purpose.LOCOMOTION) ImGui.textWrapped("行走动作本身不建立路径；沿路径行进可使用自动步态。");
                    ImGui.popID();
                }
                if(count==0) ImGui.textWrapped(poses ? "没有匹配的兼容姿态。" : "没有匹配的兼容动作。");
            }
            ImGui.endChild();
            if (poses) renderSavedPoseControls(model, id, context, bones, blocked, canApply);
            if(!message.isEmpty()) ImGui.textWrapped(message);
        } finally {ImGui.popID();}
    }

    private void renderSavedPoseControls(TimelineModel model, UUID id, ActorAnimationEditContext context,
                                         Collection<String> bones, boolean blocked, boolean canApply) {
        ImGui.beginDisabled(blocked);
        if(ImGui.smallButton("重新加载个人姿态库")) {
            try {ActorSavedPoseLibrary.reload();thumbnails.clear();editing=null;message="姿态库已重新加载。";}
            catch(java.io.IOException failure) {message=failure.getMessage();}
        }
        ImGui.endDisabled();
        ImGui.inputTextWithHint("##poseName",poseText("name"),name);
        ImGui.beginDisabled(!canApply || context==null);
        if(ImGui.button("保存动画层旋转姿态")) {
            try {
                if (context != null) {
                    ActorSavedPoseLibrary.add(model,ActorSavedPoseLibrary.capture(name.get(),context.asset().sample(context.assetTime(),AnimationLoopMode.ONCE),bones));
                }
                message="已保存到当前存档的“我的姿态”。";
            } catch(java.io.IOException | RuntimeException failure) {message=failure.getMessage();}
        }
        ImGui.endDisabled();ImGui.textWrapped("保存动画层中的旋转，不包含路径、自动步态、脚底接触和注视结果。");
        var runtime = com.director.actor.runtime.ActorRuntimeController.getInstance();
        boolean canSaveVisible = !blocked && !bones.isEmpty() && runtime.poseFeedback(model, id) != null;
        ImGui.beginDisabled(!canSaveVisible);
        if (ImGui.button(poseText("save_visible"))) {
            try {
                var visible = runtime.visiblePose(model, id);
                if (visible == null || !RigPoseComposer.sameLayout(rig, visible.rig())) {
                    message = poseText("visible_unavailable");
                } else {
                    ActorSavedPoseLibrary.add(model, ActorSavedPoseLibrary.capture(name.get(), visible, bones));
                    message = "已保存到当前存档的“我的姿态”。";
                }
            } catch (java.io.IOException | RuntimeException failure) { message = failure.getMessage(); }
        }
        ImGui.endDisabled();
        ImGui.textWrapped(poseText("visible_hint"));
        if (!blocked && !canSaveVisible && !bones.isEmpty()) ImGui.textDisabled(poseText("visible_unavailable"));
        if(editing!=null) {
            try {
                if(!ActorSavedPoseLibrary.current().contains(editing)) editing=null;
                else {
                    if(deleting) ImGui.textWrapped("删除姿态“"+editing.name()+"”？已写入的关键帧保留；删除可撤销。");
                    else ImGui.inputText("新名称",rename);
                    ImGui.beginDisabled(blocked);
                    if(ImGui.button(deleting?"确认删除":"保存名称")) {ActorSavedPoseLibrary.edit(model,editing,deleting?null:rename.get());editing=null;}
                    ImGui.endDisabled();ImGui.sameLine();if(ImGui.button("取消")) editing=null;
                }
            } catch(java.io.IOException | RuntimeException failure) {message=failure.getMessage();}
        }
    }

    private static String poseText(String key) {
        return net.minecraft.client.resource.language.I18n.translate("gui.director.actor.saved_pose." + key);
    }

    private boolean matches(String text) {return text.toLowerCase(Locale.ROOT).contains(query.get().strip().toLowerCase(Locale.ROOT));}
    private List<ActorPoseThumbnail.Segment> cached(Object key,java.util.function.Supplier<RigPose> source) {
        var existing=thumbnails.get(key);
        if(existing!=null) return existing;
        if(thumbnails.size()>=128) thumbnails.remove(thumbnails.keySet().iterator().next());
        var pose=source.get();pose.setLimbBendMode(appearance);
        var result=ActorPoseThumbnail.project(pose);thumbnails.put(key,result);return result;
    }
    private void apply(TimelineModel model,UUID id,Collection<String> bones,java.util.function.UnaryOperator<RigPose> operation) {
        try {
            boolean changed=ActorPoseGalleryEditing.apply(model,id,bones,operation);
            if(changed) ActorAnimationAuthoringSettings.getInstance().setEditTarget(ActorAnimationAuthoringSettings.EditTarget.ANIMATION);
            message=changed?"姿态已写入当前时间，可撤销；可继续选择骨骼微调。":"姿态未变化或目标不可编辑。";refresh(id);
        }
        catch(IllegalArgumentException failure) {message=failure.getMessage();}
    }
    private static void refresh(UUID id) {com.director.actor.runtime.ActorRuntimeController.getInstance().refreshPosePreview(id);}
    private static void thumbnail(java.util.function.Supplier<List<ActorPoseThumbnail.Segment>> source) {
        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY();
        if(ImGui.isRectVisible(52,52)) {
            var draw=ImGui.getWindowDrawList();int color=ImGui.getColorU32(imgui.flag.ImGuiCol.Text);
            for(var s:source.get()) draw.addLine(x+4+s.x1()*44,y+4+s.y1()*44,x+4+s.x2()*44,y+4+s.y2()*44,color,2);
        }
        ImGui.dummy(52,52);
    }
}
