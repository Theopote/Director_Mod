package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import java.util.*;

/** Detached whole-key clipboard. Group edits are all-or-nothing. */
public final class ActorRootKeyframeClipboard {
    private List<EntityClipKeyframe> snapshots = List.of();
    public boolean copy(EntityClip clip, EntityClipKeyframe key) {
        return key != null && copy(clip, List.of(key));
    }
    public boolean copy(EntityClip clip, Collection<EntityClipKeyframe> keys) {
        if (clip == null || keys == null || keys.isEmpty() || !clip.getKeyframes().containsAll(keys)) return false;
        var ordered = keys.stream().distinct().sorted(Comparator.comparingDouble(EntityClipKeyframe::getTime)).toList();
        if (ordered.stream().anyMatch(k -> !valid(k))) return false;
        snapshots = ordered.stream().map(EntityClipKeyframe::copy).toList();
        return true;
    }
    public boolean copyChannels(EntityClip clip,Collection<EntityClipKeyframe> keys,int mask) {
        if(!copy(clip,keys))return false;
        snapshots=snapshots.stream().filter(k -> (k.getChannels()&mask)!=0).map(k->{k.setChannels(k.getChannels()&mask);return k;}).toList();
        return !snapshots.isEmpty();
    }
    public EntityClipKeyframe paste(TimelineModel model, TimelineTrack owner, EntityClip clip) {
        var keys = pasteAll(model, owner, clip); return keys.isEmpty() ? null : keys.getFirst();
    }
    public List<EntityClipKeyframe> pasteAll(TimelineModel model, TimelineTrack owner, EntityClip clip) {
        if(snapshots.isEmpty() || !ActorRootKeyframeEditing.editable(model,owner,clip)) return List.of();
        int channels=snapshots.stream().mapToInt(EntityClipKeyframe::getChannels).reduce(0,(a,b)->a|b);
        if((channels&3)!=0 && ActorMovementSource.routeOwnsInterval(model,owner)) return List.of();
        if(clip instanceof EntityTransformClip && clip.getKeyframes().isEmpty()) return List.of();
        float offset=model.getCurrentTime()-snapshots.getFirst().getTime();
        var changes=new ArrayList<Replacement>();var result=new ArrayList<EntityClipKeyframe>();
        for(var snapshot:snapshots) {
            var incoming=snapshot.copy();incoming.setTime(snapshot.getTime()+offset);
            if(!valid(incoming) || incoming.getTime()<clip.getStartTime() || incoming.getTime()>clip.getEndTime()
                    || changes.stream().anyMatch(c->Math.abs(c.after().getTime()-incoming.getTime())<=1e-4f)) return List.of();
            var matches=clip.getKeyframes().stream().filter(k->Math.abs(k.getTime()-incoming.getTime())<=1e-4f).toList();
            if(matches.size()>1) return List.of();
            var existing=matches.isEmpty()?null:matches.getFirst();
            if(existing!=null && ((existing.getChannels()&incoming.getChannels())!=0
                    || existing.getInterpolation()!=incoming.getInterpolation())) return List.of();
            var after=existing==null?incoming:existing.copy();
            if(existing!=null) {
                if((incoming.getChannels()&1)!=0) after.setPosition(incoming.getPosition().x,incoming.getPosition().y,incoming.getPosition().z);
                if((incoming.getChannels()&2)!=0) after.setRotation(incoming.getRotation().x,incoming.getRotation().y,incoming.getRotation().z);
                if((incoming.getChannels()&4)!=0) after.setScale(incoming.getScale().x,incoming.getScale().y,incoming.getScale().z);
                after.setChannels(existing.getChannels()|incoming.getChannels());
            }
            var target=existing==null?incoming:existing;
            changes.add(new Replacement(target,existing==null?null:existing.copy(),after.copy()));result.add(target);
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Paste actor root channels",
                ()->replace(clip,changes,false),()->replace(clip,changes,true)));
        return List.copyOf(result);
    }
    private record Replacement(EntityClipKeyframe target,EntityClipKeyframe before,EntityClipKeyframe after) {}
    private static boolean same(EntityClipKeyframe a,EntityClipKeyframe b) {
        return a.getTime()==b.getTime() && a.getChannels()==b.getChannels() && a.getInterpolation()==b.getInterpolation()
                && a.getPosition().equals(b.getPosition()) && a.getRotation().equals(b.getRotation()) && a.getScale().equals(b.getScale());
    }
    private static void replace(EntityClip clip,List<Replacement> changes,boolean forward) {
        for(var change:changes) {
            var expected=forward?change.before():change.after();
            if(expected==null?clip.getKeyframes().stream().anyMatch(k->Math.abs(k.getTime()-change.after().getTime())<=1e-4f):
                    !clip.getKeyframes().contains(change.target()) || !same(change.target(),expected))
                throw new IllegalArgumentException("整体关键帧已变化，无法粘贴或撤销");
        }
        for(var change:changes) {
            var next=forward?change.after():change.before();var target=change.target();
            if(next==null) clip.removeKeyframe(target.getId());
            else {
                target.setChannels(next.getChannels());target.setInterpolation(next.getInterpolation());
                target.setPosition(next.getPosition().x,next.getPosition().y,next.getPosition().z);
                target.setRotation(next.getRotation().x,next.getRotation().y,next.getRotation().z);
                target.setScale(next.getScale().x,next.getScale().y,next.getScale().z);
                if(!clip.getKeyframes().contains(target)) clip.addKeyframe(target);
            }
        }
    }
    public static boolean delete(TimelineModel model, TimelineTrack owner, EntityClip clip, Collection<EntityClipKeyframe> keys) {
        if (!ActorRootKeyframeEditing.editable(model, owner, clip) || keys == null || keys.isEmpty()
                || !clip.getKeyframes().containsAll(keys)) return false;
        var removed = keys.stream().distinct().toList();
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Delete actor root keyframes",
                () -> removed.forEach(clip::addKeyframe), () -> removed.forEach(k -> clip.removeKeyframe(k.getId()))));
        return true;
    }
    private static boolean valid(EntityClipKeyframe key) {
        return Float.isFinite(key.getTime()) && key.getPosition().isFinite() && key.getRotation().isFinite()
                && key.getScale().isFinite() && key.getScale().x > 0 && key.getScale().y > 0 && key.getScale().z > 0;
    }
}
