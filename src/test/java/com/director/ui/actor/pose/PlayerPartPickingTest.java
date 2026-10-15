package com.director.ui.actor.pose;
import com.director.actor.rig.skeleton.*;
import net.minecraft.util.math.Vec3d;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlayerPartPickingTest {
    @Test void clicksPartSurfacesAwayFromBoneOrigins() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var world=PlayerRenderWorldPose.resolve(pose,new Vector3f(4,2,8),35,new Vector3f(1.3f));
        for(String id:new String[]{"body","head","left_arm","right_arm","left_forearm","right_forearm","left_leg","right_leg","left_shin","right_shin"}) {
            float y=id.equals("body")?-.35f:id.equals("head")?-.25f:id.endsWith("arm")&&!id.contains("forearm")?.05f:.2f;
            float x=id.startsWith("left")&&id.contains("arm")?.0625f:id.startsWith("right")&&id.contains("arm")?-.0625f:0;
            var matrix=world.transform(world.rig().requireBoneIndex(id));
            var origin=matrix.transformPosition(new Vector3f(x,y,2));
            var direction=matrix.transformDirection(new Vector3f(0,0,-1)).normalize();
            assertEquals(id,BoneRayPicker.pick(world,new Vec3d(origin.x,origin.y,origin.z),new Vec3d(direction.x,direction.y,direction.z)),id);
        }
    }
}
