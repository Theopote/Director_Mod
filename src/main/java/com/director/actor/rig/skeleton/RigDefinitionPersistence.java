package com.director.actor.rig.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** JSON (de)serialization for a model-independent {@link RigDefinition}, used for custom skeletons. */
public final class RigDefinitionPersistence {
    private RigDefinitionPersistence() {}

    public static JsonObject write(RigDefinition rig) {
        JsonObject out = new JsonObject();
        out.addProperty("id", rig.id());
        out.addProperty("displayName", rig.displayName());
        JsonArray bones = new JsonArray();
        for (BoneDefinition bone : rig.bones()) bones.add(writeBone(bone));
        out.add("bones", bones);
        return out;
    }

    public static RigDefinition read(JsonObject in) {
        String id = required(in, "id").getAsString();
        String displayName = in.has("displayName") ? in.get("displayName").getAsString() : id;
        JsonArray bonesArray = required(in, "bones").getAsJsonArray();
        List<BoneDefinition> bones = new ArrayList<>();
        for (var element : bonesArray) bones.add(readBone(element.getAsJsonObject()));
        return new RigDefinition(id, displayName, bones);
    }

    private static JsonObject writeBone(BoneDefinition bone) {
        JsonObject out = new JsonObject();
        out.addProperty("id", bone.id());
        out.addProperty("displayName", bone.displayName());
        out.addProperty("parentIndex", bone.parentIndex());
        out.add("translation", writeVector(bone.restLocal().translation()));
        out.add("rotation", writeQuaternion(bone.restLocal().rotation()));
        out.add("scale", writeVector(bone.restLocal().scale()));
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("translation", bone.capabilities().translation());
        capabilities.addProperty("rotation", bone.capabilities().rotation());
        capabilities.addProperty("scale", bone.capabilities().scale());
        out.add("capabilities", capabilities);
        return out;
    }

    private static BoneDefinition readBone(JsonObject in) {
        String id = required(in, "id").getAsString();
        String displayName = in.has("displayName") ? in.get("displayName").getAsString() : id;
        int parentIndex = required(in, "parentIndex").getAsInt();
        Vector3f translation = readVector(required(in, "translation").getAsJsonObject());
        Quaternionf rotation = readQuaternion(required(in, "rotation").getAsJsonObject());
        Vector3f scale = readVector(required(in, "scale").getAsJsonObject());
        BoneTransform restLocal = new BoneTransform(translation, rotation, scale);
        JsonObject capabilitiesObj = required(in, "capabilities").getAsJsonObject();
        BoneCapabilities capabilities = new BoneCapabilities(
                required(capabilitiesObj, "translation").getAsBoolean(),
                required(capabilitiesObj, "rotation").getAsBoolean(),
                required(capabilitiesObj, "scale").getAsBoolean());
        return new BoneDefinition(id, displayName, parentIndex, restLocal, capabilities);
    }

    private static JsonObject writeVector(Vector3f v) {
        JsonObject out = new JsonObject();
        out.addProperty("x", v.x); out.addProperty("y", v.y); out.addProperty("z", v.z);
        return out;
    }

    private static Vector3f readVector(JsonObject in) {
        return new Vector3f(required(in, "x").getAsFloat(), required(in, "y").getAsFloat(), required(in, "z").getAsFloat());
    }

    private static JsonObject writeQuaternion(Quaternionf q) {
        JsonObject out = new JsonObject();
        out.addProperty("x", q.x); out.addProperty("y", q.y); out.addProperty("z", q.z); out.addProperty("w", q.w);
        return out;
    }

    private static Quaternionf readQuaternion(JsonObject in) {
        return new Quaternionf(required(in, "x").getAsFloat(), required(in, "y").getAsFloat(),
                required(in, "z").getAsFloat(), required(in, "w").getAsFloat());
    }

    private static com.google.gson.JsonElement required(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull())
            throw new IllegalArgumentException("Missing rig field: " + name);
        return object.get(name);
    }
}
