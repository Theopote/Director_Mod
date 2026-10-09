package com.director.ui.timeline.render;
import com.director.actor.rig.skeleton.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorBoneChannelLayoutTest {
    @Test void collapsedRowsKeepBoneOrder() {
        var rows=ActorBoneChannelLayout.rows(HumanoidRig.DEFINITION,Set.of());
        assertEquals(HumanoidRig.DEFINITION.boneCount(),rows.size());
        for(int i=0;i<rows.size();i++) {assertEquals(i,rows.get(i).boneIndex());assertEquals(-1,rows.get(i).channel());}
    }
    @Test void expandedHeadHasOnlySupportedChannelsAndFollowingBonesShift() {
        var rig=HumanoidRig.DEFINITION;int head=rig.requireBoneIndex(HumanoidRig.HEAD);
        var rows=ActorBoneChannelLayout.rows(rig,Set.of(HumanoidRig.HEAD));
        assertEquals(rig.boneCount()+2,rows.size());
        assertEquals(new ActorBoneChannelLayout.Row(head,-1),rows.get(head));
        assertEquals(new ActorBoneChannelLayout.Row(head,1),rows.get(head+1));
        assertEquals(new ActorBoneChannelLayout.Row(head,2),rows.get(head+2));
        assertEquals(new ActorBoneChannelLayout.Row(head+1,-1),rows.get(head+3));
    }
    @Test void humanoidRootDoesNotOfferUnsupportedRotation() {
        var rig=HumanoidRig.DEFINITION;var rows=ActorBoneChannelLayout.rows(rig,Set.of(HumanoidRig.ROOT));
        assertEquals(new ActorBoneChannelLayout.Row(0,-1),rows.get(0));
        assertEquals(new ActorBoneChannelLayout.Row(0,0),rows.get(1));
        assertEquals(new ActorBoneChannelLayout.Row(0,2),rows.get(2));
        assertFalse(ActorBoneChannelLayout.supports(rig,0,1));
    }
    @Test void allExpandedRowsMapIntoOwnerHeightWithoutChangingOtherTracks() {
        var rig=HumanoidRig.DEFINITION;
        var names=new HashSet<String>();rig.bones().forEach(b->names.add(b.id()));
        var rows=ActorBoneChannelLayout.rows(rig,names);
        assertEquals(rig.boneCount()*3,rows.size());
        assertEquals(rig.boneCount(),ActorBoneChannelLayout.rows(rig,Set.of()).size());
        assertTrue(rows.stream().filter(r->r.channel()>=0).allMatch(r->ActorBoneChannelLayout.supports(rig,r.boneIndex(),r.channel())));
    }
}
