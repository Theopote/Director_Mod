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
