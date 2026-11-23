package com.director.actor;

/** Standing eye heights from Minecraft 1.21.10 entity dimensions, before actor scale. */
public final class ActorEyeHeight {
    private ActorEyeHeight() {}

    public static float standing(ActorType type) {
        if (type == null) return 1.62f;
        return switch (type) {
            case PLAYER, VILLAGER -> 1.62f;
            case ZOMBIE, SKELETON -> 1.74f;
            case COW -> 1.3f;
            case SHEEP -> 1.235f;
            case CUSTOM -> 1.7775f;
        };
    }

    public static float scaled(ActorType type, float scaleY) {
        float scale = Float.isFinite(scaleY) && scaleY > 0f ? scaleY : 1f;
        return standing(type) * scale;
    }
}
