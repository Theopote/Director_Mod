package com.director.core.container;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.NbtReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BlockEntity NBT 读写（Minecraft 1.21.10 直接 API，无反射）。
 */
public final class BlockEntityNbtIO {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockEntityNbtIO.class);

    private BlockEntityNbtIO() {
    }

    @Nullable
    public static NbtCompound capture(World world, BlockEntity blockEntity) {
        if (world == null || blockEntity == null) {
            return null;
        }
        try {
            return blockEntity.createNbt(world.getRegistryManager());
        } catch (Exception e) {
            LOGGER.debug("[BlockEntityNbtIO] 捕获 BlockEntity NBT 失败: {}", e.getMessage());
            return null;
        }
    }

    public static boolean restore(BlockEntity blockEntity,
                                  NbtCompound nbt,
                                  RegistryWrapper.WrapperLookup registries) {
        if (blockEntity == null || nbt == null || registries == null) {
            return false;
        }
        try {
            blockEntity.read(NbtReadView.create(ErrorReporter.EMPTY, registries, nbt));
            blockEntity.markDirty();
            return true;
        } catch (Exception e) {
            LOGGER.debug("[BlockEntityNbtIO] 恢复 BlockEntity NBT 失败: {}", e.getMessage());
            return false;
        }
    }
}
