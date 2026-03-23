package com.director.actor;

import java.util.*;

/**
 * ActorRegistry v1.0
 *
 * <p>ActorRegistry = 项目级（Project Scope）管理器
 * <p>唯一职责：管理 Actor 生命周期（创建 / 删除 / 查询）
 *
 * <p>设计特点：
 * <ul>
 *   <li>项目级：一个 Project 一个 ActorRegistry</li>
 *   <li>与 TimelineModel 解耦：Timeline 不认识 ActorRegistry</li>
 *   <li>可序列化：支持保存/加载</li>
 *   <li>可热更新：支持运行时添加/删除 Actor</li>
 * </ul>
 *
 * <p>使用方式：
 * <ul>
 *   <li>Project 层持有 ActorRegistry</li>
 *   <li>Timeline 只持有 ActorRef</li>
 *   <li>Runtime 通过 ActorRegistry.resolve(ActorRef) 获取 Actor</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorRegistry {

    private final Map<UUID, Actor> actors = new LinkedHashMap<>();

    // ======================
    // 基础 CRUD
    // ======================

    /**
     * 创建新演员
     *
     * @param name 演员名称
     * @param type 演员类型
     * @return 创建的 Actor
     */
    public Actor createActor(String name, ActorType type) {
        Actor actor = new Actor(name, type);
        actors.put(actor.getActorId(), actor);
        return actor;
    }

    /**
     * 注册演员（已存在的 Actor）
     *
     * @param actor Actor
     */
    public void registerActor(Actor actor) {
        if (actor == null) return;
        actors.put(actor.getActorId(), actor);
    }

    /**
     * 移除演员
     *
     * @param actorId 演员ID
     * @return 如果成功移除则返回 true
     */
    public boolean removeActor(UUID actorId) {
        // 自动保存（可选）
        // if (removed) ActorRegistryManager.save();
        return actors.remove(actorId) != null;
    }

    /**
     * 获取演员
     *
     * @param actorId 演员ID
     * @return Actor，如果不存在则返回 empty
     */
    public Optional<Actor> getActor(UUID actorId) {
        return Optional.ofNullable(actors.get(actorId));
    }

    /**
     * 获取所有演员
     *
     * @return 所有演员的不可修改列表
     */
    public List<Actor> getAllActors() {
        return List.copyOf(actors.values());
    }

    /**
     * 检查是否包含指定演员
     *
     * @param actorId 演员ID
     * @return 如果包含则返回 true
     */
    public boolean contains(UUID actorId) {
        return actors.containsKey(actorId);
    }

    /**
     * 清空所有演员
     */
    public void clear() {
        actors.clear();
    }

    /**
     * 获取演员数量
     *
     * @return 演员数量
     */
    public int size() {
        return actors.size();
    }

    /**
     * 检查是否为空
     *
     * @return 如果为空则返回 true
     */
    public boolean isEmpty() {
        return actors.isEmpty();
    }

    // ======================
    // ActorRef 支持
    // ======================

    /**
     * 解析 ActorRef 为 Actor
     *
     * @param ref ActorRef
     * @return Actor，如果不存在则返回 empty
     */
    public Optional<Actor> resolve(ActorRef ref) {
        if (ref == null) return Optional.empty();
        return getActor(ref.getActorId());
    }

    /**
     * 检查 ActorRef 是否有效（指向的 Actor 存在）
     *
     * @param ref ActorRef
     * @return 如果有效则返回 true
     */
    public boolean isValid(ActorRef ref) {
        if (ref == null) return false;
        return contains(ref.getActorId());
    }

    /**
     * 检查是否存在指定名称的演员
     *
     * @param name 演员名称
     * @return 如果存在则返回 true
     */
    public boolean hasActorWithName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        return actors.values().stream()
                .anyMatch(actor -> trimmed.equals(actor.getName()));
    }
}

