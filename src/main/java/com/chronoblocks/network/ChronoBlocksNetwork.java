package com.chronoblocks.network;

import com.chronoblocks.ChronoBlocksMod;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChronoBlocks 网络管理器
 * <p>
 * 负责注册和管理模组的网络数据包，包括：
 * - 客户端到服务器的数据包
 * - 服务器到客户端的数据包
 * - 网络事件处理
 * 
 * 注意：由于 Fabric API 1.21+ 的网络系统有重大变化，
 * 这里提供简化的网络管理实现，专注于数据包标识符定义。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ChronoBlocksNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronoBlocksNetwork.class);
    
    // 数据包标识符 - 使用 ChronoBlocksMod.id() 辅助方法
    public static final Identifier UI_OPEN_PACKET = ChronoBlocksMod.id("ui_open");
    public static final Identifier UI_CLOSE_PACKET = ChronoBlocksMod.id("ui_close");
    public static final Identifier CONFIG_SYNC_PACKET = ChronoBlocksMod.id("config_sync");
    public static final Identifier PROJECT_DATA_PACKET = ChronoBlocksMod.id("project_data");
    
    /**
     * 初始化网络系统
     */
    public static void initialize() {
        LOGGER.info("[ChronoBlocks] 初始化网络系统...");
        
        // 注册数据包标识符
        registerPacketIdentifiers();
        
        LOGGER.info("[ChronoBlocks] 网络系统初始化完成");
    }
    
    /**
     * 注册数据包标识符
     */
    private static void registerPacketIdentifiers() {
        LOGGER.debug("[ChronoBlocks] 注册数据包标识符:");
        LOGGER.debug("[ChronoBlocks] - UI_OPEN_PACKET: {}", UI_OPEN_PACKET);
        LOGGER.debug("[ChronoBlocks] - UI_CLOSE_PACKET: {}", UI_CLOSE_PACKET);
        LOGGER.debug("[ChronoBlocks] - CONFIG_SYNC_PACKET: {}", CONFIG_SYNC_PACKET);
        LOGGER.debug("[ChronoBlocks] - PROJECT_DATA_PACKET: {}", PROJECT_DATA_PACKET);
        
        // 注意：在 Fabric API 1.21+ 中，数据包处理器的注册方式发生了变化
        // 这里只注册标识符，实际的数据包处理需要在客户端和服务器端分别实现
        LOGGER.info("[ChronoBlocks] 数据包标识符注册完成");
    }
    
    /**
     * 获取 UI 打开数据包标识符
     */
    public static Identifier getUIOpenPacketId() {
        return UI_OPEN_PACKET;
    }
    
    /**
     * 获取 UI 关闭数据包标识符
     */
    public static Identifier getUIClosePacketId() {
        return UI_CLOSE_PACKET;
    }
    
    /**
     * 获取配置同步数据包标识符
     */
    public static Identifier getConfigSyncPacketId() {
        return CONFIG_SYNC_PACKET;
    }
    
    /**
     * 获取项目数据数据包标识符
     */
    public static Identifier getProjectDataPacketId() {
        return PROJECT_DATA_PACKET;
    }
}
