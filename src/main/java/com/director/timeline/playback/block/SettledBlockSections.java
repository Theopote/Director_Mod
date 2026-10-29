package com.director.timeline.playback.block;

import com.director.core.container.visibility.ClientSectionInvalidation;
import net.minecraft.util.math.BlockPos;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Predicate;

/** Only entirely normal, world-ready sections can sleep; overlays are rebuilt every frame. */
final class SettledBlockSections {
    record Visits(long checked, long skipped) {}
    private static final class Section {
        final long key;
        final List<BlockPos> positions = new ArrayList<>();
        Object proof;
        boolean ready;
        Section(long key) { this.key = key; }
    }

    private final ClientSectionInvalidation changes;
    private Set<BlockPos> positions;
    private WeakReference<Object> world = new WeakReference<>(null);
    private long snapshotRevision;
    private List<Section> sections = List.of();

    SettledBlockSections(ClientSectionInvalidation changes) { this.changes = changes; }

    Visits visit(Object currentWorld, Set<BlockPos> visible, long contentRevision, Predicate<BlockPos> verify) {
        if (positions != visible) {
            var grouped = new LinkedHashMap<Long, Section>();
            for (BlockPos pos : visible) {
                long key = ClientSectionInvalidation.section(pos);
                grouped.computeIfAbsent(key, Section::new).positions.add(pos);
            }
            sections = new ArrayList<>(grouped.values());
            positions = visible;
            world.clear();
        }
        if (world.get() != currentWorld || snapshotRevision != contentRevision) {
            for (var section : sections) section.ready = false;
            world = new WeakReference<>(currentWorld);
            snapshotRevision = contentRevision;
        }
        long checked = 0, skipped = 0;
        for (var section : sections) {
            if (section.ready && changes.unchanged(currentWorld, section.key, section.proof)) {
                skipped += section.positions.size();
                continue;
            }
            Object proof = changes.token(currentWorld, section.key);
            boolean ready = true;
            for (BlockPos pos : section.positions) {
                if (!verify.test(pos)) ready = false;
                checked++;
            }
            section.ready = ready && changes.unchanged(currentWorld, section.key, proof);
            section.proof = proof;
        }
        return new Visits(checked, skipped);
    }
}
