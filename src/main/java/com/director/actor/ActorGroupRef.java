package com.director.actor;

import java.util.Objects;
import java.util.UUID;

public final class ActorGroupRef {
    private final UUID groupId;

    public ActorGroupRef(UUID groupId) {
        this.groupId = Objects.requireNonNull(groupId, "groupId");
    }

    public static ActorGroupRef of(ActorGroup group) {
        if (group == null) {
            throw new IllegalArgumentException("group cannot be null");
        }
        return new ActorGroupRef(group.getGroupId());
    }

    public UUID getGroupId() {
        return groupId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorGroupRef that)) return false;
        return groupId.equals(that.groupId);
    }

    @Override
    public int hashCode() {
        return groupId.hashCode();
    }
}
