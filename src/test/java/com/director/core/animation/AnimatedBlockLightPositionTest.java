package com.director.core.animation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnimatedBlockLightPositionTest {
    @Test void staticSnapshotsUseIdentityWithoutAllocatingTransform() {
        var pos=new BlockPos(-7,40,2);var snapshot=new BlockAnimationSnapshot(pos,null);
        assertSame(pos,BlockAnimationRenderer.displayedLightPosition(pos,snapshot));
    }
    @Test void translatedSnapshotsSampleDisplayedCenterIncludingNegativeCoordinates() {
        var pos=new BlockPos(-7,40,2);var snapshot=new BlockAnimationSnapshot(pos,null);
        var transform=new MatrixStack();transform.translate(-3.75,2,9);snapshot.setTransform(transform);
        assertEquals(new BlockPos(-11,42,11),BlockAnimationRenderer.displayedLightPosition(pos,snapshot));
    }
}
