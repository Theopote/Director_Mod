package com.director.actor.preview;

import com.director.actor.ActorType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.VillagerEntity;

/** Client previews that keep the vanilla mesh and accept a sampled pose. */
public final class NativeMobActorEntities {
    private NativeMobActorEntities() {}

    public static Entity create(ActorType type, ClientWorld world) {
        return switch (type) {
            case ZOMBIE -> new Zombie(world);
            case SKELETON -> new Skeleton(world);
            case VILLAGER -> new Villager(world);
            case COW -> new Cow(world);
            case SHEEP -> new Sheep(world);
            case PLAYER, CUSTOM -> null;
        };
    }

    public static final class Zombie extends ZombieEntity implements NativeMobActorPreview {
        private final NativeActorPoseCarrier pose = new NativeActorPoseCarrier();
        public Zombie(ClientWorld world) { super(EntityType.ZOMBIE, world); }
        @Override public NativeActorPoseCarrier director$pose() { return pose; }
        @Override public void tick() {}
        @Override public void tickMovement() {}
    }

    public static final class Skeleton extends SkeletonEntity implements NativeMobActorPreview {
        private final NativeActorPoseCarrier pose = new NativeActorPoseCarrier();
        public Skeleton(ClientWorld world) { super(EntityType.SKELETON, world); }
        @Override public NativeActorPoseCarrier director$pose() { return pose; }
        @Override public void tick() {}
        @Override public void tickMovement() {}
    }

    public static final class Villager extends VillagerEntity implements NativeMobActorPreview {
        private final NativeActorPoseCarrier pose = new NativeActorPoseCarrier();
        public Villager(ClientWorld world) { super(EntityType.VILLAGER, world); }
        @Override public NativeActorPoseCarrier director$pose() { return pose; }
        @Override public void tick() {}
        @Override public void tickMovement() {}
    }

    public static final class Cow extends CowEntity implements NativeMobActorPreview {
        private final NativeActorPoseCarrier pose = new NativeActorPoseCarrier();
        public Cow(ClientWorld world) { super(EntityType.COW, world); }
        @Override public NativeActorPoseCarrier director$pose() { return pose; }
        @Override public void tick() {}
        @Override public void tickMovement() {}
    }

    public static final class Sheep extends SheepEntity implements NativeMobActorPreview {
        private final NativeActorPoseCarrier pose = new NativeActorPoseCarrier();
        public Sheep(ClientWorld world) { super(EntityType.SHEEP, world); }
        @Override public NativeActorPoseCarrier director$pose() { return pose; }
        @Override public void tick() {}
        @Override public void tickMovement() {}
    }
}
