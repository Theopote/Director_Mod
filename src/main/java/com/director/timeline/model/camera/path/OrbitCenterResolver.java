package com.director.timeline.model.camera.path;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 解析围绕摄像机的环绕中心（LookAt / Center）。
 *
 * <p>规则：
 * <ul>
 *   <li>准星命中方块 → 方块中心</li>
 *   <li>看向空气 → 沿视线前方默认距离处（{@link #DEFAULT_AIR_LOOK_DISTANCE} 格）</li>
 * </ul>
 */
public final class OrbitCenterResolver {

    /** 看向空气时，环绕中心落在视线前方的默认距离（方块） */
    public static final double DEFAULT_AIR_LOOK_DISTANCE = 10.0;

    private OrbitCenterResolver() {
    }

    public static Vec3d resolve(MinecraftClient client, CameraPathEnterContext ctx) {
        if (ctx == null || ctx.position == null) {
            return Vec3d.ZERO;
        }
        return resolve(client, ctx.position, ctx.getForward());
    }

    public static Vec3d resolve(MinecraftClient client, Vec3d cameraPos, Vec3d forward) {
        BlockHitResult hit = resolveBlockHit(client);
        if (hit != null) {
            BlockPos blockPos = hit.getBlockPos();
            return new Vec3d(
                    blockPos.getX() + 0.5,
                    blockPos.getY() + 0.5,
                    blockPos.getZ() + 0.5
            );
        }

        Vec3d dir = forward;
        if (dir == null || dir.lengthSquared() < 1e-12) {
            dir = new Vec3d(0, 0, 1);
        } else {
            dir = dir.normalize();
        }
        return cameraPos.add(dir.multiply(DEFAULT_AIR_LOOK_DISTANCE));
    }

    private static BlockHitResult resolveBlockHit(MinecraftClient client) {
        BlockHitResult hit = null;
        try {
            hit = com.director.client.input.DirectorInputSystem.getCurrentRay();
        } catch (Exception ignored) {
        }
        if (hit == null && client != null && client.crosshairTarget instanceof BlockHitResult bhr) {
            hit = bhr;
        }
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return hit;
        }
        return null;
    }
}
