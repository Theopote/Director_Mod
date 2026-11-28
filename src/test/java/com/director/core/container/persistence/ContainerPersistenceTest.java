package com.director.core.container.persistence;

import com.director.core.DirectorServices;
import com.director.core.container.AnimationMode;
import com.director.core.container.Container;
import com.director.core.container.ContainerAnimationPreset;
import com.director.core.container.ContainerType;
import com.director.core.container.ContainerManager;
import com.director.persistence.AtomicFileWriter;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.util.WorldIdentifier;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.Uuids;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void gzipNbtRoundTripPreservesMetadataPresetAndAuditTimestamps() throws IOException {
        UUID id = UUID.randomUUID();
        Container source = new Container(id, "Persisted container", ContainerType.GENERAL);
        source.setColor(0x12345678);
        source.setOpacity(0.375f);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("description", "描述 with unicode 数据");
        metadata.put("tagColor", 0xFF112233);
        metadata.put("titleColor", 0x44556677);
        metadata.put("iconColor", 0x8899AABB);
        metadata.put("string", "value");
        metadata.put("boolean", Boolean.TRUE);
        metadata.put("byte", Byte.valueOf((byte) 7));
        metadata.put("short", Short.valueOf((short) 1234));
        metadata.put("integer", Integer.valueOf(123456));
        metadata.put("long", Long.valueOf(9876543210L));
        metadata.put("float", Float.valueOf(1.25f));
        metadata.put("double", Double.valueOf(2.5d));
        metadata.put("uuid", UUID.randomUUID());
        metadata.put("nullValue", null);
        metadata.forEach(source::setMetadata);

        ContainerAnimationPreset preset = new ContainerAnimationPreset(
            AnimationMode.FLY_IN, 1.75f, 0.25f, "EASE_IN_OUT_CUBIC", "Parallel");
        preset.setParam("distance", 12.5f);
        preset.setParam("enabled", Boolean.TRUE);
        preset.setParam("repeat", Integer.valueOf(3));
        preset.setParam("label", "entry");
        source.setDefaultAnimationPreset(preset);
        source.restoreTimestampsForRecovery(1234L, 5678L);

        NbtCompound root = new NbtCompound();
        NbtList containers = new NbtList();
        containers.add(ContainerPersistence.encodeContainer(source));
        root.put("containers", containers);
        Path file = tempDir.resolve("world/director/containers.dat");
        AtomicFileWriter.write(file, temp -> NbtIo.writeCompressed(root, temp));

        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
        NbtCompound loadedRoot = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        Container restored = ContainerPersistence.decodeContainer(
            loadedRoot.getList("containers").orElseThrow().getCompound(0).orElseThrow());

        assertEquals(id, restored.getId());
        assertEquals("Persisted container", restored.getName());
        assertEquals(0x12345678, restored.getColor());
        assertEquals(0.375f, restored.getOpacity());
        assertEquals(1234L, restored.getCreatedTime());
        assertEquals(5678L, restored.getModifiedTime());
        metadata.forEach((key, expected) -> {
            assertTrue(restored.hasMetadata(key), key);
            Object actual = restored.getMetadata(key);
            assertEquals(expected, actual, key);
            if (expected != null) {
                assertEquals(expected.getClass(), actual.getClass(), key);
            }
        });

        ContainerAnimationPreset restoredPreset = restored.getDefaultAnimationPreset();
        assertEquals(AnimationMode.FLY_IN, restoredPreset.getMode());
        assertEquals(1.75f, restoredPreset.getSuggestedDuration());
        assertEquals(0.25f, restoredPreset.getDelay());
        assertEquals("EASE_IN_OUT_CUBIC", restoredPreset.getEasing());
        assertEquals("Parallel", restoredPreset.getOrdering());
        assertEquals(preset.getParams(), restoredPreset.getParams());

        // The committed NBT is a snapshot, independent of later in-memory preset/metadata edits.
        ContainerAnimationPreset changedPreset = source.getDefaultAnimationPreset();
        changedPreset.setMode(AnimationMode.INSTANT);
        changedPreset.setParam("distance", 99.0f);
        source.setDefaultAnimationPreset(changedPreset);
        source.setMetadata("description", "changed after save");
        Container restoredAgain = ContainerPersistence.decodeContainer(
            loadedRoot.getList("containers").orElseThrow().getCompound(0).orElseThrow());
        assertEquals(AnimationMode.FLY_IN, restoredAgain.getDefaultAnimationPreset().getMode());
        assertEquals(12.5f, restoredAgain.getDefaultAnimationPreset().getParam("distance"));
        assertEquals("描述 with unicode 数据", restoredAgain.getMetadata("description"));
    }

    @Test
    void defaultAnimationPresetGetterReturnsDetachedCopy() {
        Container source = new Container("Preset copy", ContainerType.GENERAL);
        ContainerAnimationPreset original = source.getDefaultAnimationPreset();
        original.setMode(AnimationMode.FLY_IN);
        original.setParam("distance", 12.5f);
        source.setDefaultAnimationPreset(original);

        ContainerAnimationPreset detached = source.getDefaultAnimationPreset();
        assertNotSame(original, detached);
        assertNotSame(original.getParams(), detached.getParams());
        detached.setMode(AnimationMode.EXPLODE);
        detached.setDelay(9.0f);
        detached.getParams().clear();

        ContainerAnimationPreset stored = source.getDefaultAnimationPreset();
        assertEquals(AnimationMode.FLY_IN, stored.getMode());
        assertEquals(0.0f, stored.getDelay());
        assertEquals(12.5f, stored.getParam("distance"));

        original.setParam("distance", 99.0f);
        assertEquals(12.5f, source.getDefaultAnimationPreset().getParam("distance"));
    }

    @Test
    void legacyNbtWithoutMetadataOrPresetKeepsCompatibleDefaults() {
        UUID id = UUID.randomUUID();
        NbtCompound legacy = new NbtCompound();
        legacy.putIntArray("id", Uuids.toIntArray(id));
        legacy.putString("name", "Legacy");
        legacy.putString("type", ContainerType.GENERAL.name());
        legacy.putLong("created", 1000L);
        legacy.putLong("modified", 2000L);

        Container restored = ContainerPersistence.decodeContainer(legacy);

        assertEquals(id, restored.getId());
        assertEquals("Legacy", restored.getName());
        assertEquals(1000L, restored.getCreatedTime());
        assertEquals(2000L, restored.getModifiedTime());
        assertTrue(restored.getMetadataView().isEmpty());
        assertEquals(ContainerAnimationPreset.defaultPreset().getMode(),
            restored.getDefaultAnimationPreset().getMode());
        assertEquals(ContainerAnimationPreset.DEFAULT_SUGGESTED_DURATION,
            restored.getDefaultAnimationPreset().getSuggestedDuration());
        assertTrue(restored.getDefaultAnimationPreset().getParams().isEmpty());
    }

    @Test
    void missingTimestampsReceiveConstructionDefaults() {
        NbtCompound legacy = new NbtCompound();
        legacy.putIntArray("id", Uuids.toIntArray(UUID.randomUUID()));
        legacy.putString("name", "Legacy without timestamps");
        legacy.putString("type", ContainerType.GENERAL.name());
        long before = System.currentTimeMillis();

        Container restored = ContainerPersistence.decodeContainer(legacy);

        long after = System.currentTimeMillis();
        assertTrue(restored.getCreatedTime() >= before && restored.getCreatedTime() <= after);
        assertTrue(restored.getModifiedTime() >= before && restored.getModifiedTime() <= after);
    }

    @Test
    void unsupportedAndUnknownMetadataValuesDoNotAbortRoundTrip() {
        Container source = new Container("Tolerant metadata", ContainerType.GENERAL);
        source.setMetadata("supported", "kept");
        source.setMetadata("unsupported", new Object());
        ContainerAnimationPreset preset = source.getDefaultAnimationPreset();
        preset.setParam("supported", 1.5f);
        preset.setParam("unsupported", new Object());
        source.setDefaultAnimationPreset(preset);

        NbtCompound encoded = ContainerPersistence.encodeContainer(source);
        NbtCompound metadata = encoded.getCompound("metadata").orElseThrow();
        assertFalse(metadata.getKeys().contains("unsupported"));
        NbtCompound unknown = new NbtCompound();
        unknown.putString("type", "future-type");
        metadata.put("unknown", unknown);
        encoded.put("metadata", metadata);

        Container restored = ContainerPersistence.decodeContainer(encoded);
        assertEquals("kept", restored.getMetadata("supported"));
        assertFalse(restored.hasMetadata("unsupported"));
        assertFalse(restored.hasMetadata("unknown"));
        assertEquals(1.5f, restored.getDefaultAnimationPreset().getParam("supported"));
        assertFalse(restored.getDefaultAnimationPreset().getParams().containsKey("unsupported"));
    }

    @Test
    void invalidPresetModeFallsBackWithoutLosingOtherPresetFields() {
        Container source = new Container("Invalid preset", ContainerType.GENERAL);
        ContainerAnimationPreset preset = source.getDefaultAnimationPreset();
        preset.setMode(AnimationMode.EXPLODE);
        preset.setDelay(0.5f);
        preset.setParam("strength", 0.75f);
        source.setDefaultAnimationPreset(preset);
        NbtCompound encoded = ContainerPersistence.encodeContainer(source);
        encoded.getCompound("defaultAnimationPreset").orElseThrow().putString("mode", "NOT_A_MODE");

        Container restored = ContainerPersistence.decodeContainer(encoded);

        assertEquals(AnimationMode.INSTANT, restored.getDefaultAnimationPreset().getMode());
        assertEquals(0.5f, restored.getDefaultAnimationPreset().getDelay());
        assertEquals(0.75f, restored.getDefaultAnimationPreset().getParam("strength"));
    }

    @Test
    void duplicateUuidRecoveryIsStableAcrossFileOrderAndReEncoding() {
        UUID originalId = UUID.randomUUID();
        for (boolean reverse : List.of(false, true)) {
            Container first = new Container(originalId, "First occurrence", ContainerType.GENERAL);
            Container second = new Container(originalId, "Second occurrence", ContainerType.GENERAL);
            NbtList list = new NbtList();
            List<Container> fileOrder = reverse ? List.of(second, first) : List.of(first, second);
            fileOrder.forEach(container -> list.add(ContainerPersistence.encodeContainer(container)));

            ContainerPersistence.DecodedContainers decoded = ContainerPersistence.decodeContainers(list);

            assertEquals(1, decoded.repairedDuplicateIds());
            assertEquals(0, decoded.isolatedParentReferences());
            assertEquals(2, decoded.roots().size());
            Container retained = decoded.orderedContainers().getFirst();
            Container repaired = decoded.orderedContainers().get(1);
            assertEquals(originalId, retained.getId());
            assertNotEquals(originalId, repaired.getId());
            assertNotEquals(retained.getId(), repaired.getId());
            assertEquals(Map.of(
                originalId,
                Map.of(retained.getId(), retained.getName(), repaired.getId(), repaired.getName())),
                decoded.recoveryTargets());

            NbtList reencoded = new NbtList();
            decoded.orderedContainers().forEach(container -> reencoded.add(ContainerPersistence.encodeContainer(container)));
            ContainerPersistence.DecodedContainers secondLoad = ContainerPersistence.decodeContainers(reencoded);
            assertEquals(0, secondLoad.repairedDuplicateIds());
            assertTrue(secondLoad.recoveryTargets().isEmpty());
            assertEquals(decoded.orderedContainers().stream().map(Container::getId).toList(),
                secondLoad.orderedContainers().stream().map(Container::getId).toList());
        }
    }

    @Test
    void duplicateChildrenKeepTheirUniqueParentAndOriginalAuditTimestamps() {
        UUID parentId = UUID.randomUUID();
        UUID duplicateId = UUID.randomUUID();
        List<NbtCompound> tags = new ArrayList<>();
        tags.add(containerTag(duplicateId, "Child A", parentId, 2000L, 3000L));
        tags.add(containerTag(parentId, "Unique parent", null, 1000L, 1500L));
        tags.add(containerTag(duplicateId, "Child B", parentId, 4000L, 5000L));
        NbtList list = new NbtList();
        tags.forEach(list::add);

        ContainerPersistence.DecodedContainers decoded = ContainerPersistence.decodeContainers(list);

        assertEquals(1, decoded.repairedDuplicateIds());
        assertEquals(0, decoded.isolatedParentReferences());
        assertEquals(1, decoded.roots().size());
        Container parent = decoded.roots().getFirst();
        assertEquals(parentId, parent.getId());
        assertEquals(2, parent.getChildren().size());
        assertEquals(1000L, parent.getCreatedTime());
        assertEquals(1500L, parent.getModifiedTime());
        assertEquals(2, decoded.recoveryTargets().get(duplicateId).size());
        for (Container child : parent.getChildren()) {
            assertSame(parent, child.getParent());
            long[] expected = child.getName().equals("Child A") ? new long[] {2000L, 3000L}
                : new long[] {4000L, 5000L};
            assertEquals(expected[0], child.getCreatedTime());
            assertEquals(expected[1], child.getModifiedTime());
        }
    }

    @Test
    void parentReferenceToDuplicateUuidIsIsolatedRegardlessOfFileOrder() {
        UUID duplicateParentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        for (boolean childFirst : List.of(false, true)) {
            NbtCompound parentA = containerTag(duplicateParentId, "Parent A", null, 1L, 2L);
            NbtCompound parentB = containerTag(duplicateParentId, "Parent B", null, 3L, 4L);
            NbtCompound child = containerTag(childId, "Ambiguous child", duplicateParentId, 5L, 6L);
            NbtList list = new NbtList();
            if (childFirst) {
                list.add(child);
                list.add(parentB);
                list.add(parentA);
            } else {
                list.add(parentA);
                list.add(parentB);
                list.add(child);
            }

            ContainerPersistence.DecodedContainers decoded = ContainerPersistence.decodeContainers(list);

            assertEquals(1, decoded.repairedDuplicateIds());
            assertEquals(1, decoded.isolatedParentReferences());
            assertEquals(3, decoded.roots().size());
            Container restoredChild = decoded.orderedContainers().stream()
                .filter(container -> container.getId().equals(childId))
                .findFirst()
                .orElseThrow();
            assertNull(restoredChild.getParent());
            assertEquals(5L, restoredChild.getCreatedTime());
            assertEquals(6L, restoredChild.getModifiedTime());
            assertEquals(2, decoded.recoveryTargets().get(duplicateParentId).size());
        }
    }

    @Test
    void cyclicParentReferencesRemainIsolatedInsteadOfCreatingACycle() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        NbtList list = new NbtList();
        list.add(containerTag(firstId, "First", secondId, 1L, 2L));
        list.add(containerTag(secondId, "Second", firstId, 3L, 4L));

        ContainerPersistence.DecodedContainers decoded = ContainerPersistence.decodeContainers(list);
        Container first = decoded.orderedContainers().getFirst();
        Container second = decoded.orderedContainers().get(1);

        assertFalse(first == second.getParent() && second == first.getParent());
        assertFalse(first.isDescendantOf(first));
        assertFalse(second.isDescendantOf(second));
    }

    @Test
    void recoveryMappingSurvivesContainerReloadBeforeTimelineLoad() throws IOException {
        DirectorServices.initialize();
        ContainerTimelineService service = ContainerTimelineService.requireInitialized();
        TimelineModel previousModel = service.getTimelineModel();
        service.setTimelineModel(null);
        service.clearContainerRecoveryMapping();
        WorldIdentifier.overrideWorldSavePathForTesting(tempDir);
        UUID originalId = UUID.randomUUID();
        NbtCompound root = new NbtCompound();
        NbtList duplicates = new NbtList();
        duplicates.add(ContainerPersistence.encodeContainer(
            new Container(originalId, "First", ContainerType.GENERAL)));
        duplicates.add(ContainerPersistence.encodeContainer(
            new Container(originalId, "Second", ContainerType.GENERAL)));
        root.put("containers", duplicates);
        AtomicFileWriter.write(tempDir.resolve("director/containers.dat"),
            temp -> NbtIo.writeCompressed(root, temp));

        try {
            // A non-global manager still exercises the production constructor/load/save path.
            ContainerManager manager = new ContainerManager();
            UUID recoveredId = manager.getContainer("Second").getId();
            assertNotEquals(originalId, recoveredId);
            NbtCompound repairedFile = NbtIo.readCompressed(
                tempDir.resolve("director/containers.dat"), NbtSizeTracker.ofUnlimitedBytes());
            assertTrue(repairedFile.getCompound("uuidRecovery").isPresent());

            // Simulate a process restart: only the persisted alias map can repair old timeline JSON.
            service.clearContainerRecoveryMapping();
            ContainerPersistence.loadForCurrentWorld(manager);
            assertEquals(recoveredId, manager.getContainer("Second").getId());

            TimelineModel staleTimeline = new TimelineModel(new TimelineViewportState());
            staleTimeline.setSuppressPersistenceWrites(true);
            TimelineClip clip = new TimelineClip("legacy binding", 0, 4, 0);
            clip.setBoundContainerId(originalId);
            clip.setBoundContainerName("Second");
            TimelineTrack track = new TimelineTrack("containers", TrackType.CONTAINER);
            assertTrue(track.addClip(clip));
            staleTimeline.addTrack(track);

            service.setTimelineModel(staleTimeline);

            assertEquals(recoveredId, clip.getBoundContainerId());
            assertEquals("Second", clip.getBoundContainerName());
        } finally {
            service.clearContainerRecoveryMapping();
            service.setTimelineModel(previousModel);
            WorldIdentifier.overrideWorldSavePathForTesting(null);
        }
    }

    private static NbtCompound containerTag(UUID id, String name, UUID parentId,
                                            long created, long modified) {
        Container container = new Container(id, name, ContainerType.GENERAL);
        container.restoreTimestampsForRecovery(created, modified);
        NbtCompound tag = ContainerPersistence.encodeContainer(container);
        if (parentId != null) {
            tag.putIntArray("parent", Uuids.toIntArray(parentId));
        }
        return tag;
    }
}
