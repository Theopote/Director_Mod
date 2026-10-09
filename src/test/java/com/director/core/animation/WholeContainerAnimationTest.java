package com.director.core.animation;

import com.google.gson.Gson;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.director.core.container.Container;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationTest {
    private ClipAnimationConfig config() {
        var config = ClipAnimationConfig.defaultConfig();
        config.setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        config.setDuration(2);
        config.setDelay(1);
        return config;
    }

    @Test void enterAndExitHaveAtomicWorldHandoffs() {
        var config = config();
        for (boolean exit : new boolean[] {false, true}) {
            var before = WholeContainerAnimation.sample(config, 10, 20, 10.5f, exit);
            assertEquals(exit, before.worldVisible());
            assertEquals(exit, before.renderVisible());
            var during = WholeContainerAnimation.sample(config, 10, 20, 12, exit);
            assertFalse(during.worldVisible());
            assertTrue(during.renderVisible());
            assertTrue(during.animating());
            assertEquals(0.5f, during.scale(), 0.0001f);
            var after = WholeContainerAnimation.sample(config, 10, 20, 13, exit);
            assertEquals(!exit, after.worldVisible());
            assertEquals(!exit, after.renderVisible());
            assertFalse(after.animating());
        }
    }

    @Test void scalingMovesBlockPositionsAndSizesAroundOnePivot() {
        var config = config();
        var frame = WholeContainerAnimation.sample(config, 0, 10, 2, false);
        var pivot = new Vec3d(2, 0, 0.5);
        Vector3f left = worldPoint(frame, new BlockPos(0, 0, 0), pivot, new Vector3f(0.5f, 0.5f, 0.5f));
        Vector3f right = worldPoint(frame, new BlockPos(3, 0, 0), pivot, new Vector3f(0.5f, 0.5f, 0.5f));
        assertEquals(1.25f, left.x, 0.0001f);
        assertEquals(2.75f, right.x, 0.0001f);
        assertEquals(1.5f, right.x - left.x, 0.0001f);
        Vector3f edge = worldPoint(frame, new BlockPos(0, 0, 0), pivot, new Vector3f(1.5f, 0.5f, 0.5f));
        assertEquals(0.5f, edge.x - left.x, 0.0001f);
    }

    private Vector3f worldPoint(WholeContainerAnimation.Frame frame, BlockPos pos, Vec3d pivot, Vector3f point) {
        return frame.localTransform(pos, pivot).peek().getPositionMatrix().transformPosition(point)
                .add(pos.getX(), pos.getY(), pos.getZ());
    }

    @Test void flyPreservesShapeAndReverseSeekingIsDeterministic() {
        var config = config();
        config.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.FLY);
        config.getWholeContainer().setOffset(-20, 8, 4);
        var mid = WholeContainerAnimation.sample(config, 0, 10, 2, false);
        assertEquals(-10, mid.x());
        assertEquals(4, mid.y());
        assertEquals(2, mid.z());
        assertEquals(1, mid.scale());
        WholeContainerAnimation.sample(config, 0, 10, 8, false);
        assertEquals(mid, WholeContainerAnimation.sample(config, 0, 10, 2, false));
        var exit = WholeContainerAnimation.sample(config, 0, 10, 2, true);
        assertEquals(mid.x(), exit.x());
    }

    @Test void shortenedClipsAndExcessDelayRespectClipBoundaries() {
        var config = config();
        assertTrue(WholeContainerAnimation.sample(config, 0, 2, 1.5f, false).animating());
        assertTrue(WholeContainerAnimation.sample(config, 0, 2, 2, false).worldVisible());
        config.setDelay(20);
        assertFalse(WholeContainerAnimation.sample(config, 0, 2, 1.99f, false).renderVisible());
        assertTrue(WholeContainerAnimation.sample(config, 0, 2, 2, false).worldVisible());
        assertFalse(WholeContainerAnimation.sample(config, 0, 2, 2, true).worldVisible());
    }

    @Test void pivotsIncludeFullBlockExtentsAndBottomStaysGrounded() {
        var bounds = new Container.BoundingBox(new BlockPos(-4, 2, 6), new BlockPos(-1, 5, 9));
        assertEquals(new Vec3d(-2, 4, 8), WholeContainerAnimation.pivot(bounds, WholeContainerAnimationConfig.Pivot.CENTER));
        assertEquals(new Vec3d(-2, 2, 8), WholeContainerAnimation.pivot(bounds, WholeContainerAnimationConfig.Pivot.BOTTOM_CENTER));
    }

    @Test void copyAndJsonPreserveScopeWithoutSharingSettings() {
        var config = config();
        config.getWholeContainer().setOffset(3, 4, 5);
        var copy = config.copy();
        copy.getWholeContainer().setOffset(8, 9, 10);
        assertEquals(3, config.getWholeContainer().getOffsetX());
        assertTrue(copy.isWholeContainer());
        var gson = new Gson();
        var restored = gson.fromJson(gson.toJson(config), ClipAnimationConfig.class);
        assertTrue(restored.isWholeContainer());
        assertEquals(3, restored.getWholeContainer().getOffsetX());
        assertEquals(ClipAnimationConfig.AnimationScope.PER_BLOCK,
                gson.fromJson("{}", ClipAnimationConfig.class).getScope());
    }
}
