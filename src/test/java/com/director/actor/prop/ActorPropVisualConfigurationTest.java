package com.director.actor.prop;
import com.director.timeline.model.entity.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ActorPropVisualConfigurationTest {
    Map<String,Object> params() { return new HashMap<>(Map.of("slot","MAIN_HAND","propId","stone")); }
    ActorPropTimelineSampler.Snapshot sample(Map<String,Object> params, boolean thrown) {
        var track=new ActorTrack(); var attach=new EntityActionClip("Attach",1,2);
        attach.setActionType(EntityActionClip.ActionType.ATTACH_PROP); attach.getParams().putAll(params); track.addClip(attach);
        if(thrown) { var c=new EntityActionClip("Throw",3,4); c.setActionType(EntityActionClip.ActionType.THROW_PROP);
            c.getParams().putAll(Map.of("slot","MAIN_HAND","targetX",3,"targetY",2,"targetZ",1,"flightTime",2)); track.addClip(c); }
        return ActorPropTimelineSampler.sample(track,3.5f,t->new net.minecraft.util.math.Vec3d(0,0,0));
    }
    @Test void missingFieldsPreserveLegacyDefaults() {
        var value=sample(params(),false).held().get(ActorPropSlot.MAIN_HAND); assertEquals(1,value.scale()); assertEquals("FIXED",value.itemPose());
    }
    @Test void scaleAndHandPoseAreInheritedByFlightAndRuntime() {
        var p=params(); p.put("propScale",2); p.put("itemPose","HAND"); var flight=sample(p,true).flights().getFirst();
        assertEquals(2,flight.attachment().scale()); assertEquals("HAND",flight.attachment().itemPose());
        var prop=flight.attachment().create(flight.slot()); assertEquals(2,prop.getVisualScale()); assertEquals("HAND",prop.getItemPose());
    }
    @Test void invalidVisualValuesRejectBeforeRemovingHeldState() {
        for(Object scale:List.of(0,-1,11,Float.NaN,"2")) { var p=params(); p.put("propScale",scale);
            assertThrows(IllegalArgumentException.class,()->PropEventParameters.validate(EntityActionClip.ActionType.ATTACH_PROP,p)); }
        var p=params(); p.put("itemPose","INVALID"); assertThrows(IllegalArgumentException.class,()->PropEventParameters.validate(EntityActionClip.ActionType.ATTACH_PROP,p));
        p.put("itemPose","FIXED"); p.put("propScale",.01f); assertDoesNotThrow(()->PropEventParameters.validate(EntityActionClip.ActionType.ATTACH_PROP,p));
    }
    @Test void scaledBlockRemainsCenteredAndSizeMultiplierDoesNotMoveSocket() {
        var m=ActorPropPreviewVisual.modelTransform(PropVisualType.BLOCK,new Matrix4f().translate(3,4,5),2);
        assertTrue(m.transformPosition(new Vector3f(.5f)).distance(new Vector3f(3,4,5))<.0001);
        assertEquals(new Vector3f(1),m.getScale(new Vector3f()));
    }
    @Test void sameTimeParameterEditReplacesRuntimeInstance() {
        var p=params(); var runtime=new ActorPropRuntime(); runtime.sync(sample(p,false)); var before=runtime.getProp(ActorPropSlot.MAIN_HAND);
        p.put("propScale",2); runtime.sync(sample(p,false)); var after=runtime.getProp(ActorPropSlot.MAIN_HAND);
        assertNotSame(before,after); assertEquals(2,after.getVisualScale()); runtime.sync(sample(p,false)); assertSame(after,runtime.getProp(ActorPropSlot.MAIN_HAND));
    }
}
