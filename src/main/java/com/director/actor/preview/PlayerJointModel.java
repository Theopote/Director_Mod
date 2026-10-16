package com.director.actor.preview;

import net.minecraft.client.model.*;
import com.director.actor.rig.skeleton.*;

/** Alternate segmented cuboids are visible only for authored player joints. */
public final class PlayerJointModel {
    private static final String UPPER="director_upper", LOWER="director_lower", CONNECTOR="director_joint_connector";
    private PlayerJointModel() {}
    public static void build(ModelData data,Dilation dilation,boolean slim) {
        var root=data.getRoot();
        var body=root.getChild("body");
        body.addChild("director_torso",ModelPartBuilder.create().uv(16,16).cuboid(-4,-12,-2,8,12,4,dilation),ModelTransform.NONE);
        body.getChild("jacket").addChild("director_torso",ModelPartBuilder.create().uv(16,32).cuboid(-4,-12,-2,8,12,4,dilation.add(.25f)),ModelTransform.NONE);
        split(root.getChild("left_arm"),32,48,-1,-2,slim?3:4,dilation);
        split(root.getChild("right_arm"),40,16,slim?-2:-3,-2,slim?3:4,dilation);
        split(root.getChild("left_arm").getChild("left_sleeve"),48,48,-1,-2,slim?3:4,dilation.add(.25f));
        split(root.getChild("right_arm").getChild("right_sleeve"),40,32,slim?-2:-3,-2,slim?3:4,dilation.add(.25f));
        split(root.getChild("left_leg"),16,48,-2,0,4,dilation);
        split(root.getChild("right_leg"),0,16,-2,0,4,dilation);
        split(root.getChild("left_leg").getChild("left_pants"),0,48,-2,0,4,dilation.add(.25f));
        split(root.getChild("right_leg").getChild("right_pants"),0,32,-2,0,4,dilation.add(.25f));
    }
    private static void split(ModelPartData part,int u,int v,float x,float y,float width,Dilation dilation) {
        part.addChild(UPPER,ModelPartBuilder.create().uv(u,v).cuboid(x,y,-2,width,6,4,dilation),ModelTransform.NONE);
        part.addChild(LOWER,ModelPartBuilder.create().uv(u,v+6).cuboid(x,0,-2,width,6,4,dilation),ModelTransform.origin(0,y+6,0));
        // Placeholder gives ModelPart a render call; its geometry is replaced by the connector renderer.
        part.addChild(CONNECTOR,ModelPartBuilder.create().uv(u,v).cuboid(0,0,0,1,1,1),ModelTransform.origin(0,y+6,0));
    }
    public static void torso(ModelPart part,boolean active) {
        if(!part.hasChild("director_torso")) return;
        part.hidden=active; part.getChild("director_torso").visible=active;
    }
    public static void apply(ModelPart part,RigPose pose,String joint) {
        if (!part.hasChild(UPPER)) return;
        boolean active=pose!=null && pose.rig().findBoneIndex(joint)>=0
                && pose.isAuthored(pose.rig().requireBoneIndex(joint));
        part.hidden=active;
        part.getChild(UPPER).visible=false;
        var lower=part.getChild(LOWER); lower.visible=false;
        var connector=part.getChild(CONNECTOR); connector.visible=active;
        if(active) {
            var value=pose.localTransform(joint);
            var angles=ArmorStandEulerAngles.fromQuaternion(value.rotation());
            lower.pitch=angles.x; lower.yaw=angles.y; lower.roll=angles.z;
            lower.xScale=value.scale().x; lower.yScale=value.scale().y; lower.zScale=value.scale().z;
            PlayerJointSeam.update(part,part.getChild(UPPER),lower,connector,value.rotation(),value.scale());
        }
    }
}
