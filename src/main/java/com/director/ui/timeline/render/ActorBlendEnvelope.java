package com.director.ui.timeline.render;

import com.director.actor.rig.skeleton.PoseBlendSettings;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.entity.*;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import java.util.*;

/** A bounded, viewport-clipped view of the exact playback envelope; no timeline objects are created. */
public final class ActorBlendEnvelope {
    public record Point(float x,float weight,boolean transition) {}
    private ActorBlendEnvelope() {}
    public static PoseBlendSettings settings(TimelineClip clip) {
        if(!(clip instanceof EntityTimelineClip wrapper)) return null;
        var entity=wrapper.getEntityClip();
        String id; PoseBlendSettings settings;
        if(entity instanceof EntitySpawnClip spawn) { id=spawn.getBoneAnimationId();settings=spawn.getBlendSettings(); }
        else if(entity instanceof EntityAnimationClip animation) { id=animation.getAnimationId();settings=animation.getBlendSettings(); }
        else return null;
        return known(id)?settings:null;
    }
    public static PoseBlendSettings presetSettings(TimelineClip clip) {
        if(clip instanceof EntityTimelineClip wrapper && wrapper.getEntityClip() instanceof EntitySpawnClip spawn
                && spawn.getPreset()!=null && known(spawn.getPreset().animationId())) return spawn.getPreset().blend();
        return null;
    }
    private static boolean known(String id) {
        return id!=null && (AnimationAssetLibrary.getInstance().find(id).isPresent()
                || com.director.actor.animation.AnimationLibrary.getInstance().get(id).isPresent());
    }
    public static float configuredWeight(PoseBlendSettings settings,float localTime,float duration) {
        if(settings.channels()==0 || !settings.allBones() && settings.boneIds().isEmpty()) return 0;
        return settings.effectiveWeight(settings.envelopeAt(localTime,duration));
    }
    /** Includes exact transition boundaries even when a fade is shorter than a sample interval. */
    public static List<Point> points(PoseBlendSettings settings,float startX,float endX,float viewportLeft,float viewportRight,float duration) {
        if(!Float.isFinite(startX) || !Float.isFinite(endX) || !Float.isFinite(viewportLeft) || !Float.isFinite(viewportRight)
                || !Float.isFinite(duration) || duration<=0 || endX<=startX) return List.of();
        float left=Math.max(startX,viewportLeft),right=Math.min(endX,viewportRight);
        if(right<=left) return List.of();
        var xs=new TreeSet<Float>();xs.add(left);xs.add(right);
        int segments=Math.clamp((int)Math.ceil((right-left)/4),1,128);
        for(int i=1;i<segments;i++) xs.add(left+(right-left)*i/segments);
        double total=(double)settings.fadeIn()+settings.fadeOut();
        double scale=total>duration?duration/total:1;
        double width=(double)endX-startX;
        float inEnd=(float)(startX+width*settings.fadeIn()*scale/duration);
        float outStart=(float)(endX-width*settings.fadeOut()*scale/duration);
        if(inEnd>=left && inEnd<=right) xs.add(inEnd);
        if(outStart>=left && outStart<=right) xs.add(outStart);
        var result=new ArrayList<Point>();
        for(float x:xs) {
            float local=(float)(((double)x-startX)/width*duration);
            result.add(new Point(x,configuredWeight(settings,local,duration),
                    x>startX && x<endX && (settings.fadeIn()>0 && x==inEnd || settings.fadeOut()>0 && x==outStart)));
        }
        return List.copyOf(result);
    }
}
