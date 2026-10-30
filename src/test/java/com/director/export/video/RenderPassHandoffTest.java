package com.director.export.video;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RenderPassHandoffTest {
    @Test void capturedPixelsWaitForSuccessfulRenderStateRestoration() {
        var result = new CompletableFuture<String>();
        var pass = new CompletableFuture<Void>();
        var closes = new AtomicInteger();
        RenderPassHandoff.completeAfter(result, pass, "pixels", frame -> closes.incrementAndGet());
        assertFalse(result.isDone());
        pass.complete(null);
        assertEquals("pixels", result.join());
        assertEquals(0, closes.get());
    }

    @Test void failedRestorationCannotPublishAnAlreadyCapturedFrame() {
        var result = new CompletableFuture<String>();
        var pass = new CompletableFuture<Void>();
        var closes = new AtomicInteger();
        RenderPassHandoff.completeAfter(result, pass, "pixels", frame -> closes.incrementAndGet());
        pass.completeExceptionally(new IllegalStateException("restore failed"));
        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, closes.get());
    }

    @Test void lateScreenshotAfterFailedPassIsReleasedImmediately() {
        var result = new CompletableFuture<String>();
        var pass = new CompletableFuture<Void>();
        var closes = new AtomicInteger();
        pass.completeExceptionally(new IllegalStateException("render failed"));
        RenderPassHandoff.completeAfter(result, pass, "pixels", frame -> closes.incrementAndGet());
        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, closes.get());
    }

    @Test void canceledRequestReleasesItsFrameAfterThePassFinishes() {
        var result = new CompletableFuture<String>();
        var pass = new CompletableFuture<Void>();
        var closes = new AtomicInteger();
        RenderPassHandoff.completeAfter(result, pass, "pixels", frame -> closes.incrementAndGet());
        result.cancel(false);
        pass.complete(null);
        assertEquals(1, closes.get());
        assertTrue(result.isCancelled());
    }
}
