package com.director.ui.actor.pose;
import com.director.actor.rig.skeleton.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorSavedPoseLibraryTest {
    @TempDir Path directory;
    @Test void savedPoseRoundTripsAndAppliesOnlyCapturedBones() throws Exception {
        var source = new RigPose(HumanoidRig.DEFINITION);
        var q = new Quaternionf().rotateX(.8f);
        source.setLocalTransform(HumanoidRig.RIGHT_ARM,source.localTransform(HumanoidRig.RIGHT_ARM).withRotation(q));
        var saved = ActorSavedPoseLibrary.capture("举手",source,List.of(HumanoidRig.RIGHT_ARM));
        Path file=directory.resolve("poses.json");
        ActorSavedPoseLibrary.save(file,List.of(saved));
        var loaded=ActorSavedPoseLibrary.read(file).getFirst();
        assertEquals("举手",loaded.name());
        var target=new RigPose(HumanoidRig.DEFINITION);
        target.setLocalTransform(HumanoidRig.RIGHT_ARM,new BoneTransform(new Vector3f(1,2,3),new Quaternionf(),new Vector3f(2)));
        var applied=loaded.apply(target,List.of(HumanoidRig.RIGHT_ARM,HumanoidRig.LEFT_ARM));
        assertTrue(applied.localTransform(HumanoidRig.RIGHT_ARM).rotation().equals(q,1e-5f));
        assertEquals(new Vector3f(2),applied.localTransform(HumanoidRig.RIGHT_ARM).scale());
        assertEquals(new Vector3f(1,2,3),applied.localTransform(HumanoidRig.RIGHT_ARM).translation());
        assertTrue(applied.localTransform(HumanoidRig.LEFT_ARM).rotation().equals(new Quaternionf(),1e-5f));
    }
    @Test void rejectsEmptyNameAndUneditableRoot() {
        var pose=new RigPose(HumanoidRig.DEFINITION);
        assertThrows(IllegalArgumentException.class,()->ActorSavedPoseLibrary.capture("",pose,List.of(HumanoidRig.HEAD)));
        assertThrows(IllegalArgumentException.class,()->ActorSavedPoseLibrary.capture("根",pose,List.of(HumanoidRig.ROOT)));
    }
    @Test void renameDeleteAndRestorationPersistWithoutChangingPose() throws Exception {
        var pose=ActorSavedPoseLibrary.capture("a",new RigPose(HumanoidRig.DEFINITION),List.of("head"));
        var before=List.of(pose);
        var renamed=ActorSavedPoseLibrary.edit(before,pose,"b");
        assertEquals(pose.rotations(),renamed.getFirst().rotations());
        var deleted=ActorSavedPoseLibrary.edit(renamed,renamed.getFirst(),null);
        Path file=directory.resolve("managed.json");
        ActorSavedPoseLibrary.save(file,before);
        ActorSavedPoseLibrary.replaceFile(file,before,renamed);
        ActorSavedPoseLibrary.replaceFile(file,renamed,deleted);
        assertTrue(ActorSavedPoseLibrary.read(file).isEmpty());
        ActorSavedPoseLibrary.replaceFile(file,deleted,renamed);
        ActorSavedPoseLibrary.replaceFile(file,renamed,before);
        assertEquals(before,ActorSavedPoseLibrary.read(file));
        assertThrows(java.io.IOException.class,()->ActorSavedPoseLibrary.replaceFile(file,deleted,renamed));
        assertEquals(before,ActorSavedPoseLibrary.read(file));
    }
    @Test void rejectsDuplicateNamesAndStaleTargetsAndCleansInputBuffer() {
        var pose=new RigPose(HumanoidRig.DEFINITION);
        var a=ActorSavedPoseLibrary.capture("a",pose,List.of("head"));
        var b=ActorSavedPoseLibrary.capture("b",pose,List.of("head"));
        assertThrows(IllegalArgumentException.class,()->ActorSavedPoseLibrary.edit(List.of(a,b),a,"b"));
        assertThrows(IllegalArgumentException.class,()->ActorSavedPoseLibrary.edit(List.of(b),a,null));
        assertEquals("new",ActorSavedPoseLibrary.edit(List.of(a),a,"new\u0000r").getFirst().name());
    }
}
