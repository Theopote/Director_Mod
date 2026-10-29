package com.director.core.container;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContainerGeometryRevisionTest {
    @Test void effectivePositionChangesAdvanceVersionAndMetadataAndNoOpsDoNot() {
        var container = new Container("building");
        long initial = container.getGeometryRevision();
        container.addBlocks(List.of(BlockPos.ORIGIN));
        long added = container.getGeometryRevision();
        assertTrue(added > initial);
        container.addBlocks(List.of(BlockPos.ORIGIN)); container.removeBlock(new BlockPos(9, 9, 9));
        container.setName("renamed"); container.setColor(42); container.setOpacity(0.5f);
        container.setState(ContainerState.HIDDEN, false); container.setState(ContainerState.VISIBLE, false);
        assertEquals(added, container.getGeometryRevision());
        container.removeBlock(BlockPos.ORIGIN);
        assertTrue(container.getGeometryRevision() > added);
        long removed = container.getGeometryRevision();
        container.addBlocks(List.of(new BlockPos(1, 0, 0)));
        assertTrue(container.getGeometryRevision() > removed);
        long replaced = container.getGeometryRevision();
        container.clearBlocks();
        assertTrue(container.getGeometryRevision() > replaced);
    }
}
