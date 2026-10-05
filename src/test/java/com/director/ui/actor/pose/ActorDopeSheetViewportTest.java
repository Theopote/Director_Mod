package com.director.ui.actor.pose;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorDopeSheetViewportTest {
    private ActorDopeSheetViewport view(float y,float width,float pps,float start,List<?> rows) {
        return new ActorDopeSheetViewport(10,y,82,width,100,pps,start,30,rows);
    }
    @Test void unchangedMappingMatchesButScrollingResizeAndZoomCancel() {
        var rows=List.of("root:T","head:R"); var old=view(20,400,100,0,rows);
        assertTrue(old.matches(view(20,400,100,0,rows)));
        assertFalse(old.matches(view(21,400,100,0,rows)));
        assertFalse(old.matches(view(20,401,100,0,rows)));
        assertFalse(old.matches(view(20,400,101,0,rows)));
        assertFalse(old.matches(view(20,400,100,.1f,rows)));
    }
    @Test void rowOrderAndIdentityAreFrozenAndInvalidMappingRejected() {
        var rows=new ArrayList<>(List.of("root:T","head:R")); var old=view(20,400,100,0,rows);
        rows.clear(); assertEquals(2,old.rows().size());
        assertFalse(old.matches(view(20,400,100,0,List.of("head:R","root:T"))));
        assertFalse(old.matches(view(20,400,100,0,List.of("root:T","head:S"))));
        assertFalse(view(20,400,Float.NaN,0,List.of()).valid());
        assertFalse(old.matches(null)); assertFalse(view(20,0,100,0,List.of()).valid());
    }
}
