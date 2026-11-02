package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.rig.RigProfileStyle;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.*;
import java.util.*;

/** Checked project edits shared by appearance inspectors. */
public final class ActorAppearanceEditing {
    private ActorAppearanceEditing() {}
    public record Style(float motion,float tempo,float lean,float arms,float legs) {
        public static Style capture(RigProfileStyle s) {return new Style(s.motionScale,s.tempo,s.leanBias,s.armExpressiveness,s.legExpressiveness);}
        void apply(RigProfileStyle s) {s.motionScale=motion;s.tempo=tempo;s.leanBias=lean;s.armExpressiveness=arms;s.legExpressiveness=legs;}
        void validate() {
            if(!range(motion,.5f,1.5f)||!range(tempo,.7f,1.3f)||!range(lean,.5f,1.5f)||!range(arms,.3f,1.5f)||!range(legs,.3f,1.5f))
                throw new IllegalArgumentException("风格数值超出范围");
        }
    }
    public record Binding(String rig,String model) {
        public static Binding capture(Actor a){return new Binding(a.getCustomRigId(),a.getCustomModelId());}
        void apply(Actor a){a.setCustomRigId(rig);a.setCustomModelId(model);}
        void validate() {
            if(rig!=null && com.director.actor.rig.skeleton.CustomRigLibrary.getInstance().find(rig).isEmpty())
                throw new IllegalArgumentException("骨架不存在");
            if(model!=null && com.director.actor.model.CustomModelLibrary.getInstance().find(model).filter(m->Objects.equals(m.rigId(),rig)).isEmpty())
                throw new IllegalArgumentException("外观模型不存在或与骨架不匹配");
        }
    }
    public static boolean editable(TimelineModel model) {
        return model!=null && !model.isPlaying() && !model.isScrubbing()
                && !com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().hasPendingDraft()
                && !com.director.ui.actor.pose.ActorBonePropertiesPanel.hasNumericDraft()
                && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging();
    }
    private static void require(TimelineModel model,Actor actor) {
        if(!editable(model)) throw new IllegalArgumentException("请先暂停并完成当前草稿");
        if(ActorRegistryManager.getInstance().getActor(actor.getActorId()).orElse(null)!=actor)
            throw new IllegalArgumentException("演员已替换或工程已切换");
    }
    private static boolean range(float v,float min,float max){return Float.isFinite(v)&&v>=min&&v<=max;}
    public static void style(TimelineModel model,Actor actor,Style before,Style after) {
        require(model,actor);after.validate();var style=actor.getRigProfile().getStyle();
        if(!before.equals(Style.capture(style))) throw new IllegalArgumentException("风格已变化，请重新编辑");
        if(before.equals(after)) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Edit actor style",
            ()->applyStyle(model,actor,style,after,before),()->applyStyle(model,actor,style,before,after)));
    }
    private static void applyStyle(TimelineModel model,Actor actor,RigProfileStyle style,Style expected,Style next) {
        require(model,actor);
        if(actor.getRigProfile().getStyle()!=style || !expected.equals(Style.capture(style))) throw new IllegalArgumentException("风格目标已变化");
        next.apply(style);model.invalidateActorEditContext();ActorRegistryManager.save();
    }
    public static void binding(TimelineModel model,Actor actor,Binding next) {
        require(model,actor);next.validate();var before=Binding.capture(actor);if(before.equals(next)) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Bind actor model",
                ()->applyBinding(model,actor,next,before),()->applyBinding(model,actor,before,next)));
    }
    private static void applyBinding(TimelineModel model,Actor actor,Binding expected,Binding next) {
        require(model,actor);if(!expected.equals(Binding.capture(actor))) throw new IllegalArgumentException("模型绑定已变化");
        next.apply(actor);model.invalidateActorEditContext();
        com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
        com.director.actor.runtime.ActorRuntimeController.getInstance().refreshPosePreview(actor.getActorId());ActorRegistryManager.save();
    }
}
