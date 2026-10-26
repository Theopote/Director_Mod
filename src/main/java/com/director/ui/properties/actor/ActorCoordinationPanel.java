package com.director.ui.properties.actor;

import com.director.actor.runtime.*;
import com.director.core.DirectorServices;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import java.util.*;

/** Read-only view of the displayed frame. Navigation is deferred until inspector edits commit. */
public final class ActorCoordinationPanel {
    public enum Focus { PRESET, QUICK, MANUAL, ROUTE, GAZE }
    public record Navigation(Focus focus,UUID clipId) {}
    private ActorCoordinationPanel() {}
    public static Navigation render(EntitySpawnClip source) {
        if(source==null) return null;
        if(!ImGui.collapsingHeader("动作协调 · 当前播放头##actorCoordination",ImGuiTreeNodeFlags.DefaultOpen)) return null;
        var model=DirectorServices.get().containerTimeline().getTimelineModel();
        var wrapper=source.getTimelineClip();var owner=wrapper==null?null:wrapper.getParentTrack();
        var payload=owner==null?null:owner.getActorTrackPayload();
        if(model==null || payload==null || payload.getActorRef()==null) {ImGui.textDisabled("当前没有有效的演员绑定");return null;}
        float time=model.getCurrentTime();
        ImGui.text(String.format(Locale.ROOT,"片段：%s · %.2fs",source.getName(),time));
        if(time<source.getStartTime() || time>source.getEndTime()) {
            ImGui.textWrapped("当前检查的演员片段不在播放头区间内；此处不显示其他片段的求解结果。");return null;
        }
        EntitySpawnClip active=null;
        for(var clip:payload.findClipsAt(time)) if(clip instanceof EntitySpawnClip spawn && !spawn.isMuted()) active=spawn;
        if(active!=source) {
            ImGui.textWrapped("当前检查的片段没有驱动本帧；请检查静音状态或选择正在生效的演员片段。");return null;
        }
        var feedback=ActorRuntimeController.getInstance().poseFeedback(model,payload.getActorRef().getActorId());
        if(feedback==null || feedback.coordination()==null) {
            ImGui.textWrapped("本帧暂无有效求解快照；演员可能未显示、模型未提供骨骼求解结果，或正在等待预览刷新。");
            if(ImGui.button("查看此片段预设设置##coordinationPresetMissing")) return new Navigation(Focus.PRESET,null);
            return null;
        }
        var coordination=feedback.coordination();Navigation requested=null;
        ImGui.textDisabled("从上到下优先级递减；写入通道不等于最终独占，权重不相加为 100%。");
        ImGui.separator();
        ImGui.textWrapped("注视约束："+feedback.gaze());
        var gaze=lookAtAt(owner,time);
        if(gaze!=null && ImGui.smallButton("查看注视片段##coordinationGaze")) requested=new Navigation(Focus.GAZE,gaze.getId());
        var order=List.of(ActorPoseStage.Kind.CONTACT,ActorPoseStage.Kind.MANUAL,ActorPoseStage.Kind.QUICK,ActorPoseStage.Kind.PRESET);
        for(var kind:order) {
            var stage=coordination.stages().stream().filter(s->s.kind()==kind).findFirst().orElse(null);
            if(stage==null) continue;
            ImGui.pushID(kind.name());ImGui.separator();
            ImGui.textWrapped(label(kind)+"："+status(stage));
            if(kind==ActorPoseStage.Kind.PRESET && coordination.presetId()!=null) {
                String id=coordination.presetId();
                var asset=com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().find(id).orElse(null);
                String name=asset==null?com.director.actor.animation.AnimationLibrary.getInstance().get(id).map(a->a.getName()).orElse(id):asset.name();
                ImGui.textWrapped(name+" · "+(coordination.distanceClock()?"路径距离时钟":"片段本地时钟"));
            }
            ImGui.textWrapped(stage.detail());
            if(kind!=ActorPoseStage.Kind.CONTACT && stage.state()!=ActorPoseStage.State.UNSUPPORTED) {
                if(ImGui.smallButton("查看设置")) requested=new Navigation(kind==ActorPoseStage.Kind.PRESET?Focus.PRESET:kind==ActorPoseStage.Kind.QUICK?Focus.QUICK:Focus.MANUAL,null);
            }
            ImGui.popID();
        }
        ImGui.separator();var motion=coordination.motion();
        ImGui.textWrapped("基础动作："+motion.action()+String.format(Locale.ROOT," · 速度 %.2f 格/秒",motion.speed()));
        if(motion.routeId()!=null) {
            ImGui.textWrapped("路径："+motion.routeName()+String.format(Locale.ROOT," · %.2fs · %s",motion.duration(),motion.loop()));
            if(ImGui.smallButton("查看路径片段##coordinationRoute")) requested=new Navigation(Focus.ROUTE,motion.routeId());
        } else ImGui.textWrapped("当前未使用路径；动作按相应的片段或程序化时钟播放。");
        if(motion.relation()) ImGui.textWrapped("关系行为还会调整整体位置或朝向。");
        for(String issue:feedback.diagnostics()) ImGui.textWrapped("诊断："+issue);
        return requested;
    }
    private static String label(ActorPoseStage.Kind kind) {return switch(kind) {
        case PRESET->"动作预设";case QUICK->"快速姿态";case MANUAL->"手工骨骼关键帧";case CONTACT->"脚底接触";
    };}
    private static String status(ActorPoseStage stage) {
        if(stage.state()==ActorPoseStage.State.APPLIED) {
            long bones=stage.channels().stream().map(ActorPoseSources.Channel::bone).distinct().count();
            return String.format(Locale.ROOT,"已写入 %d 个部位 / %d 条通道",bones,stage.channels().size())+
                    (Float.isFinite(stage.weight())?String.format(Locale.ROOT," · 有效混合权重 %.0f%%",stage.weight()*100):"");
        }
        return switch(stage.state()) {
            case ABSENT->"未启用";case DISABLED->"权重为零";case EMPTY->"无声明通道";case REJECTED->"图层被拒绝";
            case UNCHANGED->"本帧未修正";case UNSUPPORTED->"不支持";case APPLIED->"已写入";
        };
    }
    private static EntityLookAtClip lookAtAt(TimelineTrack owner,float time) {
        EntityLookAtClip best=null;
        for(var clip:owner.getActorTrackPayload().getClips()) if(clip instanceof EntityLookAtClip look && !look.isMuted()
                && time>=look.getStartTime() && time<=look.getEndTime() && (best==null || look.getStrength()>=best.getStrength())) best=look;
        return best;
    }
    public static void navigate(EntitySpawnClip source,Navigation request) {
        if(request==null || source.getTimelineClip()==null) return;
        var model=DirectorServices.get().containerTimeline().getTimelineModel();var owner=source.getTimelineClip().getParentTrack();
        if(model==null || owner==null || !model.getAllTracks().contains(owner) || owner.getActorTrackPayload()==null
                || !owner.getActorTrackPayload().getClips().contains(source)) return;
        if(request.focus()==Focus.PRESET) {ActorSpawnPresetPropertyUI.requestFocus(source);return;}
        if(request.focus()==Focus.ROUTE || request.focus()==Focus.GAZE) {
            var target=owner.getActorTrackPayload().getClips().stream().filter(c->c.getId().equals(request.clipId())).findFirst().orElse(null);
            var wrapper=target==null?null:target.getTimelineClip();
            if(wrapper==null || wrapper.getParentTrack()!=owner || !owner.getClips().contains(wrapper)) return;
            model.selectItem(wrapper,false);DirectorServices.get().uiSelection().selectClip(target);
            com.director.ui.properties.PropertyContextService.get().onTimelineSelectionChanged(wrapper);return;
        }
        var ref=owner.getActorTrackPayload().getActorRef();if(ref==null) return;
        com.director.ui.actor.selection.ActorSelectionHub.selectActor(ref.getActorId());
        com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().setEditTarget(request.focus()==Focus.QUICK?
                com.director.ui.actor.pose.ActorAnimationAuthoringSettings.EditTarget.QUICK_POSE:com.director.ui.actor.pose.ActorAnimationAuthoringSettings.EditTarget.ANIMATION);
        com.director.ui.tools.ToolRegistry.get().activate(com.director.ui.tools.ToolRegistry.ACTOR_POSE_TOOL_ID);
    }
}
