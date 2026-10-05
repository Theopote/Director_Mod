package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.director.ui.actor.pose.ActorAnimationAuthoringService.*;

class ActorKeyframeClipboardTest {
    private AnimationAsset asset() { return new AnimationAsset("test:clipboard","Clipboard",HumanoidRig.DEFINITION,10,AnimationLoopMode.ONCE); }
    @Test void mixedChannelsUseEarliestOriginAndPreserveValuesAndModes() {
        var asset=asset(); var track=asset.getOrCreateBoneTrack("head");
        track.rotation().put(3,new Quaternionf().rotateX(.4f),InterpolationMode.SMOOTH);
        track.translation().put(1,new Vector3f(2),InterpolationMode.STEP);
        var clipboard=new ActorKeyframeClipboard();
        var translation=new Key("head",Channel.TRANSLATION,1);
        assertTrue(clipboard.copy(asset,List.of(new Key("head",Channel.ROTATION,3),translation,translation)));
        assertEquals(2,clipboard.size());
        var pasted=clipboard.at(asset,5,10);
        assertEquals(7f,pasted.get(0).time()); assertEquals(5f,pasted.get(1).time());
        assertEquals(InterpolationMode.STEP,pasted.get(1).interpolation());
        track.translation().put(1,new Vector3f(99),InterpolationMode.LINEAR);
        assertEquals(new Vector3f(2),pasted.get(1).value());
        ((Vector3f)pasted.get(1).value()).set(999);
        assertEquals(new Vector3f(2),clipboard.at(asset,5,10).get(1).value());
    }
    @Test void missingSourcePreservesPreviousClipboardAndBoundsRejectWholePaste() {
        var asset=asset(); asset.getOrCreateBoneTrack("head").scale().put(2,new Vector3f(2),InterpolationMode.LINEAR);
        asset.getOrCreateBoneTrack("head").scale().put(4,new Vector3f(3),InterpolationMode.LINEAR);
        var clipboard=new ActorKeyframeClipboard();
        assertTrue(clipboard.copy(asset,List.of(new Key("head",Channel.SCALE,2),new Key("head",Channel.SCALE,4))));
        assertFalse(clipboard.copy(asset,List.of(new Key("head",Channel.SCALE,9)))); assertEquals(2,clipboard.size());
        assertTrue(clipboard.at(asset,9,10).isEmpty()); assertTrue(clipboard.at(asset,3,4).isEmpty());
        assertTrue(clipboard.at(asset,Float.NaN,10).isEmpty()); assertFalse(clipboard.compatible(null));
        assertEquals(2,clipboard.at(asset,3,5).size());
    }
    @Test void incompatibleRigCannotReceiveCopiedKeys() {
        var source=asset(); source.getOrCreateBoneTrack("head").scale().put(1,new Vector3f(2),InterpolationMode.LINEAR);
        var clipboard=new ActorKeyframeClipboard(); assertTrue(clipboard.copy(source,List.of(new Key("head",Channel.SCALE,1))));
        var otherRig=new com.director.actor.rig.skeleton.RigDefinition("test:other","Other",HumanoidRig.DEFINITION.bones());
        var other=new AnimationAsset("test:other_asset","Other",otherRig,10,AnimationLoopMode.ONCE);
        assertFalse(clipboard.compatible(other)); assertTrue(clipboard.at(other,2,10).isEmpty());
    }

}
