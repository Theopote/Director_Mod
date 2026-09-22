package com.director.actor.model;

import com.director.actor.rig.skeleton.RigWorldPose;
import com.director.mixin.accessor.BlockDisplayEntityAccessor;
import com.director.mixin.accessor.DisplayEntityAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
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
 * Client-only renderer for {@link CustomModelDefinition}s: one block-display entity per
 * {@link BoneCuboid}, repositioned every frame to follow the owning actor's {@link RigWorldPose}.
 *
 * <p>This is the arbitrary-topology counterpart of {@code HumanoidArmorStandRigBinding}: it does not
 * require the rig to match the built-in humanoid bone layout.
 */
public final class CustomModelPreviewRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomModelPreviewRenderer.class);
    private static final CustomModelPreviewRenderer INSTANCE = new CustomModelPreviewRenderer();

    private record SpawnedCuboid(BoneCuboid cuboid, DisplayEntity.BlockDisplayEntity entity) {}

    private final Map<UUID, List<SpawnedCuboid>> spawned = new HashMap<>();
    private final Map<UUID, String> spawnedModelId = new HashMap<>();

    private CustomModelPreviewRenderer() {}

    public static CustomModelPreviewRenderer getInstance() { return INSTANCE; }

    /** Spawns (if needed) and repositions this actor's cuboids to match {@code worldPose} for the current frame. */
    public void sync(UUID actorId, ClientWorld world, RigWorldPose worldPose, CustomModelDefinition model) {
        if (actorId == null || world == null || worldPose == null || model == null) {
            remove(actorId);
            return;
        }
        if (!model.id().equals(spawnedModelId.get(actorId))) {
            remove(actorId);
            spawned.put(actorId, spawnAll(world, model));
            spawnedModelId.put(actorId, model.id());
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
        ((DisplayEntityAccessor) (Object) sc.entity()).director$invokeSetTransformation(transform);
    }

    private List<SpawnedCuboid> spawnAll(ClientWorld world, CustomModelDefinition model) {
        List<SpawnedCuboid> list = new ArrayList<>();
        for (BoneCuboid cuboid : model.cuboids()) {
            try {
                DisplayEntity.BlockDisplayEntity entity =
                        new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
                ((BlockDisplayEntityAccessor) (Object) entity).director$invokeSetBlockState(resolveBlockState(cuboid.blockStateId()));
                entity.setInvulnerable(true);
                entity.setNoGravity(true);
                entity.setSilent(true);
                world.addEntity(entity);
                list.add(new SpawnedCuboid(cuboid, entity));
            } catch (RuntimeException e) {
                LOGGER.warn("[CustomModelPreviewRenderer] 生成方块显示实体失败: {}", cuboid.boneId(), e);
            }
        }
        return list;
    }

    private static BlockState resolveBlockState(String blockStateId) {
        Identifier identifier = Identifier.tryParse(blockStateId);
        Block block = identifier == null ? Blocks.WHITE_CONCRETE : Registries.BLOCK.get(identifier);
        return block.getDefaultState();
    }

    public void remove(UUID actorId) {
        if (actorId == null) return;
        List<SpawnedCuboid> list = spawned.remove(actorId);
        spawnedModelId.remove(actorId);
        if (list == null) return;
        for (SpawnedCuboid sc : list) {
            try {
                sc.entity().discard();
            } catch (RuntimeException e) {
                LOGGER.warn("[CustomModelPreviewRenderer] 移除方块显示实体失败", e);
            }
        }
    }

    public void clear() {
        for (UUID actorId : new ArrayList<>(spawned.keySet())) remove(actorId);
    }
}
