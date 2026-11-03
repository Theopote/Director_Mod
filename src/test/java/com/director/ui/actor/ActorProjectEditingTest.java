package com.director.ui.actor;
import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorProjectEditingTest {
    final TimelineModel model = new TimelineModel(new TimelineViewportState());
    final TimelineCommandHistory history = new TimelineCommandHistory();
    final ActorRegistry registry = new ActorRegistry();
    @BeforeEach void setup() { ActorRegistryManager.setRegistry(registry); TimelineCommandBridge.bind(history); }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    @Test void groupPropertiesMembershipAndDeletionUndoInOrder() {
        var group = ActorProjectEditing.createGroup(model, registry, "Cast");
        var actor = registry.createActor("Player", ActorType.PLAYER);
        ActorProjectEditing.setMembership(model, registry, group, actor.getActorId(), true);
        var before = ActorProjectEditing.GroupState.capture(group);
        ActorProjectEditing.updateGroup(model, registry, group, before, before.withName("New").withSpacing(3));
        ActorProjectEditing.deleteGroup(model, registry, group, () -> {});
        assertTrue(registry.getGroup(group.getGroupId()).isEmpty());
        assertTrue(history.undo()); assertSame(group, registry.getGroup(group.getGroupId()).orElseThrow());
        assertTrue(history.undo()); assertEquals(before, ActorProjectEditing.GroupState.capture(group));
        assertTrue(history.undo()); assertFalse(group.containsActor(actor.getActorId()));
        assertTrue(history.undo()); assertTrue(registry.getGroup(group.getGroupId()).isEmpty());
        assertTrue(history.redo()); assertTrue(history.redo()); assertTrue(history.redo());
        assertEquals("New", group.getName()); assertEquals(3, group.getFormationSpacing());
        assertTrue(group.containsActor(actor.getActorId()));
    }
    @Test void memberOrderIsUndoableAndControlsSequentialDelay() {
        var group = registry.createGroup("Cast");
        var a = registry.createActor("A", ActorType.PLAYER).getActorId();
        var b = registry.createActor("B", ActorType.PLAYER).getActorId();
        group.addActor(a); group.addActor(b);
        group.setDelayPolicy(ActorGroup.DelayPolicy.SEQUENTIAL); group.setDelayStepSeconds(.5f);
        ActorProjectEditing.moveMember(model, registry, group, b, -1);
        assertEquals(java.util.List.of(b, a), group.getActorIds());
        var triggers = com.director.actor.group.ActorGroupController.getInstance().applyDelayPolicy(group, com.director.actor.action.ActorActionType.IDLE, 2);
        assertEquals(b, triggers.getFirst().actorId()); assertEquals(2, triggers.getFirst().triggerTime());
        assertTrue(history.undo()); assertEquals(java.util.List.of(a, b), group.getActorIds());
        assertTrue(history.redo()); assertEquals(java.util.List.of(b, a), group.getActorIds());
    }
    @Test void memberOrderBoundaryAndPlaybackDoNotCreateCommands() {
        var group = registry.createGroup("Cast");
        var a = registry.createActor("A", ActorType.PLAYER).getActorId(); group.addActor(a);
        ActorProjectEditing.moveMember(model, registry, group, a, -1);
        assertFalse(history.undo());
        model.setPlaying(true);
        assertThrows(IllegalArgumentException.class, () -> ActorProjectEditing.moveMember(model, registry, group, a, 1));
        assertEquals(java.util.List.of(a), group.getActorIds()); assertFalse(history.undo());
    }
    @Test void batchAddPreservesOrderDeduplicatesAndUndoesTogether() {
        var group = registry.createGroup("Cast");
        var a = registry.createActor("A", ActorType.PLAYER).getActorId();
        var b = registry.createActor("B", ActorType.PLAYER).getActorId();
        var c = registry.createActor("C", ActorType.PLAYER).getActorId();
        group.addActor(a);
        ActorProjectEditing.addMembers(model, registry, group, java.util.List.of(b, a, b, c));
        assertEquals(java.util.List.of(a, b, c), group.getActorIds());
        assertTrue(history.undo()); assertEquals(java.util.List.of(a), group.getActorIds()); assertFalse(history.undo());
        assertTrue(history.redo()); assertEquals(java.util.List.of(a, b, c), group.getActorIds());
    }
    @Test void batchAddRejectsMissingActorWithoutPartialMutation() {
        var group = registry.createGroup("Cast");
        var a = registry.createActor("A", ActorType.PLAYER).getActorId();
        assertThrows(IllegalArgumentException.class, () -> ActorProjectEditing.addMembers(model, registry, group, java.util.List.of(a, java.util.UUID.randomUUID())));
        assertTrue(group.getActorIds().isEmpty()); assertFalse(history.undo());
    }
    @Test void appearanceUndoRetainsPreviousSkinBinding() {
        var actor = registry.createActor("P", ActorType.PLAYER); actor.setSkinFileName("old.png");
        var before = ActorProjectEditing.Appearance.capture(actor);
        ActorProjectEditing.appearance(model, registry, actor, before.withSkin("new.png"), () -> {});
        assertEquals("new.png", actor.getSkinFileName());
        assertTrue(history.undo()); assertEquals(before, ActorProjectEditing.Appearance.capture(actor));
        assertTrue(history.redo()); assertEquals("new.png", actor.getSkinFileName());
    }
    @Test void staleGroupSnapshotCannotOverwriteNewProperties() {
        var group = registry.createGroup("Cast"); var before = ActorProjectEditing.GroupState.capture(group);
        group.setName("Other");
        assertThrows(IllegalArgumentException.class, () -> ActorProjectEditing.updateGroup(model, registry, group, before, before.withSpacing(4)));
        assertEquals("Other", group.getName()); assertFalse(history.undo());
    }
}
