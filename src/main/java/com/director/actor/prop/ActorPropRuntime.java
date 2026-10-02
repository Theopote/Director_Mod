package com.director.actor.prop;

import net.minecraft.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ActorPropRuntime v1.0
 *
 * <p>演员道具运行时：管理 Actor 当前携带的所有道具
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 每个 Actor 一个独立的 PropRuntime</li>
 *   <li>✅ 管理道具的生命周期（attach / detach）</li>
 *   <li>✅ 每帧更新道具的 Transform（跟随演员）</li>
 *   <li>❌ 不关心世界实体</li>
 *   <li>❌ 不生成真实 MC Entity</li>
 * </ul>
 *
 * <p>职责边界：
 * <ul>
 *   <li>✅ 管理 RuntimeProp 的创建/销毁</li>
 *   <li>✅ 更新道具的 Transform（跟随演员）</li>
 *   <li>❌ 不关心 Timeline / Clip</li>
 *   <li>❌ 不关心渲染细节（由 RuntimeProp 负责）</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorPropRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPropRuntime.class);

    /** 每个槽位的道具映射 */
    private final Map<ActorPropSlot, RuntimeProp> props = new EnumMap<>(ActorPropSlot.class);

    /** 关联的 PreviewActorEntity（用于更新 Transform） */
    private Entity actorEntity;
    private Map<ActorPropSlot,ActorPropTimelineSampler.Attachment> sampledHeld = Map.of();
    private final Map<String,RuntimeProp> sampledFlights = new HashMap<>();
    private final Map<String,ActorPropTimelineSampler.Attachment> flightAttachments = new HashMap<>();

    /** Reconcile sampled state without recreating unchanged held props on each frame. */
    public void sync(ActorPropTimelineSampler.Snapshot snapshot) {
        for (var slot : new ArrayList<>(props.keySet())) if (!snapshot.held().containsKey(slot)) detach(slot);
        for (var entry : snapshot.held().entrySet()) {
            if (!entry.getValue().equals(sampledHeld.get(entry.getKey())) || !props.containsKey(entry.getKey()))
                attach(entry.getKey(),entry.getValue().create(entry.getKey()));
        }
        sampledHeld = snapshot.held();
        var active = new HashSet<String>();
        for (var flight : snapshot.flights()) {
            active.add(flight.id());
            var prop = sampledFlights.get(flight.id());
            if (prop == null || !flight.attachment().equals(flightAttachments.get(flight.id()))) {
                if (prop != null) prop.dispose();
                prop = flight.attachment().create(flight.slot()); sampledFlights.put(flight.id(),prop);
                flightAttachments.put(flight.id(),flight.attachment());
            }
            prop.setSlot(flight.slot());
            prop.setWorldMatrix(flight.transform());
        }
        for (var id : new ArrayList<>(sampledFlights.keySet())) if (!active.contains(id)) {
            sampledFlights.remove(id).dispose(); flightAttachments.remove(id);
        }
    }
    public Collection<RuntimeProp> getTimelineProjectiles() {
        return Collections.unmodifiableCollection(sampledFlights.values());
    }


    /**
     * 构造函数
     */
    public ActorPropRuntime() {
    }

    // =========================================================
    // Prop 管理
    // =========================================================

    /**
     * 装备道具
     *
     * @param slot 槽位
     * @param prop 道具
     */
    public void attach(ActorPropSlot slot, RuntimeProp prop) {
        if (slot == null || prop == null) {
            return;
        }

        // 如果该槽位已有道具，先卸下
        detach(slot);

        props.put(slot, prop);
        prop.setSlot(slot);

        LOGGER.debug("[ActorPropRuntime] 装备道具: {} -> {}", slot, prop.getDescriptor().getId());
    }

    /**
     * 卸下道具
     *
     * @param slot 槽位
     */
    public void detach(ActorPropSlot slot) {
        if (slot == null) {
            return;
        }

        RuntimeProp prop = props.remove(slot);
        if (prop != null) {
            prop.dispose();
            LOGGER.debug("[ActorPropRuntime] 卸下道具: {} -> {}", slot, prop.getDescriptor().getId());
        }
    }

    /**
     * 卸下所有道具
     */
    public void detachAll() {
        for (ActorPropSlot slot : new ArrayList<>(props.keySet())) {
            detach(slot);
        }
    }

    /**
     * 获取指定槽位的道具
     *
     * @param slot 槽位
     * @return 道具，如果不存在则返回 null
     */
    public RuntimeProp getProp(ActorPropSlot slot) {
        return props.get(slot);
    }

    /** Transfer ownership to a projectile without disposing its visual resource. */
    public RuntimeProp release(ActorPropSlot slot) {
        return slot == null ? null : props.remove(slot);
    }

    /**
     * 获取所有道具
     *
     * @return 所有道具的集合（只读）
     */
    public Collection<RuntimeProp> getAllProps() {
        return Collections.unmodifiableCollection(props.values());
    }

    /**
     * 检查指定槽位是否有道具
     *
     * @param slot 槽位
     * @return 如果有道具则返回 true
     */
    public boolean hasProp(ActorPropSlot slot) {
        return slot != null && props.containsKey(slot);
    }

    // =========================================================
    // Update（每帧调用）
    // =========================================================

    /**
     * 设置关联的 PreviewActorEntity
     * <p>用于更新道具的 Transform（跟随演员）
     *
     * @param entity PreviewActorEntity
     */
    public void setActorEntity(Entity entity) {
        this.actorEntity = entity;
    }

    /**
     * 更新所有道具的 Transform（每帧调用）
     * <p>让道具跟随演员的位置、旋转
     */
    private boolean heldVisible = true;
    public void setHeldVisible(boolean visible) { heldVisible = visible; }

    public void update() { update(reason->{}); }
    public void update(java.util.function.Consumer<com.director.actor.runtime.ActorDiagnosticReason> diagnostic) {
        if (actorEntity == null) {
            return;
        }

        for (RuntimeProp prop : props.values()) {
            prop.updateTransform(actorEntity);
            if (actorEntity.getEntityWorld() instanceof net.minecraft.client.world.ClientWorld world)
                prop.syncVisual(world,heldVisible);
            var failure=prop.previewFailure(heldVisible); if(failure!=null) diagnostic.accept(failure);
        }
        if (actorEntity.getEntityWorld() instanceof net.minecraft.client.world.ClientWorld world) {
            for (var prop : sampledFlights.values()) {
                prop.syncVisual(world,true);
                var failure=prop.previewFailure(true); if(failure!=null) diagnostic.accept(failure);
            }
        }
    }

    /**
     * 清理所有资源
     */
    public void dispose() {
        detachAll();
        sampledHeld = Map.of();
        sampledFlights.values().forEach(RuntimeProp::dispose); sampledFlights.clear(); flightAttachments.clear();
        actorEntity = null;
    }
}

