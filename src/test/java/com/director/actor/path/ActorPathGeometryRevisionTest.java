package com.director.actor.path;

import com.director.timeline.model.entity.ActorRouteClip;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorPathGeometryRevisionTest {
    @Test void attachedGeometryEditsBumpAndNamesCopiesAndAddsDoNot() {
        var library = new ActorPathLibrary();
        var path = new ActorPathDefinition("Curve");
        long added = library.geometryRevision();
        library.add(path);
        assertEquals(added, library.geometryRevision());

        path.setName("Renamed");
        path.setLocked(true);
        path.setVisible(false);
        assertEquals(added, library.geometryRevision());

        path.copy();
        assertEquals(added, library.geometryRevision());

        var loose = new ActorPathDefinition("Loose");
        loose.setWaypoints(List.of(new Vector3f(1, 0, 0)));
        loose.setInterpolationMode(ActorRouteClip.InterpolationMode.LINEAR);
        assertEquals(added, library.geometryRevision());

        path.setWaypoints(List.of(new Vector3f(), new Vector3f(2, 0, 0)));
        assertTrue(library.geometryRevision() > added);
        long shaped = library.geometryRevision();
        path.setWaypoints(null);
        assertTrue(library.geometryRevision() > shaped);
        shaped = library.geometryRevision();
        path.setInterpolationMode(ActorRouteClip.InterpolationMode.LINEAR);
        assertTrue(library.geometryRevision() > shaped);

        shaped = library.geometryRevision();
        library.add(new ActorPathDefinition("Second"));
        assertEquals(shaped, library.geometryRevision());
        assertTrue(library.remove(path.getId()));
        assertTrue(library.geometryRevision() > shaped);
        shaped = library.geometryRevision();
        assertEquals(false, library.remove(UUID.randomUUID()));
        assertEquals(shaped, library.geometryRevision());

        library.clear();
        assertTrue(library.geometryRevision() > shaped);
        shaped = library.geometryRevision();
        library.clear();
        assertEquals(shaped, library.geometryRevision());
    }
}
