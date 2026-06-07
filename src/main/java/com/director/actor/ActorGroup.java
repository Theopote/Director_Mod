package com.director.actor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Project-level actor group / troupe.
 *
 * <p>ActorGroup stores membership and group-level staging policy. It does not
 * own timeline clips and it does not hold live entity instances.</p>
 */
public final class ActorGroup {
    public enum FormationType {
        FREE,
        LINE,
        CIRCLE,
        GRID,
        FOLLOW_LEADER
    }

    public enum DelayPolicy {
        NONE,
        SEQUENTIAL,
        WAVE,
        RANDOM
    }

    private final UUID groupId;
    private String name;
    private final Set<UUID> actorIds = new LinkedHashSet<>();
    private FormationType formationType = FormationType.FREE;
    private DelayPolicy delayPolicy = DelayPolicy.NONE;
    private float delayStepSeconds = 0.1f;

    public ActorGroup(String name) {
        this(UUID.randomUUID(), name);
    }

    public ActorGroup(UUID groupId, String name) {
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.name = (name == null || name.isBlank()) ? "Actor Group" : name.trim();
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }

    public List<UUID> getActorIds() {
        return List.copyOf(actorIds);
    }

    public boolean addActor(UUID actorId) {
        return actorId != null && actorIds.add(actorId);
    }

    public boolean removeActor(UUID actorId) {
        return actorId != null && actorIds.remove(actorId);
    }

    public boolean containsActor(UUID actorId) {
        return actorId != null && actorIds.contains(actorId);
    }

    public void clearActors() {
        actorIds.clear();
    }

    public int size() {
        return actorIds.size();
    }

    public FormationType getFormationType() {
        return formationType;
    }

    public void setFormationType(FormationType formationType) {
        this.formationType = formationType != null ? formationType : FormationType.FREE;
    }

    public DelayPolicy getDelayPolicy() {
        return delayPolicy;
    }

    public void setDelayPolicy(DelayPolicy delayPolicy) {
        this.delayPolicy = delayPolicy != null ? delayPolicy : DelayPolicy.NONE;
    }

    public float getDelayStepSeconds() {
        return delayStepSeconds;
    }

    public void setDelayStepSeconds(float delayStepSeconds) {
        this.delayStepSeconds = Math.max(0.0f, delayStepSeconds);
    }
}
