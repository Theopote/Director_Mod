package com.director.actor.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Global holder for {@link ActorEditorOverrideProvider}; UI installs at director client init. */
public final class ActorEditorOverrides {
    private static final AtomicReference<ActorEditorOverrideProvider> PROVIDER =
            new AtomicReference<>(ActorEditorOverrideProvider.NONE);

    private ActorEditorOverrides() {}

    public static void install(ActorEditorOverrideProvider provider) {
        PROVIDER.set(provider == null ? ActorEditorOverrideProvider.NONE : provider);
    }

    public static ActorEditorOverrideProvider get() {
        return Objects.requireNonNullElse(PROVIDER.get(), ActorEditorOverrideProvider.NONE);
    }
}
