package com.director.timeline;

import com.director.timeline.model.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerFinalVisibilityTest {
    @Test void defaultContainerKeepsItsFinalOpacityToTheRightOfTheClip() {
        var clip = new TimelineClip("building", 2, 4, 0);
        for (float time : new float[] {4, 4.1f, 5, 6, 10_000})
            assertEquals(1, clip.computeAlphaAtGlobalTime(time));
    }

    @Test void explicitFadeCannotRevealHiddenFinalKeyframeOrDivideByZero() {
        var clip = new TimelineClip("building", 2, 4, 0);
        clip.addVisibilityKeyframe(VisibilityKeyframe.hide(2));
        clip.setPostAnimationFadeEnabled(true);
        assertEquals(0, clip.computeAlphaAtGlobalTime(5));
        clip.setPostAnimationFadeEnd(0);
        assertTrue(Float.isFinite(clip.computeAlphaAtGlobalTime(5)));
    }

    @Test void optInFadeSurvivesCopiesAndProjectPersistenceWhileLegacyDefaultsStayVisible() {
        var clip = new TimelineClip("building", 2, 4, 0);
        clip.setPostAnimationFadeEnabled(true);
        assertEquals(0.675f, clip.computeAlphaAtGlobalTime(5), 0.0001);
        assertTrue(new TimelineClip(clip).isPostAnimationFadeEnabled());
        assertTrue(clip.duplicateShallow().isPostAnimationFadeEnabled());
        var viewport = new TimelineViewportState();
        var model = new TimelineModel(viewport); model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("blocks", TrackType.CONTAINER); track.addClip(clip); model.addTrack(track);
        var gson = new Gson();
        var dto = gson.fromJson(gson.toJson(TimelineModelPersistence.toDTO(model, viewport)), TimelineModelPersistence.TimelineDTO.class);
        var loaded = new TimelineModel(new TimelineViewportState()); loaded.setSuppressPersistenceWrites(true);
        TimelineModelPersistence.fromDTO(dto, loaded, new TimelineViewportState());
        assertTrue(loaded.getTracks().getFirst().getClips().getFirst().isPostAnimationFadeEnabled());
        assertFalse(gson.fromJson("{}", TimelineModelPersistence.ClipDTO.class).postAnimationFadeEnabled);
    }
}
