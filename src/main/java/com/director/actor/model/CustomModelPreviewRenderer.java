package com.director.actor.model;

import com.director.actor.Actor;
import com.director.actor.preview.ActorPosePreview;
import com.director.actor.rig.skeleton.RigDefinitions;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.rig.skeleton.RigPoseComposer;
import com.director.actor.rig.skeleton.RigWorldPose;
import com.director.mixin.accessor.BlockDisplayEntityAccessor;
import com.director.mixin.accessor.DisplayEntityAccessor;
import com.director.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-only renderer for {@link CustomModelDefinition}s: one display entity per
 * {@link BoneCuboid}, repositioned every frame to follow the owning actor's {@link RigWorldPose}.
 */
public final class CustomModelPreviewRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomModelPreviewRenderer.class);
    private static final CustomModelPreviewRenderer INSTANCE = new CustomModelPreviewRenderer();

    private record SpawnedCuboid(BoneCuboid cuboid, DisplayEntity entity) {}
    public record SelectionTarget(UUID actorId,String boneId) {}
    private final Map<net.minecraft.entity.Entity,SelectionTarget> selectionTargets=new java.util.WeakHashMap<>();
    public SelectionTarget selectionTarget(net.minecraft.entity.Entity entity) { return selectionTargets.get(entity); }

    private final Map<UUID, List<SpawnedCuboid>> spawned = new HashMap<>();
    private final Map<UUID, CustomModelDefinition> spawnedModels = new HashMap<>();

    private CustomModelPreviewRenderer() {}

    public static CustomModelPreviewRenderer getInstance() { return INSTANCE; }

    /** Uses the host's final pose, including gaze, to place its custom display model. */
    public void syncActor(Actor actor, Entity host, boolean visible) {
        UUID actorId = actor.getActorId();
        String modelId = actor.getCustomModelId();
        if (!visible || modelId == null || modelId.isBlank()) {
            remove(actorId);
            return;
        }
        var model = CustomModelLibrary.getInstance().find(modelId).orElse(null);
        var rig = model == null ? null : RigDefinitions.find(model.rigId()).orElse(null);
        if (rig == null) {
            remove(actorId);
            return;
        }
        RigPose pose = null;
        Vector3f scale = null;
        if (host instanceof ActorPosePreview preview) {
            var current = preview.getFinalRigPose();
            if (current != null && RigPoseComposer.sameLayout(current.rig(), rig)) pose = current;
            scale = preview.getActorTransformScale();
            host.setInvisible(true);
        }
        if (!(host.getEntityWorld() instanceof ClientWorld world)) return;
        if (pose == null) pose = new RigPose(rig);
        if (scale == null || !scale.isFinite()) scale = new Vector3f(1);
        var actorTransform = new Matrix4f()
                .translate((float) host.getX(), (float) host.getY(), (float) host.getZ())
                .rotateY((float) Math.toRadians(-host.getYaw()))
                .scale(scale);
        sync(actorId, world, RigWorldPose.resolve(pose, actorTransform), model);
    }

    public void sync(UUID actorId, ClientWorld world, RigWorldPose worldPose, CustomModelDefinition model) {
        if (actorId == null || world == null || worldPose == null || model == null) {
            remove(actorId);
            return;
        }
        var existing = spawned.get(actorId);
        boolean staleWorld = existing != null && existing.stream().anyMatch(sc ->
                sc.entity().isRemoved() || sc.entity().getEntityWorld() != world);
        if (staleWorld || spawnedModels.get(actorId) != model) {
            remove(actorId);
            var parts=spawnAll(world,model);
            spawned.put(actorId,parts);
            for(var part:parts) selectionTargets.put(part.entity(),new SelectionTarget(actorId,part.cuboid().boneId()));
            spawnedModels.put(actorId, model);
        }
        for (SpawnedCuboid sc : spawned.getOrDefault(actorId, List.of())) {
            int boneIndex = worldPose.rig().findBoneIndex(sc.cuboid().boneId());
            if (boneIndex < 0) continue;
            applyTransform(sc, worldPose.transform(boneIndex));
        }
    }

    private void applyTransform(SpawnedCuboid sc, Matrix4f boneWorldMatrix) {
        Vector3f translation = boneWorldMatrix.getTranslation(new Vector3f());
        Quaternionf rotation = boneWorldMatrix.getNormalizedRotation(new Quaternionf());
        Vector3f boneScale = boneWorldMatrix.getScale(new Vector3f());
        sc.entity().setPosition(translation.x, translation.y, translation.z);
        AffineTransformation transform;
        if (sc.entity() instanceof DisplayEntity.ItemDisplayEntity) {
            // Geometry (offset + size) is already baked into the item model JSON.
            // Apply bone rotation/scale only; FIXED display scale is forced to 1 in the model.
            transform = new AffineTransformation(
                    new Vector3f(), new Quaternionf(rotation), new Vector3f(boneScale), new Quaternionf());
        } else {
            Vector3f localOffset = new Vector3f(sc.cuboid().offset()).mul(boneScale);
            Vector3f rotatedOffset = rotation.transform(localOffset);
            Vector3f displayScale = new Vector3f(sc.cuboid().size()).mul(boneScale);
            transform = new AffineTransformation(
                    rotatedOffset, new Quaternionf(rotation), displayScale, new Quaternionf());
        }
        ((DisplayEntityAccessor) sc.entity()).director$invokeSetTransformation(transform);
    }

    private List<SpawnedCuboid> spawnAll(ClientWorld world, CustomModelDefinition model) {
        List<SpawnedCuboid> list = new ArrayList<>();
        for (BoneCuboid cuboid : model.cuboids()) {
            try {
                DisplayEntity entity = cuboid.usesCustomTexture()
                        ? spawnTexturedCuboid(world, cuboid)
                        : spawnPlaceholderCuboid(world, cuboid);
                entity.setInvulnerable(true);
                entity.setNoGravity(true);
                entity.setSilent(true);
                world.addEntity(entity);
                list.add(new SpawnedCuboid(cuboid, entity));
            } catch (RuntimeException e) {
                LOGGER.warn("[CustomModelPreviewRenderer] 生成显示实体失败: {}", cuboid.boneId(), e);
            }
        }
        return list;
    }

    private static DisplayEntity spawnPlaceholderCuboid(ClientWorld world, BoneCuboid cuboid) {
        DisplayEntity.BlockDisplayEntity entity = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        ((BlockDisplayEntityAccessor) entity).director$invokeSetBlockState(resolveBlockState(cuboid.blockStateId()));
        return entity;
    }

    private static DisplayEntity spawnTexturedCuboid(ClientWorld world, BoneCuboid cuboid) {
        if (ModItems.CUSTOM_MODEL_CARRIER == null) {
            throw new IllegalStateException("Custom model carrier item is not registered");
        }
        Identifier modelId = Identifier.tryParse(cuboid.itemModelId());
        if (modelId == null) {
            throw new IllegalArgumentException("Invalid item model id: " + cuboid.itemModelId());
        }
        DisplayEntity.ItemDisplayEntity entity = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        ItemStack stack = new ItemStack(ModItems.CUSTOM_MODEL_CARRIER);
        stack.set(DataComponentTypes.ITEM_MODEL, modelId);
        entity.setItemStack(stack);
        entity.setItemDisplayContext(ItemDisplayContext.FIXED);
        return entity;
    }

    private static BlockState resolveBlockState(String blockStateId) {
        Identifier identifier = Identifier.tryParse(blockStateId);
        Block block = identifier == null ? Blocks.WHITE_CONCRETE : Registries.BLOCK.get(identifier);
        return block.getDefaultState();
    }

    public void remove(UUID actorId) {
        if (actorId == null) return;
        List<SpawnedCuboid> list = spawned.remove(actorId);
        spawnedModels.remove(actorId);
        if (list == null) return;
        for (SpawnedCuboid sc : list) {
            selectionTargets.remove(sc.entity());
            try {
                sc.entity().discard();
            } catch (RuntimeException e) {
                LOGGER.warn("[CustomModelPreviewRenderer] 移除显示实体失败", e);
            }
        }
    }

    public void clear() {
        for (UUID actorId : new ArrayList<>(spawned.keySet())) remove(actorId);
    }
}
