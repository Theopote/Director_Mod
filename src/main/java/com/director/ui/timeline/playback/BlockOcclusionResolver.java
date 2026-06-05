package com.director.ui.timeline.playback;

import com.director.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * 基于容器边界盒的简化遮挡检测器。
 *
 * <p>当前实现：仅检测目标容器的包围盒内，与其它容器方块位置的重叠。</p>
 */
public class BlockOcclusionResolver {

    /**
     * 计算哪些容器的方块会与目标容器发生空间遮挡（基于包围盒重叠）。
     *
     * @param target     当前正在播放的容器
     * @param candidates 可能遮挡的其它容器集合
     * @return 容器ID -> 需要淡化/隐藏的方块集合
     */
    public Map<UUID, Set<BlockPos>> resolveOccludingBlocks(Container target, Collection<Container> candidates) {
        Map<UUID, Set<BlockPos>> occluding = new HashMap<>();
        if (target == null || candidates == null || candidates.isEmpty()) {
            return occluding;
        }

        Container.BoundingBox targetBox = target.getBoundingBox();
        if (targetBox == null) {
            return occluding;
        }

        for (Container candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            Set<BlockPos> hits = new HashSet<>();
            for (BlockPos pos : candidate.getBlocksView()) {
                if (targetBox.contains(pos)) {
                    hits.add(pos);
                }
            }
            if (!hits.isEmpty()) {
                occluding.put(candidate.getId(), hits);
            }
        }
        return occluding;
    }
}
