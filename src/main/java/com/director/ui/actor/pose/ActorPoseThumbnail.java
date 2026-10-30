package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import org.joml.Vector3f;
import java.util.*;

/** CPU-only, model-space skeleton projection. Normalized segments can be cached by the browser. */
public final class ActorPoseThumbnail {
    public record Segment(float x1,float y1,float x2,float y2) {}
    private ActorPoseThumbnail() {}
    public static List<Segment> project(RigPose pose) {
        var matrices=pose.calculateModelTransforms();
        var lines=new ArrayList<Vector3f[]>();
        var rig=pose.rig();
        for(int i=0;i<rig.boneCount();i++) {
            var bone=rig.bone(i);
            if(bone.parentIndex()>=0 && !HumanoidRig.ROOT.equals(rig.bone(bone.parentIndex()).id()))
                lines.add(new Vector3f[]{matrices[bone.parentIndex()].transformPosition(new Vector3f()),matrices[i].transformPosition(new Vector3f())});
            String id=bone.id();
            float length=HumanoidRig.HEAD.equals(id)?.3f:
                    (id.equals(HumanoidRig.LEFT_ARM)||id.equals(HumanoidRig.RIGHT_ARM))?
                            (rig.findBoneIndex(ArticulatedPlayerRig.LEFT_FOREARM)>=0?-.25f:-.65f):
                    (id.equals(HumanoidRig.LEFT_LEG)||id.equals(HumanoidRig.RIGHT_LEG))?
                            (rig.findBoneIndex(ArticulatedPlayerRig.LEFT_SHIN)>=0?-.375f:-.75f):
                    LimbBendTransform.isJoint(id)?-.375f:0;
            if(length!=0) lines.add(new Vector3f[]{matrices[i].transformPosition(new Vector3f()),matrices[i].transformPosition(new Vector3f(0,length,0))});
        }
        if(lines.isEmpty()) return List.of();
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(var line:lines) for(var point:line) {
            point.set(point.x+.5f*point.z,-point.y+.18f*point.z,0);
            minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);
            minY=Math.min(minY,point.y);maxY=Math.max(maxY,point.y);
        }
        float size=Math.max(.01f,Math.max(maxX-minX,maxY-minY));
        float cx=(maxX+minX)/2,cy=(maxY+minY)/2;
        var result=new ArrayList<Segment>();
        for(var line:lines) result.add(new Segment(.5f+(line[0].x-cx)/size,.5f+(line[0].y-cy)/size,
                .5f+(line[1].x-cx)/size,.5f+(line[1].y-cy)/size));
        return List.copyOf(result);
    }
}
