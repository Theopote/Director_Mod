package com.director.mixin.client;

import com.director.actor.model.texture.DirectorRuntimeResourcePack;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.VanillaResourcePackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(VanillaResourcePackProvider.class)
public abstract class DirectorRuntimeResourcePackMixin {
    @Inject(method = "register(Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void registerDirectorRuntimePack(Consumer<ResourcePackProfile> consumer, CallbackInfo ci) {
        if ((Object) this instanceof DefaultClientResourcePackProvider) {
            DirectorRuntimeResourcePack.registerProfile(consumer);
        }
    }
}
