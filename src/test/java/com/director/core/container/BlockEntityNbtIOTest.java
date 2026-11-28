package com.director.core.container;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockEntityNbtIOTest {
    @Test void snapshotComparisonIgnoresPositionAndAllowsAdditionalRuntimeData() {
        NbtCompound expected = new NbtCompound();
        expected.putString("id", "minecraft:chest");
        expected.putInt("x", 1);
        expected.putInt("y", 2);
        expected.putInt("z", 3);
        NbtCompound items = new NbtCompound();
        items.putInt("Count", 4);
        expected.put("Items", items);

        NbtCompound actual = expected.copy();
        actual.putInt("x", 9);
        actual.putInt("y", 9);
        actual.putInt("z", 9);
        actual.putLong("LastTicked", 42);
        NbtCompound actualItems = actual.getCompound("Items").orElseThrow();
        actualItems.putInt("RuntimeSlot", 1);

        assertTrue(BlockEntityNbtIO.matches(expected, actual));
    }

    @Test void snapshotComparisonRejectsBusinessDataTypeAndListDifferences() {
        NbtCompound expected = new NbtCompound();
        expected.putString("id", "minecraft:chest");
        expected.putInt("Count", 4);

        NbtCompound wrongType = expected.copy();
        wrongType.putString("id", "minecraft:barrel");
        NbtCompound wrongValue = expected.copy();
        wrongValue.putInt("Count", 3);
        NbtCompound wrongElementType = expected.copy();
        wrongElementType.putString("Count", "4");

        assertFalse(BlockEntityNbtIO.matches(expected, wrongType));
        assertFalse(BlockEntityNbtIO.matches(expected, wrongValue));
        assertFalse(BlockEntityNbtIO.matches(expected, wrongElementType));
        assertFalse(BlockEntityNbtIO.matches(expected, null));
    }
}
