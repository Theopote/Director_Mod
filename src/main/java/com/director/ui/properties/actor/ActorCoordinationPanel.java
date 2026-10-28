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
    private static final List<ActorPoseStage.Kind> ORDER=List.of(ActorPoseStage.Kind.CONTACT,
            ActorPoseStage.Kind.MANUAL,ActorPoseStage.Kind.QUICK,ActorPoseStage.Kind.PRESET);
    private static String tr(String key,Object... args) {
        return com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.coordination."+key,args);
    }
    private ActorCoordinationPanel() {}
    public static Navigation render(EntitySpawnClip source) {
        if(source==null) return null;
        if(!ImGui.collapsingHeader(tr("title")+"##actorCoordination",ImGuiTreeNodeFlags.DefaultOpen)) return null;
        var model=DirectorServices.get().containerTimeline().getTimelineModel();
        var wrapper=source.getTimelineClip();var owner=wrapper==null?null:wrapper.getParentTrack();
        var payload=owner==null?null:owner.getActorTrackPayload();
        if(model==null || payload==null || payload.getActorRef()==null) {ImGui.textDisabled(tr("no_actor"));return null;}
        float time=model.getCurrentTime();
        ImGui.text(tr("clip_range",source.getName(),source.getStartTime(),source.getEndTime(),time));
        if(time<source.getStartTime() || time>source.getEndTime()) {
            ImGui.textWrapped(tr("outside_clip"));return null;
        }
        EntitySpawnClip active=null;
        for(var clip:payload.findClipsAt(time)) if(clip instanceof EntitySpawnClip spawn && !spawn.isMuted()) active=spawn;
        if(active!=source) {
            ImGui.textWrapped(tr("inactive_clip"));return null;
        }
        var feedback=ActorRuntimeController.getInstance().poseFeedback(model,payload.getActorRef().getActorId());
        if(feedback==null || feedback.coordination()==null) {
            ImGui.textWrapped(tr("no_snapshot"));
            if(ImGui.button(tr("view_preset")+"##coordinationPresetMissing")) return new Navigation(Focus.PRESET,null);
            return null;
        }
        var coordination=feedback.coordination();Navigation requested=null;
        ImGui.textDisabled(tr("layer_order"));
        ImGui.separator();
        ImGui.textWrapped(tr("gaze",feedback.gaze()));
        var gaze=lookAtAt(owner,time);
        if(gaze!=null && ImGui.smallButton(tr("view_gaze")+"##coordinationGaze")) requested=new Navigation(Focus.GAZE,gaze.getId());
        for(var kind:ORDER) {
            var stage=coordination.stages().stream().filter(s->s.kind()==kind).findFirst().orElse(null);
            if(stage==null) continue;
            ImGui.pushID(kind.name());ImGui.separator();
            ImGui.textWrapped(label(kind)+"："+status(stage));
            if(kind==ActorPoseStage.Kind.PRESET && coordination.presetId()!=null) {
                String id=coordination.presetId();
                var asset=com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().find(id).orElse(null);
                String name=asset==null?com.director.actor.animation.AnimationLibrary.getInstance().get(id).map(a->a.getName()).orElse(id):asset.name();
                ImGui.textWrapped(name+" · "+tr(coordination.distanceClock()?"distance_clock":"local_clock"));
            }
            ImGui.textWrapped(stage.detailCode()==ActorPoseStage.Detail.DIAGNOSTIC?stage.detail():
                    tr("detail."+stage.detailCode().name().toLowerCase(Locale.ROOT)));
            if(kind!=ActorPoseStage.Kind.CONTACT && stage.state()!=ActorPoseStage.State.UNSUPPORTED) {
                if(ImGui.smallButton(tr("view_settings"))) requested=new Navigation(kind==ActorPoseStage.Kind.PRESET?Focus.PRESET:kind==ActorPoseStage.Kind.QUICK?Focus.QUICK:Focus.MANUAL,null);
            }
            ImGui.popID();
        }
        ImGui.separator();var motion=coordination.motion();
        ImGui.textWrapped(tr("motion",com.director.i18n.ActorPropertyAdapterI18n.actionSemanticLabel(motion.action()),motion.speed()));
        if(motion.routeId()!=null) {
            ImGui.textWrapped(tr("route",motion.routeName(),motion.duration(),com.director.i18n.ActorPropertyAdapterI18n.routeLoopLabel(motion.loop())));
            if(ImGui.smallButton(tr("view_route")+"##coordinationRoute")) requested=new Navigation(Focus.ROUTE,motion.routeId());
        } else ImGui.textWrapped(tr("no_route"));
        if(motion.relation()) ImGui.textWrapped(tr("relation"));
        for(String issue:feedback.diagnostics()) ImGui.textWrapped(tr("diagnostic",issue));
        return requested;
    }
    private static String label(ActorPoseStage.Kind kind) {return tr("kind."+kind.name().toLowerCase(Locale.ROOT));}
    private static String status(ActorPoseStage stage) {
        if(stage.state()==ActorPoseStage.State.APPLIED) {
            long bones=stage.channels().stream().map(ActorPoseSources.Channel::bone).distinct().count();
            return tr("written",bones,stage.channels().size())+
                    (Float.isFinite(stage.weight())?tr("weight",stage.weight()*100):"");
        }
        return tr("state."+stage.state().name().toLowerCase(Locale.ROOT));
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
