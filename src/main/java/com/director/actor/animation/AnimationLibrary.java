package com.director.actor.animation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AnimationLibrary v1.0
 *
 * <p>动画资源库，管理项目中所有可用的动画。
 *
 * <p>设计理念：
 * <ul>
 *   <li>单例模式，全局唯一</li>
 *   <li>支持动态注册/卸载动画</li>
 *   <li>提供分类查询（按来源、按标签）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class AnimationLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationLibrary.class);
    private static final AnimationLibrary INSTANCE = new AnimationLibrary();

    private final Map<String, AnimationData> animations = new LinkedHashMap<>();

    private AnimationLibrary() {
        BuiltInAnimations.registerInto(this);
    }

    public static AnimationLibrary getInstance() {
        return INSTANCE;
    }

    /**
     * 注册动画
     *
     * @param animationData 动画数据
     */
    public void register(AnimationData animationData) {
        if (animationData == null) {
            LOGGER.warn("[AnimationLibrary] 尝试注册 null 动画");
            return;
        }

        if (animationData.getId() == null || animationData.getId().isBlank()) {
            LOGGER.warn("[AnimationLibrary] 动画 ID 为空，跳过注册");
            return;
        }

        animations.put(animationData.getId(), animationData);
        LOGGER.info("[AnimationLibrary] 注册动画: {} ({})", animationData.getId(), animationData.getName());
    }

    /**
     * 获取动画
     *
     * @param animationId 动画ID
     * @return 动画数据，如果不存在则返回 empty
     */
    public Optional<AnimationData> get(String animationId) {
        return Optional.ofNullable(animations.get(animationId));
    }

    /**
     * 移除动画
     *
     * @param animationId 动画ID
     * @return 如果成功移除则返回 true
     */
    public boolean remove(String animationId) {
        boolean removed = animations.remove(animationId) != null;
        if (removed) {
            LOGGER.info("[AnimationLibrary] 移除动画: {}", animationId);
        }
        return removed;
    }

    /**
     * 清空所有动画
     */
    public void clear() {
        int count = animations.size();
        animations.clear();
        LOGGER.info("[AnimationLibrary] 清空所有动画 (共 {} 个)", count);
    }

    /**
     * 获取所有动画ID
     *
     * @return 动画ID列表
     */
    public List<String> getAllAnimationIds() {
        return new ArrayList<>(animations.keySet());
    }

    /**
     * 获取所有动画
     *
     * @return 动画列表
     */
    public List<AnimationData> getAllAnimations() {
        return new ArrayList<>(animations.values());
    }

    /**
     * 按来源过滤动画
     *
     * @param source 动画来源
     * @return 符合条件的动画列表
     */
    public List<AnimationData> getBySource(AnimationData.AnimationSource source) {
        List<AnimationData> result = new ArrayList<>();
        for (AnimationData data : animations.values()) {
            if (data.getSource() == source) {
                result.add(data);
            }
        }
        return result;
    }

    /**
     * 检查动画是否存在
     *
     * @param animationId 动画ID
     * @return 如果存在则返回 true
     */
    public boolean contains(String animationId) {
        return animations.containsKey(animationId);
    }

    /**
     * 获取动画数量
     *
     * @return 动画数量
     */
    public int size() {
        return animations.size();
    }

    /**
     * 检查是否为空
     *
     * @return 如果为空则返回 true
     */
    public boolean isEmpty() {
        return animations.isEmpty();
    }
}
