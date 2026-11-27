package com.director.actor.runtime;

import com.director.actor.Actor;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.prop.ActorPropTimelineSampler;
import com.director.actor.rig.skeleton.RigPose;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.timeline.playback.entity.EntitySample;

import java.util.function.Supplier;

/** Per-track caches for playback, historical poses, and prop reconstruction. */
final class ActorTrackSampleCache {
    private static final int HISTORY_CAPACITY = 64;

    record CommittedPose(Actor actor, EntitySample sample, RigPose pose) {}

    private record ClipEpoch(long trackRevision, long pathRevision) {}
    private record PoseEpoch(ClipEpoch clips, long globalRevision, long assetRevision, int appearance) {}
    private record PlaybackEntry(int time, ClipEpoch epoch, EntitySample sample) {}
    private record PropEntry(int time, PoseEpoch epoch, ActorPropTimelineSampler.Snapshot snapshot) {}

    private final ActorTrack track;
    private final EntityClipSampler sampler;
    private final BoundedTimeCache<EntitySample> historicalClips = new BoundedTimeCache<>(HISTORY_CAPACITY);
    private final BoundedTimeCache<CommittedPose> historicalPoses = new BoundedTimeCache<>(HISTORY_CAPACITY);
    private final BoundedTimeCache<Boolean> absentPoses = new BoundedTimeCache<>(HISTORY_CAPACITY);
    private PlaybackEntry playback;
    private ClipEpoch historicalClipEpoch;
    private PoseEpoch historicalPoseEpoch;
    private PropEntry props;

    ActorTrackSampleCache(ActorTrack track, EntityClipSampler sampler) {
        this.track = track;
        this.sampler = sampler;
    }

    /** World changes discard derived samples while preserving the sampler's event history. */
    void clear() {
        playback = null;
        historicalClipEpoch = null;
        historicalClips.clear();
        historicalPoseEpoch = null;
        historicalPoses.clear();
        absentPoses.clear();
        props = null;
    }

    /** Cache the pre-relation sample without one-shot events or later placement changes. */
    EntitySample sampleClips(float time, long paths, boolean emitEvents) {
        var epoch = new ClipEpoch(track.samplingRevision(), paths);
        int key = Float.floatToIntBits(time);
        if (playback != null && playback.time() == key && playback.epoch().equals(epoch)) {
            return playback.sample() == null ? null : playback.sample().copy();
        }
        if (!emitEvents) {
            if (!epoch.equals(historicalClipEpoch)) {
                historicalClips.clear();
                historicalClipEpoch = epoch;
            }
            var stored = historicalClips.get(key);
            if (stored != null) return stored.copy();
        }

        var sampled = emitEvents ? sampler.sampleAt(time) : sampler.sampleSnapshot(time);
        var stored = sampled == null ? null : sampled.copy();
        if (stored != null) stored.actionEvents.clear();
        if (emitEvents) playback = new PlaybackEntry(key, epoch, stored);
        else if (stored != null) historicalClips.put(key, stored);
        return sampled;
    }

    CommittedPose committedPose(float time, long paths, int appearance, Supplier<CommittedPose> reconstruct) {
        var epoch = poseEpoch(paths, appearance);
        if (!epoch.equals(historicalPoseEpoch)) {
            historicalPoses.clear();
            absentPoses.clear();
            historicalPoseEpoch = epoch;
        }
        int key = Float.floatToIntBits(time);
        if (absentPoses.containsKey(key)) return null;
        var cached = historicalPoses.get(key);
        if (cached != null) return cached;
        var pose = reconstruct.get();
        if (pose == null) absentPoses.put(key, Boolean.TRUE);
        else historicalPoses.put(key, pose);
        return pose;
    }

    ActorPropTimelineSampler.Snapshot propSnapshot(float time, long paths, int appearance,
                                                   Supplier<ActorPropTimelineSampler.Snapshot> reconstruct) {
        var epoch = poseEpoch(paths, appearance);
        int key = Float.floatToIntBits(time);
        if (props != null && props.time() == key && props.epoch().equals(epoch)) return props.snapshot();
        var snapshot = reconstruct.get();
        props = snapshot == null ? null : new PropEntry(key, epoch, snapshot);
        return snapshot;
    }

    private PoseEpoch poseEpoch(long paths, int appearance) {
        return new PoseEpoch(new ClipEpoch(track.samplingRevision(), paths), ActorTrack.globalSamplingRevision(),
                AnimationAssetLibrary.getInstance().revision(), appearance);
    }
}
