package com.director.actor.runtime;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ActorRuntimeDiagnosticsTest {
    ActorRuntimeDiagnostics store=new ActorRuntimeDiagnostics(); UUID actor=UUID.randomUUID(); Object track=new Object();
    @Test void messagesAreActorAndTrackScoped() {
        store.begin(2); store.report(actor,track,2,"root",false,"Blocked");
        assertEquals(1,store.current(actor,2,List.of(track)).size());
        assertTrue(store.current(UUID.randomUUID(),2,List.of(track)).isEmpty()); assertTrue(store.current(actor,2,List.of(new Object())).isEmpty());
    }
    @Test void timeChangesAndNextCleanFrameDoNotShowOldWarnings() {
        store.begin(2); store.report(actor,track,2,"root",false,"Blocked"); assertTrue(store.current(actor,3,List.of(track)).isEmpty());
        store.begin(2); assertTrue(store.current(actor,2,List.of(track)).isEmpty());
    }
    @Test void historicalEventKeepsSampleTimeAndDoesNotBecomeCurrentTimeIssue() {
        store.begin(5); store.report(actor,track,1,"root",true,"Event origin unavailable");
        assertEquals(1,store.current(actor,5,List.of(track)).getFirst().sampleTime()); assertTrue(store.current(actor,1,List.of(track)).isEmpty());
    }
    @Test void duplicatesAndUnboundedSpamAreLimited() {
        store.begin(2); store.report(actor,track,2,"root",false,"Blocked"); store.report(actor,track,2,"root",false,"Blocked"); assertEquals(1,store.current(actor,2,List.of(track)).size());
        for(int i=0;i<20;i++) store.report(actor,track,i,"route",false,"Issue "+i);
        assertEquals(8,store.current(actor,2,List.of(track)).size());
    }
    @Test void clearAndInvalidReportsAreSafe() {
        store.begin(2); store.report(actor,track,Float.NaN,"root",false,"Invalid"); store.report(actor,track,2,"root",false,""); assertTrue(store.current(actor,2,List.of(track)).isEmpty());
        store.report(actor,track,2,"root",true,"Hidden"); store.clear(); assertTrue(store.current(actor,2,List.of(track)).isEmpty());
    }
    @Test void returnedListCannotMutateStore() {
        store.begin(2); store.report(actor,track,2,"root",false,"Blocked"); assertThrows(UnsupportedOperationException.class,()->store.current(actor,2,List.of(track)).clear());
    }
}
