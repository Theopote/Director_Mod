package com.director.mixin.accessor;

import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private block-state setter so custom model cuboids can pick their placeholder block. */
@Mixin(DisplayEntity.BlockDisplayEntity.class)
public interface BlockDisplayEntityAccessor {
    @Invoker("setBlockState")
    void director$invokeSetBlockState(BlockState state);
}
