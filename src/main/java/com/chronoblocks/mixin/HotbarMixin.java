package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 🧭 HotbarMixin - 隐藏物品栏与HUD元素
 * ==============================================
 * 在ChronoBlocks模式下隐藏：
 * - 底部物品栏
 * - 血量、护甲、经验条等HUD
 * - 聊天消息
 * - 其他游戏UI元素
 * 
 * 基于Minecraft 1.21.4源码中的InGameHud类方法签名
 */
@Mixin(InGameHud.class)
public class HotbarMixin {

    /**
     * 🚫 拦截底部物品栏渲染
     * 在ChronoBlocks模式下完全隐藏热键栏
     * 基于Minecraft 1.21.4源码：renderHotbar(DrawContext context, RenderTickCounter tickCounter)
     */
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染物品栏
        }
    }

    /**
     * 🚫 拦截状态栏渲染（血量、饥饿、经验条等）
     * 在ChronoBlocks模式下隐藏所有状态信息
     * 基于Minecraft 1.21.4源码：renderStatusBars(DrawContext context)
     */
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusBars(DrawContext context, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染状态栏
        }
    }

    /**
     * 🚫 拦截经验条渲染
     * 在ChronoBlocks模式下隐藏经验条
     * 基于Minecraft 1.21.4源码：renderExperienceBar(DrawContext context, int x)
     */
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void onRenderExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染经验条
        }
    }

    /**
     * 🚫 拦截经验等级渲染
     * 在ChronoBlocks模式下隐藏经验等级显示
     * 基于Minecraft 1.21.4源码：renderExperienceLevel(DrawContext context, RenderTickCounter tickCounter)
     */
    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void onRenderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染经验等级
        }
    }

    /**
     * 🚫 拦截聊天消息渲染
     * 在ChronoBlocks模式下可选择隐藏聊天消息
     * 基于Minecraft 1.21.4源码：renderChat(DrawContext context, RenderTickCounter tickCounter)
     */
    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    private void onRenderChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染聊天
        }
    }

    // 注意：在Minecraft 1.21.4中，调试信息渲染已经移到DebugHud类中
    // 不再在InGameHud类中有renderDebugOverlay方法
    // 如果需要隐藏调试信息，需要创建DebugHudMixin

    /**
     * 🚫 拦截状态效果渲染
     * 在ChronoBlocks模式下隐藏状态效果图标
     * 基于Minecraft 1.21.4源码：renderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter)
     */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染状态效果
        }
    }

    /**
     * 🚫 拦截主HUD渲染
     * 在ChronoBlocks模式下隐藏主HUD（包括物品栏、状态栏等）
     * 基于Minecraft 1.21.4源码：renderMainHud(DrawContext context, RenderTickCounter tickCounter)
     */
    @Inject(method = "renderMainHud", at = @At("HEAD"), cancellable = true)
    private void onRenderMainHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染主HUD
        }
    }
}