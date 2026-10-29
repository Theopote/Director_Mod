package com.director.timeline.playback.block;

import com.director.core.animation.sequence.BlockSequenceEntry;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
        return new BoundarySet(this, cutoff, exit, count);
    }

    /** Visits only the changed sequence interval when both sets belong to the same schedule. */
    static void forEachDifference(Set<BlockPos> included, Set<BlockPos> excluded, Consumer<BlockPos> action) {
        if (included == excluded || included.isEmpty()) return;
        SequenceVisibility owner = included instanceof BoundarySet a ? a.owner
                : excluded instanceof BoundarySet b ? b.owner : null;
        if (owner != null) {
            int[] a = owner.range(included), b = owner.range(excluded);
            if (a != null && b != null) {
                owner.visitRange(a[0], Math.min(a[1], b[0]), action);
                owner.visitRange(Math.max(a[0], b[1]), a[1], action);
                return;
            }
        }
        // Different schedules/content versions: retain correct set semantics without full copies.
        for (BlockPos pos : included) if (!excluded.contains(pos)) action.accept(pos);
    }

    private int[] range(Set<BlockPos> set) {
        if (set == all) return new int[] {0, entries.size() + 1};
        if (set.isEmpty()) return new int[] {0, 0};
        if (set instanceof BoundarySet view && view.owner == this)
            return view.exit ? new int[] {view.cutoff, entries.size() + 1} : new int[] {0, view.cutoff};
        return null;
    }

    /** Bounds of schedule entries represented by a frame, excluding legacy positions without entries. */
    int[] entryRange(Set<BlockPos> set) {
        int[] range = range(set);
        if (range != null) range[1] = Math.min(range[1], entries.size());
        return range;
    }

    private void visitRange(int from, int to, Consumer<BlockPos> action) {
        for (int i = from; i < Math.min(to, entries.size()); i++) {
            var entry = entries.get(i);
            if (entry != null && Objects.equals(ranks.get(entry.getPos()), i)) action.accept(entry.getPos());
        }
        // Positions missing from a legacy sequence belong to its exit complement and full set.
        if (from < to && from <= entries.size() && to > entries.size() && ranks.size() < all.size())
            for (BlockPos pos : all) if (!ranks.containsKey(pos)) action.accept(pos);
    }

    private static final class BoundarySet extends AbstractSet<BlockPos> {
        final SequenceVisibility owner;
        final int cutoff, count;
        final boolean exit;

        BoundarySet(SequenceVisibility owner, int cutoff, boolean exit, int count) {
            this.owner = owner; this.cutoff = cutoff; this.exit = exit; this.count = count;
        }

        @Override public int size() { return count; }
        @Override public boolean contains(Object pos) {
            if (!owner.all.contains(pos)) return false;
            Integer rank = owner.ranks.get(pos);
            boolean prefix = rank != null && rank < cutoff;
            return exit ? !prefix : prefix;
        }
        @Override public Iterator<BlockPos> iterator() {
            Iterator<?> source = exit ? owner.all.iterator() : owner.entries.subList(0, cutoff).iterator();
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
                            if (entry != null && Objects.equals(owner.ranks.get(entry.getPos()), index)) next = entry.getPos();
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
        @Override public boolean add(BlockPos pos) { throw new UnsupportedOperationException(); }
        @Override public boolean remove(Object pos) { throw new UnsupportedOperationException(); }
        @Override public boolean addAll(Collection<? extends BlockPos> positions) { throw new UnsupportedOperationException(); }
        @Override public boolean removeAll(Collection<?> positions) { throw new UnsupportedOperationException(); }
        @Override public boolean retainAll(Collection<?> positions) { throw new UnsupportedOperationException(); }
        @Override public boolean removeIf(Predicate<? super BlockPos> filter) { throw new UnsupportedOperationException(); }
        @Override public void clear() { throw new UnsupportedOperationException(); }
    }
}
