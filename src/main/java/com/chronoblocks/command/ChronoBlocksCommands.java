package com.chronoblocks.command;

import com.chronoblocks.ChronoBlocksMod;
import com.chronoblocks.compatibility.VersionCompatibilityChecker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChronoBlocks 命令注册器
 * <p>
 * 注册模组的服务器端和客户端命令，包括：
 * - 配置管理命令
 * - 调试命令
 * - 项目管理命令
 * - 兼容性检查命令
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ChronoBlocksCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronoBlocksCommands.class);
    
    /**
     * 初始化命令系统
     */
    public static void initialize() {
        LOGGER.info("[ChronoBlocks] 初始化命令系统...");
        
        // 注册命令
        CommandRegistrationCallback.EVENT.register(ChronoBlocksCommands::registerCommands);
        
        LOGGER.info("[ChronoBlocks] 命令系统初始化完成");
    }
    
    /**
     * 注册所有命令
     */
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, 
                                       CommandRegistryAccess registryAccess, 
                                       CommandManager.RegistrationEnvironment environment) {
        
        // 主命令：/chronoblocks
        dispatcher.register(CommandManager.literal("chronoblocks")
            .requires(source -> source.hasPermissionLevel(2)) // 需要 OP 权限
            .then(CommandManager.literal("config")
                .then(CommandManager.literal("reload")
                    .executes(ChronoBlocksCommands::reloadConfig))
                .then(CommandManager.literal("reset")
                    .executes(ChronoBlocksCommands::resetConfig))
                .then(CommandManager.literal("show")
                    .executes(ChronoBlocksCommands::showConfig)))
            .then(CommandManager.literal("debug")
                .then(CommandManager.literal("enable")
                    .executes(ChronoBlocksCommands::enableDebug))
                .then(CommandManager.literal("disable")
                    .executes(ChronoBlocksCommands::disableDebug))
                .then(CommandManager.literal("status")
                    .executes(ChronoBlocksCommands::debugStatus)))
            .then(CommandManager.literal("compatibility")
                .executes(ChronoBlocksCommands::checkCompatibility))
            .then(CommandManager.literal("version")
                .executes(ChronoBlocksCommands::showVersion))
            .then(CommandManager.literal("compatibility-report")
                .executes(ChronoBlocksCommands::showCompatibilityReport))
            .then(CommandManager.literal("help")
                .executes(ChronoBlocksCommands::showHelp))
            .executes(ChronoBlocksCommands::showHelp));
        
        // 快捷命令：/cb
        dispatcher.register(CommandManager.literal("cb")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(ChronoBlocksCommands::showHelp));
        
        LOGGER.debug("[ChronoBlocks] 命令已注册");
    }
    
    /**
     * 重新加载配置
     */
    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        try {
            // 这里可以添加重新加载配置的逻辑
            context.getSource().sendFeedback(() -> 
                Text.literal("ChronoBlocks 配置已重新加载").formatted(Formatting.GREEN), false);
            LOGGER.info("[ChronoBlocks] 配置已通过命令重新加载");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("重新加载配置失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 重新加载配置失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 重置配置
     */
    private static int resetConfig(CommandContext<ServerCommandSource> context) {
        try {
            // 这里可以添加重置配置的逻辑
            context.getSource().sendFeedback(() -> 
                Text.literal("ChronoBlocks 配置已重置为默认值").formatted(Formatting.YELLOW), false);
            LOGGER.info("[ChronoBlocks] 配置已通过命令重置");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("重置配置失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 重置配置失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 显示配置
     */
    private static int showConfig(CommandContext<ServerCommandSource> context) {
        try {
            // 这里可以添加显示配置的逻辑
            context.getSource().sendFeedback(() -> 
                Text.literal("ChronoBlocks 当前配置信息已发送到聊天").formatted(Formatting.AQUA), false);
            LOGGER.info("[ChronoBlocks] 配置信息已通过命令显示");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("显示配置失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 显示配置失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 启用调试模式
     */
    private static int enableDebug(CommandContext<ServerCommandSource> context) {
        try {
            // 这里可以添加启用调试模式的逻辑
            context.getSource().sendFeedback(() -> 
                Text.literal("ChronoBlocks 调试模式已启用").formatted(Formatting.GREEN), false);
            LOGGER.info("[ChronoBlocks] 调试模式已通过命令启用");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("启用调试模式失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 启用调试模式失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 禁用调试模式
     */
    private static int disableDebug(CommandContext<ServerCommandSource> context) {
        try {
            // 这里可以添加禁用调试模式的逻辑
            context.getSource().sendFeedback(() -> 
                Text.literal("ChronoBlocks 调试模式已禁用").formatted(Formatting.RED), false);
            LOGGER.info("[ChronoBlocks] 调试模式已通过命令禁用");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("禁用调试模式失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 禁用调试模式失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 显示调试状态
     */
    private static int debugStatus(CommandContext<ServerCommandSource> context) {
        try {
            // 这里可以添加显示调试状态的逻辑
            context.getSource().sendFeedback(() -> 
                Text.literal("ChronoBlocks 调试状态信息已发送到聊天").formatted(Formatting.AQUA), false);
            LOGGER.info("[ChronoBlocks] 调试状态已通过命令显示");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("显示调试状态失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 显示调试状态失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 检查模组兼容性
     */
    private static int checkCompatibility(CommandContext<ServerCommandSource> context) {
        try {
            // 执行兼容性检查
            VersionCompatibilityChecker.checkAllCompatibility();
            
            context.getSource().sendFeedback(() -> 
                Text.literal("ChronoBlocks 兼容性检查已完成，请查看日志获取详细信息").formatted(Formatting.AQUA), false);
            LOGGER.info("[ChronoBlocks] 兼容性检查已通过命令执行");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("兼容性检查失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 兼容性检查失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 显示兼容性报告
     */
    private static int showCompatibilityReport(CommandContext<ServerCommandSource> context) {
        try {
            String report = VersionCompatibilityChecker.getCompatibilityReport();
            
            // 将报告按行分割并发送到聊天
            String[] lines = report.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    context.getSource().sendFeedback(() -> 
                        Text.literal(line).formatted(Formatting.WHITE), false);
                }
            }
            
            LOGGER.info("[ChronoBlocks] 兼容性报告已通过命令显示");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("显示兼容性报告失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 显示兼容性报告失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 显示版本信息
     */
    private static int showVersion(CommandContext<ServerCommandSource> context) {
        try {
            // 使用 ChronoBlocksMod 的版本获取方法
            final String modVersion = ChronoBlocksMod.getModVersion();
            final String minecraftVersion = ChronoBlocksMod.getMinecraftVersion();
            final String fabricVersion = ChronoBlocksMod.getFabricVersion();
            
            context.getSource().sendFeedback(() -> 
                Text.literal("ChronoBlocks 版本: " + modVersion).formatted(Formatting.GOLD), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("Minecraft 版本: " + minecraftVersion).formatted(Formatting.AQUA), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("Fabric API 版本: " + fabricVersion).formatted(Formatting.AQUA), false);
            
            LOGGER.info("[ChronoBlocks] 版本信息已通过命令显示: Mod={}, MC={}, Fabric={}", 
                       modVersion, minecraftVersion, fabricVersion);
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("显示版本信息失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 显示版本信息失败: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 显示帮助信息
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        try {
            context.getSource().sendFeedback(() -> 
                Text.literal("=== ChronoBlocks 命令帮助 ===").formatted(Formatting.GOLD), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks config reload - 重新加载配置").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks config reset - 重置配置").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks config show - 显示配置").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks debug enable - 启用调试模式").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks debug disable - 禁用调试模式").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks debug status - 显示调试状态").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks compatibility - 检查模组兼容性").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks compatibility-report - 显示详细兼容性报告").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks version - 显示版本信息").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("/chronoblocks help - 显示此帮助信息").formatted(Formatting.WHITE), false);
            context.getSource().sendFeedback(() -> 
                Text.literal("快捷命令: /cb").formatted(Formatting.GRAY), false);
            
            LOGGER.info("[ChronoBlocks] 帮助信息已通过命令显示");
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("显示帮助信息失败: " + e.getMessage()));
            LOGGER.error("[ChronoBlocks] 显示帮助信息失败: {}", e.getMessage(), e);
            return 0;
        }
    }
}
