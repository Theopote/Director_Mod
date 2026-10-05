package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import java.util.*;
import static com.director.ui.actor.pose.ActorAnimationAuthoringService.*;

/** Shared, detached keyframe clipboard. Earliest selected asset key is the time origin. */
public final class ActorKeyframeClipboard {
    private static final ActorKeyframeClipboard INSTANCE=new ActorKeyframeClipboard();
    public static ActorKeyframeClipboard getInstance() { return INSTANCE; }
    private String rigId;
    private List<Paste> content=List.of();
    public int size() { return content.size(); }
    public boolean compatible(AnimationAsset asset) { return asset!=null && Objects.equals(rigId,asset.rigId()); }
    public boolean copy(AnimationAsset asset,Collection<Key> keys) {
        if(asset==null || keys==null || keys.isEmpty() || keys.stream().anyMatch(Objects::isNull)) return false;
        var distinct=new LinkedHashSet<>(keys);
        float origin=distinct.stream().map(Key::time).min(Float::compare).orElse(0f);
        if(!Float.isFinite(origin)) return false;
        var result=new ArrayList<Paste>();
        for(var key:distinct) {
            if(key.channel()==null || !Float.isFinite(key.time())) return false;
            var track=asset.findBoneTrack(key.boneId()).orElse(null);
            if(track==null) return false;
            ChannelKeyframe<?> frame=switch(key.channel()) {
                case TRANSLATION -> track.translation().findAt(key.time()).orElse(null);
                case ROTATION -> track.rotation().findAt(key.time()).orElse(null);
                case SCALE -> track.scale().findAt(key.time()).orElse(null);
            };
            if(frame==null) return false;
            result.add(new Paste(key.boneId(),key.channel(),key.time()-origin,frame.value(),frame.interpolation()));
        }
        content=List.copyOf(result); rigId=asset.rigId(); return true;
    }
    /** No clamping: a partial or distorted paste is rejected as a whole. */
    public List<Paste> at(AnimationAsset asset,float origin,float limit) {
        if(!compatible(asset) || !Float.isFinite(origin) || !Float.isFinite(limit) || origin<0) return List.of();
        var result=new ArrayList<Paste>();
        for(var key:content) {
            float time=origin+key.time();
            if(!Float.isFinite(time) || time<0 || time>Math.min(asset.duration(),limit)) return List.of();
            result.add(new Paste(key.boneId(),key.channel(),time,key.value(),key.interpolation()));
        }
        return List.copyOf(result);
    }
}
