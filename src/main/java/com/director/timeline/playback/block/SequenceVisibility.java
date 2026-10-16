package com.director.timeline.playback.block;

import com.director.core.animation.sequence.BlockSequenceEntry;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Immutable sequence-boundary views. Old frames remain safe for asynchronous server consumers. */
final class SequenceVisibility {
    record Frame(Set<BlockPos> render, Set<BlockPos> world) {}
    private final List<BlockSequenceEntry> entries;
    private final Set<BlockPos> all;
    private final Map<BlockPos, Integer> ranks = new HashMap<>();
    private final int[] sizes;
    private int lastRender = -1, lastWorld = -1;
    private boolean lastExit;
    private Frame lastFrame;

    SequenceVisibility(List<BlockSequenceEntry> entries, Set<BlockPos> all) {
        this.entries = entries;
        this.all = all;
        sizes = new int[entries.size() + 1];
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry != null && entry.getPos() != null && all.contains(entry.getPos()))
                ranks.putIfAbsent(entry.getPos(), i);
            sizes[i + 1] = ranks.size();
        }
    }

    Frame sample(int renderCutoff, int worldCutoff, boolean exit) {
        int render = Math.clamp(renderCutoff, 0, entries.size());
        int world = Math.clamp(worldCutoff, 0, entries.size());
        if (lastFrame != null && render == lastRender && world == lastWorld && exit == lastExit) return lastFrame;
        Set<BlockPos> renderSet = lastFrame != null && render == lastRender && exit == lastExit
                ? lastFrame.render() : view(render, exit);
        Set<BlockPos> worldSet = world == render ? renderSet
                : lastFrame != null && world == lastWorld && exit == lastExit ? lastFrame.world() : view(world, exit);
        lastRender = render; lastWorld = world; lastExit = exit;
        return lastFrame = new Frame(renderSet, worldSet);
    }

    private Set<BlockPos> view(int cutoff, boolean exit) {
        int count = exit ? all.size() - sizes[cutoff] : sizes[cutoff];
        if (count == 0) return Set.of();
        if (count == all.size()) return all;
        return Collections.unmodifiableSet(new AbstractSet<>() {
            @Override public int size() { return count; }
            @Override public boolean contains(Object pos) {
                if (!all.contains(pos)) return false;
                Integer rank = ranks.get(pos);
                boolean prefix = rank != null && rank < cutoff;
                return exit ? !prefix : prefix;
            }
            @Override public Iterator<BlockPos> iterator() {
                Iterator<?> source = exit ? all.iterator() : entries.subList(0, cutoff).iterator();
                return new Iterator<>() {
                    BlockPos next;
                    int index;
                    @Override public boolean hasNext() {
                        while (next == null && source.hasNext()) {
                            Object item = source.next();
                            if (exit) {
                                BlockPos pos = (BlockPos) item;
                                if (contains(pos)) next = pos;
                            } else {
                                var entry = (BlockSequenceEntry) item;
                                if (entry != null && Objects.equals(ranks.get(entry.getPos()), index)) next = entry.getPos();
                                index++;
                            }
                        }
                        return next != null;
                    }
                    @Override public BlockPos next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        BlockPos result = next; next = null; return result;
                    }
                };
            }
        });
    }
}
