package com.director.actor.prop;
import com.director.timeline.model.entity.*;
import com.director.actor.rig.skeleton.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ActorPropFlightTransformTest {
    ActorTrack track() {
        var t=new ActorTrack(); var a=new EntityActionClip("Attach",0,.1f); a.setActionType(EntityActionClip.ActionType.ATTACH_PROP);
        a.getParams().putAll(Map.of("propId","stone","slot","MAIN_HAND","propScale",2)); t.addClip(a);
        var b=new EntityActionClip("Throw",1,1.1f); b.setActionType(EntityActionClip.ActionType.THROW_PROP);
        b.getParams().putAll(Map.of("slot","MAIN_HAND","targetX",8,"targetY",4,"targetZ",0,"flightTime",2,"arcHeight",0)); t.addClip(b); return t;
    }
    @Test void flightFreezesLaunchRotationAndNonuniformScaleAtEventTime() {
        var launch=new Matrix4f().translate(2,4,0).rotateXYZ(.3f,.8f,.1f).scale(2,3,4);
        var flight=ActorPropTimelineSampler.sampleTransforms(track(),2,(at,slot,attachment)->{assertEquals(1,at); return launch;}).flights().getFirst();
        assertEquals(new net.minecraft.util.math.Vec3d(5,4,0),flight.position());
        var expected=new Matrix4f(launch).setTranslation(5,4,0); assertTrue(expected.equals(flight.transform(),.00001f));
        assertEquals(new Vector3f(2,4,0),launch.getTranslation(new Vector3f()));
    }
    @Test void snapshotsAndRuntimeMatricesDoNotAliasCaller() {
        var launch=new Matrix4f().rotateY(.8f).scale(2);
        var flight=ActorPropTimelineSampler.sampleTransforms(track(),1,(at,slot,attachment)->launch).flights().getFirst();
        var expected=flight.transform(); launch.identity(); flight.transform().identity(); assertEquals(expected,flight.transform());
        var prop=flight.attachment().create(flight.slot()); prop.setWorldMatrix(flight.transform()); var matrix=prop.getWorldTransform(); matrix.identity();
        assertEquals(expected,prop.getWorldTransform()); prop.updateSocketTransform(new RigPose(HumanoidRig.DEFINITION),new Matrix4f()); assertEquals(expected,prop.getWorldTransform());
    }
    @Test void rewindResamplesSameLaunchMatrixWithoutAccumulation() {
        var launch=new Matrix4f().translate(2,4,0).rotateY(.7f); var t=track();
        var a=ActorPropTimelineSampler.sampleTransforms(t,1.5f,(at,slot,attachment)->launch).flights().getFirst();
        ActorPropTimelineSampler.sampleTransforms(t,2.5f,(at,slot,attachment)->launch);
        var b=ActorPropTimelineSampler.sampleTransforms(t,1.5f,(at,slot,attachment)->launch).flights().getFirst(); assertEquals(a,b);
    }
    @Test void invalidLaunchMatrixDoesNotGenerateBrokenVisual() {
        var launch=new Matrix4f().setTranslation(Float.NaN,0,0);
        assertTrue(ActorPropTimelineSampler.sampleTransforms(track(),2,(at,slot,attachment)->launch).flights().isEmpty());
    }
    @Test void hostRootOffsetAndFullPoseSocketAreEquivalent() {
        var pose=new RigPose(HumanoidRig.DEFINITION); var offset=new Vector3f(1,2,3); var root=pose.localTransform(HumanoidRig.ROOT);
        pose.setLocalTransform(HumanoidRig.ROOT,new BoneTransform(offset,root.rotation(),root.scale()));
        var world=new Matrix4f().translate(4,5,6).rotateY(.7f).scale(2,3,4);
        var full=ActorPropSocketResolver.resolve(pose,world,ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        var shifted=new Matrix4f(world).setTranslation(world.transformPosition(new Vector3f(offset)));
        pose.setLocalTransform(HumanoidRig.ROOT,new BoneTransform(new Vector3f(),root.rotation(),root.scale()));
        var held=ActorPropSocketResolver.resolve(pose,shifted,ActorPropSlot.MAIN_HAND,new Vector3f(),new Vector3f()).orElseThrow();
        assertTrue(full.equals(held,.00001f));
    }
}
