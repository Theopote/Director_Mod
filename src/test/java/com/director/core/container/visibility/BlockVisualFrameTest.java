package com.director.core.container.visibility;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockVisualFrameTest {
    @Test void unchangedHiddenBlocksRemainHiddenButAnimationSuppressionIsTemporary() {
        var manager = BlockVisualStateManager.getInstance();
        manager.clearAll();
        var hidden = new BlockPos(1, 2, 3);
        var animated = new BlockPos(2, 2, 3);
        manager.setBlockState(hidden, BlockVisualStateManager.BlockVisualState.hidden());
        manager.setBlockState(animated, BlockVisualStateManager.BlockVisualState.normal());
        manager.setAnimatedBlockState(animated,
                BlockVisualStateManager.BlockVisualState.normal().withSuppressWorldRendering(true));
        manager.finishFrame();
        manager.beginTimelineFrame();
        assertTrue(manager.getBlockState(hidden).isHidden());
        assertFalse(manager.getBlockState(animated).shouldSuppressWorldRendering());
        assertTrue(manager.hasWorldSuppressionChanged(animated));
    }

    @Test void animatedGhostRestoresItsPersistentOpacityInsteadOfResettingToNormal() {
        var manager = BlockVisualStateManager.getInstance();
        manager.clearAll();
        var pos = BlockPos.ORIGIN;
        var ghost = BlockVisualStateManager.BlockVisualState.ghost(0.4f, true, true);
        manager.setBlockState(pos, ghost);
        manager.setAnimatedBlockState(pos, ghost.withSuppressWorldRendering(true));
        manager.setAnimatedBlockState(pos, ghost.withSuppressWorldRendering(true));
        manager.beginTimelineFrame();
        assertSame(ghost, manager.getBlockState(pos));
        manager.clearAll();
        manager.beginTimelineFrame();
        assertTrue(manager.getBlockState(pos).isNormal());
    }
}
