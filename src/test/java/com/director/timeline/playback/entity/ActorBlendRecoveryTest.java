package com.director.timeline.playback.entity;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorBlendRecoveryTest {
    private final Gson gson=new Gson();
    private JsonObject source() {
        var clip=new EntitySpawnClip("Keep actor",2,9);clip.setBoneAnimationId("project:keep");clip.setPosition(1,2,3);
        clip.setBlendSettings(new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,.4f,RigPose.ROTATION,false,Set.of("head"),.3f,.5f));
        var key=new EntityClipKeyframe(4);key.setPosition(4,5,6);key.setChannels(RigPose.TRANSLATION);clip.addKeyframe(key);
        return gson.toJsonTree(TimelineModelPersistence.entityClipToDTO(clip)).getAsJsonObject();
    }
    private EntitySpawnClip load(JsonObject json) {
        return (EntitySpawnClip)TimelineModelPersistence.entityClipFromDTO(gson.fromJson(json,TimelineModelPersistence.EntityClipDTO.class));
    }
    private void kept(EntitySpawnClip clip) {
        assertEquals("Keep actor",clip.getName());assertEquals(2,clip.getStartTime());assertEquals(9,clip.getEndTime());
        assertEquals("project:keep",clip.getBoneAnimationId());assertEquals(1,clip.getKeyframes().size());
        assertEquals(4,clip.getKeyframes().getFirst().getTime());assertEquals(new org.joml.Vector3f(1,2,3),clip.getPosition());
    }
    @Test void missingAndNullNumericFieldsRecoverWithoutLosingClipKeysOrValidSettings() {
        var json=source();json.remove("poseBlendWeight");json.add("poseBlendFadeIn",JsonNull.INSTANCE);
        var clip=load(json);kept(clip);assertEquals(1,clip.getBlendSettings().weight());assertEquals(0,clip.getBlendSettings().fadeIn());
        assertEquals(PoseLayer.BlendMode.ADDITIVE,clip.getBlendSettings().mode());assertEquals(.5f,clip.getBlendSettings().fadeOut());
        assertEquals(Set.of("head"),clip.getBlendSettings().boneIds());assertFalse(clip.getLoadWarnings().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->clip.getLoadWarnings().clear());
    }
    @Test void wrongJsonTypesAreIsolatedBeforeDtoDeserializationCanFail() {
        var json=source();json.add("poseBlendWeight",new JsonObject());json.add("poseBlendFadeIn",new JsonArray());
        json.addProperty("poseBlendChannels","rotation");json.addProperty("poseBlendAllBones",42);json.addProperty("poseBlendBones",false);
        var clip=load(json);kept(clip);assertEquals(0,clip.getBlendSettings().weight());assertEquals(0,clip.getBlendSettings().channels());
        assertFalse(clip.getBlendSettings().allBones());assertTrue(clip.getBlendSettings().boneIds().isEmpty());
        assertEquals(0,clip.getBlendSettings().fadeIn());assertTrue(clip.getLoadWarnings().size()>=5);
    }
    @Test void finiteOutOfRangeValuesClampAndNonfiniteValuesDisableOnlyTheAffectedField() {
        var json=source();json.addProperty("poseBlendWeight",1.8f);json.addProperty("poseBlendFadeOut",-2);
        var clip=load(json);assertEquals(1,clip.getBlendSettings().weight());assertEquals(0,clip.getBlendSettings().fadeOut());
        for(var bad:List.of(new JsonPrimitive("NaN"),new JsonPrimitive(Double.POSITIVE_INFINITY),new JsonPrimitive("0.5"))) {
            json=source();json.add("poseBlendWeight",bad);clip=load(json);kept(clip);assertEquals(0,clip.getBlendSettings().weight());
            assertEquals(.3f,clip.getBlendSettings().fadeIn());
        }
    }
    @Test void unknownModeDisablesLayerAndUnsupportedChannelBitsDoNotBroadenMask() {
        var json=source();json.addProperty("poseBlendMode","UNSUPPORTED");var clip=load(json);kept(clip);
        assertEquals(PoseLayer.BlendMode.OVERRIDE,clip.getBlendSettings().mode());assertEquals(0,clip.getBlendSettings().weight());
        json=source();json.addProperty("poseBlendChannels",10);assertEquals(RigPose.ROTATION,load(json).getBlendSettings().channels());
        for(Number bad:List.<Number>of(-1,1.5,1e100)) {
            json.addProperty("poseBlendChannels",bad);assertEquals(0,load(json).getBlendSettings().channels());
        }
    }
    @Test void customBoneMasksNeverBecomeAllBonesOnMalformedInputAndUnknownIdsAreRetained() {
        var json=source();json.remove("poseBlendAllBones");
        var bones=new JsonArray();bones.add("head");bones.add("custom:tail");bones.add("head");bones.add(JsonNull.INSTANCE);bones.add(" ");bones.add(17);
        json.add("poseBlendBones",bones);var clip=load(json);kept(clip);
        assertFalse(clip.getBlendSettings().allBones());assertEquals(Set.of("head","custom:tail"),clip.getBlendSettings().boneIds());
        json.remove("poseBlendBones");json.addProperty("poseBlendAllBones",false);
        clip=load(json);assertFalse(clip.getBlendSettings().allBones());assertTrue(clip.getBlendSettings().boneIds().isEmpty());
    }
    @Test void repairedSettingsSaveAsValidDataAndWarningsAreNotPersisted() {
        var json=source();json.add("poseBlendWeight",new JsonArray());var first=load(json);assertFalse(first.getLoadWarnings().isEmpty());
        var saved=gson.toJsonTree(TimelineModelPersistence.entityClipToDTO(first)).getAsJsonObject();
        assertFalse(saved.has("loadWarnings"));var second=load(saved);kept(second);
        assertEquals(first.getBlendSettings(),second.getBlendSettings());assertTrue(second.getLoadWarnings().isEmpty());
        assertEquals(first.getBlendSettings(),second.getBoneAnimation().getBlendSettings());
    }
    @Test void malformedExplicitAnimationDoesNotBlockOtherClipsInTheDocument() {
        var animation=new EntityAnimationClip("Keep reference",2,9);animation.setAnimationId("project:wave");
        var json=gson.toJsonTree(TimelineModelPersistence.entityClipToDTO(animation)).getAsJsonObject();json.addProperty("poseBlendWeight",false);
        var list=new JsonArray();list.add(json);list.add(source());
        var dtos=gson.fromJson(list,TimelineModelPersistence.EntityClipDTO[].class);
        var restored=Arrays.stream(dtos).map(TimelineModelPersistence::entityClipFromDTO).toList();assertEquals(2,restored.size());
        var clip=(EntityAnimationClip)restored.getFirst();assertEquals("project:wave",clip.getAnimationId());
        assertEquals(0,clip.getBlendSettings().weight());assertFalse(clip.getLoadWarnings().isEmpty());
        assertTrue(restored.getLast().getLoadWarnings().isEmpty());
    }
    @Test void absentOptionalBlendBlockUsesDefaultsWithoutRepairWarnings() {
        var json=source();new ArrayList<>(json.keySet()).stream().filter(k->k.startsWith("poseBlend")).forEach(json::remove);
        var clip=load(json);kept(clip);assertEquals(PoseBlendSettings.DEFAULT,clip.getBlendSettings());assertTrue(clip.getLoadWarnings().isEmpty());
    }
}
