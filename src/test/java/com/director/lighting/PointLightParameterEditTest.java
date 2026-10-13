package com.director.lighting;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import com.director.ui.lighting.PointLightParameterEdit;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointLightParameterEditTest {
    TimelineModel model;
    PointLight light;
    TimelineCommandHistory history;
    @BeforeEach void setup() {
        model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        light = PointLight.create(0, 0, 0);
        model.replacePointLight(light.id(), light);
        history = new TimelineCommandHistory();
    }
    void record(PointLight before, PointLight after) {
        history.executeCommand(new StateChangeCommand(model, "Light parameter gesture",
                () -> model.replacePointLight(before.id(), before),
                () -> model.replacePointLight(after.id(), after)));
    }
    @Test void parameterAdjustmentChangesLightSamplingBeforeCommit() {
        var edit = new PointLightParameterEdit(model, light);
        var changed = light.settings(6, 12, 2, true);
        assertTrue(edit.preview(changed));
        assertEquals(changed, model.getPointLight(light.id()));
        assertEquals(6, PointLightSampler.level(model.getPointLights(), 0, 0, 0));
        assertEquals(1.5, PointLightSampler.level(model.getPointLights(), 6, 0, 0));
        assertEquals(0, history.getUndoSize());
        assertTrue(edit.commit(this::record));
        assertEquals(1, history.getUndoSize());
    }
    @Test void continuousSliderChangesProduceOneUndoAndRedoStepOnRelease() {
        var edit = new PointLightParameterEdit(model, light);
        for (int i = 14; i >= 3; i--) edit.preview(light.settings(i, 8, 1, true));
        var result = model.getPointLight(light.id());
        edit.commit(this::record);
        assertEquals(1, history.getUndoSize());
        assertFalse(edit.commit(this::record));
        history.undo(); assertEquals(light, model.getPointLight(light.id()));
        history.redo(); assertEquals(result, model.getPointLight(light.id()));
    }
    @Test void enabledAndPositionChangesPreviewImmediately() {
        var edit = new PointLightParameterEdit(model, light);
        edit.preview(light.settings(15, 8, 1, false));
        assertEquals(0, PointLightSampler.level(model.getPointLights(), 0, 0, 0));
        edit.commit(this::record);
        var move = new PointLightParameterEdit(model, model.getPointLight(light.id()));
        move.preview(light.at(20, 0, 0));
        assertEquals(0, PointLightSampler.level(model.getPointLights(), 0, 0, 0));
        assertEquals(15, PointLightSampler.level(model.getPointLights(), 20, 0, 0));
        move.commit(this::record);
        assertEquals(2, history.getUndoSize());
    }
    @Test void externalUndoOrDeletionCannotBeOverwrittenByLateBlur() {
        var edit = new PointLightParameterEdit(model, light);
        edit.preview(light.settings(7, 8, 1, true));
        model.replacePointLight(light.id(), light);
        assertFalse(edit.preview(light.settings(8, 8, 1, true)));
        assertFalse(edit.commit(this::record));
        assertEquals(light, model.getPointLight(light.id()));
        var deleted = new PointLightParameterEdit(model, light);
        deleted.preview(light.settings(9, 8, 1, true));
        model.replacePointLight(light.id(), null);
        assertFalse(deleted.commit(this::record));
        assertTrue(model.getPointLights().isEmpty());
        assertEquals(0, history.getUndoSize());
    }
    @Test void sceneResetInvalidatesThePendingGestureEvenIfLightIdIsReused() throws Exception {
        var edit = new PointLightParameterEdit(model, light);
        var changed = light.settings(9, 8, 1, true);
        edit.preview(changed);
        var reset = TimelineModel.class.getDeclaredMethod("resetInMemoryToDefaults");
        reset.setAccessible(true); reset.invoke(model);
        model.replacePointLight(light.id(), changed);
        assertFalse(edit.commit(this::record));
        assertEquals(0, history.getUndoSize());
    }
    @Test void returningToOriginalValueDoesNotCreateAnUndoEntry() {
        var edit = new PointLightParameterEdit(model, light);
        edit.preview(light.settings(4, 8, 1, true));
        edit.preview(light);
        assertFalse(edit.commit(this::record));
        assertEquals(0, history.getUndoSize());
    }
    @Test void switchingTargetsCommitsThePreviousLightWithoutChangingTheNextLight() {
        var other = PointLight.create(12, 0, 0);
        model.replacePointLight(other.id(), other);
        var edit = new PointLightParameterEdit(model, light);
        edit.preview(light.settings(4, 8, 1, true));
        assertFalse(edit.belongsTo(model, other.id()));
        edit.commit(this::record);
        assertEquals(other, model.getPointLight(other.id()));
        assertEquals(1, history.getUndoSize());
        assertEquals(4, model.getPointLight(light.id()).brightness());
    }
}
