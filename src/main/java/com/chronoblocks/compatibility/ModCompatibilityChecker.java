package com.chronoblocks.compatibility;

import com.chronoblocks.config.ChronoBlocksConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 模组兼容性检查器
 * <p>
 * 负责检查和管理模组兼容性，包括：
 * - 检查必需模组是否存在
 * - 检查可选模组的兼容性
 * - 提供兼容性报告
 * - 处理模组冲突
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ModCompatibilityChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCompatibilityChecker.class);
    
    // 兼容性状态
    private static final Map<String, CompatibilityStatus> compatibilityStatus = new HashMap<>();
    
    // 已知的兼容模组列表
    private static final Map<String, ModInfo> knownMods = new HashMap<>();
    
    /**
     * 兼容性状态枚举
     */
    public enum CompatibilityStatus {
        COMPATIBLE("兼容"),
        INCOMPATIBLE("不兼容"),
        UNKNOWN("未知"),
        REQUIRED_MISSING("必需模组缺失"),
        VERSION_MISMATCH("版本不匹配");
        
        private final String displayName;
        
        CompatibilityStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
         * 模组信息类
         */
        public record ModInfo(String modId, String name, String version, boolean isRequired, String[] supportedVersions,
                              String description) {
    }
    
    /**
     * 初始化兼容性检查器
     */
    public static void initialize() {
        LOGGER.info("[ChronoBlocks] 初始化模组兼容性检查器...");
        
        // 初始化已知模组信息
        initializeKnownMods();
        
        // 执行兼容性检查
        if (ChronoBlocksConfig.isModCompatibilityEnabled()) {
            checkCompatibility();
        } else {
            LOGGER.info("[ChronoBlocks] 模组兼容性检查已禁用");
        }
        
        LOGGER.info("[ChronoBlocks] 模组兼容性检查器初始化完成");
    }
    
    /**
     * 初始化已知模组信息
     */
    private static void initializeKnownMods() {
        // 必需模组
        knownMods.put("fabric", new ModInfo(
            "fabric", "Fabric API", "0.119.4+", true,
            new String[]{"0.119.4+", "0.128.2+", "0.17.0+"},
            "Fabric API - 模组开发的核心 API"
        ));
        
        knownMods.put("fabricloader", new ModInfo(
            "fabricloader", "Fabric Loader", "0.17.0+", true,
            new String[]{"0.17.0+", "0.18.0+", "0.19.0+"},
            "Fabric Loader - 模组加载器"
        ));
        
        // 可选模组
        knownMods.put("jei", new ModInfo(
            "jei", "Just Enough Items", "12.0.0+", false,
            new String[]{"12.0.0+", "13.0.0+", "14.0.0+"},
            "Just Enough Items - 物品查看器"
        ));
        
        knownMods.put("wthit", new ModInfo(
            "wthit", "WTHIT", "5.0.0+", false,
            new String[]{"5.0.0+", "6.0.0+", "7.0.0+"},
            "WTHIT - What The Hell Is That - 实体信息显示"
        ));
        
        knownMods.put("modmenu", new ModInfo(
            "modmenu", "Mod Menu", "4.0.0+", false,
            new String[]{"4.0.0+", "5.0.0+", "6.0.0+"},
            "Mod Menu - 模组菜单"
        ));
        
        LOGGER.debug("[ChronoBlocks] 已初始化 {} 个已知模组", knownMods.size());
    }
    
    /**
     * 执行兼容性检查
     */
    public static void checkCompatibility() {
        LOGGER.info("[ChronoBlocks] 开始模组兼容性检查...");
        
        // 检查必需模组
        checkRequiredMods();
        
        // 检查可选模组
        checkOptionalMods();
        
        // 生成兼容性报告
        generateCompatibilityReport();
        
        LOGGER.info("[ChronoBlocks] 模组兼容性检查完成");
    }
    
    /**
     * 检查必需模组
     */
    private static void checkRequiredMods() {
        String[] requiredMods = ChronoBlocksConfig.getRequiredMods();
        
        for (String modId : requiredMods) {
            if (modId.isEmpty()) continue;
            
            ModInfo modInfo = knownMods.get(modId);
            if (modInfo == null) {
                LOGGER.warn("[ChronoBlocks] 未知的必需模组: {}", modId);
                compatibilityStatus.put(modId, CompatibilityStatus.UNKNOWN);
                continue;
            }
            
            if (FabricLoader.getInstance().isModLoaded(modId)) {
                ModContainer modContainer = FabricLoader.getInstance().getModContainer(modId).orElse(null);
                if (modContainer != null) {
                    String version = modContainer.getMetadata().getVersion().getFriendlyString();
                    if (isVersionCompatible(version, modInfo.supportedVersions)) {
                        compatibilityStatus.put(modId, CompatibilityStatus.COMPATIBLE);
                        LOGGER.info("[ChronoBlocks] 必需模组 {} 兼容 (版本: {})", modId, version);
                    } else {
                        compatibilityStatus.put(modId, CompatibilityStatus.VERSION_MISMATCH);
                        LOGGER.warn("[ChronoBlocks] 必需模组 {} 版本不匹配 (当前: {}, 支持: {})", 
                                  modId, version, Arrays.toString(modInfo.supportedVersions));
                    }
                } else {
                    compatibilityStatus.put(modId, CompatibilityStatus.UNKNOWN);
                    LOGGER.warn("[ChronoBlocks] 无法获取模组 {} 的版本信息", modId);
                }
            } else {
                compatibilityStatus.put(modId, CompatibilityStatus.REQUIRED_MISSING);
                LOGGER.error("[ChronoBlocks] 必需模组 {} 未安装", modId);
            }
        }
    }
    
    /**
     * 检查可选模组
     */
    private static void checkOptionalMods() {
        String[] optionalMods = ChronoBlocksConfig.getOptionalMods();
        
        for (String modId : optionalMods) {
            if (modId.isEmpty()) continue;
            
            ModInfo modInfo = knownMods.get(modId);
            if (modInfo == null) {
                LOGGER.debug("[ChronoBlocks] 未知的可选模组: {}", modId);
                compatibilityStatus.put(modId, CompatibilityStatus.UNKNOWN);
                continue;
            }
            
            if (FabricLoader.getInstance().isModLoaded(modId)) {
                ModContainer modContainer = FabricLoader.getInstance().getModContainer(modId).orElse(null);
                if (modContainer != null) {
                    String version = modContainer.getMetadata().getVersion().getFriendlyString();
                    if (isVersionCompatible(version, modInfo.supportedVersions)) {
                        compatibilityStatus.put(modId, CompatibilityStatus.COMPATIBLE);
                        LOGGER.info("[ChronoBlocks] 可选模组 {} 兼容 (版本: {})", modId, version);
                    } else {
                        compatibilityStatus.put(modId, CompatibilityStatus.VERSION_MISMATCH);
                        LOGGER.warn("[ChronoBlocks] 可选模组 {} 版本不匹配 (当前: {}, 支持: {})", 
                                  modId, version, Arrays.toString(modInfo.supportedVersions));
                    }
                } else {
                    compatibilityStatus.put(modId, CompatibilityStatus.UNKNOWN);
                    LOGGER.debug("[ChronoBlocks] 无法获取模组 {} 的版本信息", modId);
                }
            } else {
                compatibilityStatus.put(modId, CompatibilityStatus.UNKNOWN);
                LOGGER.debug("[ChronoBlocks] 可选模组 {} 未安装", modId);
            }
        }
    }
    
    /**
     * 检查版本兼容性
     */
    private static boolean isVersionCompatible(String currentVersion, String[] supportedVersions) {
        if (supportedVersions == null || supportedVersions.length == 0) {
            return true; // 没有版本限制
        }
        
        for (String supportedVersion : supportedVersions) {
            if (isVersionCompatible(currentVersion, supportedVersion)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查单个版本兼容性
     */
    private static boolean isVersionCompatible(String currentVersion, String supportedVersion) {
        try {
            // Handle different version formats
            if (supportedVersion.endsWith("+")) {
                String minVersion = supportedVersion.substring(0, supportedVersion.length() - 1);
                return compareVersions(currentVersion, minVersion) >= 0;
            } else if (supportedVersion.contains("+") && supportedVersion.contains(".")) {
                // Handle format like "0.119.4+1.21.4" - extract base version
                String baseVersion = supportedVersion.split("\\+")[0];
                return compareVersions(currentVersion, baseVersion) >= 0;
            } else if (supportedVersion.contains("-")) {
                String[] range = supportedVersion.split("-");
                String minVersion = range[0];
                String maxVersion = range[1];
                return compareVersions(currentVersion, minVersion) >= 0 &&
                       compareVersions(currentVersion, maxVersion) <= 0;
            } else {
                return currentVersion.equals(supportedVersion);
            }
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 版本比较失败: {} vs {}", currentVersion, supportedVersion);
            return false;
        }
    }
    
    /**
     * 比较版本号
     */
    private static int compareVersions(String version1, String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");
        
        int maxLength = Math.max(v1Parts.length, v2Parts.length);
        
        for (int i = 0; i < maxLength; i++) {
            int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;
            
            if (v1Part < v2Part) return -1;
            if (v1Part > v2Part) return 1;
        }
        
        return 0;
    }
    
    /**
     * 生成兼容性报告
     */
    private static void generateCompatibilityReport() {
        LOGGER.info("[ChronoBlocks] === 模组兼容性报告 ===");
        
        int compatibleCount = 0;
        int incompatibleCount = 0;
        int unknownCount = 0;
        
        for (Map.Entry<String, CompatibilityStatus> entry : compatibilityStatus.entrySet()) {
            String modId = entry.getKey();
            CompatibilityStatus status = entry.getValue();
            ModInfo modInfo = knownMods.get(modId);
            
            String modName = modInfo != null ? modInfo.name : modId;
            String statusText = status.getDisplayName();
            
            switch (status) {
                case COMPATIBLE:
                    LOGGER.info("[ChronoBlocks] ✓ {} ({}) - {}", modName, modId, statusText);
                    compatibleCount++;
                    break;
                case INCOMPATIBLE:
                case REQUIRED_MISSING:
                case VERSION_MISMATCH:
                    LOGGER.warn("[ChronoBlocks] ✗ {} ({}) - {}", modName, modId, statusText);
                    incompatibleCount++;
                    break;
                case UNKNOWN:
                    LOGGER.debug("[ChronoBlocks] ? {} ({}) - {}", modName, modId, statusText);
                    unknownCount++;
                    break;
            }
        }
        
        LOGGER.info("[ChronoBlocks] 兼容性统计: 兼容 {} 个, 不兼容 {} 个, 未知 {} 个", 
                   compatibleCount, incompatibleCount, unknownCount);
        
        if (incompatibleCount > 0) {
            LOGGER.warn("[ChronoBlocks] 发现 {} 个不兼容的模组，可能影响功能", incompatibleCount);
        }
    }
    
    /**
     * 获取模组兼容性状态
     */
    public static CompatibilityStatus getModStatus(String modId) {
        return compatibilityStatus.getOrDefault(modId, CompatibilityStatus.UNKNOWN);
    }

    /**
     * 检查是否有不兼容的必需模组
     */
    public static boolean hasIncompatibleRequiredMods() {
        String[] requiredMods = ChronoBlocksConfig.getRequiredMods();
        for (String modId : requiredMods) {
            if (modId.isEmpty()) continue;
            CompatibilityStatus status = getModStatus(modId);
            if (status == CompatibilityStatus.REQUIRED_MISSING || 
                status == CompatibilityStatus.INCOMPATIBLE ||
                status == CompatibilityStatus.VERSION_MISMATCH) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取兼容性报告摘要
     */
    public static String getCompatibilitySummary() {
        int compatibleCount = 0;
        int incompatibleCount = 0;
        int unknownCount = 0;
        
        for (CompatibilityStatus status : compatibilityStatus.values()) {
            switch (status) {
                case COMPATIBLE:
                    compatibleCount++;
                    break;
                case INCOMPATIBLE:
                case REQUIRED_MISSING:
                case VERSION_MISMATCH:
                    incompatibleCount++;
                    break;
                case UNKNOWN:
                    unknownCount++;
                    break;
            }
        }
        
        return String.format("兼容: %d, 不兼容: %d, 未知: %d", 
                           compatibleCount, incompatibleCount, unknownCount);
    }
}
