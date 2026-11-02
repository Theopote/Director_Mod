package com.director.ui.actor;
import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorAppearanceEditingTest {
    final TimelineModel model=new TimelineModel(new TimelineViewportState());
    final ActorRegistry registry=new ActorRegistry();
    final TimelineCommandHistory history=new TimelineCommandHistory();
    Actor actor;
    @BeforeEach void setup(){ActorRegistryManager.setRegistry(registry);actor=registry.createActor("P",ActorType.PLAYER);TimelineCommandBridge.bind(history);}
    @AfterEach void cleanup(){TimelineCommandBridge.bind(null);ActorRegistryManager.setRegistry(null);}
    @Test void styleIsOneCommandWithUndoRedoAndRejectsStaleInput() {
        var before=ActorAppearanceEditing.Style.capture(actor.getRigProfile().getStyle());
        var after=new ActorAppearanceEditing.Style(1.2f,1.1f,1,.8f,1);
        ActorAppearanceEditing.style(model,actor,before,after);assertEquals(after,ActorAppearanceEditing.Style.capture(actor.getRigProfile().getStyle()));
        assertEquals(1,history.getUndoSize());assertTrue(history.undo());assertEquals(before,ActorAppearanceEditing.Style.capture(actor.getRigProfile().getStyle()));
        assertTrue(history.redo());assertThrows(IllegalArgumentException.class,()->ActorAppearanceEditing.style(model,actor,before,after));
    }
    @Test void playbackAndMissingBindingsRejectWithoutMutation() {
        var before=ActorAppearanceEditing.Style.capture(actor.getRigProfile().getStyle());model.setPlaying(true);
        assertThrows(IllegalArgumentException.class,()->ActorAppearanceEditing.style(model,actor,before,new ActorAppearanceEditing.Style(1.2f,1,1,1,1)));
        model.setPlaying(false);assertThrows(IllegalArgumentException.class,()->ActorAppearanceEditing.binding(model,actor,new ActorAppearanceEditing.Binding("missing",null)));
        assertNull(actor.getCustomRigId());assertEquals(0,history.getUndoSize());
    }
    @Test void modelRigBindingCanUndoWithoutDeletingPreviousReferences() {
        var rig=new com.director.actor.rig.skeleton.RigDefinition("test:binding-rig","Test",com.director.actor.rig.skeleton.HumanoidRig.DEFINITION.bones());
        var library=com.director.actor.rig.skeleton.CustomRigLibrary.getInstance();library.register(rig);
        try {
            actor.setCustomModelId("old:missing-model");var before=ActorAppearanceEditing.Binding.capture(actor);
            ActorAppearanceEditing.binding(model,actor,new ActorAppearanceEditing.Binding(rig.id(),null));
            assertEquals(rig.id(),actor.getCustomRigId());assertNull(actor.getCustomModelId());
            assertTrue(history.undo());assertEquals(before,ActorAppearanceEditing.Binding.capture(actor));
            assertTrue(history.redo());assertEquals(rig.id(),actor.getCustomRigId());
        } finally {library.remove(rig.id());}
    }
}
