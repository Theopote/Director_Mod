package com.chronoblocks.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 世界标识工具类
 * 用于获取当前世界的唯一标识，用于区分不同的场景/世界
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class WorldIdentifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldIdentifier.class);
    
    // 缓存当前世界标识，避免频繁计算
    private static String cachedWorldId = null;
    // 缓存当前维度键，用于调试和日志记录（虽然不直接用于世界切换检测，但保留以便跟踪当前维度）
    @SuppressWarnings("unused")
    private static RegistryKey<World> cachedWorldKey = null;
    
    /**
     * 获取当前世界的唯一标识
     * 
     * @return 世界标识字符串，如果无法获取则返回 "default"
     */
    public static String getCurrentWorldId() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                LOGGER.debug("[WorldIdentifier] 客户端为null，返回default");
                return "default";
            }
            
            // 尝试从客户端世界获取
            ClientWorld world = client.world;
            if (world != null) {
                RegistryKey<World> worldKey = world.getRegistryKey();
                
                // 计算当前世界的标识（基于存档名称，不包括维度信息）
                String currentWorldId = computeWorldId(client, worldKey);
                
                // 如果世界ID相同（同一存档，可能是不同维度），返回缓存
                if (currentWorldId.equals(cachedWorldId)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[WorldIdentifier] 使用缓存的世界标识: {} (维度: {})", cachedWorldId, worldKey.getValue());
                    }
                    // 更新缓存的维度键（同一存档的不同维度）
                    cachedWorldKey = worldKey;
                    return cachedWorldId;
                }
                
                // 世界ID不同，说明是新的存档
                if (cachedWorldId != null) {
                    LOGGER.info("[WorldIdentifier] 检测到新存档: {} -> {} (当前维度: {})", 
                        cachedWorldId, currentWorldId, worldKey.getValue());
                } else {
                    LOGGER.info("[WorldIdentifier] 初始化存档标识: {} (维度: {})", 
                        currentWorldId, worldKey.getValue());
                }
                cachedWorldKey = worldKey;
                cachedWorldId = currentWorldId;
                return currentWorldId;
            }
            
            // 如果没有世界，尝试从服务器获取
            if (client.getServer() != null) {
                String levelName = client.getServer().getSaveProperties().getLevelName();
                if (levelName != null && !levelName.isEmpty()) {
                    String worldId = sanitizeWorldName(levelName);
                    LOGGER.info("[WorldIdentifier] 从服务器获取世界名称: {} -> {}", levelName, worldId);
                    return worldId;
                }
            }
            
            LOGGER.debug("[WorldIdentifier] 无法获取世界信息，返回default");
        } catch (Exception e) {
            LOGGER.warn("[WorldIdentifier] 获取世界标识失败: {}", e.getMessage(), e);
        }
        
        return "default";
    }
    
    /**
     * 计算世界标识
     * 注意：同一存档的不同维度（主世界、下界、末地）共享同一个世界ID，
     * 这样ChronoBlocks窗口的布局可以在同一存档的不同维度之间保持
     */
    private static String computeWorldId(MinecraftClient client, RegistryKey<World> worldKey) {
        try {
            // 优先方法：从服务器获取存档名称（同一存档的所有维度共享布局）
            if (client.getServer() != null) {
                String levelName = client.getServer().getSaveProperties().getLevelName();
                if (levelName != null && !levelName.isEmpty()) {
                    // 只使用存档名称，不包括维度信息，这样同一存档的所有维度共享布局
                    String worldId = sanitizeWorldName(levelName);
                    LOGGER.debug("[WorldIdentifier] 从存档名称获取世界ID: {} -> {}", levelName, worldId);
                    return worldId;
                }
            }
            
            // 备用方法1: 尝试从客户端获取存档名称
            // 如果无法从服务器获取，使用世界键的命名空间（通常是存档相关的标识）
            String namespace = worldKey.getValue().getNamespace();
            String path = worldKey.getValue().getPath();
            // 对于单机存档，命名空间通常是 "minecraft"，路径是维度名称
            // 我们需要获取存档名称，而不是维度名称
            // 暂时使用命名空间作为标识，但这可能不够准确
            
            // 备用方法2: 使用命名空间和路径的组合（但不包括维度路径，只使用存档相关的部分）
            // 如果命名空间是 minecraft，尝试从其他地方获取存档名称
            if ("minecraft".equals(namespace)) {
                // 对于主世界维度，尝试使用其他方法
                // 这里暂时返回 "minecraft" + path，但理想情况下应该获取存档名称
                LOGGER.warn("[WorldIdentifier] 无法获取存档名称，使用维度信息作为临时标识: {}/{}", namespace, path);
                return sanitizeWorldName(namespace + "_" + path);
            }
            
            // 最后备用方法：使用命名空间和路径的组合
            return sanitizeWorldName(namespace + "_" + path);
            
        } catch (Exception e) {
            LOGGER.warn("[WorldIdentifier] 计算世界标识失败: {}", e.getMessage());
            return "default";
        }
    }
    
    /**
     * 清理世界名称，使其适合作为文件名
     */
    private static String sanitizeWorldName(String name) {
        if (name == null || name.isEmpty()) {
            return "default";
        }
        
        // 移除或替换不适合文件名的字符
        return name
            .replaceAll("[^a-zA-Z0-9_\\-]", "_")
            .replaceAll("_{2,}", "_")
            .toLowerCase();
    }
    
    /**
     * 清除缓存（当切换世界时调用）
     */
    public static void clearCache() {
        cachedWorldId = null;
        cachedWorldKey = null;
        LOGGER.debug("[WorldIdentifier] 世界标识缓存已清除");
    }
    
    /**
     * 检查是否切换了存档（基于存档名称，而不是维度）
     * 注意：同一存档的不同维度（主世界、下界、末地）不算切换
     * 
     * @return true 如果存档已切换
     */
    public static boolean hasWorldChanged() {
        try {
            String currentWorldId = getCurrentWorldId();
            
            // 如果是首次调用，还没有缓存
            if (cachedWorldId == null) {
                cachedWorldId = currentWorldId;
                return false; // 首次检测不算切换
            }
            
            // 比较当前世界ID和缓存的世界ID
            boolean changed = !currentWorldId.equals(cachedWorldId);
            if (changed && LOGGER.isDebugEnabled()) {
                LOGGER.debug("[WorldIdentifier] 检测到存档切换: {} -> {}", 
                    cachedWorldId, currentWorldId);
            }
            return changed;
            
        } catch (Exception e) {
            LOGGER.warn("[WorldIdentifier] 检查世界切换失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取世界保存目录路径
     * 
     * @return 世界保存目录路径，如果无法获取则返回 null
     */
    public static Path getWorldSavePath() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return null;
            }
            
            // 尝试从运行目录获取
            Path runDir = Paths.get(".");
            Path savesDir = runDir.resolve("saves");
            
            if (savesDir.toFile().exists()) {
                String worldId = getCurrentWorldId();
                if (!"default".equals(worldId)) {
                    // 尝试查找对应的世界目录
                    java.io.File[] saves = savesDir.toFile().listFiles();
                    if (saves != null) {
                        for (java.io.File save : saves) {
                            if (save.isDirectory() && sanitizeWorldName(save.getName()).equals(worldId)) {
                                return save.toPath();
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.warn("[WorldIdentifier] 获取世界保存路径失败: {}", e.getMessage());
        }
        
        return null;
    }
}