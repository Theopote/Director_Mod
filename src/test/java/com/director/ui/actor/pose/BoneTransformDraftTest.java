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
}
