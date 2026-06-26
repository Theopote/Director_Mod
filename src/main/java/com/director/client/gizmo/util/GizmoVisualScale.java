package com.director.client.gizmo.util;

import net.minecraft.util.math.Vec3d;

/**
 * 与 {@link com.director.client.gizmo.GizmoRenderer} 一致的 Gizmo 视距缩放。
 */
public final class GizmoVisualScale {

  public static final double BASE_ROTATION_RING_RADIUS = 2.0;
  private static final double MIN_DISTANCE_MULTIPLIER = 8.0;
  private static final double DISTANCE_SCALE_DIVISOR = 15.0;

  private GizmoVisualScale() {}

  public static double distanceMultiplier(Vec3d pivot, Vec3d cameraPos) {
    if (pivot == null || cameraPos == null) {
      return 1.0;
    }
    double distance = pivot.distanceTo(cameraPos);
    if (distance < MIN_DISTANCE_MULTIPLIER) {
      distance = MIN_DISTANCE_MULTIPLIER;
    }
    return distance / DISTANCE_SCALE_DIVISOR;
  }

  public static double rotationRingRadius(Vec3d pivot, Vec3d cameraPos) {
    return BASE_ROTATION_RING_RADIUS * distanceMultiplier(pivot, cameraPos);
  }
}
