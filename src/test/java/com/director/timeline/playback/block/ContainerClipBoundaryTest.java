package com.director.timeline.playback.block;

import com.director.core.DirectorServices;
import com.director.core.animation.ClipAnimationConfig;
import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.timeline.model.TimelineClip;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ContainerClipBoundaryTest {
    private Set<?> blocks(TimelineClipBlockController controller, Container container, TimelineClip clip,
            float time, boolean enabled, String accessor) throws Exception {
        Method sample = TimelineClipBlockController.class.getDeclaredMethod("calculateVisibleBlocksInternal",
                TimelineClip.class, float.class, boolean.class, Container.class, ClipAnimationConfig.class, boolean.class);
        sample.setAccessible(true);
        Object result = sample.invoke(controller, clip, time, enabled, container, clip.getAnimationConfig(), true);
        Method field = result.getClass().getDeclaredMethod(accessor); field.setAccessible(true);
        return (Set<?>) field.invoke(result);
    }

    @Test void completedContainerKeepsAllBlocksAndReusesItsStableSetAcrossRightSideTimes() throws Exception {
        DirectorServices.initialize();
        var controller = TimelineClipBlockController.getInstance();
        var container = new Container("building");
        container.addBlocks(Set.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6)));
        var clip = new TimelineClip("building", 2, 4, 0);
        var right = blocks(controller, container, clip, 100, true, "worldBlocks");
        assertEquals(container.getBlocksView(), right);
        assertSame(right, blocks(controller, container, clip, 101, true, "worldBlocks"));
        assertTrue(blocks(controller, container, clip, 1, true, "renderBlocks").isEmpty());
        assertTrue(blocks(controller, container, clip, 100, false, "renderBlocks").isEmpty());
        container.setType(ContainerType.DEMOLITION);
        assertTrue(blocks(controller, container, clip, 100, true, "worldBlocks").isEmpty());
    }
}
