package com.chronoblocks.core.animation;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 动画应用器（v4.5 一体化交互系统）
 * 
 * 核心职责：
 * - 采样关键帧数据（从 Track）
 * - 应用变换到容器（通过 ContainerManager）
 * - 管理容器变换状态
 * - 提供调试信息
 * 
 * 工作流程：
 * 1. PlaybackController.update() 调用 applyAnimation(track, sample)
 * 2. 根据 track.targetId 查找对应的 Container
 * 3. 从 Keyframe 创建 ContainerTransform
 * 4. 应用变换到 Container（将来会影响渲染）
 * 
 * @author ChronoBlocks Team
 * @version 4.5
 */
public class AnimationApplicator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationApplicator.class);
    private static final AnimationApplicator INSTANCE = new AnimationApplicator();
    
    // ========== 容器管理器引用 ==========
    private ContainerManager containerManager;
    
    // ========== 当前变换缓存 ==========
    // 存储每个容器当前应用的变换状态
    private final Map<UUID, ContainerTransform> containerTransforms = new HashMap<>();
    
    // ========== 统计信息 ==========
    private long totalAppliedFrames = 0;
    private long failedApplications = 0;
    
    private AnimationApplicator() {
        // 私有构造函数，单例模式
    }
    
    public static AnimationApplicator get() {
        return INSTANCE;
    }
    
    // ========== 初始化 ==========
    
    /**
     * 设置容器管理器引用
     * 必须在使用前调用
     */
    public void setContainerManager(ContainerManager containerManager) {
        this.containerManager = containerManager;
        LOGGER.info("[AnimationApplicator] Container manager registered");
    }
    
    /**
     * 获取容器管理器
     */
    public ContainerManager getContainerManager() {
        return containerManager;
    }
    
    // ========== 核心应用方法 ==========
    
    /**
     * 将采样的关键帧应用到对应的容器
     * 
     * @param track 轨道（包含目标容器 ID）
     * @param sample 采样的关键帧数据
     */
    public void applyAnimation(Track track, Keyframe sample) {
        if (track == null || sample == null) {
            return;
        }
        
        // 检查容器管理器是否已初始化
        if (containerManager == null) {
            if (failedApplications % 100 == 0) { // 避免日志洪水
                LOGGER.warn("[AnimationApplicator] Container manager not initialized, skipping animation application");
            }
            failedApplications++;
            return;
        }
        
        // 获取目标容器
        String targetId = track.getTargetId();
        if (targetId == null || targetId.isEmpty()) {
            LOGGER.trace("[AnimationApplicator] Track '{}' has no target ID", track.getName());
            return;
        }
        
        Container container = findContainer(targetId);
        if (container == null) {
            if (totalAppliedFrames % 60 == 0) { // 每秒最多记录一次
                LOGGER.debug("[AnimationApplicator] Container not found for ID: {}", targetId);
            }
            failedApplications++;
            return;
        }
        
        // 从关键帧创建变换
        ContainerTransform transform = createTransformFromKeyframe(sample);
        
        // 应用变换到容器
        applyTransformToContainer(container, transform);
        
        // 更新统计
        totalAppliedFrames++;
        
        // 定期日志
        if (LOGGER.isTraceEnabled() && totalAppliedFrames % 300 == 0) {
            LOGGER.trace("[AnimationApplicator] Applied {} frames ({}failures)", 
                totalAppliedFrames, failedApplications);
        }
    }
    
    // ========== 容器查找 ==========
    
    /**
     * 根据 ID 查找容器
     * 支持多种 ID 格式：
     * - UUID 字符串
     * - 容器名称
     */
    private Container findContainer(String targetId) {
        try {
            // 尝试作为 UUID 解析
            UUID uuid = UUID.fromString(targetId);
            Container container = containerManager.getContainer(uuid);
            if (container != null) {
                return container;
            }
        } catch (IllegalArgumentException e) {
            // 不是 UUID，尝试作为名称查找
        }
        
        // 作为名称查找
        return containerManager.getContainer(targetId);
    }
    
    // ========== 变换转换 ==========
    
    /**
     * 从关键帧创建容器变换
     */
    private ContainerTransform createTransformFromKeyframe(Keyframe keyframe) {
        ContainerTransform transform = new ContainerTransform();
        
        // 复制位置
        float[] pos = keyframe.getPosition();
        if (pos != null && pos.length >= 3) {
            transform.setPosition(pos[0], pos[1], pos[2]);
        }
        
        // 复制旋转
        float[] rot = keyframe.getRotation();
        if (rot != null && rot.length >= 3) {
            transform.setRotation(rot[0], rot[1], rot[2]);
        }
        
        // 复制缩放
        float[] scale = keyframe.getScale();
        if (scale != null && scale.length >= 3) {
            transform.setScale(scale[0], scale[1], scale[2]);
        }
        
        // 复制不透明度
        transform.setOpacity(keyframe.getOpacity());
        
        return transform;
    }
    
    // ========== 变换应用 ==========
    
    /**
     * 将变换应用到容器
     * 当前实现：只存储变换数据，实际渲染应用将在后续实现
     */
    private void applyTransformToContainer(Container container, ContainerTransform transform) {
        // 存储当前变换
        containerTransforms.put(container.getId(), new ContainerTransform(transform));
        
        // 【将来】这里会调用实际的方块渲染变换
        // 例如：
        // - 更新容器的 center 位置
        // - 应用旋转矩阵到所有方块
        // - 应用缩放到方块大小
        // - 设置方块的透明度/可见性
        
        LOGGER.trace("[AnimationApplicator] Applied transform to container '{}': {}", 
            container.getName(), transform);
    }
    
    // ========== 查询方法 ==========
    
    /**
     * 获取容器当前的变换状态
     */
    public ContainerTransform getContainerTransform(UUID containerId) {
        ContainerTransform transform = containerTransforms.get(containerId);
        return transform != null ? new ContainerTransform(transform) : new ContainerTransform();
    }
    
    /**
     * 获取容器当前的变换状态
     */
    public ContainerTransform getContainerTransform(Container container) {
        if (container == null) return new ContainerTransform();
        return getContainerTransform(container.getId());
    }
    
    /**
     * 检查容器是否有动画变换
     */
    public boolean hasTransform(UUID containerId) {
        return containerTransforms.containsKey(containerId);
    }
    
    // ========== 重置方法 ==========
    
    /**
     * 重置特定容器的变换
     */
    public void resetContainerTransform(UUID containerId) {
        containerTransforms.remove(containerId);
        LOGGER.debug("[AnimationApplicator] Reset transform for container: {}", containerId);
    }
    
    /**
     * 重置所有容器的变换
     */
    public void resetAllTransforms() {
        int count = containerTransforms.size();
        containerTransforms.clear();
        LOGGER.info("[AnimationApplicator] Reset all transforms ({} containers)", count);
    }
    
    // ========== 调试信息 ==========
    
    /**
     * 获取统计信息
     */
    public String getStatistics() {
        return String.format("AnimationApplicator Stats: Applied=%d, Failed=%d, Active=%d", 
            totalAppliedFrames, failedApplications, containerTransforms.size());
    }
    
    /**
     * 获取详细调试信息
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AnimationApplicator Debug Info ===\n");
        sb.append(String.format("Total Applied Frames: %d\n", totalAppliedFrames));
        sb.append(String.format("Failed Applications: %d\n", failedApplications));
        sb.append(String.format("Active Transforms: %d\n", containerTransforms.size()));
        sb.append(String.format("Container Manager: %s\n", 
            containerManager != null ? "Initialized" : "NOT INITIALIZED"));
        
        if (!containerTransforms.isEmpty()) {
            sb.append("\nActive Container Transforms:\n");
            containerTransforms.forEach((id, transform) -> {
                Container container = containerManager != null ? 
                    containerManager.getContainer(id) : null;
                String name = container != null ? container.getName() : "Unknown";
                sb.append(String.format("  - %s (%s): %s\n", name, id, transform));
            });
        }
        
        return sb.toString();
    }
    
    /**
     * 重置统计信息
     */
    public void resetStatistics() {
        totalAppliedFrames = 0;
        failedApplications = 0;
        LOGGER.info("[AnimationApplicator] Statistics reset");
    }
}

