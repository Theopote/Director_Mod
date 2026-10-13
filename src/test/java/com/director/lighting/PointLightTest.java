package com.director.lighting;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.director.timeline.model.TimelineViewportState;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PointLightTest {
    private PointLight light() { return PointLight.create(.5,.5,.5); }
    private TimelineModel model() {
        var m=new TimelineModel(new TimelineViewportState());m.setSuppressPersistenceWrites(true);return m;
    }
    @Test void radialFalloffIsContinuousAndZeroBeyondRange() {
        var lights=List.of(light());
        assertEquals(15,PointLightSampler.level(lights,.5,.5,.5),1e-8);
        assertEquals(7.5,PointLightSampler.level(lights,4.5,.5,.5),1e-8);
        assertEquals(0,PointLightSampler.level(lights,8.5,.5,.5));
        assertEquals(0,PointLightSampler.level(lights,100,.5,.5));
        assertEquals(7.5,PointLightSampler.level(lights,.5,-3.5,.5),1e-8);
    }
    @Test void falloffExponentChangesTheInteriorButKeepsPeakAndRange() {
        var l=light().settings(12,8,2,true);
        assertEquals(3,PointLightSampler.level(List.of(l),4.5,.5,.5),1e-8);
        assertEquals(12,PointLightSampler.level(List.of(l),.5,.5,.5),1e-8);
    }
    @Test void packedLightPreservesSkyAndBrighterExistingLight() {
        assertEquals(0x00d000f0,PointLightSampler.packed(List.of(light()),0x00d00030,.5,.5,.5));
        assertEquals(0x00d000b0,PointLightSampler.packed(List.of(light()),0x00d000b0,4.5,.5,.5));
        assertEquals(0x00d00078,PointLightSampler.packed(List.of(light()),0x00d00030,4.5,.5,.5));
    }
    @Test void disabledAndZeroIntensityLightsDoNotContribute() {
        assertEquals(0,PointLightSampler.level(List.of(light().settings(15,8,1,false)),.5,.5,.5));
        assertEquals(0,PointLightSampler.level(List.of(light().settings(0,8,1,true)),.5,.5,.5));
    }
    @Test void multipleLightsUseStrongestContributionWithoutOverbrightening() {
        assertEquals(15,PointLightSampler.level(List.of(light(),light()),.5,.5,.5));
        assertEquals(240,PointLightSampler.packed(List.of(light(),light()),0,.5,.5,.5));
    }
    @Test void moveRebuildsBothFormerAndNewInfluenceVolumes() {
        var before=light().at(-.1,20,0);var after=before.at(100,20,0);
        var affected=PointLightChanges.affected(List.of(before),List.of(after));
        assertEquals(2,affected.size());
        assertTrue(affected.get(0).minX()<=-9 && affected.get(0).maxX()>=7);
        assertTrue(affected.get(1).minX()<=92 && affected.get(1).maxX()>=108);
        assertTrue(PointLightChanges.affected(List.of(before),List.of(before)).isEmpty());
    }
    @Test void deletionAndDisableInvalidateOldLightWhileEnableInvalidatesNewLight() {
        var light=light();var disabled=light.settings(15,8,1,false);
        assertEquals(1,PointLightChanges.affected(List.of(light),List.of()).size());
        assertEquals(1,PointLightChanges.affected(List.of(light),List.of(disabled)).size());
        assertEquals(1,PointLightChanges.affected(List.of(disabled),List.of(light)).size());
    }
    @Test void parametersRejectNonFiniteAndUnsupportedValues() {
        var l=light();
        assertThrows(IllegalArgumentException.class,()->l.at(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->l.settings(16,8,1,true));
        assertThrows(IllegalArgumentException.class,()->l.settings(15,Float.POSITIVE_INFINITY,1,true));
        assertThrows(IllegalArgumentException.class,()->l.settings(15,8,0,true));
        assertThrows(IllegalArgumentException.class,()->l.settings(15,33,1,true));
    }
    @Test void persistenceRoundTripRestoresSettingsAndStableId() {
        var model=model();var light=light().at(-1.25,70,9).settings(7,13,2,false);
        model.replacePointLight(light.id(),light);
        var dto=TimelineModelPersistence.toDTO(model,null);
        var gson=new Gson();
        var decoded=gson.fromJson(gson.toJson(dto),TimelineModelPersistence.TimelineDTO.class);
        var restored=model();TimelineModelPersistence.fromDTO(decoded,restored,null);
        assertEquals(List.of(light),restored.getPointLights());
        assertTrue(restored.isSuppressPersistenceWrites());
    }
    @Test void loadingLegacyScenesClearsLightsAndDoesNotIntroduceAny() {
        var model=model();var light=light();model.replacePointLight(light.id(),light);
        var dto=new Gson().fromJson("{\"tracks\":[]}",TimelineModelPersistence.TimelineDTO.class);
        TimelineModelPersistence.fromDTO(dto,model,null);assertTrue(model.getPointLights().isEmpty());
        dto.pointLights=null;model.replacePointLight(light.id(),light);
        TimelineModelPersistence.fromDTO(dto,model,null);assertTrue(model.getPointLights().isEmpty());
    }
    @Test void timelineMemoryResetRemovesLightsAlongWithTracks() throws Exception {
        var model=model();var light=light();model.replacePointLight(light.id(),light);
        var reset=TimelineModel.class.getDeclaredMethod("resetInMemoryToDefaults");reset.setAccessible(true);reset.invoke(model);
        assertTrue(model.getPointLights().isEmpty());
    }
    @Test void duplicateIdsReplaceAndSnapshotsCannotBeMutated() {
        var model=model();var before=light();var snapshot=model.getPointLights();
        model.replacePointLight(before.id(),before);model.replacePointLight(before.id(),before.at(9,0,0));
        assertEquals(1,model.getPointLights().size());assertTrue(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class,()->model.getPointLights().clear());
        assertThrows(IllegalArgumentException.class,()->model.replacePointLight(UUID.randomUUID(),before));
    }
    @Test void creationMovementAndDeletionUndoAndRedoAsSeparateGestures() {
        var model=model();var history=new TimelineCommandHistory();var before=light();var after=before.at(12,2,3);
        history.executeCommand(new StateChangeCommand(model,"create",()->model.replacePointLight(before.id(),null),()->model.replacePointLight(before.id(),before)));
        history.executeCommand(new StateChangeCommand(model,"move",()->model.replacePointLight(before.id(),before),()->model.replacePointLight(before.id(),after)));
        history.executeCommand(new StateChangeCommand(model,"delete",()->model.replacePointLight(before.id(),after),()->model.replacePointLight(before.id(),null)));
        assertTrue(model.getPointLights().isEmpty());history.undo();assertEquals(after,model.getPointLight(before.id()));
        history.undo();assertEquals(before,model.getPointLight(before.id()));history.undo();assertTrue(model.getPointLights().isEmpty());
        history.redo();history.redo();assertEquals(after,model.getPointLight(before.id()));history.redo();assertTrue(model.getPointLights().isEmpty());
    }
}
