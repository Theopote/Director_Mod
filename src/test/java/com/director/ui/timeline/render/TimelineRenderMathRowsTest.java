package com.director.ui.timeline.render;

import org.junit.jupiter.api.Test;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntityStateClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.ActorPoseClip;
import com.director.timeline.model.entity.EntityLookAtClip;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRenderMathRowsTest {
    @Test void renderingAndHitsShareExpandedOffsetsAfterScrolling() {
        var viewport = new TimelineRenderViewport();
        viewport.setTrackHeight(30);
        viewport.setTrackGap(2);
        viewport.update(0, 0, 800, 120);
        viewport.setScrollY(100);
        var math = new TimelineRenderMath(viewport);
        math.setRowLayout(new TimelineRowLayout(30, 2, 20, 0, 7, 0));
        assertEquals(204, math.trackContentY(2));
        assertEquals(104, math.trackIndexToY(2));
        assertEquals(2, math.yToTrackIndex(104));
        assertEquals(-1, math.yToTrackIndex(0), "child rows must not accept track/clip hits");
        assertArrayEquals(new int[]{1, 2}, math.getVisibleTrackRange(3));
    }

    @Test void collapseReplacesLayoutAndRejectsOutsideHits() {
        var viewport = new TimelineRenderViewport();
        viewport.update(0, 0, 800, 120);
        var math = new TimelineRenderMath(viewport);
        math.setRowLayout(new TimelineRowLayout(30, 2, 20, 7, 0));
        assertEquals(172, math.trackContentY(1));
        math.setRowLayout(new TimelineRowLayout(30, 2, 20, 0, 0));
        assertEquals(32, math.trackContentY(1));
        assertEquals(-1, math.headerAtContentY(100));
        assertEquals(-1, math.headerAtContentY(-1));
        viewport.setScrollY(100);
        assertArrayEquals(new int[]{-1, -1}, math.getVisibleTrackRange(2));
    }

    @Test void tallActorBoundsUseTheWholeHeaderAndPlaceTheStateInItsBand() {
        var model = new TimelineModel(new TimelineViewportState());
        var track = new TimelineTrack("Actor", TrackType.ACTOR);
        model.addTrack(track);
        var state = new EntityTimelineClip(new EntityStateClip("State", 1, 2), 0);
        track.addClip(state);
        var viewport = new TimelineRenderViewport();
        viewport.update(0, 0, 800, 200);
        var math = new TimelineRenderMath(viewport);
        float trackHeight = TimelineRenderer.getTrackHeight();
        math.setRowLayout(new TimelineRowLayout(new float[]{6 * trackHeight}, 2f, 16f, 2));
        var hitTest = new TimelineRenderHitTest(math, viewport, model);

        var label = hitTest.getTrackHeaderBounds(0, 150, 30, 10, 150);
        assertEquals(20f, label.y1());
        assertEquals(20f + 6 * trackHeight, label.y2());
        var clip = hitTest.getClipRect(track, state, 0, 30, 10);
        assertEquals(20f + 5 * trackHeight, clip.y1(), 1e-4f);
        assertEquals(20f + 6 * trackHeight, clip.y2(), 1e-4f);
    }

    @Test void actionPaintOnlyHitsTheFirstExpandedBehaviorRow() {
        float gap = com.director.ui.theme.UITheme.Spacing.TIMELINE_TRACK_GAP;
        var layout = new TimelineRowLayout(new float[]{22f, 22f}, gap, 24f, 7, 7);
        var expanded = TimelineRenderer.actionPaintRow(layout, 1, true, true);
        float expectedTop = layout.trackTop(1) + 22 + gap + ActorRootTimelineRows.COUNT * 24;
        assertEquals(expectedTop, expanded.top());
        assertFalse(expanded.contains(expectedTop - 1));
        assertTrue(expanded.contains(expectedTop));
        assertFalse(expanded.contains(expectedTop + 24));
        assertNull(TimelineRenderer.actionPaintRow(layout, 1, true, false));
        var main = TimelineRenderer.actionPaintRow(layout, 1, false, true);
        assertEquals(layout.trackTop(1), main.top());
        assertFalse(main.contains(expectedTop));
    }

    @Test void tallActorGivesEachBehaviorASeparateDefaultHeightBand() {
        float height = TimelineRenderer.getTrackHeight();
        float tall = 6 * height;
        var behaviors = java.util.List.of(new ActorActionClip("Action", 1, 2),
                new ActorPoseClip("Pose", 1, 2), new EntityLookAtClip("Look", 1, 2),
                new EntityStateClip("State", 1, 2));
        assertEquals(height, TimelineRenderer.clipBandHeight(tall));
        for (int i = 0; i < behaviors.size(); i++) {
            var wrapper = new EntityTimelineClip(behaviors.get(i), 0);
            assertEquals((2 + i) * height, TimelineRenderer.clipBandOffset(wrapper, tall));
            assertEquals((1 + i) * height, TimelineRenderer.clipBandOffset(wrapper, 5 * height));
            assertEquals(0f, TimelineRenderer.clipBandOffset(wrapper, height));
        }
        var actionPaint = TimelineRenderer.actionPaintRow(new TimelineRowLayout(tall, 2, 24, 0), 0, false, false);
        assertEquals(height, actionPaint.height());
        assertFalse(actionPaint.contains(3 * height));
    }

    @Test void tallActorWithExpandedBonesUsesOnlyTheHeaderBehaviorRows() {
        float height = TimelineRenderer.getTrackHeight();
        float tall = 6 * height;
        int rootRows = ActorRootTimelineRows.COUNT;
        assertEquals(rootRows + 4, ActorBoneTimelineRows.boneRowStart(height));
        assertEquals(rootRows, ActorBoneTimelineRows.boneRowStart(tall));
        var layout = new TimelineRowLayout(tall, 2f, ActorBoneTimelineRows.CHILD_HEIGHT, rootRows + 1, 0);
        assertNull(TimelineRenderer.actionPaintRow(layout, 0, true, true));
        assertTrue(TimelineRenderer.actionPaintRow(layout, 0, false, true).contains(2 * height));
        assertEquals(tall + 2f + (rootRows + 1) * ActorBoneTimelineRows.CHILD_HEIGHT, layout.trackTop(1));
    }
}
