package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import org.joml.Vector3f;

/** Bounded deterministic coordinate solve against the actual deformed sole. */
final class ActorLegContactIk {
    private ActorLegContactIk() {}

    /** Blend solved rotations, rather than a quantized target solve, for continuous release. */
    static void solve(RigPose pose, String leg, Vector3f target, float weight) {
        if (pose == null || leg == null) return;
        LegChain chain = pose.rig().findLeg(leg);
        if (chain == null) chain = LegChain.thigh(leg);
        solve(pose, chain, target, weight);
    }

    static void solve(RigPose pose, String leg, Vector3f target) {
        solve(pose, leg, target, 1);
    }

    static void solve(RigPose pose, LegChain chain, Vector3f target, float weight) {
        if (pose == null || chain == null || !Float.isFinite(weight) || weight <= 0) return;
        weight = Math.clamp(weight, 0, 1);
        if (!chain.hasKnee()) {
            solveSingleSegment(pose, chain, target, weight);
            return;
        }
        var solved = solveTwoBone(pose, chain, target);
        if (solved == null) return;
        for (String bone : new String[]{chain.legBone(), chain.lowerBone()}) {
            int i = pose.rig().findBoneIndex(bone);
            if (i < 0) continue;
            var before = pose.localTransform(i);
            var after = solved.localTransform(i);
            if (before.rotation().equals(after.rotation(), 1e-7f)) continue;
            var rotation = before.rotation().slerp(after.rotation(), weight);
            pose.setLocalTransform(i, before.withRotation(rotation), RigPose.ROTATION);
        }
    }

    /** Single-segment limbs (villager / quadruped): pitch the thigh toward the target sole. */
    private static void solveSingleSegment(RigPose pose, LegChain chain, Vector3f target, float weight) {
        if (!target.isFinite()) return;
        int hip = pose.rig().findBoneIndex(chain.legBone());
        if (hip < 0) return;
        var current = pose.localTransform(hip);
        var sole = ActorRouteFootContact.solePosition(pose, chain);
        var delta = target.sub(sole, new Vector3f());
        if (!delta.isFinite() || delta.lengthSquared() < 1e-10f) return;
        if (delta.length() > .3f) delta.normalize().mul(.3f);
        int parent = pose.rig().bone(hip).parentIndex();
        if (parent >= 0) {
            var inverse = pose.calculateModelTransforms()[parent].invert();
            inverse.transformDirection(delta);
        }
        if (!delta.isFinite()) return;
        float pitch = Math.clamp(-delta.z * 2.2f, -.6f, .6f) * weight;
        float roll = Math.clamp(delta.x * 1.4f, -.35f, .35f) * weight;
        var rotation = current.rotation().rotateXYZ(pitch, 0f, roll);
        pose.setLocalTransform(hip, current.withRotation(rotation), RigPose.ROTATION);
    }

