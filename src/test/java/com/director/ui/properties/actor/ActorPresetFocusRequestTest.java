package com.director.ui.properties.actor;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorPresetFocusRequestTest {
    @Test void focusIsOneShotAndExpiresInsteadOfReopeningAnOldInspector() {
        var time=new AtomicLong();var request=new ActorPresetFocusRequest<Object>(time::get);
        var a=new Object();request.request(a);
        assertTrue(request.consume(a));assertFalse(request.consume(a));
        request.request(a);time.set(500_000_001L);
        assertFalse(request.consume(a));
    }
    @Test void differentInspectorInvalidatesThePendingTarget() {
        var request=new ActorPresetFocusRequest<Object>(() -> 0);
        var a=new Object();request.request(a);
        assertFalse(request.consume(new Object()));assertFalse(request.consume(a));
        request.request(a);request.request(null);assertFalse(request.consume(a));
    }
}
