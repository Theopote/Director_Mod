package com.director.ui.actor.path;

import com.director.timeline.model.entity.ActorRouteClip;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorPathControllerDraftTest {
    private ActorPathController controller() throws Exception {
        var constructor = ActorPathController.class.getDeclaredConstructor(); constructor.setAccessible(true);
        return constructor.newInstance();
    }
    private void set(ActorPathController controller, String field, Object value) throws Exception {
        var member = ActorPathController.class.getDeclaredField(field); member.setAccessible(true); member.set(controller, value);
    }
    @SuppressWarnings("unchecked") private List<Vec3d> points(ActorPathController controller) throws Exception {
        var member = ActorPathController.class.getDeclaredField("pathPoints"); member.setAccessible(true);
        return (List<Vec3d>) member.get(controller);
    }
    @Test void sceneCurvePickFindsVisibleUnlockedPathWithoutLoadingItFirst() throws Exception {
        var c = controller(); var path = new com.director.actor.path.ActorPathDefinition("Pick test");
        path.setWaypoints(List.of(new Vector3f(-1,0,5),new Vector3f(1,0,5)));
        var library = com.director.actor.path.ActorPathLibraryManager.getInstance(); library.add(path);
        var method = ActorPathController.class.getDeclaredMethod("pickLibraryPath", Vec3d.class, Vec3d.class); method.setAccessible(true);
        try {
            assertEquals(path.getId(), method.invoke(c, Vec3d.ZERO, new Vec3d(0,0,1)));
            path.setVisible(false); assertNotEquals(path.getId(), method.invoke(c, Vec3d.ZERO, new Vec3d(0,0,1)));
            path.setVisible(true); path.setLocked(true); assertNotEquals(path.getId(), method.invoke(c, Vec3d.ZERO, new Vec3d(0,0,1)));
        } finally { library.remove(path.getId()); }
    }
    @Test void completingSinglePointDraftCancelsWithoutCreatingPath() throws Exception {
        var controller = controller(); points(controller).add(new Vec3d(1,2,3));
        set(controller, "drawing", true); set(controller, "selectedAnchorIndex", 0);
        controller.finalizePath(); assertFalse(controller.isDrawing());
        assertEquals(0, controller.getAnchorCount()); assertEquals(-1, controller.getSelectedAnchorIndex());
    }
    @Test void startingNewDraftClearsOldRouteBindingWithoutChangingRoute() throws Exception {
        var controller = controller(); var source = new ActorRouteClip("Old", 0, 3);
        source.setWaypoints(List.of(new Vector3f(), new Vector3f(4,0,0)));
        set(controller, "editingClip", source); set(controller, "editingLibraryPathId", UUID.randomUUID());
        points(controller).add(new Vec3d(9,9,9)); set(controller, "selectedAnchorIndex", 0);
        var method = ActorPathController.class.getDeclaredMethod("beginNewDraft"); method.setAccessible(true); method.invoke(controller);
        assertFalse(controller.isRouteEditing()); assertFalse(controller.isLibraryEditing());
        assertEquals(0, controller.getAnchorCount()); assertEquals(-1, controller.getSelectedAnchorIndex());
        assertEquals(new Vector3f(4,0,0), source.getWaypoints().getLast());
    }
    @Test void cancellationRestoresDraftAndReleaseCannotCommitCancelledGesture() throws Exception {
        var controller = controller(); var before = List.of(Vec3d.ZERO, new Vec3d(4,0,0));
        points(controller).addAll(List.of(Vec3d.ZERO, new Vec3d(10,0,0)));
        set(controller, "anchorDragActive", true); set(controller, "anchorDragDirty", true);
        set(controller, "dragBeforePoints", before); controller.cancelAnchorDrag();
        assertEquals(before, controller.getAnchorPositions()); controller.finishAnchorDrag();
        assertEquals(before, controller.getAnchorPositions());
    }
    @Test void deactivationDiscardsGestureAndClearsPersistentTargetReference() throws Exception {
        var controller = controller(); var source = new ActorRouteClip("Route", 0, 3);
        source.setWaypoints(List.of(new Vector3f(), new Vector3f(4,0,0)));
        set(controller, "active", true); set(controller, "editingClip", source);
        set(controller, "anchorDragActive", true); set(controller, "anchorDragDirty", true);
        set(controller, "dragBeforePoints", List.of(Vec3d.ZERO, new Vec3d(4,0,0)));
        points(controller).addAll(List.of(Vec3d.ZERO, new Vec3d(20,0,0))); controller.deactivate();
        assertFalse(controller.isActive()); assertFalse(controller.isRouteEditing()); assertEquals(0, controller.getAnchorCount());
        assertEquals(new Vector3f(4,0,0), source.getWaypoints().getLast()); controller.finishAnchorDrag();
        assertEquals(new Vector3f(4,0,0), source.getWaypoints().getLast());
    }
}
