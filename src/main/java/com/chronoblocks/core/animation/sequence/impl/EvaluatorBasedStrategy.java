package com.chronoblocks.core.animation.sequence.impl;

import com.chronoblocks.core.animation.sequence.*;
import com.chronoblocks.core.animation.sequence.SequenceStrategy;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于排序算子的序列策略
 * 
 * <p>使用新的排序系统，支持参考系、主次排序、复合策略等。
 * 这是未来序列策略的基础实现。
 * 
 * <p>改进：
 * <ul>
 *   <li>使用归一化机制确保权重语义稳定</li>
 *   <li>支持时间映射（score → time）</li>
 *   <li>添加结果缓存提升性能</li>
 *   <li>支持多种并发模式</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0 - 添加归一化、时间映射和缓存
 */
public class EvaluatorBasedStrategy implements SequenceStrategy {
    
    /**
     * 缓存键：用于标识缓存条目
     */
    private static class CacheKey {
        final String containerId;
        final int configHash;
        
        CacheKey(String containerId, int configHash) {
            this.containerId = containerId;
            this.configHash = configHash;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return configHash == cacheKey.configHash && Objects.equals(containerId, cacheKey.containerId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(containerId, configHash);
        }
    }
    
    /**
     * 结果缓存（使用 WeakHashMap 避免内存泄漏）
     */
    private static final Map<CacheKey, List<BlockSchedule>> scheduleCache = new ConcurrentHashMap<>();
    
    @Override
    public List<BlockPos> order(List<BlockPos> blocks, Container container, SequenceParameters params) {
        // 使用新的方法生成 schedule，然后提取 BlockPos
        List<BlockSchedule> schedule = generateSchedule(blocks, container, params, 0.0f, 1.0f);
        List<BlockPos> result = new ArrayList<>();
        for (BlockSchedule bs : schedule) {
            result.add(bs.getBlockPos());
        }
        return result;
    }
    
    /**
     * 生成方块时间调度（带归一化和时间映射）
     * 
     * @param blocks 方块列表
     * @param container 容器
     * @param params 序列参数
     * @param clipStartTime 片段开始时间（秒）
     * @param clipDuration 片段持续时间（秒）
     * @return 方块调度列表
     */
    public List<BlockSchedule> generateSchedule(List<BlockPos> blocks, Container container, 
                                                 SequenceParameters params, float clipStartTime, float clipDuration) {
        if (blocks == null || blocks.isEmpty() || container == null) {
            return new ArrayList<>();
        }
        
        // 检查缓存
        int configHash = computeConfigHash(params);
        CacheKey cacheKey = new CacheKey(container.getId().toString(), configHash);
        List<BlockSchedule> cached = scheduleCache.get(cacheKey);
        if (cached != null) {
            // 返回缓存的副本（调整时间）
            List<BlockSchedule> result = new ArrayList<>();
            for (BlockSchedule bs : cached) {
                result.add(new BlockSchedule(
                    bs.getBlockPos(),
                    clipStartTime + bs.getStartTime(),
                    bs.getDuration(),
                    bs.getNormalizedScore(),
                    bs.getGroupIndex()
                ));
            }
            return result;
        }
        
        // 使用新的 BlockSequenceGenerator
        List<BlockSequenceEntry> entries = BlockSequenceGenerator.generate(blocks, container, params);
        
        // 转换为 BlockSchedule
        List<BlockSchedule> schedule = new ArrayList<>();
        float globalDelay = params.getGlobalDelay();
        float perBlockDuration = params.getPerBlockDuration();
        
        // 如果 perBlockDuration 为 0，自动计算
        if (perBlockDuration <= 0.0f) {
            perBlockDuration = Math.max(0.01f, clipDuration / entries.size());
        }
        
        float baseStart = clipStartTime + globalDelay;
        float availableTime = clipDuration - perBlockDuration;
        if (availableTime < 0) availableTime = 0;
        
        ConcurrencyMode concurrency = params.getConcurrencyMode();
        
        switch (concurrency) {
            case ALL_AT_ONCE:
                // 所有方块同时开始
                for (BlockSequenceEntry entry : entries) {
                    schedule.add(new BlockSchedule(
                        entry.getPos(),
                        baseStart,
                        perBlockDuration,
                        entry.getNormalizedTime(),
                        entry.getGroupId()
                    ));
                }
                break;
                
            case IN_BATCHES:
                // 按批次执行
                int batchSize = params.getBatchSize();
                float batchInterval = params.getBatchInterval();
                int batchIndex = 0;
                
                for (int i = 0; i < entries.size(); i++) {
                    if (i > 0 && i % batchSize == 0) {
                        batchIndex++;
                    }
                    BlockSequenceEntry entry = entries.get(i);
                    float batchStart = baseStart + batchIndex * batchInterval;
                    schedule.add(new BlockSchedule(
                        entry.getPos(),
                        batchStart,
                        perBlockDuration,
                        entry.getNormalizedTime(),
                        entry.getGroupId()
                    ));
                }
                break;
                
            case ONE_BY_ONE:
            default:
                // 线性映射：基于归一化时间
                for (BlockSequenceEntry entry : entries) {
                    float startTime = baseStart + entry.getNormalizedTime() * availableTime;
                    schedule.add(new BlockSchedule(
                        entry.getPos(),
                        startTime,
                        perBlockDuration,
                        entry.getNormalizedTime(),
                        entry.getGroupId()
                    ));
                }
                break;
        }
        
        // 缓存结果（使用相对时间）
        List<BlockSchedule> cacheSchedule = new ArrayList<>();
        for (BlockSchedule bs : schedule) {
            cacheSchedule.add(new BlockSchedule(
                bs.getBlockPos(),
                bs.getStartTime() - clipStartTime, // 相对时间
                bs.getDuration(),
                bs.getNormalizedScore(),
                bs.getGroupIndex()
            ));
        }
        scheduleCache.put(cacheKey, cacheSchedule);
        
        return schedule;
    }
    
    /**
     * 计算配置哈希（用于缓存键）
     */
    private int computeConfigHash(SequenceParameters params) {
        return Objects.hash(
            params.getFlowType(),
            params.getFillType(),
            params.getOriginType(),
            params.getPlaneYaw(), params.getPlanePitch(),
            params.getCustomX(), params.getCustomY(), params.getCustomZ(),
            params.getPriorityWeight(),
            params.isReverse(), params.isAlternating(),
            params.getExtraParams(),
            params.getConcurrencyMode(), params.getBatchSize(), params.getBatchInterval(),
            params.getTimeMappingStrategy(), params.getPerBlockDuration(), params.getGlobalDelay()
        );
    }
    
    
    @Override
    public String getStrategyName() {
        return "EvaluatorBased";
    }
    
    @Override
    public String getDisplayName() {
        return "基于排序算子";
    }
    
    @Override
    public String getDescription() {
        return "使用新的排序系统，支持参考系、主次排序、复合策略等";
    }
}

