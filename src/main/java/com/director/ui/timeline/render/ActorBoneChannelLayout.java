package com.director.ui.timeline.render;
import com.director.actor.rig.skeleton.*;
import java.util.*;

/** One mapping shared by row height, rendering and mouse hit testing. */
final class ActorBoneChannelLayout {
    record Row(int boneIndex,int channel) {}
    static boolean supports(RigDefinition rig,int index,int channel) {
        var bone=rig.bone(index);var caps=bone.capabilities();
        return switch(channel) {
            case 0 -> caps.translation();
            case 1 -> caps.rotation() && !(HumanoidTopology.matches(rig) && HumanoidRig.ROOT.equals(bone.id()));
            case 2 -> caps.scale();
            default -> false;
        };
    }
    static List<Row> rows(RigDefinition rig,Set<String> expanded) {
        var rows=new ArrayList<Row>();
        for(int i=0;i<rig.boneCount();i++) {
            rows.add(new Row(i,-1));
            if(expanded.contains(rig.bone(i).id()))
                for(int c=0;c<3;c++)if(supports(rig,i,c))rows.add(new Row(i,c));
        }
        return List.copyOf(rows);
    }
}
