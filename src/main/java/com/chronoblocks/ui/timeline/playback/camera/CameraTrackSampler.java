package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Stateless sampler that converts ordered camera keyframes into an interpolated pose.
 */
public class CameraTrackSampler {

    private final List<CameraKeyframe> keyframes = new ArrayList<>();

    public void setKeyframes(List<CameraKeyframe> source) {
        keyframes.clear();
        if (source != null) {
            keyframes.addAll(source);
            keyframes.sort(Comparator.comparing(CameraKeyframe::getTime));
        }
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    public Optional<CameraSample> sample(float timeSeconds) {
        if (keyframes.isEmpty()) {
            return Optional.empty();
        }
        if (keyframes.size() == 1) {
            return Optional.of(toSample(keyframes.getFirst()));
        }

        CameraKeyframe previous = keyframes.getFirst();
        for (int i = 1; i < keyframes.size(); i++) {
            CameraKeyframe next = keyframes.get(i);
            if (timeSeconds <= next.getTime()) {
                return Optional.of(interpolate(previous, next, timeSeconds));
            }
            previous = next;
        }

        return Optional.of(toSample(keyframes.getLast()));
    }

    private CameraSample interpolate(CameraKeyframe a, CameraKeyframe b, float timeSeconds) {
        float duration = Math.max(0.0001f, b.getTime() - a.getTime());
        float t = MathHelper.clamp((timeSeconds - a.getTime()) / duration, 0.0f, 1.0f);

        Vec3d posA = vec(a.getPosition());
        Vec3d posB = vec(b.getPosition());
        Vec3d position = new Vec3d(
            MathHelper.lerp(t, posA.x, posB.x),
            MathHelper.lerp(t, posA.y, posB.y),
            MathHelper.lerp(t, posA.z, posB.z)
        );

        Vector3f rotA = a.getRotation();
        Vector3f rotB = b.getRotation();
        float yaw = lerpAngle(rotA.x, rotB.x, t);
        float pitch = lerpAngle(rotA.y, rotB.y, t);
        float roll = lerpAngle(rotA.z, rotB.z, t);

        float fov = MathHelper.lerp(t, a.getFov(), b.getFov());
        float near = MathHelper.lerp(t, a.getNearPlane(), b.getNearPlane());
        float far = MathHelper.lerp(t, a.getFarPlane(), b.getFarPlane());

        return new CameraSample(position, yaw, pitch, roll, fov, near, far);
    }

    private CameraSample toSample(CameraKeyframe keyframe) {
        Vec3d position = vec(keyframe.getPosition());
        Vector3f rotation = keyframe.getRotation();
        return new CameraSample(
            position,
            rotation.x,
            rotation.y,
            rotation.z,
            keyframe.getFov(),
            keyframe.getNearPlane(),
            keyframe.getFarPlane()
        );
    }

    private Vec3d vec(Vector3f v) {
        return new Vec3d(v.x, v.y, v.z);
    }

    private float lerpAngle(float a, float b, float t) {
        float delta = MathHelper.wrapDegrees(b - a);
        return a + delta * t;
    }
}
