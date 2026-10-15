package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import com.director.persistence.AtomicFileWriter;
import com.director.util.WorldIdentifier;
import com.google.gson.*;
import org.joml.Quaternionf;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Named static rotation poses, scoped to the current world and rig. */
public final class ActorSavedPoseLibrary {
    public record Rotation(float x, float y, float z, float w) {
        public Rotation {
            float length = x*x+y*y+z*z+w*w;
            if (!Float.isFinite(length) || length < 1e-8f) throw new IllegalArgumentException("Invalid pose rotation");
        }
        Quaternionf quaternion() { return new Quaternionf(x,y,z,w).normalize(); }
    }
    public record Saved(String name, String rigId, Map<String, Rotation> rotations) {
        public Saved {
            name = com.director.util.DisplayNames.clean(name);
            if (name.isEmpty() || rigId == null || rotations == null || rotations.isEmpty())
                throw new IllegalArgumentException("姿态名称和部位不能为空");
            rotations = Map.copyOf(rotations);
        }
        public RigPose apply(RigPose source, Collection<String> selected) {
            if (!rigId.equals(source.rig().id())) throw new IllegalArgumentException("姿态骨骼类型不匹配");
            RigPose result = source.copy();
            for (String id : selected) {
                var rotation = rotations.get(id);
                if (rotation != null && ActorBoneInspector.supports(source.rig(),id,RigPose.ROTATION))
                    result.setLocalTransform(source.rig().requireBoneIndex(id),
                            result.localTransform(id).withRotation(rotation.quaternion()),RigPose.ROTATION);
            }
            return result;
        }
    }
    private static Path loadedFile;
    private static List<Saved> loaded = List.of();
    private ActorSavedPoseLibrary() {}

    public static Saved capture(String name, RigPose pose, Collection<String> bones) {
        Map<String, Rotation> values = new LinkedHashMap<>();
        for (String id : bones) if (ActorBoneInspector.supports(pose.rig(),id,RigPose.ROTATION)) {
            var q = pose.localTransform(id).rotation();
            values.put(id,new Rotation(q.x,q.y,q.z,q.w));
        }
        return new Saved(name,pose.rig().id(),values);
    }
    public static List<Saved> current() throws IOException {
        var world = WorldIdentifier.getWorldSavePath();
        if (world == null) throw new IOException("当前存档目录不可用");
        Path file = world.resolve("director/actor_poses.json");
        if (!file.equals(loadedFile)) {
            List<Saved> next = read(file);
            loaded = next; loadedFile = file;
        }
        return loaded;
    }
    public static void add(Saved pose) throws IOException {
        List<Saved> next = new ArrayList<>(current());
        if (next.stream().anyMatch(p -> p.name().equals(pose.name())))
            throw new IllegalArgumentException("姿态名称已存在，请使用其他名称");
        next.add(pose);
        save(loadedFile,next);
        loaded = List.copyOf(next);
    }
    /** Prepare a checked replacement without mutating the live library. Null name deletes. */
    static List<Saved> edit(List<Saved> source, Saved target, String newName) {
        int index = source.indexOf(target);
        if (index < 0) throw new IllegalArgumentException("姿态已变化，请重新选择");
        List<Saved> next = new ArrayList<>(source);
        if (newName == null) next.remove(index);
        else {
            Saved renamed = new Saved(newName,target.rigId(),target.rotations());
            if (source.stream().anyMatch(p -> p != target && p.name().equals(renamed.name())))
                throw new IllegalArgumentException("姿态名称已存在");
            next.set(index,renamed);
        }
        return List.copyOf(next);
    }
    public static void edit(com.director.timeline.model.TimelineModel model, Saved target, String name) throws IOException {
        List<Saved> before = current(), after = edit(before,target,name);
        if (before.equals(after)) return;
        Path file = loadedFile;
        com.director.ui.timeline.commands.TimelineCommandBridge.execute(
            new com.director.ui.timeline.commands.BaseTimelineCommand(model,"actor_pose_library") {
                @Override protected void doExecute() { replace(file,before,after); }
                @Override protected void doUndo() { replace(file,after,before); }
                @Override public String getDescription() { return name == null ? "Delete saved actor pose" : "Rename actor pose"; }
            });
    }
    private static void replace(Path file,List<Saved> expected,List<Saved> next) {
        try {
            var current = current();
            if (!file.equals(loadedFile) || !expected.equals(current))
                throw new IllegalArgumentException("姿态库或存档已变化，无法修改");
            replaceFile(file,expected,next);
            loaded = next;
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    static void replaceFile(Path file,List<Saved> expected,List<Saved> next) throws IOException {
        if (!read(file).equals(expected)) throw new IOException("姿态文件已在外部修改，请重新加载后操作");
        save(file,next);
    }
    public static void save(Path file, List<Saved> poses) throws IOException {
        AtomicFileWriter.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(poses));
    }
    public static List<Saved> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        try {
            Saved[] values = new Gson().fromJson(Files.readString(file),Saved[].class);
            if (values == null) throw new IllegalArgumentException("Empty pose library");
            Set<String> names = new HashSet<>();
            for (var value : values) if (value == null || !names.add(value.name()))
                throw new IllegalArgumentException("Duplicate or missing pose");
            return List.copyOf(Arrays.asList(values));
        } catch (RuntimeException e) { throw new IOException("姿态库读取失败",e); }
    }
}
