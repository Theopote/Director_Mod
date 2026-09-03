package com.director.actor.rig.skeleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable, model-independent bone hierarchy. */
public final class RigDefinition {
    private final String id;
    private final String displayName;
    private final List<BoneDefinition> bones;
    private final Map<String, Integer> boneIndexById;

    public RigDefinition(String id, String displayName, List<BoneDefinition> bones) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Rig id must not be blank");
        }
        if (bones == null || bones.isEmpty()) {
            throw new IllegalArgumentException("Rig must define at least one bone");
        }
        this.id = id;
        this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
        this.bones = Collections.unmodifiableList(new ArrayList<>(bones));
        this.boneIndexById = buildAndValidateIndex(this.bones);
    }

    private static Map<String, Integer> buildAndValidateIndex(List<BoneDefinition> bones) {
        Map<String, Integer> index = new HashMap<>();
        int roots = 0;
        for (int i = 0; i < bones.size(); i++) {
            BoneDefinition bone = bones.get(i);
            if (index.putIfAbsent(bone.id(), i) != null) {
                throw new IllegalArgumentException("Duplicate bone id: " + bone.id());
            }
            if (bone.parentIndex() == -1) {
                roots++;
            } else if (bone.parentIndex() < 0 || bone.parentIndex() >= bones.size()) {
                throw new IllegalArgumentException("Invalid parent index for bone " + bone.id());
            } else if (bone.parentIndex() == i) {
                throw new IllegalArgumentException("Bone cannot parent itself: " + bone.id());
            }
        }
        if (roots != 1) {
            throw new IllegalArgumentException("Rig must have exactly one root bone, found " + roots);
        }
        for (int i = 0; i < bones.size(); i++) {
            boolean[] visited = new boolean[bones.size()];
            int cursor = i;
            while (cursor != -1) {
                if (visited[cursor]) {
                    throw new IllegalArgumentException("Bone hierarchy contains a cycle at " + bones.get(cursor).id());
                }
                visited[cursor] = true;
                cursor = bones.get(cursor).parentIndex();
            }
        }
        return Collections.unmodifiableMap(index);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int boneCount() { return bones.size(); }
    public List<BoneDefinition> bones() { return bones; }
    public BoneDefinition bone(int index) { return bones.get(index); }
    public int requireBoneIndex(String boneId) {
        Integer index = boneIndexById.get(boneId);
        if (index == null) throw new IllegalArgumentException("Unknown bone: " + boneId);
        return index;
    }
    public int findBoneIndex(String boneId) { return boneIndexById.getOrDefault(boneId, -1); }
}
