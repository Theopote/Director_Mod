package com.director.lighting;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;

class LightRefreshTest {
    private final PointLight light = PointLight.create(0, 0, 0);
    @Test void scopesPinTerrainAndRestoreLiveSampling() {
        var builds = new LightBuildSnapshots();
        builds.publish(List.of(light));
        var live = List.of(light.settings(3, 8, 1, true));
        try (var outer = builds.openScope()) {
            assertEquals(List.of(light), builds.sampling(live));
            builds.publish(live);
            try (var inner = builds.openScope()) {
                assertEquals(List.of(light), builds.sampling(live));
            }
            assertEquals(List.of(light), builds.sampling(live));
        }
        assertEquals(live, builds.sampling(live));
    }
    @Test void jobCountsBeforeExecutionAndThroughUpload() {
        var builds = new LightBuildSnapshots();
        builds.publish(List.of(light));
        var executor = new ArrayList<Runnable>();
        var upload = new CompletableFuture<String>();
        var live = List.of(light.settings(2, 8, 1, true));
        var task = builds.submit(() -> {
            assertEquals(List.of(light), builds.sampling(live));
            return upload;
        }, executor::add);
        assertEquals(1, builds.activeJobs());
        assertFalse(builds.publish(live));
        executor.getFirst().run();
        assertFalse(task.join().isDone());
        assertEquals(1, builds.activeJobs());
        assertEquals(live, builds.sampling(live));
        assertFalse(builds.publish(live));
        upload.complete("uploaded");
        assertEquals("uploaded", task.join().join());
        assertEquals(0, builds.activeJobs());
        assertTrue(builds.publish(live));
    }
    @Test void failedSubmissionReleasesJob() {
        var builds = new LightBuildSnapshots();
        assertThrows(RejectedExecutionException.class, () -> builds.submit(
                () -> CompletableFuture.completedFuture(1), job -> { throw new RejectedExecutionException(); }));
        assertEquals(0, builds.activeJobs());
    }
    @Test void failedBuildReleasesJobAndThreadLocal() {
        var builds = new LightBuildSnapshots();
        builds.publish(List.of(light));
        var job = builds.submit(() -> { throw new IllegalStateException(); }, Runnable::run);
        assertThrows(CompletionException.class, job::join);
        assertEquals(0, builds.activeJobs());
        assertEquals(List.of(), builds.sampling(List.of()));
    }
    @Test void failedUploadReleasesJob() {
        var builds = new LightBuildSnapshots();
        var upload = new CompletableFuture<String>();
        var job = builds.submit(() -> upload, Runnable::run);
        upload.completeExceptionally(new IllegalStateException());
        assertThrows(CompletionException.class, () -> job.join().join());
        assertEquals(0, builds.activeJobs());
    }
    @Test void cancelledUploadReleasesJob() {
        var builds = new LightBuildSnapshots();
        var upload = new CompletableFuture<String>();
        builds.submit(() -> upload, Runnable::run);
        upload.cancel(false);
        assertEquals(0, builds.activeJobs());
    }
    @Test void worldResetSeparatesOldJobsFromNewGeneration() {
        var builds = new LightBuildSnapshots();
        builds.publish(List.of(light));
        var executor = new ArrayList<Runnable>();
        var oldUpload = new CompletableFuture<String>();
        builds.submit(() -> {
            assertEquals(List.of(light), builds.sampling(List.of()));
            return oldUpload;
        }, executor::add);
        builds.reset();
        assertEquals(0, builds.activeJobs());
        assertTrue(builds.publish(List.of()));
        var newUpload = new CompletableFuture<String>();
        builds.submit(() -> newUpload, Runnable::run);
        executor.getFirst().run();
        oldUpload.complete("old");
        assertEquals(1, builds.activeJobs());
        newUpload.complete("new");
        assertEquals(0, builds.activeJobs());
    }
    @Test void requestsCoalesceAndInvalidatePublishedOldPosition() {
        var builds = new LightBuildSnapshots();
        var refresh = new LightTerrainRefresh(builds);
        refresh.request(List.of(light));
        assertNotNull(refresh.publish(true));
        var upload = new CompletableFuture<String>();
        builds.submit(() -> upload, Runnable::run);
        refresh.request(List.of(light.at(100, 0, 0)));
        refresh.request(List.of(light.at(200, 0, 0)));
        assertNull(refresh.publish(true));
        assertTrue(refresh.awaiting(false));
        upload.complete("done");
        var bounds = refresh.publish(true);
        assertEquals(2, bounds.size());
        assertTrue(bounds.get(0).minX() < 0);
        assertTrue(bounds.get(1).minX() > 180);
        assertEquals(List.of(light.at(200, 0, 0)), builds.lights());
    }
    @Test void deletionWhileBuildingEventuallyPublishesDarkness() {
        var builds = new LightBuildSnapshots();
        var refresh = new LightTerrainRefresh(builds);
        refresh.request(List.of(light));
        refresh.publish(true);
        var upload = new CompletableFuture<String>();
        builds.submit(() -> upload, Runnable::run);
        refresh.request(List.of());
        assertNull(refresh.publish(true));
        upload.complete("done");
        assertEquals(1, refresh.publish(true).size());
        assertEquals(List.of(), builds.lights());
    }
    @Test void exportWaitsForRenderAfterPublishingAndForDirtyTerrain() {
        var refresh = new LightTerrainRefresh(new LightBuildSnapshots());
        refresh.request(List.of(light));
        assertTrue(refresh.awaiting(true));
        assertNull(refresh.publish(false));
        refresh.publish(true);
        refresh.requireRender();
        assertTrue(refresh.awaiting(true));
        refresh.request(List.of(light.settings(4, 8, 1, true)));
        assertNull(refresh.publish(true));
        refresh.rendered();
        assertTrue(refresh.awaiting(false));
        assertNotNull(refresh.publish(true));
        refresh.requireRender();
        assertTrue(refresh.awaiting(true));
        refresh.rendered();
        assertFalse(refresh.awaiting(true));
    }
    @Test void returningToPublishedValueDropsSupersededChanges() {
        var refresh = new LightTerrainRefresh(new LightBuildSnapshots());
        refresh.request(List.of(light));
        refresh.publish(true);
        refresh.request(List.of(light.at(100, 0, 0)));
        refresh.request(List.of(light));
        assertNull(refresh.publish(true));
        assertFalse(refresh.awaiting(true));
    }
    @Test void resetDropsPendingRequestAndRenderWait() {
        var refresh = new LightTerrainRefresh(new LightBuildSnapshots());
        refresh.request(List.of(light));
        refresh.requireRender();
        refresh.reset();
        assertFalse(refresh.awaiting(true));
        assertEquals(List.of(), refresh.requested());
    }
    @Test void unrelatedTerrainDoesNotDelayExportWithoutLightingWork() {
        var refresh = new LightTerrainRefresh(new LightBuildSnapshots());
        assertFalse(refresh.hasTerrainWork());
        assertFalse(refresh.awaiting(false));
    }
    @Test void lightingBatchStillWaitsAfterItsFirstRender() {
        var refresh = new LightTerrainRefresh(new LightBuildSnapshots());
        refresh.request(List.of(light));
        refresh.publish(true);
        refresh.requireRender();
        refresh.rendered();
        assertTrue(refresh.awaiting(false));
        assertFalse(refresh.awaiting(true));
        assertFalse(refresh.hasTerrainWork());
    }
    @Test void sectionBoundsIncludePositiveAndNegativeHalo() {
        var b = new PointLightChanges.Bounds(0, 0, 0, 15, 15, 15);
        assertTrue(LightSectionBounds.intersects(b, 0, 0, 0));
        assertTrue(LightSectionBounds.intersects(b, -1, 0, 0));
        assertTrue(LightSectionBounds.intersects(b, 1, 0, 0));
        assertTrue(LightSectionBounds.intersects(b, 0, -1, 0));
        assertTrue(LightSectionBounds.intersects(b, 0, 0, 1));
        assertFalse(LightSectionBounds.intersects(b, -2, 0, 0));
        assertFalse(LightSectionBounds.intersects(b, 0, 2, 0));
        assertFalse(LightSectionBounds.intersects(b, 0, 0, 2));
    }
    @Test void extremeBoundsDoNotOverflowHalo() {
        var b = new PointLightChanges.Bounds(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0);
        assertTrue(LightSectionBounds.intersects(b, -1_875_000, 0, 0));
        assertTrue(LightSectionBounds.intersects(b, 1_875_000, 0, 0));
        assertFalse(LightSectionBounds.intersects(b, 0, 10, 0));
    }
}
