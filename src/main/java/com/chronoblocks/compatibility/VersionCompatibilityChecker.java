package com.chronoblocks.compatibility;

import com.chronoblocks.ChronoBlocksMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 版本兼容性检查器
 * <p>
 * 负责检查 Minecraft、Fabric API 和其他依赖的版本兼容性，
 * 确保模组在正确的环境中运行。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class VersionCompatibilityChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionCompatibilityChecker.class);
    
    // 版本模式
    private static final Pattern MINECRAFT_VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern FABRIC_VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(\\+.*)?$");
    
    // 支持的版本范围
    private static final String MINECRAFT_MIN_VERSION = "1.21.0";
    private static final String MINECRAFT_MAX_VERSION = "1.21.4";
    private static final String MINECRAFT_RECOMMENDED_VERSION = "1.21.4";
    
    private static final String FABRIC_MIN_VERSION = "0.119.4";
    private static final String FABRIC_RECOMMENDED_VERSION = "0.119.4+1.21.4";
    
    private static final int JAVA_MIN_VERSION = 21;
    private static final int JAVA_RECOMMENDED_VERSION = 21;
    
    /**
     * 检查所有版本兼容性
     */
    public static void checkAllCompatibility() {
        LOGGER.info("[ChronoBlocks] 开始版本兼容性检查...");
        
        boolean minecraftOk = checkMinecraftCompatibility();
        boolean fabricOk = checkFabricCompatibility();
        boolean javaOk = checkJavaCompatibility();
        
        if (minecraftOk && fabricOk && javaOk) {
            LOGGER.info("[ChronoBlocks] 所有版本兼容性检查通过");
        } else {
            LOGGER.warn("[ChronoBlocks] 部分版本兼容性检查未通过，模组可能无法正常工作");
        }
    }
    
    /**
     * 检查 Minecraft 版本兼容性
     */
    public static boolean checkMinecraftCompatibility() {
        try {
            String currentVersion = ChronoBlocksMod.getMinecraftVersion();
            LOGGER.debug("[ChronoBlocks] 检查 Minecraft 版本兼容性: {}", currentVersion);
            
            if (currentVersion == null || currentVersion.equals("未知")) {
                LOGGER.warn("[ChronoBlocks] 无法获取 Minecraft 版本信息");
                return false;
            }
            
            if (!isMinecraftVersionSupported(currentVersion)) {
                LOGGER.warn("[ChronoBlocks] Minecraft 版本不兼容: {}", currentVersion);
                LOGGER.warn("[ChronoBlocks] 支持的版本范围: {} - {}", MINECRAFT_MIN_VERSION, MINECRAFT_MAX_VERSION);
                LOGGER.warn("[ChronoBlocks] 推荐版本: {}", MINECRAFT_RECOMMENDED_VERSION);
                return false;
            }
            
            LOGGER.info("[ChronoBlocks] Minecraft 版本兼容: {}", currentVersion);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] Minecraft 版本兼容性检查失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查 Fabric API 版本兼容性
     */
    public static boolean checkFabricCompatibility() {
        try {
            String currentVersion = ChronoBlocksMod.getFabricVersion();
            LOGGER.debug("[ChronoBlocks] 检查 Fabric API 版本兼容性: {}", currentVersion);
            
            if (currentVersion == null || currentVersion.equals("未知")) {
                LOGGER.warn("[ChronoBlocks] 无法获取 Fabric API 版本信息");
                return false;
            }
            
            if (!isFabricVersionSupported(currentVersion)) {
                LOGGER.warn("[ChronoBlocks] Fabric API 版本不兼容: {}", currentVersion);
                LOGGER.warn("[ChronoBlocks] 支持的最低版本: {}", FABRIC_MIN_VERSION);
                LOGGER.warn("[ChronoBlocks] 推荐版本: {}", FABRIC_RECOMMENDED_VERSION);
                return false;
            }
            
            LOGGER.info("[ChronoBlocks] Fabric API 版本兼容: {}", currentVersion);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] Fabric API 版本兼容性检查失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查 Java 版本兼容性
     */
    public static boolean checkJavaCompatibility() {
        try {
            String javaVersion = System.getProperty("java.version");
            LOGGER.debug("[ChronoBlocks] 检查 Java 版本兼容性: {}", javaVersion);
            
            int currentVersion = getJavaVersionNumber(javaVersion);
            
            if (currentVersion < JAVA_MIN_VERSION) {
                LOGGER.warn("[ChronoBlocks] Java 版本不兼容: {}", javaVersion);
                LOGGER.warn("[ChronoBlocks] 支持的最低版本: Java {}", JAVA_MIN_VERSION);
                LOGGER.warn("[ChronoBlocks] 推荐版本: Java {}", JAVA_RECOMMENDED_VERSION);
                return false;
            }
            
            LOGGER.info("[ChronoBlocks] Java 版本兼容: {}", javaVersion);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] Java 版本兼容性检查失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查 Minecraft 版本是否支持
     */
    private static boolean isMinecraftVersionSupported(String version) {
        if (!MINECRAFT_VERSION_PATTERN.matcher(version).matches()) {
            return false;
        }
        
        try {
            String[] currentParts = version.split("\\.");
            String[] minParts = MINECRAFT_MIN_VERSION.split("\\.");
            String[] maxParts = MINECRAFT_MAX_VERSION.split("\\.");
            
            int currentMajor = Integer.parseInt(currentParts[0]);
            int currentMinor = Integer.parseInt(currentParts[1]);
            int currentPatch = Integer.parseInt(currentParts[2]);
            
            int minMajor = Integer.parseInt(minParts[0]);
            int minMinor = Integer.parseInt(minParts[1]);
            int minPatch = Integer.parseInt(minParts[2]);
            
            int maxMajor = Integer.parseInt(maxParts[0]);
            int maxMinor = Integer.parseInt(maxParts[1]);
            int maxPatch = Integer.parseInt(maxParts[2]);
            
            // 检查是否在范围内
            if (currentMajor < minMajor || currentMajor > maxMajor) {
                return false;
            }
            
            if (currentMajor == minMajor && currentMinor < minMinor) {
                return false;
            }
            
            if (currentMajor == maxMajor && currentMinor > maxMinor) {
                return false;
            }
            
            if (currentMajor == minMajor && currentMinor == minMinor && currentPatch < minPatch) {
                return false;
            }

            return currentMajor != maxMajor || currentMinor != maxMinor || currentPatch <= maxPatch;

        } catch (NumberFormatException e) {
            LOGGER.debug("[ChronoBlocks] 无法解析 Minecraft 版本: {}", version);
            return false;
        }
    }
    
    /**
     * 检查 Fabric API 版本是否支持
     */
    private static boolean isFabricVersionSupported(String version) {
        if (!FABRIC_VERSION_PATTERN.matcher(version).matches()) {
            return false;
        }
        
        try {
            // 提取基础版本号 (去掉 + 后面的部分)
            String baseVersion = version.split("\\+")[0];
            String[] currentParts = baseVersion.split("\\.");
            String[] minParts = FABRIC_MIN_VERSION.split("\\.");
            
            int currentMajor = Integer.parseInt(currentParts[0]);
            int currentMinor = Integer.parseInt(currentParts[1]);
            int currentPatch = Integer.parseInt(currentParts[2]);
            
            int minMajor = Integer.parseInt(minParts[0]);
            int minMinor = Integer.parseInt(minParts[1]);
            int minPatch = Integer.parseInt(minParts[2]);
            
            // 检查是否 >= 最低版本
            if (currentMajor > minMajor) {
                return true;
            }
            
            if (currentMajor == minMajor && currentMinor > minMinor) {
                return true;
            }

            return currentMajor == minMajor && currentMinor == minMinor && currentPatch >= minPatch;

        } catch (NumberFormatException e) {
            LOGGER.debug("[ChronoBlocks] 无法解析 Fabric API 版本: {}", version);
            return false;
        }
    }
    
    /**
     * 获取 Java 版本号
     */
    private static int getJavaVersionNumber(String javaVersion) {
        if (javaVersion == null) {
            return 0;
        }
        
        try {
            // 处理不同的 Java 版本格式
            String[] parts = javaVersion.split("\\.");
            if (javaVersion.startsWith("1.")) {
                // Java 8 及以下格式: 1.8.0_xxx
                if (parts.length >= 2) {
                    return Integer.parseInt(parts[1]);
                }
            } else {
                // Java 9 及以上格式: 11, 17, 21, etc.
                return Integer.parseInt(parts[0]);
            }
        } catch (NumberFormatException e) {
            LOGGER.debug("[ChronoBlocks] 无法解析 Java 版本: {}", javaVersion);
        }
        
        return 0;
    }
    
    /**
     * 获取兼容性报告
     */
    public static String getCompatibilityReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== ChronoBlocks 版本兼容性报告 ===\n");
        
        // Minecraft 兼容性
        String mcVersion = ChronoBlocksMod.getMinecraftVersion();
        boolean mcCompatible = isMinecraftVersionSupported(mcVersion);
        report.append(String.format("Minecraft: %s %s\n", mcVersion, mcCompatible ? "✓" : "✗"));
        
        // Fabric API 兼容性
        String fabricVersion = ChronoBlocksMod.getFabricVersion();
        boolean fabricCompatible = isFabricVersionSupported(fabricVersion);
        report.append(String.format("Fabric API: %s %s\n", fabricVersion, fabricCompatible ? "✓" : "✗"));
        
        // Java 兼容性
        String javaVersion = System.getProperty("java.version");
        int javaVersionNum = getJavaVersionNumber(javaVersion);
        boolean javaCompatible = javaVersionNum >= JAVA_MIN_VERSION;
        report.append(String.format("Java: %s %s\n", javaVersion, javaCompatible ? "✓" : "✗"));
        
        // 推荐版本
        report.append("\n推荐版本:\n");
        report.append(String.format("- Minecraft: %s\n", MINECRAFT_RECOMMENDED_VERSION));
        report.append(String.format("- Fabric API: %s\n", FABRIC_RECOMMENDED_VERSION));
        report.append(String.format("- Java: %d+\n", JAVA_RECOMMENDED_VERSION));
        
        return report.toString();
    }
    
    /**
     * 检查是否有任何兼容性问题
     */
    public static boolean hasCompatibilityIssues() {
        return !checkMinecraftCompatibility() || 
               !checkFabricCompatibility() || 
               !checkJavaCompatibility();
    }
}