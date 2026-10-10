package com.director.core.animation;

import com.google.gson.Gson;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.director.core.container.Container;
import org.joml.Vector3f;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
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

    @Test void shorterRotationKeepsAllTurnsAndDoublesAngularSpeed() {
        for (var effect : new WholeContainerAnimationConfig.Effect[] {
                WholeContainerAnimationConfig.Effect.ROTATE, WholeContainerAnimationConfig.Effect.TRANSFORM}) {
            for (boolean exit : new boolean[] {false, true}) {
                var config = config();
                config.setDelay(0);
                config.setEasing("LINEAR");
                config.getWholeContainer().setEffect(effect);
                config.getWholeContainer().setRotation(-720, 1080, 360);
                for (float duration : new float[] {2, 1, 0.5f}) {
                    config.setDuration(duration);
                    var start = WholeContainerAnimation.sample(config, 10, 20, 10, exit);
                    assertEquals(exit ? 0 : 1080, start.rotationY(), 0.001f);
                    float totalX = 0, totalY = 0, totalZ = 0;
                    var previous = start;
                    for (int step = 1; step <= 64; step++) {
                        var frame = WholeContainerAnimation.sample(config, 10, 20,
                                10 + duration * step / 64, exit);
                        // EXIT's final invisible frame has no transform; use the authored endpoint.
                        float x = step == 64 && exit ? -720 : frame.rotationX();
                        float y = step == 64 && exit ? 1080 : frame.rotationY();
                        float z = step == 64 && exit ? 360 : frame.rotationZ();
                        totalX += Math.abs(x - previous.rotationX());
                        totalY += Math.abs(y - previous.rotationY());
                        totalZ += Math.abs(z - previous.rotationZ());
                        previous = frame;
                    }
                    assertEquals(720, totalX, 0.001f);
                    assertEquals(1080, totalY, 0.001f);
                    assertEquals(360, totalZ, 0.001f);
                    var later = WholeContainerAnimation.sample(config, 10, 20, 10.125f, exit);
                    assertEquals(1080 / duration, Math.abs(later.rotationY() - start.rotationY()) / 0.125f, 0.001f);
                }
            }
        }
    }

    @Test void durationEditsPreserveThePreviewPoseForEveryEffectAndDirection() {
        for (var effect : WholeContainerAnimationConfig.Effect.values()) {
            for (boolean exit : new boolean[] {false, true}) {
                var config = config();
                config.getWholeContainer().setEffect(effect);
                config.getWholeContainer().setOffset(-12, 8, 4);
                config.getWholeContainer().setRotation(90, 720, -180);
                config.setEasing("EASE_IN_OUT_CUBIC");
                float time = 11.5f; // quarter of the old two-second animation, after delay
                var before = WholeContainerAnimation.sample(config, 10, 20, time, exit);
                float retimed = WholeContainerAnimation.retimePreviewTime(10, 20, 1, 2, 1, time);
                assertEquals(11.25f, retimed, 0.0001f);
                config.setDuration(1);
                var after = WholeContainerAnimation.sample(config, 10, 20, retimed, exit);
                assertEquals(before, after);
                // Returning to the start still plays the full effect, now twice as fast.
                config.setDuration(2);
                var oldStart = WholeContainerAnimation.sample(config, 10, 20, 11, exit);
                config.setDuration(1);
                assertEquals(oldStart, WholeContainerAnimation.sample(config, 10, 20, 11, exit));
                assertFalse(WholeContainerAnimation.sample(config, 10, 20, 12, exit).animating());
            }
        }
    }

    @Test void repeatedDurationEditsAndLengtheningDoNotAccumulatePreviewDrift() {
        float time = 11.75f;
        float oldDuration = 2;
        for (float duration : new float[] {1, 0.5f, 4, 2}) {
            time = WholeContainerAnimation.retimePreviewTime(10, 20, 1, oldDuration, duration, time);
            oldDuration = duration;
        }
        assertEquals(11.75f, time, 0.0001f);
    }

    @Test void durationEditUsesTheActualClippedWindowAndLeavesOtherTimesAlone() {
        assertEquals(11.25f, WholeContainerAnimation.retimePreviewTime(10, 12, 1, 4, 0.5f, 11.5f));
        assertEquals(10.5f, WholeContainerAnimation.retimePreviewTime(10, 20, 1, 2, 1, 10.5f));
        assertEquals(14, WholeContainerAnimation.retimePreviewTime(10, 20, 1, 2, 1, 14));
        assertEquals(12, WholeContainerAnimation.retimePreviewTime(10, 12, 20, 2, 1, 12));
        assertEquals(12, WholeContainerAnimation.retimePreviewTime(10, 20, 1, 2, 1, 13));
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

    @Test void rotationPreservesDistancesAndTheSharedPivot() {
        var config = config();
        config.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.ROTATE);
        config.getWholeContainer().setRotation(0, 180, 0);
        var frame = WholeContainerAnimation.sample(config, 0, 10, 2, false);
        var pivot = new Vec3d(2, 0, 0.5);
        var left = worldPoint(frame, new BlockPos(0, 0, 0), pivot, new Vector3f(0.5f, 0.5f, 0.5f));
        var right = worldPoint(frame, new BlockPos(3, 0, 0), pivot, new Vector3f(0.5f, 0.5f, 0.5f));
        assertEquals(2, left.x, 0.0001f);
        assertEquals(2, right.x, 0.0001f);
        assertEquals(2, left.z, 0.0001f);
        assertEquals(-1, right.z, 0.0001f);
        assertEquals(3, left.distance(right), 0.0001f);
        var fixed = worldPoint(frame, BlockPos.ORIGIN, pivot, new Vector3f(2, 0, 0.5f));
        assertEquals(2, fixed.x, 0.0001f);
        assertEquals(0, fixed.y, 0.0001f);
        assertEquals(0.5f, fixed.z, 0.0001f);
        assertEquals(1, frame.scale());
        assertEquals(0, frame.y());
    }

    @Test void rotationKeepsMultipleTurnsAndSamplesBothDirectionsDeterministically() {
        var config = config();
        config.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.ROTATE);
        config.getWholeContainer().setRotation(-720, 1080, 360);
        var enter = WholeContainerAnimation.sample(config, 0, 10, 1.5f, false);
        var exit = WholeContainerAnimation.sample(config, 0, 10, 1.5f, true);
        assertEquals(-540, enter.rotationX());
        assertEquals(810, enter.rotationY());
        assertEquals(270, enter.rotationZ());
        assertEquals(-180, exit.rotationX());
        assertEquals(270, exit.rotationY());
        assertEquals(90, exit.rotationZ());
        WholeContainerAnimation.sample(config, 0, 10, 9, false);
        assertEquals(enter, WholeContainerAnimation.sample(config, 0, 10, 1.5f, false));
        var end = WholeContainerAnimation.sample(config, 0, 10, 3, false);
        assertTrue(end.worldVisible());
        assertNull(end.localTransform(BlockPos.ORIGIN, Vec3d.ZERO));
    }

    @Test void combinedTransformScalesAndRotatesBeforeApplyingWorldOffset() {
        var config = config();
        var settings = config.getWholeContainer();
        settings.setEffect(WholeContainerAnimationConfig.Effect.TRANSFORM);
        settings.setOffset(20, 4, -8);
        settings.setRotation(0, 180, 0);
        var frame = WholeContainerAnimation.sample(config, 0, 10, 2, false);
        var pivot = new Vec3d(3, 4, -1);
        var point = worldPoint(frame, BlockPos.ORIGIN, pivot, new Vector3f(2, 4, 0));
        assertEquals(13.5f, point.x, 0.0001f);
        assertEquals(6, point.y, 0.0001f);
        assertEquals(-4.5f, point.z, 0.0001f);
        var center = worldPoint(frame, BlockPos.ORIGIN, pivot, new Vector3f(3, 4, -1));
        assertEquals(13, center.x, 0.0001f);
        assertEquals(6, center.y, 0.0001f);
        assertEquals(-5, center.z, 0.0001f);
        var other = worldPoint(frame, BlockPos.ORIGIN, pivot, new Vector3f(2, 6, 0));
        assertEquals(1, other.distance(point), 0.0001f);
        assertEquals(frame, WholeContainerAnimation.sample(config, 0, 10, 2, true));
    }

    @Test void customPivotFollowsTheBoundsCenterWhenTheAssemblyMoves() {
        var settings = new WholeContainerAnimationConfig();
        settings.setPivot(WholeContainerAnimationConfig.Pivot.CUSTOM);
        settings.setCustomPivot(-1.25f, 3.5f, 2);
        var before = new Container.BoundingBox(new BlockPos(-4, 2, 6), new BlockPos(-1, 5, 9));
        var after = new Container.BoundingBox(new BlockPos(6, -3, 10), new BlockPos(9, 0, 13));
        assertEquals(new Vec3d(-3.25, 7.5, 10), WholeContainerAnimation.pivot(before, settings));
        assertEquals(new Vec3d(6.75, 2.5, 14), WholeContainerAnimation.pivot(after, settings));
        settings.setPivot(WholeContainerAnimationConfig.Pivot.BOTTOM_CENTER);
        assertEquals(new Vec3d(-2, 2, 8), WholeContainerAnimation.pivot(before, settings));
    }

    @Test void rendererRotatesNormalsTogetherWithTheBlockGeometry() throws Exception {
        var config = config();
        config.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.ROTATE);
        var frame = WholeContainerAnimation.sample(config, 0, 10, 2, false);
        var snapshot = new BlockAnimationSnapshot(BlockPos.ORIGIN, null);
        snapshot.setTransform(frame.localTransform(BlockPos.ORIGIN, Vec3d.ZERO));
        var target = new MatrixStack();
        target.translate(1, 2, 3);
        target.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        var apply = BlockAnimationRenderer.class.getDeclaredMethod("applyAnimationTransformFromSnapshot",
                MatrixStack.class, BlockAnimationSnapshot.class);
        apply.setAccessible(true);
        apply.invoke(null, target, snapshot);
        var point = target.peek().getPositionMatrix().transformPosition(new Vector3f(1, 0, 0));
        var normal = target.peek().getNormalMatrix().transform(new Vector3f(1, 0, 0));
        assertEquals(1, point.x, 0.0001f);
        assertEquals(3, point.y, 0.0001f);
        assertEquals(3, point.z, 0.0001f);
        assertEquals(0, normal.x, 0.0001f);
        assertEquals(1, normal.y, 0.0001f);
        assertEquals(0, normal.z, 0.0001f);
        assertEquals(1, normal.length(), 0.0001f);
    }

    @Test void legacyFlyAndScaleDoNotAcquireRotationOrExtraTranslation() {
        var gson = new Gson();
        var config = config();
        var legacy = gson.fromJson("{\"effect\":\"FLY\",\"pivot\":\"CENTER\",\"scale\":0.2,\"offsetY\":8}",
                WholeContainerAnimationConfig.class);
        config.setWholeContainer(legacy);
        var fly = WholeContainerAnimation.sample(config, 0, 10, 2, false);
        assertEquals(1, fly.scale());
        assertEquals(4, fly.y());
        assertEquals(0, fly.rotationY());
        config.getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.SCALE);
        var scale = WholeContainerAnimation.sample(config, 0, 10, 2, false);
        assertEquals(0.6f, scale.scale(), 0.0001f);
        assertEquals(0, scale.y());
        assertEquals(0, scale.rotationY());
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
