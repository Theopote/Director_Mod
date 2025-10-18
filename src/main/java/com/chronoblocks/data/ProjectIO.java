package com.chronoblocks.data;

import com.chronoblocks.core.animation.AnimationClip;
import com.chronoblocks.core.animation.Keyframe;
import com.chronoblocks.core.animation.Track;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal JSON serializer for the current project: one AnimationClip + container IDs.
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public class ProjectIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectIO.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class ProjectDTO {
        public ClipDTO clip;
        public List<String> containers = new ArrayList<>();
    }

    public static class ClipDTO {
        public String name;
        public List<TrackDTO> tracks = new ArrayList<>();
    }

    public static class TrackDTO {
        public String name;
        public String targetId;
        public boolean targetIsContainer;
        public List<KeyframeDTO> keyframes = new ArrayList<>();
    }

    public static class KeyframeDTO {
        public float time;
        public float[] position;
        public float[] rotation;
        public float[] scale;
        public float opacity;
    }

    public static ProjectDTO toDTO(AnimationClip clip) {
        ProjectDTO p = new ProjectDTO();
        p.clip = new ClipDTO();
        p.clip.name = clip != null ? clip.getName() : "Unnamed";
        if (clip != null) {
            for (Track t : clip.getTracks()) {
                TrackDTO td = new TrackDTO();
                td.name = t.getName();
                td.targetId = t.getTargetId();
                td.targetIsContainer = t.isTargetContainer();
                for (Keyframe k : t.getKeyframes()) {
                    KeyframeDTO kd = new KeyframeDTO();
                    kd.time = k.getTime();
                    kd.position = k.getPosition().clone();
                    kd.rotation = k.getRotation().clone();
                    kd.scale = k.getScale().clone();
                    kd.opacity = k.getOpacity();
                    td.keyframes.add(kd);
                }
                p.clip.tracks.add(td);
            }
        }
        // TODO: 保存容器信息
        // 将来需要从 ContainerManager 实例获取容器列表
        // p.containers = new ArrayList<>(containerManager.getAllContainers());
        LOGGER.debug("[ProjectIO] 序列化项目，动画片段: {}", clip != null ? clip.getName() : "null");
        return p;
    }

    public static AnimationClip fromDTO(ProjectDTO dto) {
        AnimationClip clip = new AnimationClip(dto.clip != null ? dto.clip.name : "Unnamed");
        if (dto.clip != null) {
            for (TrackDTO td : dto.clip.tracks) {
                Track t = new Track(td.name != null ? td.name : "Track", td.targetId, td.targetIsContainer);
                for (KeyframeDTO kd : td.keyframes) {
                    Keyframe k = new Keyframe(kd.time);
                    System.arraycopy(kd.position, 0, k.getPosition(), 0, 3);
                    System.arraycopy(kd.rotation, 0, k.getRotation(), 0, 3);
                    System.arraycopy(kd.scale, 0, k.getScale(), 0, 3);
                    k.setOpacity(kd.opacity);
                    t.addKeyframe(k);
                }
                t.sortByTime();
                clip.addTrack(t);
            }
        }
        // TODO: 恢复容器信息
        // 将来需要将容器信息恢复到 ContainerManager 实例
        // for (String id : dto.containers) {
        //     containerManager.restoreContainer(id);
        // }
        LOGGER.debug("[ProjectIO] 反序列化项目，容器数量: {}", dto.containers.size());
        return clip;
    }

    public static void saveToFile(AnimationClip clip, File file) throws IOException {
        ProjectDTO dto = toDTO(clip);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(GSON.toJson(dto));
        }
    }

    public static AnimationClip loadFromFile(File file) throws IOException {
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            ProjectDTO dto = GSON.fromJson(r, ProjectDTO.class);
            return fromDTO(dto);
        }
    }
}
