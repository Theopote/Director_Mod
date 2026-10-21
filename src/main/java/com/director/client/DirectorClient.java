package com.director.client;

import com.director.core.DirectorServices;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import com.director.registry.ModItems;
import com.director.client.debug.DirectorDebugger;
import com.director.client.renderer.pipeline.DirectorRendererRegistry;
// ImGui 相关导入已移除，现在由 ImGuiRenderer 统一管理
import com.director.client.input.DirectorKeyCategories;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Director客户端管理器
 * 参考TreeFactory项目的优秀实现方式
 * <p>
 * 优化版本特性：
 * - 线程安全的初始化状态管理
 * - 重试机制和错误恢复
 * - 资源泄漏防护
 * - 延迟窗口句柄获取
 * - 高性能事件注册和键绑定
 * - 精确的物品使用检测
 */
public class DirectorClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorClient.class);
    
    // ImGui 组件现在由 ImGuiRenderer 统一管理
    // 移除了重复的 ImGui 状态管理，避免冲突
    
    // 关闭 Director 界面后，唯一注册到 Minecraft 控制设置的快捷键（打开 Director UI）
    private static KeyBinding directorKeyBinding;
    
    // 性能优化：缓存玩家手持的工具状态
    private static volatile boolean playerHoldingTool = false;
    private static volatile long lastToolCheckTime = 0;
    private static final long TOOL_CHECK_INTERVAL = 100; // 100ms检查间隔
    
    // 防抖机制：避免频繁调用
    private static volatile long lastItemUseTime = 0;
    private static final long ITEM_USE_DEBOUNCE = 500; // 500ms防抖间隔
    
    // 工具检测：支持多个工具类型
    private static volatile Item[] directorTools = null;
    private static volatile long lastToolsCacheTime = 0;
    private static final long TOOLS_CACHE_DURATION = 5000; // 5秒缓存工具列表
    
    // windowHandleSupplier 已移除，现在由 ImGuiRenderer 统一管理

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Director] DirectorClient.onInitializeClient() called");
        
        // 注册自定义键绑定
        registerKeyBindings();
        initializeRendererRegistry();
        
        // 初始化容器实时接收订阅器 (v5.0)
        initializeContainerIntakeSystem();
        
        // 初始化客户端服务（ImGui）与快捷键绑定
        try {
            com.director.core.DirectorServices.initializeClient();
            com.director.ui.input.ShortcutInitializer.initialize();
            LOGGER.info("[Director] 客户端服务与 ShortcutDispatcher 快捷键已初始化");
        } catch (Exception e) {
            LOGGER.error("[Director] 初始化客户端服务失败", e);
        }
        
        // 注册客户端启动完成事件
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            DirectorMemoryRequirements.onClientStarted(client);
            LOGGER.info("[Director] Minecraft client started, proceeding with ImGui initialization.");
            try {
                LOGGER.info("[Director] Calling DirectorServices.get().imgui().init()...");
                DirectorServices.get().imgui().init();
                if (DirectorServices.get().imgui().isInitialized()) {
                    LOGGER.info("[Director] ImGui renderer initialized successfully.");
                } else {
                    LOGGER.error("[Director] ImGui renderer failed to initialize after client start.");
                }
            } catch (Exception e) {
                LOGGER.error("[Director] A critical error occurred during ImGui initialization.", e);
            }
            
            // 在客户端完全启动后注册其他事件
            registerEventHandlers();
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.director.actor.runtime.ActorRuntimeController.getInstance().clearWorldPreviews();
            client.execute(com.director.season.SeasonWorldEffects::clear);
        });

        // 注册客户端停止事件
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            // ImGui 清理现在由 ImGuiRenderer 统一处理
            com.director.actor.runtime.ActorRuntimeController.getInstance().clearWorldPreviews();
            // 这里只处理客户端特定的清理
        });
    }
    
    /**
     * 注册打开 Director 的 Minecraft 按键（仅在未打开 Director 界面时生效）。
     * Director 内的编辑快捷键由 {@link com.director.ui.input.ShortcutDispatcher} 经 GLFW 事件分发，
     */
    private void registerKeyBindings() {
        directorKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.director.open_ui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            DirectorKeyCategories.GENERAL
        ));
        LOGGER.info("[Director] 已注册打开 Director 快捷键（默认 C）");
    }
    
    /**
     * 注册事件处理器（在客户端完全启动后调用）
     */
    private void registerEventHandlers() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            DirectorMemoryRequirements.onPlayerJoin(client);
            com.director.actor.model.texture.CustomModelResourceWarmup.scheduleForCurrentWorld();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client ->
                com.director.lighting.PointLightRuntime.update(com.director.timeline.playback.TimelinePlaybackSampler.resolveModel()));

        // 注册键绑定事件处理器
        registerKeyBindingHandler();
        
        // 注册季节积雪管理器 tick 事件
        registerSeasonSnowManager();
        
        // 注册优化的物品使用检测
        registerOptimizedItemUseHandler();
        
        LOGGER.info("[Director] Event handlers registered successfully");
    }

    private void initializeRendererRegistry() {
        try {
            DirectorRendererRegistry registry = DirectorRendererRegistry.get();
            if (!registry.isInitialized()) {
                registry.initialize();
                LOGGER.info("[Director] Renderer registry initialized at client startup");
            } else {
                LOGGER.debug("[Director] Renderer registry already initialized, skipping client startup init.");
            }
        } catch (Exception e) {
            LOGGER.error("[Director] 初始化渲染注册表失败", e);
        }
    }
    
    /**
     * 初始化容器实时接收系统 (v5.0)
     */
    private void initializeContainerIntakeSystem() {
        try {
            com.director.core.container.ContainerIntakeSubscriber.initialize();
            LOGGER.info("[Director] 容器实时接收系统已初始化");
        } catch (Exception e) {
            LOGGER.error("[Director] 初始化容器实时接收系统失败", e);
        }
    }
    
    /**
     * 注册键绑定事件处理器（仅在未打开任何界面时响应「打开 Director」）
     */
    private void registerKeyBindingHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.director.ui.DirectorScreen.tickPendingRestore(client);
            com.director.actor.runtime.ActorRuntimeController.getInstance().tickWorld();
            com.director.core.animation.WholeContainerImpactRingPlayer.tick(client);

            if (client.player != null && client.currentScreen == null) {
                if (directorKeyBinding.wasPressed()) {
                    openDirectorScreen();
                    LOGGER.debug("[Director] Director UI opened via key binding");
                }
            }
        });
    }
    
    /**
     * 注册季节积雪管理器 tick 事件
     */
    private void registerSeasonSnowManager() {
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                com.director.timeline.playback.season.TimelineSeasonController.getInstance().flushRerender(false));
    }

    /**
     * 注册优化的物品使用事件处理器
     */
    private void registerOptimizedItemUseHandler() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.currentScreen == null) {
                // 使用缓存和间隔检查减少性能开销
                if (shouldCheckToolStatus()) {
                    updateToolStatus(client.player);
                }
                
                // 只有在持有工具时才检查右键点击
                if (playerHoldingTool) {
                handleItemUse(client.player);
                }
            }
        });
    }
    
    /**
     * 检查是否应该检查工具状态（基于时间间隔）
     */
    private boolean shouldCheckToolStatus() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastToolCheckTime >= TOOL_CHECK_INTERVAL) {
            lastToolCheckTime = currentTime;
            return true;
        }
        return false;
    }
    
    /**
     * 更新工具状态缓存（使用Registry检查，支持多工具）
     */
    private void updateToolStatus(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        
        // 使用Registry检查，支持多个工具类型

        playerHoldingTool = isDirectorTool(mainHand.getItem()) ||
                            isDirectorTool(offHand.getItem());
    }
    
    /**
     * 检查物品是否为Director工具（使用Registry，支持扩展）
     */
    private static boolean isDirectorTool(Item item) {
        if (item == null) return false;
        
        // 获取缓存的工具列表
        Item[] tools = getDirectorTools();
        if (tools == null) return false;
        
        // 检查是否为Director工具
        for (Item tool : tools) {
            if (item == tool) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取Director工具列表（带缓存）
     */
    private static Item[] getDirectorTools() {
        long currentTime = System.currentTimeMillis();
        
        // 检查缓存是否有效
        if (directorTools != null && 
            currentTime - lastToolsCacheTime < TOOLS_CACHE_DURATION) {
            return directorTools;
        }
        
        // 重新构建工具列表
        try {
            // 主要工具：注册的Director工具
            Item mainTool = ModItems.DIRECTOR_TOOL;
            
            // 构建工具数组（支持扩展）
            java.util.List<Item> toolList = new java.util.ArrayList<>();
            if (mainTool != null) {
                toolList.add(mainTool);
            }
            
            // 可以在这里添加更多工具类型
            // 例如：toolList.add(ModItems.DIRECTOR_ADVANCED_TOOL);
            
            directorTools = toolList.toArray(new Item[0]);
            lastToolsCacheTime = currentTime;
            
            LOGGER.debug("[Director] Cached {} Director tools", directorTools.length);
            return directorTools;
            
        } catch (Exception e) {
            LOGGER.warn("[Director] Failed to cache Director tools: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 处理物品使用（增强版：防抖、边缘情况处理、性能优化）
     */
    private void handleItemUse(ClientPlayerEntity player) {
        // 防抖检查：避免频繁调用
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastItemUseTime < ITEM_USE_DEBOUNCE) {
            return;
        }
        
        // 边缘情况检查
        if (!shouldAllowItemUse(player)) {
            return;
        }
            
            // 检查右键点击 - 使用MinecraftClient的输入处理
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.useKey.isPressed()) {
            // 更新防抖时间
            lastItemUseTime = currentTime;
            
                // 打开Director窗口
                openDirectorScreen();
                LOGGER.info("[Director] Director工具右键点击，打开窗口");
            }
    }
    
    /**
     * 检查是否应该允许物品使用（边缘情况处理）
     */
    private boolean shouldAllowItemUse(ClientPlayerEntity player) {
        try {
            // 检查玩家是否有效
            if (player == null || !player.isAlive()) {
                return false;
            }
            
            // 检查是否在创造模式（可选：在创造模式下禁用UI）
            if (player.isCreative() && !isCreativeModeAllowed()) {
                LOGGER.debug("[Director] Item use blocked in creative mode");
                return false;
            }
            
            // UI 状态检查现在由 ImGuiRenderer 统一管理
            // 检查 ImGuiRenderer 是否已初始化
            if (!DirectorServices.get().imgui().isInitialized()) {
                LOGGER.debug("[Director] Item use blocked - ImGui not initialized");
                return false;
            }
            
            // 检查是否在多人游戏中的延迟情况
            if (isInMultiplayer() && hasHighLatency()) {
                LOGGER.debug("[Director] Item use blocked - high latency in multiplayer");
                return false;
            }
            
            // 检查玩家是否在合适的维度（可选）
            if (!isDimensionAllowed(player)) {
                LOGGER.debug("[Director] Item use blocked - dimension not allowed");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            LOGGER.warn("[Director] Error checking item use conditions: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查是否允许在创造模式下使用
     */
    private boolean isCreativeModeAllowed() {
        // 可以通过配置或设置来控制
        return true; // 默认允许
    }
    
    /**
     * 检查是否在多人游戏中
     */
    private boolean isInMultiplayer() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client != null && client.getNetworkHandler() != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查是否有高延迟
     */
    private boolean hasHighLatency() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getNetworkHandler() != null) {
                int latency = 0;
                if (client.player != null) {
                    latency = Objects.requireNonNull(client.getNetworkHandler().getPlayerListEntry(client.player.getUuid())).getLatency();
                }
                return latency > 200; // 200ms以上认为是高延迟
            }
        } catch (Exception e) {
            LOGGER.debug("[Director] Error checking latency: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 检查当前维度是否允许使用工具
     */
    private boolean isDimensionAllowed(ClientPlayerEntity player) {
        try {
            // 可以在这里添加维度限制逻辑
            // 例如：禁用在下界或末地使用
            return true; // 默认允许所有维度
        } catch (Exception e) {
            LOGGER.debug("[Director] Error checking dimension: {}", e.getMessage());
            return true;
        }
    }

    // ImGui 初始化现在由 ImGuiRenderer 统一处理
    // 移除了重复的初始化逻辑，避免冲突
    
    // ImGui 相关方法已移除，现在由 ImGuiRenderer 统一管理

    // disposeImGui 方法已移除，现在由 ImGuiRenderer 统一管理

    /**
     * 打开 Director 屏幕（增强版：线程安全、UI叠加防护、错误处理）
     */
    public static void openDirectorScreen() {
        LOGGER.debug("[Director] Attempting to open Director screen");
        
        // 记录调试信息
        DirectorDebugger.recordScreenOpen();
        
        // ImGui 初始化现在由 ImGuiRenderer 统一管理
        // 检查 ImGuiRenderer 是否已初始化
        if (!DirectorServices.get().imgui().isInitialized()) {
            LOGGER.warn("[Director] ImGui not initialized, cannot open screen");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            LOGGER.warn("[Director] Cannot open screen: MinecraftClient is null");
            return;
        }
        DirectorMemoryRequirements.adviseIfLowHeap(client);

        LOGGER.debug("[Director] ImGui is initialized, proceeding with screen opening");

        try {
            if (isDirectorScreenAlreadyOpen(client)) {
                LOGGER.debug("[Director] Director screen is already open, skipping");
                return;
            }

            if (client.isOnThread()) {
                setScreenSafely(client);
            } else {
                LOGGER.debug("[Director] Scheduling screen opening to main thread");
                client.execute(() -> setScreenSafely(client));
            }

        } catch (Exception e) {
            LOGGER.error("[Director] Failed to open Director screen", e);
        }
    }
    
    /**
     * 检查Director屏幕是否已经打开
     */
    public static void markDirectorScreenClosed() {
        lastItemUseTime = System.currentTimeMillis();
    }

    private static boolean isDirectorScreenAlreadyOpen(MinecraftClient client) {
        try {
            return client.currentScreen instanceof com.director.ui.DirectorScreen;
        } catch (Exception e) {
            LOGGER.debug("[Director] Error checking current screen: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 安全地设置屏幕（在主线程中调用）
     */
    private static void setScreenSafely(MinecraftClient client) {
        try {
            // 双重检查：确保仍在主线程且屏幕未打开
            if (!client.isOnThread()) {
                LOGGER.error("[Director] setScreenSafely called from non-main thread!");
                return;
            }
            
            if (isDirectorScreenAlreadyOpen(client)) {
                LOGGER.debug("[Director] Screen already open during execution, aborting");
                return;
            }
            
            // 设置新屏幕
            client.setScreen(new com.director.ui.DirectorScreen());
            LOGGER.debug("[Director] Director screen opened successfully");
            
        } catch (Exception e) {
            LOGGER.error("[Director] Error setting Director screen", e);
        }
    }
}
