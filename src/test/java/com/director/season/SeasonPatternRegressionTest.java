package com.director.season;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SeasonPatternRegressionTest {
    @Test void highCoverageRetainsNaturalGaps() {
        int snow=0,ice=0,n=512*512;
        for(int x=0;x<512;x++)for(int z=0;z<512;z++) {
            var pos=new BlockPos(x,64,z);
            if(NoiseField.snowCoverage(pos)<.97f)snow++;
            if(NoiseField.iceCoverage(pos)<.92f)ice++;
        }
        double sr=(double)snow/n,ir=(double)ice/n;
        System.out.println("PATCH_COVERAGE snow="+sr+" ice="+ir);
        assertTrue(sr>.9 && sr<.995,"snow gaps and strong coverage");
        assertTrue(ir>.8 && ir<.97,"ice gaps and strong coverage");
    }
    @Test void worldCoordinatesDoNotRepeatAlongOldHashDiagonal() {
        assertNotEquals(NoiseField.snowCoverage(new BlockPos(0,64,0)),NoiseField.snowCoverage(new BlockPos(20,64,-620)));
        assertNotEquals(NoiseField.iceCoverage(new BlockPos(0,64,0)),NoiseField.iceCoverage(new BlockPos(20,64,-620)));
    }
    @Test void largeAndNegativeCoordinatesAreFiniteAndRepeatable() {
        for(int x:new int[]{-30000000,-1024,0,1024,30000000})for(int z:new int[]{-30000000,-1024,0,1024,30000000}) {
            var pos=new BlockPos(x,64,z);float s=NoiseField.snowCoverage(pos),i=NoiseField.iceCoverage(pos);
            assertTrue(s>=0 && s<=1);assertTrue(i>=0 && i<=1);
            assertEquals(s,NoiseField.snowCoverage(new BlockPos(x,200,z)));
            assertEquals(i,NoiseField.iceCoverage(pos));
        }
    }
    @Test void driftHeightIsSpatiallyCoherent() {
        double adjacent=0,distant=0;
        for(int x=0;x<128;x++)for(int z=0;z<128;z++) {
            float a=NoiseField.snowThickness(new BlockPos(x,64,z));
            adjacent+=Math.abs(a-NoiseField.snowThickness(new BlockPos(x+1,64,z)));
            distant+=Math.abs(a-NoiseField.snowThickness(new BlockPos(x+100,64,z+100)));
        }
        assertTrue(adjacent<distant*.3,"neighbouring drift heights must change gently");
    }
    @Test void coverageGrowthIsNestedAndSnowEdgesAreThin() {
        for(int x=0;x<128;x++) {
            var pos=new BlockPos(x,64,x/2);float noise=NoiseField.snowCoverage(pos);
            if(NoiseField.shouldCover(pos,.3f,noise))assertTrue(NoiseField.shouldCover(pos,.7f,noise));
        }
        assertEquals(0,NoiseField.snowLayers(6,0,.5f,.6f,false));
        assertEquals(1,NoiseField.snowLayers(6,0,.5f,.499f,false));
        assertEquals(6,NoiseField.snowLayers(6,0,.5f,.2f,false));
        assertEquals(1,NoiseField.snowLayers(6,0,.5f,.2f,true));
    }
    private Set<Long> drain(SeasonScanWindow window) {
        Set<Long> result=new HashSet<>();Long pos;
        while((pos=window.poll())!=null){assertTrue(result.add(pos));window.visited(pos);}
        return result;
    }
    @Test void movingOneChunkQueuesOnlyNewStrip() {
        var window=new SeasonScanWindow(32);window.moveTo(new BlockPos(0,64,0),p->true);
        Set<Long> first=drain(window);assertEquals(65*65,first.size());
        window.moveTo(new BlockPos(16,64,0),p->true);Set<Long> moved=drain(window);
        assertEquals(16*65,moved.size());assertTrue(Collections.disjoint(first,moved));
        window.moveTo(new BlockPos(17,64,0),p->true);assertFalse(window.pending());
    }
    @Test void scanRecenterRetainsUnfinishedOverlap() {
        var window=new SeasonScanWindow(32);window.moveTo(new BlockPos(0,64,0),p->true);
        Set<Long> completed=new HashSet<>();for(int n=0;n<30;n++){long c=window.poll();completed.add(c);window.visited(c);}
        window.moveTo(new BlockPos(16,64,0),p->true);Set<Long> rest=drain(window);
        assertTrue(Collections.disjoint(completed,rest));assertEquals(65*65-30,rest.size());
    }
    @Test void newlyLoadedColumnsAreDiscoveredWithoutRescanningOverlap() {
        var window=new SeasonScanWindow(32);window.moveTo(new BlockPos(0,64,0),p->SeasonScanWindow.x(p)<16);
        Set<Long> first=drain(window);
        window.moveTo(new BlockPos(0,64,0),p->true);Set<Long> added=drain(window);
        assertTrue(Collections.disjoint(first,added));assertEquals(65*65,first.size()+added.size());
    }
    @Test void reloadedChunkIsRediscovered() {
        var window=new SeasonScanWindow(32);window.moveTo(new BlockPos(-16,64,-16),p->true);drain(window);
        window.moveTo(new BlockPos(-16,64,-16),p->SeasonScanWindow.x(p)>=0);assertFalse(window.pending());
        window.moveTo(new BlockPos(-16,64,-16),p->true);assertTrue(window.pending());
    }
}
