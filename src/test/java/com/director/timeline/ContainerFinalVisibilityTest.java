package com.director.timeline;

import com.director.core.animation.ClipAnimationConfig;
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

    @Test void simultaneousAnimationFadesInsideClipAndHoldsFinalAlphaAfterward() {
        var clip = fadingClip(2, 6, 1, 0, 0, 1);
        assertEquals(1, clip.computeAlphaAtGlobalTime(3), 0.0001);
        assertEquals(0.675f, clip.computeAlphaAtGlobalTime(3.5f), 0.0001);
        assertEquals(0.35f, clip.computeAlphaAtGlobalTime(4), 0.0001);
        assertEquals(1, clip.computeAlphaAtGlobalTime(5), 0.0001);
        assertEquals(3, clip.getEffectiveAnimationEndTime(), 0.0001);
        assertEquals(4, clip.getRestoreTime(), 0.0001);
    }

    @Test void delayedFadeInsideClipUsesEffectiveCompletionAndFinalKeyframeAlpha() {
        var clip = fadingClip(2, 8, 1, 1, 0.5f, 2.5f);
        clip.addVisibilityKeyframe(VisibilityKeyframe.normal(0));
        clip.addVisibilityKeyframe(new VisibilityKeyframe(6, 0.8f, VisibilityKeyframe.VisibilityMode.NORMAL));
        assertEquals(0.936667f, clip.computeAlphaAtGlobalTime(3.9f), 0.0001);
        assertEquals(0.8f, clip.computeAlphaAtGlobalTime(4), 0.0001);
        assertEquals(0.8f, clip.computeAlphaAtGlobalTime(4.5f), 0.0001);
        assertEquals(0.54f, clip.computeAlphaAtGlobalTime(5.5f), 0.0001);
        assertEquals(0.28f, clip.computeAlphaAtGlobalTime(6.5f), 0.0001);
        assertEquals(0.8f, clip.computeAlphaAtGlobalTime(7), 0.0001);
    }

    @Test void fadeCanCrossClipEndWithoutReversingWhenTheClipContinues() {
        var clip = fadingClip(2, 4, 1, 0, 1.5f, 2.5f);
        assertEquals(1, clip.computeAlphaAtGlobalTime(4), 0.0001);
        assertEquals(0.675f, clip.computeAlphaAtGlobalTime(5), 0.0001);
        assertEquals(0.35f, clip.computeAlphaAtGlobalTime(5.5f), 0.0001);
        assertEquals(1, clip.computeAlphaAtGlobalTime(6), 0.0001);
    }

    @Test void longSequentialTailDelaysFadeUntilEveryBlockCompletes() {
        var clip = fadingClip(2, 4, 3, 0.5f, 0.5f, 1.5f);
        clip.getAnimationConfigForEditing().getSequenceParameters().setConcurrencyMode(
                com.director.core.animation.sequence.ConcurrencyMode.ONE_BY_ONE);
        assertEquals(1, clip.computeAlphaAtGlobalTime(5), 0.0001);
        assertEquals(1, clip.computeAlphaAtGlobalTime(7.5f), 0.0001);
        assertEquals(0.675f, clip.computeAlphaAtGlobalTime(8.5f), 0.0001);
        assertEquals(0.35f, clip.computeAlphaAtGlobalTime(9), 0.0001);
        assertEquals(1, clip.computeAlphaAtGlobalTime(10), 0.0001);
        assertEquals(7.5f, clip.getEffectiveAnimationEndTime(), 0.0001);
    }

    @Test void wholeContainerTimingKeepsClipEndAsCompletionBoundary() {
        var clip = fadingClip(2, 4, 5, 1, 0, 1);
        clip.getAnimationConfigForEditing().setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        assertEquals(4, clip.getEffectiveAnimationEndTime(), 0.0001);
        assertEquals(0.675f, clip.computeAlphaAtGlobalTime(4.5f), 0.0001);
        assertEquals(0.35f, clip.computeAlphaAtGlobalTime(5), 0.0001);
    }

    private static TimelineClip fadingClip(float start, float end, float duration, float delay,
                                           float fadeStart, float fadeEnd) {
        var clip = new TimelineClip("building", start, end, 0);
        var config = clip.getAnimationConfigForEditing();
        config.setMode(com.director.core.container.AnimationMode.FADE_IN);
        config.setDuration(duration);
        config.setDelay(delay);
        config.getSequenceParameters().setConcurrencyMode(
                com.director.core.animation.sequence.ConcurrencyMode.ALL_AT_ONCE);
        clip.setPostAnimationFadeEnabled(true);
        clip.setPostAnimationFadeStart(fadeStart);
        clip.setPostAnimationFadeEnd(fadeEnd);
        return clip;
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
