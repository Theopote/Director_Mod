package com.director.ui.actor;

import com.director.actor.*;
import com.director.actor.rig.skeleton.LimbBendMode;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.*;
import java.util.*;

/** Undoable project properties, independent of ImGui widgets. */
public final class ActorProjectEditing {
    private ActorProjectEditing() {}
    public record GroupState(String name,ActorGroup.FormationType formation,float spacing,
                             ActorGroup.DelayPolicy delay,float step,List<UUID> members) {
        public GroupState {
            if(name==null || name.isBlank() || !Float.isFinite(spacing) || spacing<.1f || !Float.isFinite(step) || step<0)
                throw new IllegalArgumentException("分组参数无效");
            name=name.strip();Objects.requireNonNull(formation);Objects.requireNonNull(delay);members=List.copyOf(members);
        }
        public static GroupState capture(ActorGroup g) {return new GroupState(g.getName(),g.getFormationType(),g.getFormationSpacing(),g.getDelayPolicy(),g.getDelayStepSeconds(),g.getActorIds());}
        public GroupState withName(String v){return new GroupState(v,formation,spacing,delay,step,members);}
        public GroupState withFormation(ActorGroup.FormationType v){return new GroupState(name,v,spacing,delay,step,members);}
        public GroupState withSpacing(float v){return new GroupState(name,formation,v,delay,step,members);}
        public GroupState withDelay(ActorGroup.DelayPolicy v){return new GroupState(name,formation,spacing,v,step,members);}
        public GroupState withStep(float v){return new GroupState(name,formation,spacing,delay,v,members);}
        public GroupState withMembers(List<UUID> v){return new GroupState(name,formation,spacing,delay,step,v);}
        private void apply(ActorGroup g) {
            g.setName(name);g.setFormationType(formation);g.setFormationSpacing(spacing);g.setDelayPolicy(delay);g.setDelayStepSeconds(step);
            g.clearActors();members.forEach(g::addActor);
        }
    }
    public record Appearance(Actor.PlayerModel model,LimbBendMode bend,String skin) {
        public Appearance {Objects.requireNonNull(model);Objects.requireNonNull(bend);}
        public static Appearance capture(Actor a){return new Appearance(a.getPlayerModel(),a.getLimbBendMode(),a.getSkinFileName());}
        public Appearance withModel(Actor.PlayerModel v){return new Appearance(v,bend,skin);}
        public Appearance withBend(LimbBendMode v){return new Appearance(model,v,skin);}
        public Appearance withSkin(String v){return new Appearance(model,bend,v);}
        private void apply(Actor a){a.setPlayerModel(model);a.setLimbBendMode(bend);a.setSkinFileName(skin);}
    }
    public static void updateGroup(TimelineModel model,ActorRegistry registry,ActorGroup group,GroupState before,GroupState after) {
        editing(model,registry);requireGroup(registry,group);
        if(!before.equals(GroupState.capture(group))) throw new IllegalArgumentException("分组已变化，请重新编辑");
        if(before.equals(after)) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Edit actor group",
                ()->applyGroup(registry,group,after,before),()->applyGroup(registry,group,before,after)));
    }
    private static void applyGroup(ActorRegistry registry,ActorGroup group,GroupState expected,GroupState next) {
        requireGroup(registry,group);
        if(!expected.equals(GroupState.capture(group))) throw new IllegalArgumentException("分组状态已变化");
        next.apply(group);ActorRegistryManager.save();
    }
    public static void setMembership(TimelineModel model,ActorRegistry registry,ActorGroup group,UUID actorId,boolean member) {
        var before=GroupState.capture(group);var members=new ArrayList<>(before.members());
        if(member) {if(!registry.contains(actorId)) throw new IllegalArgumentException("演员不存在");if(!members.contains(actorId)) members.add(actorId);}
        else members.remove(actorId);
        updateGroup(model,registry,group,before,before.withMembers(members));
    }
    public static ActorGroup createGroup(TimelineModel model,ActorRegistry registry,String name) {
        editing(model,registry);var group=new ActorGroup(name);
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Create actor group",()->{
            requireGroup(registry,group);registry.removeGroup(group.getGroupId());ActorRegistryManager.save();
        },()->{
            current(registry);if(registry.getGroup(group.getGroupId()).isPresent()) throw new IllegalArgumentException("分组已存在");
            registry.registerGroup(group);ActorRegistryManager.save();
        }));return group;
    }
    public static void deleteGroup(TimelineModel model,ActorRegistry registry,ActorGroup group,Runnable clearInspection) {
        editing(model,registry);requireGroup(registry,group);
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Delete actor group",()->{
            current(registry);if(registry.getGroup(group.getGroupId()).isPresent()) throw new IllegalArgumentException("分组 ID 已占用");
            registry.registerGroup(group);ActorRegistryManager.save();
        },()->{requireGroup(registry,group);registry.removeGroup(group.getGroupId());clearInspection.run();ActorRegistryManager.save();}));
    }
    public static void appearance(TimelineModel model,ActorRegistry registry,Actor actor,Appearance after,Runnable refresh) {
        editing(model,registry);var before=Appearance.capture(actor);if(before.equals(after)) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Edit actor appearance",
                ()->applyAppearance(model,registry,actor,after,before,refresh),()->applyAppearance(model,registry,actor,before,after,refresh)));
    }
    private static void applyAppearance(TimelineModel model,ActorRegistry registry,Actor actor,Appearance expected,Appearance value,Runnable refresh) {
        current(registry);if(registry.getActor(actor.getActorId()).orElse(null)!=actor || !expected.equals(Appearance.capture(actor)))
            throw new IllegalArgumentException("演员外观已变化，请重新选择");
        value.apply(actor);model.invalidateActorEditContext();refresh.run();ActorRegistryManager.save();
    }
    private static void requireGroup(ActorRegistry registry,ActorGroup group) {
        current(registry);if(registry.getGroup(group.getGroupId()).orElse(null)!=group) throw new IllegalArgumentException("分组已删除或替换");
    }
    private static void current(ActorRegistry registry){if(ActorRegistryManager.getInstance()!=registry) throw new IllegalArgumentException("工程已切换");}
    private static void editing(TimelineModel model,ActorRegistry registry){current(registry);if(model==null || model.isPlaying() || model.isScrubbing()) throw new IllegalArgumentException("请先暂停播放或拖动再编辑");}
}
