package com.director.timeline.playback.block;

import com.director.core.DirectorServices;
import com.director.core.animation.ClipAnimationConfig;
import com.director.core.container.Container;
import com.director.core.container.visibility.ContainerVisibilityService;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.playback.TimelineSamplingContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimelineClipBlockSamplingContextTest {
    private static TimelineClipBlockController controller() throws Exception {
        DirectorServices.initialize();
        var constructor = TimelineClipBlockController.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Field field(String name) throws Exception {
        var field = TimelineClipBlockController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Field budgetField(String name) throws Exception {
        var field = TimelinePlacementBudget.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invokeUnchecked(Method method, Object target, Object... arguments) {
        try {
            method.invoke(target, arguments);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            throw new AssertionError(e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    @Test void deterministicSamplingPreservesPreviewEffectsBudgetsAndBookkeeping() throws Exception {
        UUID retainedId = UUID.randomUUID();
        for (var context : List.of(TimelineSamplingContext.EXPORT, TimelineSamplingContext.HEADLESS)) {
            var controller = controller();
            var model = new TimelineModel(new TimelineViewportState());
            model.setCurrentTime(3);
            model.setPlaying(true);
            controller.setTimelineModel(model);

            var cursor = (WholeContainerEffectCursor) field("wholeEffectCursor").get(controller);
            cursor.beginFrame(2, true, false);
            cursor.beginFrame(3, true, false);
            var clip = new TimelineClip("clip", 0, 4, 0);
            var effectTimes = (Map<TimelineClip, Float>) field("perBlockEffectSampleTime").get(controller);
            effectTimes.put(clip, 2.5f);
            var visible = (Map<UUID, Set<BlockPos>>) field("lastFrameVisibleBlocks").get(controller);
            var world = (Map<UUID, Set<BlockPos>>) field("lastFrameWorldBlocks").get(controller);
            visible.put(retainedId, Set.of(BlockPos.ORIGIN));
            world.put(retainedId, Set.of(BlockPos.ORIGIN));
            var buckets = (Map<TimelineClip, Integer>) field("lastStaticBucketByClip").get(controller);
            buckets.put(clip, 2);

            long revision = field("timelineRevision").getLong(controller);
            long sectionRevision = com.director.core.container.visibility.ClientSectionInvalidation.getInstance().revision();
            field("lastHandledRevision").setLong(controller, revision);
            field("lastHandledSectionRevision").setLong(controller, sectionRevision);
            field("lastHandledPreviewTime").setFloat(controller, 3);
            field("lastObservedPlaying").setBoolean(controller, true);
            field("timelineDrivingPaused").setBoolean(controller, false);
            field("worldHandoffPending").setBoolean(controller, true);
            field("wasScrubbing").setBoolean(controller, false);
            field("lastPerBlockEffectTime").setFloat(controller, 2.5f);
            field("perBlockEffectsPlaying").setBoolean(controller, true);
            field("cacheCleanupCounter").setInt(controller, 299);

            var scrubbing = budgetField("scrubbing");
            var instantWorld = budgetField("instantWorld");
            var catchUp = budgetField("catchUpFramesRemaining");
            boolean oldScrubbing = scrubbing.getBoolean(null);
            boolean oldInstantWorld = instantWorld.getBoolean(null);
            int oldCatchUp = catchUp.getInt(null);
            try {
                scrubbing.setBoolean(null, true);
                instantWorld.setBoolean(null, true);
                catchUp.setInt(null, 7);

                TimelineSamplingContext.run(context, controller::update);

                assertEquals(3, cursorPreviousTime(cursor), 0.0001f);
                assertTrue(cursorPreviousPlaying(cursor));
                assertEquals(2.5f, effectTimes.get(clip));
                assertEquals(2.5f, field("lastPerBlockEffectTime").getFloat(controller), 0.0001f);
                assertTrue(field("perBlockEffectsPlaying").getBoolean(controller));
                assertEquals(Set.of(BlockPos.ORIGIN), visible.get(retainedId));
                assertEquals(Set.of(BlockPos.ORIGIN), world.get(retainedId));
                assertEquals(2, buckets.get(clip));
                assertEquals(revision, field("lastHandledRevision").getLong(controller));
                assertEquals(sectionRevision, field("lastHandledSectionRevision").getLong(controller));
                assertEquals(3, field("lastHandledPreviewTime").getFloat(controller), 0.0001f);
                assertTrue(field("lastObservedPlaying").getBoolean(controller));
                assertFalse(field("timelineDrivingPaused").getBoolean(controller));
                assertTrue(field("worldHandoffPending").getBoolean(controller));
                assertFalse(field("wasScrubbing").getBoolean(controller));
                assertEquals(299, field("cacheCleanupCounter").getInt(controller));
                assertTrue(scrubbing.getBoolean(null));
                assertTrue(instantWorld.getBoolean(null));
                assertEquals(7, catchUp.getInt(null));
                assertTrue(model.isPlaying());
                assertFalse(model.isScrubbing());
                assertTrue(field("previewResyncPending").getBoolean(controller));
                assertTrue(controller.needsClipBlockUpdate(model));

                TimelineSamplingContext.run(TimelineSamplingContext.PREVIEW, controller::update);
                assertFalse(field("previewResyncPending").getBoolean(controller));
            } finally {
                scrubbing.setBoolean(null, oldScrubbing);
                instantWorld.setBoolean(null, oldInstantWorld);
                catchUp.setInt(null, oldCatchUp);
            }
        }
    }

    private static float cursorPreviousTime(WholeContainerEffectCursor cursor) throws Exception {
        var field = WholeContainerEffectCursor.class.getDeclaredField("previousTime");
        field.setAccessible(true);
        return field.getFloat(cursor);
    }

    private static boolean cursorPreviousPlaying(WholeContainerEffectCursor cursor) throws Exception {
        var field = WholeContainerEffectCursor.class.getDeclaredField("previousPlaying");
        field.setAccessible(true);
        return field.getBoolean(cursor);
    }

    @Test void strictContextsRethrowContainerFailuresWhilePreviewIsolatesThem() throws Exception {
        var controller = controller();
        var id = UUID.randomUUID();
        var failure = new IllegalStateException("broken container");
        assertFalse(controller.runIsolatedContainer(id, () -> { throw failure; }));
        for (var context : List.of(TimelineSamplingContext.EXPORT, TimelineSamplingContext.HEADLESS)) {
            var thrown = assertThrows(IllegalStateException.class,
                    () -> TimelineSamplingContext.run(context,
                            () -> controller.runIsolatedContainer(id, () -> { throw failure; })));
            assertSame(failure, thrown);
        }
        assertEquals(3, controller.getContainerFailureCount(id));
    }

    @Test void deterministicSamplingNeverBindsAnUnboundContainer() throws Exception {
        var controller = controller();
        var container = new Container("unbound");
        var method = TimelineClipBlockController.class.getDeclaredMethod("ensureContainerWorldBinding", Container.class);
        method.setAccessible(true);
        for (var context : List.of(TimelineSamplingContext.EXPORT, TimelineSamplingContext.HEADLESS)) {
            var thrown = assertThrows(IllegalStateException.class,
                    () -> TimelineSamplingContext.run(context,
                            () -> invokeUnchecked(method, controller, container)));
            assertTrue(thrown.getMessage().contains("已绑定世界"));
            assertNull(container.getLevelKey());
        }
    }

    @Test void deterministicSamplingDoesNotIntakeSnapshotsOrHideContainers() throws Exception {
        var controller = controller();
        var container = new Container("building");
        container.addBlocks(List.of(BlockPos.ORIGIN));
        RegistryKey<net.minecraft.world.World> level = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("test", "world"));
        container.setLevelKey(level);
        var state = container.getState();
        var visibility = new ContainerVisibilityService(null);
        var method = TimelineClipBlockController.class.getDeclaredMethod("ensureContainerSnapshot",
                Container.class, ContainerVisibilityService.class, com.director.timeline.model.TrackType.class);
        method.setAccessible(true);
        for (var context : List.of(TimelineSamplingContext.EXPORT, TimelineSamplingContext.HEADLESS)) {
            TimelineSamplingContext.run(context,
                    () -> invokeUnchecked(method, controller, container, visibility,
                            com.director.timeline.model.TrackType.CONTAINER));
            assertEquals(state, container.getState());
            assertEquals(level, container.getLevelKey());
            assertFalse(visibility.hasSnapshot(container));
        }
    }

    @Test void deterministicSamplingBypassesStationaryPreviewShortcut() throws Exception {
        var controller = controller();
        var model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(3);
        controller.setTimelineModel(model);

        TimelineSamplingContext.run(TimelineSamplingContext.PREVIEW, controller::update);
        assertFalse(controller.needsClipBlockUpdate(model));

        for (var context : List.of(TimelineSamplingContext.EXPORT, TimelineSamplingContext.HEADLESS)) {
            TimelineSamplingContext.run(context, controller::update);
            assertTrue(field("previewResyncPending").getBoolean(controller));
            assertTrue(controller.needsClipBlockUpdate(model));

            TimelineSamplingContext.run(TimelineSamplingContext.PREVIEW, controller::update);
            assertFalse(field("previewResyncPending").getBoolean(controller));
            assertFalse(controller.needsClipBlockUpdate(model));
        }
    }

    @Test void simultaneousEntryKeepsItsFinalVisibilityThroughoutTheClipWhileExitStaysCleared() throws Exception {
        var controller = controller();
        for (boolean exit : new boolean[] {false, true}) {
            var container = new Container(exit ? "demolition" : "building",
                    exit ? com.director.core.container.ContainerType.DEMOLITION
                            : com.director.core.container.ContainerType.GENERAL);
            container.addBlocks(List.of(BlockPos.ORIGIN));
            var clip = new TimelineClip("clip", 0, 5, 0);
            var config = clip.getAnimationConfigForEditing();
            config.setMode(com.director.core.container.AnimationMode.FADE_IN);
            config.setDuration(1);
            config.setDelay(0);
            config.setPhase(exit ? ClipAnimationConfig.AnimationPhase.EXIT
                    : ClipAnimationConfig.AnimationPhase.ENTER);
            config.getSequenceParameters().setConcurrencyMode(
                    com.director.core.animation.sequence.ConcurrencyMode.ALL_AT_ONCE);

            assertFalse(controller.sampleClipFrame(clip, 0.5f, true, container, config, true)
                    .visibility().renderBlocks().isEmpty());
            assertEquals(exit, controller.sampleClipFrame(clip, 2, true, container, config, true)
                    .visibility().renderBlocks().isEmpty());
            assertEquals(exit, controller.sampleClipFrame(clip, 5, true, container, config, true)
                    .visibility().renderBlocks().isEmpty());
            assertEquals(exit, controller.sampleClipFrame(clip, 5.1f, true, container, config, true)
                    .visibility().renderBlocks().isEmpty());
            if (exit) {
                assertEquals(1, controller.sampleClipFrame(clip, -0.5f, true, container, config, true).opacity());
            }
        }
    }

    @Test void previewVisibilityCacheDoesNotCrossSubMillisecondCompletionCutoffs() throws Exception {
        var controller = controller();
        var container = new Container("demolition", com.director.core.container.ContainerType.DEMOLITION);
        container.addBlocks(List.of(BlockPos.ORIGIN));
        var clip = new TimelineClip("clip", 0, 0.05f, 0);
        var config = clip.getAnimationConfigForEditing();
        config.setMode(com.director.core.container.AnimationMode.FADE_IN);
        config.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
        config.setDuration(0.0002f);
        config.setDelay(0);
        config.getSequenceParameters().setConcurrencyMode(
                com.director.core.animation.sequence.ConcurrencyMode.ALL_AT_ONCE);

        TimelineSamplingContext.run(TimelineSamplingContext.PREVIEW, () -> {
            assertFalse(controller.sampleClipFrame(clip, 0.00019f, true, container, config, true)
                    .visibility().renderBlocks().isEmpty());
            assertTrue(controller.sampleClipFrame(clip, 0.0002f, true, container, config, true)
                    .visibility().renderBlocks().isEmpty());
        });
    }

    @SuppressWarnings("unchecked")
    @Test void deterministicVisibilitySamplingBypassesThePreviewCache() throws Exception {
        var controller = controller();
        var container = new Container("building");
        container.addBlocks(List.of(BlockPos.ORIGIN, new BlockPos(1, 0, 0)));
        var clip = new TimelineClip("clip", 0, 4, 0);
        var cache = (Map<TimelineClip, ?>) field("visibleBlocksCacheByClip").get(controller);
        for (var context : List.of(TimelineSamplingContext.EXPORT, TimelineSamplingContext.HEADLESS)) {
            TimelineSamplingContext.run(context,
                    () -> controller.sampleClipFrame(clip, 1, true, container, clip.getAnimationConfigForEditing(), true));
            assertTrue(cache.isEmpty());
        }
        TimelineSamplingContext.run(TimelineSamplingContext.PREVIEW,
                () -> controller.sampleClipFrame(clip, 1, true, container, clip.getAnimationConfigForEditing(), true));
        assertEquals(1, cache.size());
    }
}