    private static RigPose solveTwoBone(RigPose pose, LegChain chain, Vector3f target) {
        if (!target.isFinite() || !RigPoseComposer.sameLayout(pose.rig(), ArticulatedPlayerRig.DEFINITION)) return null;
        int hip = pose.rig().requireBoneIndex(chain.legBone());
        int knee = pose.rig().requireBoneIndex(chain.lowerBone());
        var hipRest = pose.localTransform(hip);
        var kneeRest = pose.localTransform(knee);
        var encoded = LimbBendTransform.angles(kneeRest.rotation());
        float bendSign = encoded.x < 0 ? -1f : 1f;
        float minimum = bendSign > 0 ? Math.max(-.9f, -encoded.x) : -.9f;
        float maximum = bendSign > 0 ? .9f : Math.min(.9f, -encoded.x);
        var working = pose.copy();
        var cache = working.calculateModelTransforms();
        float[] angles = new float[4];
        float error = ActorRouteFootContact.solePosition(pose, chain).distanceSquared(target);
        boolean changed = false;

        // Cosine-theorem seed: interior knee angle maps to fold-from-straight LimbBend delta.
        float thigh = ActorRouteFootContact.segmentLength(pose, hip);
        float shin = ActorRouteFootContact.segmentLength(pose, knee);
        var hipPos = pose.calculateModelTransform(hip).getTranslation(new Vector3f());
        float reach = Math.clamp(hipPos.distance(target), Math.abs(thigh - shin) + 1e-4f, thigh + shin - 1e-4f);
        float cosKnee = (thigh * thigh + shin * shin - reach * reach) / Math.max(1e-6f, 2f * thigh * shin);
        cosKnee = Math.clamp(cosKnee, -1f, 1f);
        float fold = (float) (Math.PI - Math.acos(cosKnee)); // 0 when fully extended
        float analytic = Math.clamp(fold * bendSign, minimum, maximum);
        float[] analyticSeed = {-.35f * analytic, 0, 0, analytic};
        write(working, hip, knee, hipRest, kneeRest, encoded, analyticSeed);
        float candidate = sole(working, hip, knee, cache).distanceSquared(target);
        if (Float.isFinite(candidate) && candidate < error) {
            System.arraycopy(analyticSeed, 0, angles, 0, 4);
            error = candidate;
            changed = true;
        }

        // Discrete bend seeds cover LimbBend curve bias the analytic seed may miss.
        for (float bend : new float[]{.3f, .6f, .9f}) {
            float[] seed = {-.5f * bend * bendSign, 0, 0, bend * bendSign};
            write(working, hip, knee, hipRest, kneeRest, encoded, seed);
            candidate = sole(working, hip, knee, cache).distanceSquared(target);
            if (Float.isFinite(candidate) && candidate < error) {
                System.arraycopy(seed, 0, angles, 0, 4);
                error = candidate;
                changed = true;
            }
        }
        write(working, hip, knee, hipRest, kneeRest, encoded, angles);

        // Three coarse-to-fine polish rounds (each sweeps all axes).
        for (int round = 0; round < 3 && error > 1e-8f; round++) {
            float step = .12f * (float) Math.pow(.5, round);
            for (int sweep = 0; sweep < 3; sweep++) {
                for (int axis = 0; axis < 4; axis++) {
                    float original = angles[axis], best = original, bestError = error;
                    for (int sign : new int[]{-1, 1}) {
                        angles[axis] = Math.clamp(original + sign * step, axis == 3 ? minimum : -.5f, axis == 3 ? maximum : .5f);
                        write(working, hip, knee, hipRest, kneeRest, encoded, angles);
                        candidate = sole(working, hip, knee, cache).distanceSquared(target);
                        if (Float.isFinite(candidate) && candidate < bestError) {
                            best = angles[axis];
                            bestError = candidate;
                        }
                    }
                    angles[axis] = best;
                    if (best != original) changed = true;
                    error = bestError;
                    write(working, hip, knee, hipRest, kneeRest, encoded, angles);
                }
            }
        }
        return changed ? working : null;
    }

    private static Vector3f sole(RigPose pose, int hip, int knee, org.joml.Matrix4f[] cache) {
        cache[hip] = null;
        cache[knee] = null;
        float length = ActorRouteFootContact.segmentLength(pose, knee);
        return pose.calculateModelTransform(knee, cache).transformPosition(new Vector3f(0, -length, 0));
    }

    private static void write(RigPose pose, int hip, int knee, BoneTransform upper, BoneTransform lower,
                              org.joml.Vector2f encoded, float[] a) {
        pose.setLocalTransform(hip, upper.withRotation(upper.rotation().rotateXYZ(a[0], a[1], a[2])), RigPose.ROTATION);
        pose.setLocalTransform(knee, lower.withRotation(LimbBendTransform.encodedRotation(encoded.x + a[3], encoded.y)), RigPose.ROTATION);
    }
}
