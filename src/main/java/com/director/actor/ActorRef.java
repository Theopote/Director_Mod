package com.director.actor;

import java.util.Objects;
import java.util.UUID;

/**
 * ActorRef v1.0
 *
 * <p>ActorRef = 对 Actor 的"弱引用"
 * <p>Timeline / Track 里只允许出现这个
 *
 * <p>为什么一定要有 ActorRef？
 * <ul>
 *   <li>防止 Timeline 意外"持有" Actor 实例</li>
 *   <li>保证 Timeline 可被复制</li>
 *   <li>保证 Actor 可被替换</li>
 *   <li>保证 ActorRegistry 可热更新</li>
 * </ul>
 *
 * <p>这是专业编辑器必备的抽象。
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorRef {

    private final UUID actorId;

    /**
     * 构造函数
     *
     * @param actorId 演员ID
     */
    public ActorRef(UUID actorId) {
        this.actorId = Objects.requireNonNull(actorId, "actorId");
    }

    /**
     * 从 Actor 创建 ActorRef
     *
     * @param actor Actor
     * @return ActorRef
     */
    public static ActorRef of(Actor actor) {
        if (actor == null) {
            throw new IllegalArgumentException("actor cannot be null");
        }
        return new ActorRef(actor.getActorId());
    }

    public UUID getActorId() {
        return actorId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorRef that)) return false;
        return actorId.equals(that.actorId);
    }

    @Override
    public int hashCode() {
        return actorId.hashCode();
    }

    @Override
    public String toString() {
        return "ActorRef[" + actorId + "]";
    }
}

