package com.director.actor.model;

import com.director.actor.rig.skeleton.RigWorldPose;
import com.director.mixin.accessor.BlockDisplayEntityAccessor;
import com.director.mixin.accessor.DisplayEntityAccessor;
import com.director.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
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

    private final Map<UUID, List<SpawnedCuboid>> spawned = new HashMap<>();
    private final Map<UUID, String> spawnedModelId = new HashMap<>();
    private final Map<UUID, String> spawnedModelRevision = new HashMap<>();

    private CustomModelPreviewRenderer() {}

    public static CustomModelPreviewRenderer getInstance() { return INSTANCE; }

    public void sync(UUID actorId, ClientWorld world, RigWorldPose worldPose, CustomModelDefinition model) {
        if (actorId == null || world == null || worldPose == null || model == null) {
            remove(actorId);
            return;
        }
        String revision = modelRevision(model);
        if (!model.id().equals(spawnedModelId.get(actorId))
                || !revision.equals(spawnedModelRevision.get(actorId))) {
            remove(actorId);
            spawned.put(actorId, spawnAll(world, model));
            spawnedModelId.put(actorId, model.id());
            spawnedModelRevision.put(actorId, revision);
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
        sc.entity().setPosition(translation.x, translation.y, translation.z);
        Vector3f rotatedOffset = rotation.transform(new Vector3f(sc.cuboid().offset()));
        AffineTransformation transform = new AffineTransformation(
                rotatedOffset, new Quaternionf(rotation), new Vector3f(sc.cuboid().size()), new Quaternionf());
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

    private static String modelRevision(CustomModelDefinition model) {
        StringBuilder revision = new StringBuilder();
        for (BoneCuboid cuboid : model.cuboids()) {
            revision.append(cuboid.boneId()).append('|')
                    .append(cuboid.itemModelId()).append('|')
                    .append(cuboid.textureId()).append(';');
        }
        return revision.toString();
    }

    public void remove(UUID actorId) {
        if (actorId == null) return;
        List<SpawnedCuboid> list = spawned.remove(actorId);
        spawnedModelId.remove(actorId);
        spawnedModelRevision.remove(actorId);
        if (list == null) return;
        for (SpawnedCuboid sc : list) {
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
