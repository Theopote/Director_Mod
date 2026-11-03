package com.director.actor.persistence;

import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorRegistryPersistenceTest {
    @Test void groupRandomSeedAndDelayScheduleSurviveRoundTrip() {
        var registry = new ActorRegistry(); var group = registry.createGroup("Cast");
        group.addActor(registry.createActor("A", ActorType.PLAYER).getActorId());
        group.addActor(registry.createActor("B", ActorType.PLAYER).getActorId());
        group.setDelayPolicy(com.director.actor.ActorGroup.DelayPolicy.RANDOM); group.setDelayStepSeconds(.7f);
        group.setRandomSeed(Long.MIN_VALUE + 123);
        var restored = new ActorRegistry();
        ActorRegistryPersistence.loadFromJson(restored, ActorRegistryPersistence.getJsonObject(registry));
        var loaded = restored.getGroup(group.getGroupId()).orElseThrow();
        assertEquals(group.getRandomSeed(), loaded.getRandomSeed());
        var controller = com.director.actor.group.ActorGroupController.getInstance();
        assertEquals(controller.applyDelayPolicy(group, com.director.actor.action.ActorActionType.IDLE, 2),
                controller.applyDelayPolicy(loaded, com.director.actor.action.ActorActionType.IDLE, 2));
        var legacy = ActorRegistryPersistence.getJsonObject(registry);
        legacy.getAsJsonArray("groups").get(0).getAsJsonObject().remove("randomSeed");
        ActorRegistryPersistence.loadFromJson(restored, legacy);
        assertEquals(group.getGroupId().getMostSignificantBits() ^ group.getGroupId().getLeastSignificantBits(),
                restored.getGroup(group.getGroupId()).orElseThrow().getRandomSeed());
    }
    @Test
    void skinAndAllPlayerModelsSurviveRoundTrip() {
        ActorRegistry registry = new ActorRegistry();
        for (Actor.PlayerModel model : Actor.PlayerModel.values()) {
            Actor actor = registry.createActor(model.name(), ActorType.PLAYER);
            actor.setPlayerModel(model);
            actor.setLimbBendMode(model==Actor.PlayerModel.DEFAULT ? com.director.actor.rig.skeleton.LimbBendMode.SMOOTH : com.director.actor.rig.skeleton.LimbBendMode.SHARP);
            actor.setSkinFileName("skins/" + actor.getActorId() + ".png");
        }
        ActorRegistry restored = new ActorRegistry();
        ActorRegistryPersistence.loadFromJson(restored, ActorRegistryPersistence.getJsonObject(registry));
        assertEquals(registry.size(), restored.size());
        for (Actor actor : registry.getAllActors()) {
            Actor loaded = restored.getActor(actor.getActorId()).orElseThrow();
            assertEquals(actor.getPlayerModel(), loaded.getPlayerModel());
            assertEquals(actor.getLimbBendMode(), loaded.getLimbBendMode());
            assertEquals(actor.getSkinFileName(), loaded.getSkinFileName());
        }
    }

    @Test
    void missingUnknownAndNullModelsDoNotDropActors() {
        for (String value : new String[]{null, "FUTURE_MODEL", "NULL_FIELD"}) {
            ActorRegistry registry = new ActorRegistry();
            Actor actor = registry.createActor("Legacy", ActorType.PLAYER);
            var json = ActorRegistryPersistence.getJsonObject(registry);
            var entry = json.getAsJsonArray("actors").get(0).getAsJsonObject();
            if (value == null) entry.remove("playerModel");
            else if (value.equals("NULL_FIELD")) entry.add("playerModel", com.google.gson.JsonNull.INSTANCE);
            else entry.addProperty("playerModel", value);
            ActorRegistry restored = new ActorRegistry();
            ActorRegistryPersistence.loadFromJson(restored, json);
            assertEquals(Actor.PlayerModel.DEFAULT, restored.getActor(actor.getActorId()).orElseThrow().getPlayerModel());
        }
    }
}
