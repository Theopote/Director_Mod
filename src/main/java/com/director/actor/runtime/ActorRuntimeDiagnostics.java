package com.director.actor.runtime;
import java.util.*;

/** Ephemeral, frame-scoped feedback; never persisted or added to undo history. */
public final class ActorRuntimeDiagnostics {
    public record Issue(UUID actor,Object track,float sampleTime,String stage,boolean hidden,String message,ActorDiagnosticReason reason) {}
    private final List<Issue> issues=new ArrayList<>();
    private float frameTime=Float.NaN;
    public void begin(float time) { issues.clear(); frameTime=time; }
    public void clear() { issues.clear(); frameTime=Float.NaN; }
    public void report(UUID actor,Object track,float time,String stage,boolean hidden,String message) {
        if(actor==null || track==null || !Float.isFinite(time) || message==null || message.isBlank()) return;
        var issue=new Issue(actor,track,time,stage,hidden,message,ActorDiagnosticReason.fromLegacy(message));
        if(!issues.contains(issue) && issues.stream().filter(i->actor.equals(i.actor())).count()<8) issues.add(issue);
    }
    public void reportReason(UUID actor,Object track,float time,String stage,boolean hidden,ActorDiagnosticReason reason) {
        if(reason!=null) report(actor,track,time,stage,hidden,reason.legacyMessage());
    }
    public List<Issue> current(UUID actor,float time,Collection<?> tracks) {
        if(actor==null || !Float.isFinite(time) || Float.compare(time,frameTime)!=0 || tracks==null) return List.of();
        return issues.stream().filter(i->actor.equals(i.actor()) && tracks.stream().anyMatch(t->t==i.track())).toList();
    }
}
