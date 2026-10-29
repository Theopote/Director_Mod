package com.director.export.video;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameCaptureQueueTest {
    @Test void requestArrivingBetweenHeadAndTailMustWaitForNextWorldRender() {
        var queue = new FrameCaptureQueue<Object>();
        assertNull(queue.beginPreparation());
        Object late = new Object();
        queue.add(late);
        assertNull(queue.takePrepared());
        assertSame(late, queue.beginPreparation());
        queue.markPrepared(late);
        assertSame(late, queue.takePrepared());
        assertNull(queue.takePrepared());
    }

    @Test void canceledPreparedRequestCannotCaptureItsReplacement() {
        var queue = new FrameCaptureQueue<Object>();
        Object canceled = new Object(), replacement = new Object();
        queue.add(canceled);
        queue.markPrepared(queue.beginPreparation());
        queue.remove(canceled);
        queue.add(replacement);
        assertNull(queue.takePrepared());
        assertSame(replacement, queue.beginPreparation());
        queue.markPrepared(replacement);
        assertSame(replacement, queue.takePrepared());
    }

    @Test void terrainWaitAllowsResamplingButNeverReusesPreviousPreparation() {
        var queue = new FrameCaptureQueue<Object>();
        Object request = new Object();
        queue.add(request);
        queue.markPrepared(queue.beginPreparation());
        assertSame(request, queue.takePrepared());
        // The terrain was still rebuilding, so the request remains pending.
        assertSame(request, queue.beginPreparation());
        assertNull(queue.takePrepared());
        queue.markPrepared(queue.beginPreparation());
        assertSame(request, queue.takePrepared());
    }
}
