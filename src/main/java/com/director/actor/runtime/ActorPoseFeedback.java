package com.director.actor.runtime;

import java.util.*;

/** Immutable feedback for exactly one timeline edit context and sample time. */
public record ActorPoseFeedback(float time,long revision,Map<ActorPoseSources.Channel,String> sources,
                                List<String> diagnostics,String gaze,boolean gazeApplied,float gazeWeight) {
    public ActorPoseFeedback {
        sources=Map.copyOf(sources); diagnostics=List.copyOf(diagnostics); Objects.requireNonNull(gaze);
    }
    public boolean matches(float currentTime,long currentRevision) {
        return Float.compare(time,currentTime)==0 && revision==currentRevision;
    }
    public ActorPoseFeedback withGaze(String message,boolean applied,float weight) {
        return new ActorPoseFeedback(time,revision,sources,diagnostics,message,applied,weight);
    }
    public String source(String bone,int channel) {
        String lower=sources.getOrDefault(new ActorPoseSources.Channel(bone,channel),"基础动作／模型默认姿态");
        if(gazeApplied && channel==2 && "head".equals(bone))
            return gazeWeight>=1?"注视约束":String.format(Locale.ROOT,"注视约束（混合 %.0f%%）＋%s",gazeWeight*100,lower);
        return lower;
    }
}
