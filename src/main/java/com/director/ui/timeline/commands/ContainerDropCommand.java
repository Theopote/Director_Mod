package com.director.ui.timeline.commands;

import com.director.core.DirectorServices;
import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.sequence.ConcurrencyMode;
import com.director.core.animation.sequence.SequenceParameters;
import com.director.core.container.AnimationMode;
import com.director.core.container.Container;
import com.director.core.container.ContainerAnimationPreset;
import com.director.core.container.ContainerState;
import com.director.core.container.ContainerType;
import com.director.core.container.visibility.BlockVisualStateManager;
import com.director.timeline.layout.TimelineClipLayoutResolver;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.playback.block.TimelineClipBlockController;
import com.director.timeline.snapping.TimelineSnapping;
import com.director.ui.timeline.audio.TimelineAudioService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ContainerDropCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerDropCommand.class);
    private static final float MIN_CLIP_DURATION_SECONDS = 2.0f;
    private static final float MAX_INITIAL_CLIP_DURATION_SECONDS = 300.0f;
    private static final float SAFE_DEFAULT_BLOCKS_PER_SECOND = 200.0f;
    private static final int SAFE_MAX_CONCURRENT_BLOCKS = 64;
    private static final int HEAVY_CONTAINER_BLOCK_THRESHOLD = 512;
    private static final float SAFE_BATCH_INTERVAL_SECONDS = 0.05f;
    private static final float EPS = 0.001f;

    private final TimelineTrack targetTrack;
    private final List<Container> containers;
    private final List<TimelineTrack> originalTracks;
    private final Map<TimelineTrack, List<TimelineClip>> originalTrackClips = new HashMap<>();
    private final Map<TimelineClip, ClipSnapshot> clipSnapshots = new LinkedHashMap<>();
    private final Map<TimelineClip, Range> plannedRanges = new LinkedHashMap<>();
    private final List<ContainerSnapshot> containerSnapshots = new ArrayList<>();
    private final List<DropPlan> dropPlans = new ArrayList<>();
    private final TimelineClip previousSelection;
    private final TimelineClip finalSelection;
    private final Runnable onDataChanged;
    private boolean applied;

    public ContainerDropCommand(TimelineModel model,
                                TimelineTrack targetTrack,
                                List<Container> containers,
                                float dropTime,
                                Runnable onDataChanged) {
        super(model, "container_drop");
        this.targetTrack = Objects.requireNonNull(targetTrack, "targetTrack");
        this.containers = List.copyOf(Objects.requireNonNull(containers, "containers"));
        if (this.containers.isEmpty()) {
            throw new IllegalArgumentException("At least one container is required");
        }
        this.onDataChanged = onDataChanged != null ? onDataChanged : () -> {};
        this.originalTracks = new ArrayList<>(model.getAllTracks());
        this.previousSelection = model.getSelectedClip();
        validateTarget();
        planDrop(dropTime);
        this.finalSelection = dropPlans.isEmpty() ? null : dropPlans.getLast().clip();
    }

    private record Range(float start, float end) {
        private float length() {
            return end - start;
        }
    }

    private static final class MutableRange {
        private float start;
        private float end;

        private MutableRange(float start, float end) {
            this.start = start;
            this.end = end;
        }
    }

    private record ClipSnapshot(TimelineTrack parent, float start, float end) {}

    private record ContainerSnapshot(RegistryKey<World> levelKey, boolean intakeEnabled, ContainerState state) {}

    private record DropPlan(Container container, TimelineClip existingClip, TimelineClip newClip, Range range) {
        private TimelineClip clip() {
            return existingClip != null ? existingClip : newClip;
        }
    }

    private void validateTarget() {
        if (model.isPlaying() || model.isScrubbing()) {
            throw new IllegalStateException("Playback or scrubbing is active");
        }
        if (!originalTracks.contains(targetTrack)) {
            throw new IllegalArgumentException("Target track does not belong to this timeline");
        }
        if (targetTrack.getType() != TrackType.CONTAINER) {
            throw new IllegalArgumentException("Containers can only be dropped on a container track");
        }
        if (targetTrack.isLocked()) {
            throw new IllegalStateException("Target track is locked");
        }
    }

    private void planDrop(float dropTime) {
        if (!Float.isFinite(dropTime)) {
            throw new IllegalArgumentException("Drop time must be finite");
        }

        Map<TimelineClip, MutableRange> simulation = new LinkedHashMap<>();
        for (TimelineClip clip : targetTrack.getClips()) {
            if (clip != null) {
                simulation.put(clip, new MutableRange(clip.getStartTime(), clip.getEndTime()));
                snapshotClip(clip);
            }
        }
        originalTrackClips.put(targetTrack, new ArrayList<>(targetTrack.getClips()));

        float maxStart = Math.max(0f, model.getDuration() - 1f);
        float snappedDropTime = Math.max(0f, Math.min(dropTime, maxStart));
        if (model.isSnappingEnabled()) {
            snappedDropTime = TimelineSnapping.snapTime(model, snappedDropTime, targetTrack, null, 0f).snappedTime;
            snappedDropTime = Math.max(0f, Math.min(snappedDropTime, maxStart));
        }

        Set<UUID> seen = new HashSet<>();
        for (Container container : containers) {
            if (container == null || container.getType() == ContainerType.GROUP) {
                throw new IllegalArgumentException("Group containers cannot be placed on the timeline");
            }
            if (container.isLocked()) {
                throw new IllegalStateException("Container is locked: " + container.getName());
            }
            if (!seen.add(container.getId())) {
                throw new IllegalArgumentException("Duplicate container in drop payload");
            }
            if (DirectorServices.get().containers().getContainer(container.getId()) != container) {
                throw new IllegalArgumentException("Container is no longer registered: " + container.getId());
            }

            containerSnapshots.add(new ContainerSnapshot(
                    container.getLevelKey(), container.isIntakeEnabled(), container.getState()));
            List<TimelineClip> existingClips = DirectorServices.get().containerTimeline()
                    .findClipsForContainer(container.getId());
            if (existingClips.size() > 1) {
                throw new IllegalStateException("Container already has multiple timeline clips");
            }
            TimelineClip existingClip = existingClips.isEmpty() ? null : existingClips.getFirst();
            TimelineTrack oldTrack = existingClip != null ? existingClip.getParentTrack() : null;
            if (oldTrack != null) {
                if (!originalTracks.contains(oldTrack)) {
                    throw new IllegalStateException("Existing clip belongs to another timeline");
                }
                if (oldTrack.isLocked()) {
                    throw new IllegalStateException("Source track is locked");
                }
                snapshotClip(existingClip);
                originalTrackClips.computeIfAbsent(oldTrack, ignored -> new ArrayList<>(oldTrack.getClips()));
            }

            float length = existingClip != null
                    ? existingClip.getLength()
                    : calculateInitialClipDuration(container);
            if (!Float.isFinite(length) || length <= 0f) {
                throw new IllegalArgumentException("Clip length must be positive and finite");
            }

            List<TimelineClipLayoutResolver.Interval> occupied = new ArrayList<>();
            for (Map.Entry<TimelineClip, MutableRange> entry : simulation.entrySet()) {
                if (entry.getKey() != existingClip) {
                    occupied.add(new TimelineClipLayoutResolver.Interval(entry.getValue().start, entry.getValue().end));
                }
            }
            occupied.sort(Comparator.comparingDouble(TimelineClipLayoutResolver.Interval::start));

            float candidateStart = snappedDropTime;
            if (existingClip == null) {
                candidateStart = TimelineClipLayoutResolver.resolveNearestStart(
                        occupied, candidateStart, length, 0f);
            } else {
                float resolvedStart = TimelineClipLayoutResolver.resolveStartForDrag(
                        occupied, candidateStart, length, 0f);
                boolean ripple = TimelineClipLayoutResolver.overlapsAny(
                                occupied, candidateStart, candidateStart + length)
                        && Math.abs(resolvedStart - candidateStart) < EPS;
                if (ripple) {
                    rippleSimulation(simulation, existingClip, candidateStart, length);
                } else {
                    candidateStart = resolvedStart;
                }
            }

            Range range = new Range(candidateStart, candidateStart + length);
            TimelineClip newClip = null;
            if (existingClip == null) {
                newClip = createClip(container, range);
            }
            simulation.put(existingClip != null ? existingClip : newClip, new MutableRange(range.start(), range.end()));
            dropPlans.add(new DropPlan(container, existingClip, newClip, range));
        }

        for (Map.Entry<TimelineClip, MutableRange> entry : simulation.entrySet()) {
            plannedRanges.put(entry.getKey(), new Range(entry.getValue().start, entry.getValue().end));
        }
    }

    private void snapshotClip(TimelineClip clip) {
        clipSnapshots.putIfAbsent(clip, new ClipSnapshot(clip.getParentTrack(), clip.getStartTime(), clip.getEndTime()));
    }

    private static void rippleSimulation(Map<TimelineClip, MutableRange> simulation,
                                         TimelineClip moving,
                                         float insertStart,
                                         float length) {
        float insertEnd = insertStart + length;
        List<Map.Entry<TimelineClip, MutableRange>> sorted = new ArrayList<>(simulation.entrySet());
        sorted.sort(Comparator.comparingDouble(entry -> entry.getValue().start));
        for (Map.Entry<TimelineClip, MutableRange> entry : sorted) {
            TimelineClip clip = entry.getKey();
            MutableRange range = entry.getValue();
            if (clip == moving) {
                continue;
            }
            float clipStart = range.start;
            float clipEnd = range.end;
            if (clipStart >= insertStart - EPS) {
                range.start += length;
                range.end += length;
            } else if (clipEnd > insertStart + EPS && clipStart < insertEnd - EPS) {
                float delta = insertEnd - clipStart;
                range.start += delta;
                range.end += delta;
                insertEnd = Math.max(insertEnd, range.end);
            }
        }
    }

    private TimelineClip createClip(Container container, Range range) {
        TimelineClip clip = new TimelineClip(
                container.getName(), range.start(), range.end(), targetTrack.getType().getClipColor());
        clip.bindToContainer(container.getId(), container.getName());
        initializeAnimationConfig(clip, container);
        if (container.getType() == ContainerType.DEMOLITION) {
            ClipAnimationConfig config = clip.getAnimationConfig();
            config.setPhase(ClipAnimationConfig.AnimationPhase.EXIT);
            config.setMode(AnimationMode.FADE_OUT);
            clip.setAnimationConfig(config);
        }
        return clip;
    }

    private float calculateInitialClipDuration(Container container) {
        int blockCount = container.getTotalBlockCount();
        if (blockCount <= 0) {
            return MIN_CLIP_DURATION_SECONDS;
        }
        float durationFromRate = blockCount / SAFE_DEFAULT_BLOCKS_PER_SECOND;
        float durationFromConcurrency =
                (float) Math.ceil(blockCount / (float) SAFE_MAX_CONCURRENT_BLOCKS) * SAFE_BATCH_INTERVAL_SECONDS;
        return Math.min(MAX_INITIAL_CLIP_DURATION_SECONDS,
                Math.max(MIN_CLIP_DURATION_SECONDS, Math.max(durationFromRate, durationFromConcurrency)));
    }

    private void initializeAnimationConfig(TimelineClip clip, Container container) {
        ContainerAnimationPreset preset = container.getDefaultAnimationPreset();
        if (preset == null) {
            preset = ContainerAnimationPreset.defaultPreset();
        }
        float animationDuration = Math.min(preset.getSuggestedDuration(), clip.getLength());
        ClipAnimationConfig config = ClipAnimationConfig.fromPreset(
                preset.getMode(), animationDuration, preset.getDelay(), preset.getEasing(), preset.getOrdering());
        config.getParams().putAll(preset.getParams());
        applySafetyDefaults(config, container, clip.getLength());
        clip.setAnimationConfig(config);
    }

    private void applySafetyDefaults(ClipAnimationConfig config, Container container, float clipDuration) {
        int blockCount = container.getTotalBlockCount();
        if (blockCount < HEAVY_CONTAINER_BLOCK_THRESHOLD) {
            return;
        }
        SequenceParameters params = config.getSequenceParameters();
        if (params == null) {
            params = new SequenceParameters();
            config.setSequenceParameters(params);
            params = config.getSequenceParameters();
        }
        if (params.getConcurrencyMode() == ConcurrencyMode.ALL_AT_ONCE) {
            params.setConcurrencyMode(ConcurrencyMode.IN_BATCHES);
        }
        int safeBatchSize = Math.max(1, Math.min(SAFE_MAX_CONCURRENT_BLOCKS, params.getBatchSize()));
        params.setBatchSize(safeBatchSize);
        float recommendedInterval = Math.max(SAFE_BATCH_INTERVAL_SECONDS,
                Math.min(clipDuration / Math.max(1.0f, (float) Math.ceil(blockCount / (float) safeBatchSize)), 0.25f));
        params.setBatchInterval(Math.max(params.getBatchInterval(), recommendedInterval));
    }

    @Override
    protected void doExecute() {
        validateExecution();
        List<TimelineClip> audioChanged = new ArrayList<>();
        Set<TimelineClip> addedClips = new HashSet<>();
        try {
            TimelineAudioService audioService = TimelineAudioService.getInstance();
            for (Map.Entry<TimelineClip, Range> entry : plannedRanges.entrySet()) {
                ClipSnapshot snapshot = clipSnapshots.get(entry.getKey());
                Range target = entry.getValue();
                if (snapshot != null && changed(snapshot, target)) {
                    audioService.onClipTimeRangeChanged(
                            entry.getKey(), snapshot.start(), snapshot.end(), target.start(), target.end());
                    audioChanged.add(entry.getKey());
                }
            }

            for (Map.Entry<TimelineClip, Range> entry : plannedRanges.entrySet()) {
                ClipSnapshot snapshot = clipSnapshots.get(entry.getKey());
                if (snapshot != null) {
                    entry.getKey().offsetTime(entry.getValue().start() - snapshot.start());
                }
            }

            for (DropPlan plan : dropPlans) {
                TimelineClip clip = plan.clip();
                if (plan.existingClip() != null) {
                    TimelineTrack parent = clip.getParentTrack();
                    if (parent != null && parent != targetTrack) {
                        parent.removeClip(clip);
                    }
                    if (clip.getParentTrack() == null && !targetTrack.addClip(clip)) {
                        throw new IllegalStateException("Target track rejected clip: " + clip.getLabel());
                    }
                } else {
                    addedClips.add(clip);
                    if (!targetTrack.addClip(plan.newClip())) {
                        throw new IllegalStateException("Target track rejected clip: " + clip.getLabel());
                    }
                }
            }

            for (DropPlan plan : dropPlans) {
                prepareContainer(plan.container());
            }

            model.setSelectedClip(finalSelection);
            applied = true;
            invalidateVisualState();
            notifyDataChanged();
        } catch (RuntimeException | Error e) {
            rollback(addedClips, audioChanged);
            throw e;
        }
    }

    @Override
    protected void doUndo() {
        if (!applied) {
            return;
        }
        TimelineAudioService audioService = TimelineAudioService.getInstance();
        for (Map.Entry<TimelineClip, Range> entry : plannedRanges.entrySet()) {
            ClipSnapshot snapshot = clipSnapshots.get(entry.getKey());
            Range target = entry.getValue();
            if (snapshot != null && changed(snapshot, target)) {
                audioService.onClipTimeRangeChanged(
                        entry.getKey(), target.start(), target.end(), snapshot.start(), snapshot.end());
            }
        }

        for (DropPlan plan : dropPlans) {
            if (plan.newClip() != null) {
                targetTrack.removeClip(plan.newClip());
                audioService.onClipRemoved(plan.newClip());
            }
        }
        for (DropPlan plan : dropPlans) {
            if (plan.existingClip() != null) {
                TimelineTrack originalParent = clipSnapshots.get(plan.existingClip()).parent();
                if (plan.existingClip().getParentTrack() != originalParent) {
                    TimelineTrack currentParent = plan.existingClip().getParentTrack();
                    if (currentParent != null) {
                        currentParent.removeClip(plan.existingClip());
                    }
                    if (originalParent != null && !originalParent.addClip(plan.existingClip())) {
                        throw new IllegalStateException("Original track rejected clip during undo");
                    }
                }
            }
        }
        for (Map.Entry<TimelineClip, Range> entry : plannedRanges.entrySet()) {
            ClipSnapshot snapshot = clipSnapshots.get(entry.getKey());
            if (snapshot != null) {
                entry.getKey().offsetTime(snapshot.start() - entry.getKey().getStartTime());
            }
        }
        restoreTrackClips();
        removeCreatedTracks();
        for (int i = 0; i < dropPlans.size(); i++) {
            if (dropPlans.get(i).newClip() != null) {
                restoreContainer(containers.get(i), containerSnapshots.get(i));
            }
        }
        model.setSelectedClip(previousSelection);
        applied = false;
        invalidateVisualState();
        notifyDataChanged();
    }

    private void validateExecution() {
        validateTarget();
        for (DropPlan plan : dropPlans) {
            Container container = plan.container();
            if (container.isLocked() || container.getType() == ContainerType.GROUP) {
                throw new IllegalStateException("Container is no longer editable: " + container.getName());
            }
            if (DirectorServices.get().containers().getContainer(container.getId()) != container) {
                throw new IllegalStateException("Container is no longer registered: " + container.getId());
            }
            List<TimelineClip> clips = DirectorServices.get().containerTimeline()
                    .findClipsForContainer(container.getId());
            if (plan.existingClip() == null) {
                if (!clips.isEmpty()) {
                    throw new IllegalStateException("Container already has a timeline clip");
                }
            } else {
                if (clips.size() != 1 || clips.getFirst() != plan.existingClip()) {
                    throw new IllegalStateException("Container clip binding changed before execution");
                }
                TimelineTrack parent = plan.existingClip().getParentTrack();
                if (parent != null && parent.isLocked()) {
                    throw new IllegalStateException("Source track is locked");
                }
            }
        }
    }

    private void prepareContainer(Container container) {
        ensureWorldBinding(container);
        if (container.isIntakeEnabled()) {
            container.setIntakeEnabled(false);
        }
        var visibilityService = DirectorServices.get().visibilityOrNull();
        if (visibilityService != null) {
            visibilityService.prepareContainerForTimeline(container);
        }
        if (container.getState() != ContainerState.HIDDEN) {
            container.setState(ContainerState.HIDDEN, false);
        }
    }

    private void ensureWorldBinding(Container container) {
        if (container.getLevelKey() != null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            container.setLevelKey(client.world.getRegistryKey());
        }
    }

    private void rollback(Set<TimelineClip> addedClips, List<TimelineClip> audioChanged) {
        TimelineAudioService audioService = TimelineAudioService.getInstance();
        for (TimelineClip clip : audioChanged) {
            Range target = plannedRanges.get(clip);
            ClipSnapshot snapshot = clipSnapshots.get(clip);
            if (target != null && snapshot != null) {
                try {
                    audioService.onClipTimeRangeChanged(
                            clip, target.start(), target.end(), snapshot.start(), snapshot.end());
                } catch (RuntimeException | Error cleanupFailure) {
                    LOGGER.warn("[ContainerDrop] Failed to roll back audio for {}", clip.getLabel(), cleanupFailure);
                }
            }
        }
        for (TimelineClip clip : addedClips) {
            TimelineTrack parent = clip.getParentTrack();
            if (parent != null) {
                parent.removeClip(clip);
            }
            audioService.onClipRemoved(clip);
        }
        for (DropPlan plan : dropPlans) {
            if (plan.existingClip() != null) {
                TimelineTrack originalParent = clipSnapshots.get(plan.existingClip()).parent();
                if (plan.existingClip().getParentTrack() != originalParent) {
                    TimelineTrack currentParent = plan.existingClip().getParentTrack();
                    if (currentParent != null) {
                        currentParent.removeClip(plan.existingClip());
                    }
                    if (originalParent != null) {
                        originalParent.addClip(plan.existingClip());
                    }
                }
            }
        }
        for (Map.Entry<TimelineClip, Range> entry : plannedRanges.entrySet()) {
            ClipSnapshot snapshot = clipSnapshots.get(entry.getKey());
            if (snapshot != null) {
                entry.getKey().offsetTime(snapshot.start() - entry.getKey().getStartTime());
            }
        }
        restoreTrackClips();
        removeCreatedTracks();
        for (int i = 0; i < containers.size(); i++) {
            restoreContainer(containers.get(i), containerSnapshots.get(i));
        }
        model.setSelectedClip(previousSelection);
        applied = false;
        invalidateVisualState();
    }

    private void restoreTrackClips() {
        for (Map.Entry<TimelineTrack, List<TimelineClip>> entry : originalTrackClips.entrySet()) {
            List<TimelineClip> clips = entry.getKey().getClips();
            clips.clear();
            clips.addAll(entry.getValue());
            for (TimelineClip clip : entry.getValue()) {
                clip.setParentTrack(entry.getKey());
            }
        }
    }

    private void removeCreatedTracks() {
        for (TimelineTrack track : new ArrayList<>(model.getAllTracks())) {
            if (!originalTracks.contains(track)) {
                model.removeTrack(track);
            }
        }
    }

    private void restoreContainer(Container container, ContainerSnapshot snapshot) {
        container.setLevelKey(snapshot.levelKey());
        container.setIntakeEnabled(snapshot.intakeEnabled());
        container.setState(snapshot.state(), false);
    }

    private static boolean changed(ClipSnapshot snapshot, Range range) {
        return Math.abs(snapshot.start() - range.start()) > 0.00001f
                || Math.abs(snapshot.end() - range.end()) > 0.00001f;
    }

    private void invalidateVisualState() {
        for (Container container : containers) {
            BlockVisualStateManager.getInstance().clearOverridesFor(container);
        }
        TimelineClipBlockController.getInstance().invalidateState();
    }

    private void notifyDataChanged() {
        try {
            onDataChanged.run();
        } catch (RuntimeException | Error e) {
            LOGGER.warn("[ContainerDrop] Data-change callback failed", e);
        }
    }

    @Override
    public String getDescription() {
        return "Drop " + containers.size() + " container(s) on timeline";
    }
}
