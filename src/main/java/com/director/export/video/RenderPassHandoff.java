package com.director.export.video;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** A screenshot is usable only after its world pass and render-state restoration both succeed. */
final class RenderPassHandoff {
    private RenderPassHandoff() {}

    static <T> void completeAfter(CompletableFuture<T> result, CompletableFuture<Void> pass,
            T frame, Consumer<T> release) {
        pass.whenComplete((unused, failure) -> {
            if (failure != null) {
                release.accept(frame);
                result.completeExceptionally(failure);
            } else if (!result.complete(frame)) release.accept(frame);
        });
    }
}
