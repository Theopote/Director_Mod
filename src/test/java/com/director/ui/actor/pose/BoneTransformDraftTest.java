package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.BoneTransform;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoneTransformDraftTest {
    @Test void draftPersistsUntilTargetChangesAndDoesNotModifySource() {
        var draft = new BoneTransformDraft();
        var source = BoneTransform.identity();
        draft.synchronize("actor:a/time:1", source);
        draft.translation[0] = 3f;
        draft.markChanged(false);
        assertFalse(draft.synchronize("actor:a/time:1", source));
        assertEquals(3f, draft.value().translation().x);
        assertEquals(0f, source.translation().x);
        assertTrue(draft.synchronize("actor:b/time:1", source));
        assertFalse(draft.isDirty());
        assertEquals(0f, draft.translation[0]);
    }

    @Test void undoOrExternalPoseChangeDiscardsStaleDraft() {
        var draft = new BoneTransformDraft();
        draft.synchronize("target", BoneTransform.identity());
        draft.scale[0] = 2f;
        draft.markChanged(false);
        assertTrue(draft.synchronize("target", BoneTransform.translation(1f, 0f, 0f)));
        assertEquals(1f, draft.scale[0]);
        assertEquals(1f, draft.translation[0]);
    }

    @Test void invalidValuesCannotCommitAndCancelRestoresExactRotation() {
        var source = BoneTransform.identity().withRotation(new Quaternionf().rotateXYZ(.4f, 1.2f, -.6f));
        var draft = new BoneTransformDraft();
        draft.synchronize("target", source);
        draft.scale[1] = Float.NaN;
        draft.markChanged(false);
        assertFalse(draft.isValid());
        assertThrows(IllegalArgumentException.class, draft::value);
        draft.reset();
        assertFalse(draft.isDirty());
        assertEquals(source.rotation(), draft.value().rotation());
    }

    @Test void changedChannelsAccumulateAndResetWithContext() {
        var draft = new BoneTransformDraft();
        draft.synchronize("a", BoneTransform.identity());
        draft.markChannelsChanged(com.director.actor.rig.skeleton.RigPose.TRANSLATION);
        draft.markChannelsChanged(com.director.actor.rig.skeleton.RigPose.SCALE);
        assertEquals(5, draft.changedChannels());
        draft.synchronize("b", BoneTransform.identity());
        assertEquals(0, draft.changedChannels());
        assertFalse(draft.isDirty());
    }
    @Test void previewChangesOnlyEditedChannelWithoutMutatingSampleAndCancelRemovesLayer() {
        var rig=com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION;
        var base=new com.director.actor.rig.skeleton.RigPose(rig);
        String bone=com.director.actor.rig.skeleton.ArticulatedPlayerRig.LEFT_FOREARM;
        var sampled=base.localTransform(bone);
        var draft=new BoneTransformDraft();
        draft.synchronize("target",sampled);
        draft.rotation[0]=-90;
        draft.markChannelsChanged(com.director.actor.rig.skeleton.RigPose.ROTATION);
        var preview=draft.preview(base,bone);
        assertNotSame(base,preview);
        assertTrue(preview.localTransform(bone).rotation().equals(new Quaternionf().rotateX(-(float)Math.PI/2),1e-5f));
        assertEquals(sampled.translation(),preview.localTransform(bone).translation());
        assertEquals(sampled.scale(),preview.localTransform(bone).scale());
        assertTrue(base.localTransform(bone).rotation().equals(new Quaternionf(),1e-5f));
        draft.clear();
        assertSame(base,draft.preview(base,bone));
    }
    @Test void invalidPreviewDoesNotPropagateNonFiniteValues() {
        var base=new com.director.actor.rig.skeleton.RigPose(com.director.actor.rig.skeleton.HumanoidRig.DEFINITION);
        var draft=new BoneTransformDraft();
        draft.synchronize("target",base.localTransform("head"));
        draft.rotation[0]=Float.NaN;
        draft.markChannelsChanged(com.director.actor.rig.skeleton.RigPose.ROTATION);
        assertSame(base,draft.preview(base,"head"));
    }
}
