package com.director.timeline.playback.block;

import com.director.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * 基于容器边界盒的简化遮挡检测器。
 *
 * <p>当前实现：仅检测目标容器的包围盒内，与其它容器方块位置的重叠。</p>
 *
 * <p>⭐ 性能优化：添加缓存机制，避免每帧重复计算相同容器对之间的遮挡关系</p>
 */
public class BlockOcclusionResolver {

    /**
     * 遮挡检测结果缓存
     * Key: target容器ID + ":" + candidate容器ID + ":" + target.modifiedTime + ":" + candidate.modifiedTime
     * Value: 被遮挡的方块集合
     */
    private final Map<String, Set<BlockPos>> occlusionCache = new HashMap<>();

    /**
     * 缓存清理计数器：每120帧（约2秒）清理一次过期缓存
     */
    private int cacheCleanupCounter = 0;

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

        UUID targetId = target.getId();
        long targetModTime = target.getModifiedTime();

        // ⭐ 性能优化：定期清理缓存，防止内存泄漏
        if (++cacheCleanupCounter >= 120) {
            cacheCleanupCounter = 0;
            // 保留最近使用的缓存（简单策略：保留一半）
            if (occlusionCache.size() > 100) {
                List<String> keys = new ArrayList<>(occlusionCache.keySet());
                int removeCount = keys.size() / 2;
                for (int i = 0; i < removeCount; i++) {
                    occlusionCache.remove(keys.get(i));
                }
            }
        }

        for (Container candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }

            UUID candidateId = candidate.getId();
            long candidateModTime = candidate.getModifiedTime();

            // ⭐ 性能关键：构建缓存键，检查是否已计算过
            String cacheKey = targetId + ":" + candidateId + ":" + targetModTime + ":" + candidateModTime;
            Set<BlockPos> hits = occlusionCache.get(cacheKey);

            if (hits == null) {
                // 缓存未命中，需要计算
                hits = new HashSet<>();

                // ⭐ 性能优化：如果候选容器方块数量很大，先做粗略的包围盒检测
                Container.BoundingBox candidateBox = candidate.getBoundingBox();
                if (candidateBox != null && !boxesIntersect(targetBox, candidateBox)) {
                    // 包围盒完全不相交，直接跳过
                    occlusionCache.put(cacheKey, hits); // 缓存空结果
                    continue;
                }

                // 遍历候选容器的方块，检查是否在目标包围盒内
                for (BlockPos pos : candidate.getBlocksView()) {
                    if (targetBox.contains(pos)) {
                        hits.add(pos);
                    }
                }

                // 缓存结果（即使是空集合也缓存，避免重复计算）
                occlusionCache.put(cacheKey, hits);
            }

            if (!hits.isEmpty()) {
                // 返回一个新的HashSet，避免外部修改缓存
                occluding.put(candidateId, new HashSet<>(hits));
            }
        }
        return occluding;
    }

    /**
     * 快速检测两个包围盒是否相交
     */
    private boolean boxesIntersect(Container.BoundingBox box1, Container.BoundingBox box2) {
        // AABB相交检测：如果在任一轴上不重叠，则不相交
        return !(box1.getMax().getX() < box2.getMin().getX() || box2.getMax().getX() < box1.getMin().getX() ||
                 box1.getMax().getY() < box2.getMin().getY() || box2.getMax().getY() < box1.getMin().getY() ||
                 box1.getMax().getZ() < box2.getMin().getZ() || box2.getMax().getZ() < box1.getMin().getZ());
    }
}
