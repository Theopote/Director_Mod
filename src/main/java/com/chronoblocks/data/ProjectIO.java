package com.chronoblocks.data;

import com.chronoblocks.animation.core.AnimationClip;
import com.chronoblocks.animation.core.Keyframe;
import com.chronoblocks.animation.core.RotationTrack;
import com.chronoblocks.animation.core.TransformTrack;
import com.chronoblocks.animation.core.VisibilityTrack;
import net.minecraft.util.math.Vec3d;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 持久化动画项目数据（基于新动画架构），兼容旧格式。
 */
public final class ProjectIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectIO() {
    }

    public static class ProjectDTO {
        public List<ClipDTO> clips = new ArrayList<>();
        public List<String> containers = new ArrayList<>();
        @Deprecated public LegacyClipDTO legacyClip;
    }

    public static class ClipDTO {
        public String name;
        public double start;
        public double end;
        public String containerId;
        public List<TrackDTO> tracks = new ArrayList<>();
    }

    public static class TrackDTO {
        public String type;
        public String propertyPath;
        public List<KeyframeDTO> keyframes = new ArrayList<>();
    }

    public static class KeyframeDTO {
        public double time;
        public double[] vector;
        public Double scalar;
        public String easing;
    }

    // Legacy schema for backwards compatibility ------------------------------------
    @Deprecated
    public static class LegacyClipDTO {
        public String name;
        public List<LegacyTrackDTO> tracks = new ArrayList<>();
    }

    @Deprecated
    public static class LegacyTrackDTO {
        public String name;
        public String targetId;
        public boolean targetIsContainer;
        public List<LegacyKeyframeDTO> keyframes = new ArrayList<>();
    }

    @Deprecated
    public static class LegacyKeyframeDTO {
        public float time;
        public float[] position;
        public float[] rotation;
        public float[] scale;
        public float opacity;
    }
    // -----------------------------------------------------------------------------

    public static ProjectDTO toDTO(List<AnimationClip> clips) {
        ProjectDTO dto = new ProjectDTO();
        if (clips != null) {
            for (AnimationClip clip : clips) {
                dto.clips.add(toClipDTO(clip));
            }
        }
        return dto;
    }

    public static ClipDTO toClipDTO(AnimationClip clip) {
        ClipDTO dto = new ClipDTO();
        dto.name = clip.getName();
        dto.start = clip.getStart();
        dto.end = clip.getEnd();
        UUID containerId = clip.getBoundContainerId();
        dto.containerId = containerId != null ? containerId.toString() : null;

        clip.getTracks().forEach(track -> dto.tracks.add(toTrackDTO(track)));
        return dto;
    }

    private static TrackDTO toTrackDTO(com.chronoblocks.animation.core.Track<?> track) {
        TrackDTO dto = new TrackDTO();
        dto.propertyPath = track.getPropertyPath();

        switch (track) {
            case TransformTrack transformTrack -> dto.type = transformTrack.isScaleTrack() ? "scale" : "position";
            case RotationTrack rotationTrack -> dto.type = "rotation";
            case VisibilityTrack visibilityTrack -> dto.type = "visibility";
            default -> dto.type = "generic";
        }

        for (Keyframe<?> keyframe : track.getKeyframes()) {
            Object value = keyframe.value();
            if (value instanceof Vec3d vec) {
                dto.keyframes.add(vectorKeyframe(new Keyframe<>(keyframe.time(), vec)));
            } else if (value instanceof Number number) {
                dto.keyframes.add(scalarKeyframe(new Keyframe<>(keyframe.time(), number.doubleValue())));
            } else {
                dto.keyframes.add(baseKeyframe(keyframe));
            }
        }
        return dto;
    }

    private static KeyframeDTO vectorKeyframe(Keyframe<Vec3d> keyframe) {
        KeyframeDTO dto = baseKeyframe(keyframe);
        Vec3d vec = keyframe.value();
        dto.vector = new double[]{vec.x, vec.y, vec.z};
        return dto;
    }

    private static KeyframeDTO scalarKeyframe(Keyframe<?> keyframe) {
        KeyframeDTO dto = baseKeyframe(keyframe);
        Object value = keyframe.value();
        if (value instanceof Number number) {
            dto.scalar = number.doubleValue();
        }
        return dto;
    }

    private static KeyframeDTO baseKeyframe(Keyframe<?> keyframe) {
        KeyframeDTO dto = new KeyframeDTO();
        dto.time = keyframe.time();
        dto.easing = keyframe.easing().name();
        return dto;
    }

    public static List<AnimationClip> fromDTO(ProjectDTO dto) {
        List<AnimationClip> clips = new ArrayList<>();
        if (dto == null) {
            return clips;
        }

        if (dto.clips != null && !dto.clips.isEmpty()) {
            for (ClipDTO clipDTO : dto.clips) {
                clips.add(fromClipDTO(clipDTO));
            }
        } else if (dto.legacyClip != null) {
            clips.add(fromLegacyClip(dto.legacyClip));
        }
        return clips;
    }

    private static AnimationClip fromClipDTO(ClipDTO dto) {
        UUID containerId = null;
        if (dto.containerId != null && !dto.containerId.isBlank()) {
            try {
                containerId = UUID.fromString(dto.containerId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        AnimationClip clip = new AnimationClip(
            dto.name != null ? dto.name : "Clip",
            dto.start,
            dto.end,
            containerId
        );

        for (TrackDTO trackDTO : dto.tracks) {
            switch (trackDTO.type) {
                case "scale" -> clip.addTrack(buildVecTrack(new TransformTrack(trackDTO.propertyPath, true), trackDTO));
                case "position" -> clip.addTrack(buildVecTrack(new TransformTrack(trackDTO.propertyPath, false), trackDTO));
                case "rotation" -> clip.addTrack(buildVecTrack(new RotationTrack(trackDTO.propertyPath), trackDTO));
                default -> clip.addTrack(buildScalarTrack(new VisibilityTrack(), trackDTO));
            }
        }
        return clip;
    }

    private static <T extends com.chronoblocks.animation.core.Track<Vec3d>> T buildVecTrack(T track, TrackDTO dto) {
        for (KeyframeDTO k : dto.keyframes) {
            Vec3d vec = k.vector != null && k.vector.length >= 3
                ? new Vec3d(k.vector[0], k.vector[1], k.vector[2])
                : Vec3d.ZERO;
            Keyframe<Vec3d> keyframe = new Keyframe<>(k.time, vec);
            keyframe.setEasing(parseEasing(k.easing));
            track.addKeyframe(keyframe);
        }
        return track;
    }

    private static <T extends com.chronoblocks.animation.core.Track<Double>> T buildScalarTrack(T track, TrackDTO dto) {
        for (KeyframeDTO k : dto.keyframes) {
            double value = k.scalar != null ? k.scalar : 0.0;
            Keyframe<Double> keyframe = new Keyframe<>(k.time, value);
            keyframe.setEasing(parseEasing(k.easing));
            track.addKeyframe(keyframe);
        }
        return track;
    }

    private static AnimationClip fromLegacyClip(LegacyClipDTO legacy) {
        AnimationClip clip = new AnimationClip(
            legacy.name != null ? legacy.name : "Legacy Clip",
            0.0,
            0.0,
            null
        );

        TransformTrack positionTrack = new TransformTrack("transform.position", false);
        TransformTrack scaleTrack = new TransformTrack("transform.scale", true);
        RotationTrack rotationTrack = new RotationTrack("transform.rotation");
        VisibilityTrack visibilityTrack = new VisibilityTrack();

        for (LegacyTrackDTO trackDTO : legacy.tracks) {
            for (LegacyKeyframeDTO k : trackDTO.keyframes) {
                Vec3d pos = toVec(k.position);
                Vec3d rot = toVec(k.rotation);
                Vec3d scale = toVec(k.scale);
                double opacity = k.opacity;

                positionTrack.addKeyframe(new Keyframe<>(k.time, pos));
                rotationTrack.addKeyframe(new Keyframe<>(k.time, rot));
                scaleTrack.addKeyframe(new Keyframe<>(k.time, scale));
                visibilityTrack.addKeyframe(new Keyframe<>(k.time, opacity));
            }
        }

        clip.addTrack(positionTrack);
        clip.addTrack(rotationTrack);
        clip.addTrack(scaleTrack);
        clip.addTrack(visibilityTrack);
        return clip;
    }

    private static Vec3d toVec(float[] raw) {
        if (raw == null || raw.length < 3) {
            return Vec3d.ZERO;
        }
        return new Vec3d(raw[0], raw[1], raw[2]);
    }

    private static Keyframe.Easing parseEasing(String easing) {
        if (easing == null || easing.isBlank()) {
            return Keyframe.Easing.LINEAR;
        }
        try {
            return Keyframe.Easing.valueOf(easing);
        } catch (IllegalArgumentException ignored) {
            return Keyframe.Easing.LINEAR;
        }
    }

    public static void saveToFile(List<AnimationClip> clips, File file) throws IOException {
        ProjectDTO dto = toDTO(clips);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(GSON.toJson(dto));
        }
    }

    public static List<AnimationClip> loadFromFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            ProjectDTO dto = GSON.fromJson(reader, ProjectDTO.class);
            return fromDTO(dto);
        }
    }
}
