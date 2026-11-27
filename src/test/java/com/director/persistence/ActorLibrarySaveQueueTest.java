package com.director.persistence;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorLibrarySaveQueueTest {
    @Test void failedSnapshotRetainsItsWorldAndDoesNotReplaceAnotherWorld() throws InterruptedException {
        var first = Path.of("world-a", "actors.json");
        var second = Path.of("world-b", "actors.json");
        var written = new ConcurrentHashMap<Path, String>();
        var failFirst = new AtomicBoolean(true);
        var retried = new CountDownLatch(1);
        var queue = new ActorLibrarySaveQueue("ActorSaveQueueTest", (target, snapshot) -> {
            if (target.equals(first) && failFirst.getAndSet(false)) return false;
            written.put(target, snapshot);
            if (target.equals(first)) retried.countDown();
            return true;
        });

        assertFalse(queue.flush(first, "snapshot A"));
        assertTrue(queue.flush(second, "snapshot B"));
        assertTrue(retried.await(8, TimeUnit.SECONDS));
        assertEquals("snapshot A", written.get(first));
        assertEquals("snapshot B", written.get(second));
    }

    @Test void explicitFlushReplacesTheQueuedSnapshotForTheSamePath() {
        var target = Path.of("world", "paths.json");
        var written = new ConcurrentHashMap<Path, String>();
        var queue = new ActorLibrarySaveQueue("ActorSaveQueueTest", (file, snapshot) -> {
            written.put(file, snapshot);
            return true;
        });
        queue.submit(target, "older");
        assertTrue(queue.flush(target, "newer"));
        assertEquals("newer", written.get(target));
    }
}
