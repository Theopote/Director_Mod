package com.director.mixin.client;

import com.director.actor.model.texture.DirectorRuntimeResourcePack;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(DefaultClientResourcePackProvider.class)
public abstract class DirectorRuntimeResourcePackMixin {
    @Inject(method = "register", at = @At("RETURN"))
    private void registerDirectorRuntimePack(Consumer<ResourcePackProfile> consumer, CallbackInfo ci) {
        DirectorRuntimeResourcePack.registerProfile(consumer);
    }
}
