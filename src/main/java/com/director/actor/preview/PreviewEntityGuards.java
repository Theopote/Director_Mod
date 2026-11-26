package com.director.actor.preview;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;

/** Shared non-interactive / always-visible behavior for timeline preview hosts. */
public final class PreviewEntityGuards {
    private PreviewEntityGuards() {}

    public static boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    public static boolean collidesWith(Entity other) {
        return false;
    }

    public static boolean isAttackable() {
        return false;
    }

    public static boolean isPushable() {
        return false;
    }

    public static boolean canBeHitByProjectile() {
        return false;
    }

    public static boolean shouldRender(double distance) {
        return true;
    }
}
