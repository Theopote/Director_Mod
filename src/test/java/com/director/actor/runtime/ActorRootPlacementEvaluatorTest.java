package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import com.director.actor.path.GroundRoute;
import com.director.actor.prop.*;
import net.minecraft.util.math.Vec3d;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;

class ActorRootPlacementEvaluatorTest {
    private final GroundRoute.Terrain flat=new GroundRoute.Terrain() {
        public OptionalDouble floor(double x,double z,double y,double up,double down){return OptionalDouble.of(0);}
        public boolean clear(Vec3d feet){return true;}
    };
    private RigPose pose() {
        var p=new RigPose(ArticulatedPlayerRig.DEFINITION);
        p.setLocalTransform(HumanoidRig.ROOT,new BoneTransform(new Vector3f(1,2,3),new Quaternionf().rotateY(.3f),new Vector3f(1.2f)));
        return p;
    }
    @Test void exactRouteKeepsThreeDimensionalOffsetAndConsumesOnlyTranslation() {
        var p=pose();var base=new Vec3d(10,20,30);var scale=new Vector3f(2,3,4);
        var result=ActorRootPlacementEvaluator.evaluate(p,base,90,scale,ActorRootPlacementEvaluator.Mode.EXACT_ROUTE,null);
        assertTrue(result.position().distanceTo(new Vec3d(-2,26,32))<1e-5);
        var root=result.pose().localTransform(HumanoidRig.ROOT);
        assertEquals(new Vector3f(),root.translation());assertEquals(p.localTransform(HumanoidRig.ROOT).scale(),root.scale());
        assertTrue(root.rotation().equals(p.localTransform(HumanoidRig.ROOT).rotation(),1e-6f));
        assertEquals(new Vector3f(1,2,3),p.localTransform(HumanoidRig.ROOT).translation());
        assertFalse(ActorRootPlacementEvaluator.needsTerrain(p,ActorRootPlacementEvaluator.Mode.EXACT_ROUTE));
    }
    @Test void groundRuleProjectsVerticalOffsetAndReportsMissingTerrain() {
        var p=pose();assertTrue(ActorRootPlacementEvaluator.needsTerrain(p,ActorRootPlacementEvaluator.Mode.GROUND_CONSTRAINED));
        var result=ActorRootPlacementEvaluator.evaluate(p,Vec3d.ZERO,0,new Vector3f(1),ActorRootPlacementEvaluator.Mode.GROUND_CONSTRAINED,flat);
        assertEquals(new Vec3d(1,0,3),result.position());assertFalse(result.warning().isEmpty());
        assertEquals(new Vector3f(),result.pose().localTransform(HumanoidRig.ROOT).translation());
        var missing=ActorRootPlacementEvaluator.evaluate(p,Vec3d.ZERO,0,new Vector3f(1),ActorRootPlacementEvaluator.Mode.GROUND_CONSTRAINED,null);
        assertNull(missing.position());assertFalse(missing.warning().isEmpty());
        assertEquals(p.localTransform(HumanoidRig.ROOT).translation(),missing.pose().localTransform(HumanoidRig.ROOT).translation());
    }
    @Test void repeatedInputAndAlreadyConsumedOutputNeverDoubleApplyOffset() {
        var p=pose();var base=new Vec3d(4,5,6);var mode=ActorRootPlacementEvaluator.Mode.EXACT_ROUTE;
        var first=ActorRootPlacementEvaluator.evaluate(p,base,25,new Vector3f(1),mode,null);
        var repeat=ActorRootPlacementEvaluator.evaluate(p,base,25,new Vector3f(1),mode,null);
        var consumed=ActorRootPlacementEvaluator.evaluate(first.pose(),first.position(),25,new Vector3f(1),mode,null);
        assertEquals(first.position(),repeat.position());assertEquals(first.position(),consumed.position());
        var a=ActorWorldPoseEvaluator.resolve(first.pose(),new Vector3f((float)first.position().x,(float)first.position().y,(float)first.position().z),25,new Vector3f(1),true);
        var b=ActorWorldPoseEvaluator.resolve(consumed.pose(),new Vector3f((float)consumed.position().x,(float)consumed.position().y,(float)consumed.position().z),25,new Vector3f(1),true);
        for(var slot:ActorPropSlot.values()) assertTrue(ActorPropSocketResolver.resolve(a,true,slot,new Vector3f(),new Vector3f()).orElseThrow()
                .equals(ActorPropSocketResolver.resolve(b,true,slot,new Vector3f(),new Vector3f()).orElseThrow(),1e-6f));
    }
    @Test void noOffsetNeedsNoTerrainAndInvalidWorldInputsFailWithoutChangingPose() {
        var p=new RigPose(ArticulatedPlayerRig.DEFINITION);var mode=ActorRootPlacementEvaluator.Mode.GROUND_CONSTRAINED;
        assertFalse(ActorRootPlacementEvaluator.needsTerrain(p,mode));
        assertEquals(new Vec3d(1,2,3),ActorRootPlacementEvaluator.evaluate(p,new Vec3d(1,2,3),0,new Vector3f(1),mode,null).position());
        assertNull(ActorRootPlacementEvaluator.evaluate(p,new Vec3d(Double.NaN,0,0),0,new Vector3f(1),mode,null).position());
        assertNull(ActorRootPlacementEvaluator.evaluate(p,Vec3d.ZERO,0,new Vector3f(0),mode,null).position());
        assertNull(ActorRootPlacementEvaluator.evaluate(p,Vec3d.ZERO,Float.NaN,new Vector3f(1),mode,null).position());
    }
}
