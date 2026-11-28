package com.director.core.container;

import org.junit.jupiter.api.Test;
import com.director.core.DirectorServices;
import com.director.core.container.commands.BatchSetContainerStateCommand;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerGroupVisibilityTest {
    @Test
    void groupVisibilityMasksChildPreferencesWithoutOverwritingThem() {
        Container outer = new Container("outer", ContainerType.GROUP);
        Container inner = new Container("inner", ContainerType.GROUP);
        Container visibleChild = new Container("visible child", ContainerType.GENERAL);
        Container hiddenChild = new Container("hidden child", ContainerType.GENERAL);
        outer.addChild(inner);
        inner.addChild(visibleChild);
        inner.addChild(hiddenChild);
        hiddenChild.setState(ContainerState.HIDDEN, false);

        outer.setState(ContainerState.HIDDEN, false);

        assertTrue(visibleChild.isHidden());
        assertTrue(hiddenChild.isHidden());
        assertEquals(ContainerState.VISIBLE, visibleChild.getState());
        assertEquals(ContainerState.HIDDEN, hiddenChild.getState());

        visibleChild.setState(ContainerState.HIDDEN, false);
        outer.setState(ContainerState.VISIBLE, false);

        assertTrue(visibleChild.isHidden());
        assertTrue(hiddenChild.isHidden());
        assertEquals(ContainerState.HIDDEN, visibleChild.getState());
        assertEquals(ContainerState.HIDDEN, hiddenChild.getState());

        visibleChild.setState(ContainerState.VISIBLE, false);
        assertFalse(visibleChild.isHidden());
        assertTrue(hiddenChild.isHidden());
    }

    @Test
    void batchVisibilityUpdatesLocalPreferencesAndUndoRestoresThem() {
        DirectorServices.initialize();
        ContainerManager manager = DirectorServices.get().containers();
        Container group = new Container("group visibility test", ContainerType.GROUP);
        Container child = new Container("child", ContainerType.GENERAL);
        group.addChild(child);
        child.setState(ContainerState.HIDDEN, false);
        manager.addContainer(group);

        try {
            var command = new BatchSetContainerStateCommand(
                manager, List.of(group, child), ContainerState.VISIBLE);
            command.execute();
            assertEquals(ContainerState.VISIBLE, group.getState());
            assertEquals(ContainerState.VISIBLE, child.getState());
            assertFalse(child.isHidden());

            command.undo();
            assertEquals(ContainerState.VISIBLE, group.getState());
            assertEquals(ContainerState.HIDDEN, child.getState());
            assertTrue(child.isHidden());
        } finally {
            manager.removeContainer(group);
        }
    }

    @Test
    void blockStoreEntryCopyOwnsItsBlockEntityNbt() {
        NbtCompound originalNbt = new NbtCompound();
        originalNbt.putString("name", "source");
        var original = new BlockStore.Entry(null, originalNbt);
        var copy = original.copy();

        originalNbt.putString("name", "changed source");
        assertEquals("source", copy.beNbt.getString("name").orElseThrow());
        copy.beNbt.putString("name", "changed copy");
        assertEquals("changed source", original.beNbt.getString("name").orElseThrow());
    }
}
