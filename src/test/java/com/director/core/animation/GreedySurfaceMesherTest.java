package com.director.core.animation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GreedySurfaceMesherTest {
    @Test void rectangularWallBecomesOneFace() {
        var cells = new HashMap<GreedySurfaceMesher.Cell, String>();
        for (int y=0; y<12; y++) for (int x=0; x<20; x++) cells.put(new GreedySurfaceMesher.Cell(x,y), "stone");
        var faces = GreedySurfaceMesher.merge(cells);
        assertEquals(List.of(new GreedySurfaceMesher.Rectangle<>(0,0,20,12,"stone")), faces);
        assertEquals(240, cells.size()); // input is reusable
    }
    @Test void holesMaterialsAndIslandsKeepExactCoverage() {
        var cells = new HashMap<GreedySurfaceMesher.Cell, String>();
        for (int y=-3; y<4; y++) for (int x=-3; x<4; x++)
            if (x!=0 || y!=0) cells.put(new GreedySurfaceMesher.Cell(x,y), x<0 ? "stone" : "grass");
        cells.put(new GreedySurfaceMesher.Cell(1000000,-1000000), "stone");
        assertCoverage(cells);
    }
    @Test void incompatibleTextureTintLightOrAlphaCannotMerge() {
        record Material(String texture, int tint, int light, float alpha) {}
        var cells = new HashMap<GreedySurfaceMesher.Cell, Material>();
        cells.put(new GreedySurfaceMesher.Cell(0,0), new Material("stone", 1, 12, .5f));
        cells.put(new GreedySurfaceMesher.Cell(1,0), new Material("dirt", 1, 12, .5f));
        cells.put(new GreedySurfaceMesher.Cell(2,0), new Material("dirt", 2, 12, .5f));
        cells.put(new GreedySurfaceMesher.Cell(3,0), new Material("dirt", 2, 13, .5f));
        cells.put(new GreedySurfaceMesher.Cell(4,0), new Material("dirt", 2, 13, .3f));
        assertEquals(5, GreedySurfaceMesher.merge(cells).size());
        assertCoverage(cells);
    }
    @Test void randomMasksHaveNoOverlapOrInventedFaces() {
        var random = new Random(42);
        for (int round=0; round<100; round++) {
            var cells = new HashMap<GreedySurfaceMesher.Cell, Integer>();
            for (int y=0; y<15; y++) for (int x=0; x<15; x++)
                if (random.nextBoolean()) cells.put(new GreedySurfaceMesher.Cell(x,y), random.nextInt(3));
            assertCoverage(cells);
        }
    }
    @Test void emptyInputAndNullMaterialHaveDefinedBehavior() {
        assertTrue(GreedySurfaceMesher.merge(Map.of()).isEmpty());
        var cells = new HashMap<GreedySurfaceMesher.Cell, Object>();
        cells.put(new GreedySurfaceMesher.Cell(0,0), null);
        assertThrows(NullPointerException.class, () -> GreedySurfaceMesher.merge(cells));
    }
    private <T> void assertCoverage(Map<GreedySurfaceMesher.Cell,T> cells) {
        var reconstructed = new HashMap<GreedySurfaceMesher.Cell,T>();
        for (var rect : GreedySurfaceMesher.merge(cells))
            for (int y=0; y<rect.height(); y++) for (int x=0; x<rect.width(); x++) {
                var cell = new GreedySurfaceMesher.Cell(rect.u()+x,rect.v()+y);
                assertNull(reconstructed.put(cell,rect.material()), "overlapping faces");
            }
        assertEquals(cells,reconstructed);
    }
}
