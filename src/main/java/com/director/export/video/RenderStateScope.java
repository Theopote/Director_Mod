package com.director.export.video;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Restores all changed render state in reverse order, even after a partial setup failure. */
final class RenderStateScope implements AutoCloseable {
    private final Deque<Runnable> undo = new ArrayDeque<>();

    void restoreWith(Runnable restore) { undo.push(restore); }

    <T> void replace(Supplier<T> read, Consumer<T> write, T replacement) {
        T previous = read.get();
        restoreWith(() -> write.accept(previous));
        write.accept(replacement);
    }

    @Override public void close() {
        Throwable first = null;
        while (!undo.isEmpty()) {
            try { undo.pop().run(); }
            catch (RuntimeException | Error failure) {
                if (first == null) first = failure;
                else if (first != failure) first.addSuppressed(failure);
            }
        }
        if (first instanceof RuntimeException failure) throw failure;
        if (first instanceof Error failure) throw failure;
    }
}
