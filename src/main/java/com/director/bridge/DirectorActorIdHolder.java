package com.director.bridge;

import java.util.UUID;

/**
 * Exposes actorId on render states used by preview actor rendering.
 */
public interface DirectorActorIdHolder {
    UUID director$getActorId();

    void director$setActorId(UUID actorId);
}
