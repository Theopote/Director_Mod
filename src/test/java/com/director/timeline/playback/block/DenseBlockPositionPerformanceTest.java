package com.director.timeline.playback.block;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class DenseBlockPositionPerformanceTest {
    @Test void denseBuildingMembership() {
        var positions = new ArrayList<BlockPos>();
        for (int x = 0; x < 128; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < 32; y++) positions.add(new BlockPos(x - 500, y - 43, z + 1785));
        long started = System.nanoTime();
        Set<BlockPos> snapshot = BlockPositionSets.immutableCopy(positions);
        long built = System.nanoTime();
        for (BlockPos pos : positions) assertTrue(snapshot.contains(pos));
        System.out.printf("Dense positions: count=%d buildMs=%.2f lookupMs=%.2f%n", positions.size(),
                (built - started) / 1e6, (System.nanoTime() - built) / 1e6);
    }

    @Test void largeDenseSnapshotStaysImmutableAndFastAcrossFrames() {
        assertTimeout(Duration.ofSeconds(5), () -> {
            var positions = new ArrayList<BlockPos>();
            for (int x = 0; x < 256; x++)
                for (int z = 0; z < 48; z++)
                    for (int y = 0; y < 32; y++) positions.add(new BlockPos(x - 500, y - 43, z + 1785));
            var snapshot = BlockPositionSets.immutableCopy(positions);
            assertEquals(393216, snapshot.size());
            for (BlockPos pos : positions) assertTrue(snapshot.contains(pos));
            assertFalse(snapshot.contains(new BlockPos(-501, -43, 1785)));
            assertEquals(new HashSet<>(positions), snapshot);
            positions.clear();
            assertEquals(393216, snapshot.size());
            assertThrows(UnsupportedOperationException.class, snapshot::clear);
            var iterator = snapshot.iterator();
            iterator.next();
            assertThrows(UnsupportedOperationException.class, iterator::remove);
        });
    }
}
