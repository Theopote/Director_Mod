package com.director.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Director 配置管理器
 * <p>
 * 提供模组级别的配置管理，包括：
 * - 性能设置
 * - UI 设置
 * - 调试设置
 * - 兼容性设置
 * 
 * @author Director Team
 * @version 1.0
 */
public class DirectorConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 配置文件路径
    private static final Path CONFIG_DIR = Paths.get("config");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("director.json");
    
    public static final float DEFAULT_FRAME_RATE = 60.0f;
    public static final float MIN_FRAME_RATE = 1.0f;
    public static final float MAX_FRAME_RATE = 240.0f;
    public static final boolean DEFAULT_AUTO_SAVE_ENABLED = true;
    public static final int DEFAULT_AUTO_SAVE_INTERVAL_SECONDS = 5;
    public static final int MIN_AUTO_SAVE_INTERVAL_SECONDS = 1;
    public static final int MAX_AUTO_SAVE_INTERVAL_SECONDS = 3600;
    
    // 当前配置实例
    private static ConfigData currentConfig = new ConfigData();
    
    /**
     * 配置数据结构
     */
    public static class ConfigData {
        // 性能设置
        public PerformanceSettings performance = new PerformanceSettings();
        
        // UI 设置
        public UISettings ui = new UISettings();
        
        // 调试设置
        public DebugSettings debug = new DebugSettings();
        
        // 兼容性设置
        public CompatibilitySettings compatibility = new CompatibilitySettings();
        
        // 配置版本
        public String configVersion = "1.0";
        
        public static class PerformanceSettings {
            public boolean enableOptimizations = true;
            public int warnBlocksPerContainer = 50000;
            public int maxBlocksPerContainer = 200000;
            public int containerWorldBlocksPerTick = 3000;
            /** 推荐客户端 JVM 最大堆（MB）。低于此值仅提示，不阻止使用；大容器操作可能更卡。 */
            public int recommendedClientHeapMb = 8192;
            /** 时间线正常播放：每 UI 帧最多落地多少方块（节流） */
            public int timelineWorldPlacementsPerFrame = 128;
            public int timelineWorldPlacementsPerContainerPerFrame = 64;
            /** 拖动播放头 scrubbing：每 UI 帧落地预算（显著提高以快速预览） */
            public int timelineWorldPlacementsPerFrameScrubbing = 4096;
            public int timelineWorldPlacementsPerContainerPerFrameScrubbing = 2048;
            /** 松手后追帧：额外高速落地帧数与预算 */
            public int timelineScrubCatchUpFrames = 24;
            public int timelineWorldPlacementsPerFrameCatchUp = 6144;
            public int timelineWorldPlacementsPerContainerPerFrameCatchUp = 3072;
            /** 时间线目标方块异步落地时，服务端 tick 预算倍数 */
            public int timelineVisibilityServerTickMultiplierPlayback = 1;
            public int timelineVisibilityServerTickMultiplierScrubbing = 6;
            public int timelineVisibilityServerTickMultiplierCatchUp = 8;
        }
        
        public static class UISettings {
            public String defaultTheme = "dark";
            public float defaultFps = DEFAULT_FRAME_RATE;
            public boolean autoSaveEnabled = DEFAULT_AUTO_SAVE_ENABLED;
            public int autoSaveIntervalSeconds = DEFAULT_AUTO_SAVE_INTERVAL_SECONDS;
        }
        
        public static class DebugSettings {
            public boolean enableDebugMode = false;
        }
        
        public static class CompatibilitySettings {
            public boolean checkModCompatibility = true;
            public String[] requiredMods = {"fabric", "fabricloader"};
            public String[] optionalMods = {"jei", "wthit", "modmenu"};
        }
    }
    
    /**
     * 初始化配置系统
     */
    public static void initialize() {
        LOGGER.info("[Director] 初始化配置系统...");
        
        try {
            // 确保配置目录存在
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
                LOGGER.debug("[Director] 创建配置目录: {}", CONFIG_DIR);
            }
            
            // 加载或创建配置文件
            loadConfig();
            
            LOGGER.info("[Director] 配置系统初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[Director] 配置系统初始化失败: {}", e.getMessage(), e);
            // 使用默认配置
            currentConfig = new ConfigData();
        }
    }
    
    /**
     * 加载配置文件
     */
    private static void loadConfig() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            // 加载现有配置
            String configContent = Files.readString(CONFIG_FILE);
            JsonObject configJson = JsonParser.parseString(configContent).getAsJsonObject();
            
            // 检查配置版本
            String version = configJson.has("configVersion") ? 
                configJson.get("configVersion").getAsString() : "unknown";
            
            if (!"1.0".equals(version)) {
                LOGGER.warn("[Director] 检测到旧版本配置文件 ({}), 将使用默认配置", version);
                currentConfig = new ConfigData();
                saveConfig();
            } else {
                currentConfig = GSON.fromJson(configJson, ConfigData.class);
                LOGGER.info("[Director] 成功加载配置文件: {}", CONFIG_FILE);
            }
        } else {
            // 创建默认配置文件
            LOGGER.info("[Director] 配置文件不存在，创建默认配置: {}", CONFIG_FILE);
            currentConfig = new ConfigData();
            saveConfig();
        }
    }
    
    /**
     * 保存配置文件
     */
    public static boolean saveConfig() {
        try {
            String configJson = GSON.toJson(currentConfig);
            Files.writeString(CONFIG_FILE, configJson);
            LOGGER.debug("[Director] 配置文件已保存: {}", CONFIG_FILE);
            return true;
        } catch (IOException e) {
            LOGGER.error("[Director] 保存配置文件失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取当前配置
     */
    public static ConfigData getConfig() {
        return currentConfig;
    }

    public static float getDefaultFrameRate() {
        float fps = currentConfig.ui.defaultFps;
        return isValidDefaultFrameRate(fps) ? fps : DEFAULT_FRAME_RATE;
    }

    public static boolean isAutoSaveEnabled() {
        return currentConfig.ui.autoSaveEnabled;
    }

    public static int getAutoSaveIntervalSeconds() {
        return Math.clamp(currentConfig.ui.autoSaveIntervalSeconds,
                MIN_AUTO_SAVE_INTERVAL_SECONDS, MAX_AUTO_SAVE_INTERVAL_SECONDS);
    }

    public static boolean isValidDefaultFrameRate(float fps) {
        return Float.isFinite(fps) && fps >= MIN_FRAME_RATE && fps <= MAX_FRAME_RATE;
    }

    public static boolean isValidAutoSaveIntervalSeconds(int seconds) {
        return seconds >= MIN_AUTO_SAVE_INTERVAL_SECONDS && seconds <= MAX_AUTO_SAVE_INTERVAL_SECONDS;
    }

    /**
     * 更新并持久化通用设置。保存失败时恢复调用前的内存值。
     */
    public static boolean updateUISettings(float defaultFps, boolean autoSaveEnabled, int autoSaveIntervalSeconds) {
        if (!isValidDefaultFrameRate(defaultFps) || !isValidAutoSaveIntervalSeconds(autoSaveIntervalSeconds)) {
            return false;
        }

        ConfigData.UISettings ui = currentConfig.ui;
        float previousFps = ui.defaultFps;
        boolean previousAutoSaveEnabled = ui.autoSaveEnabled;
        int previousInterval = ui.autoSaveIntervalSeconds;
        ui.defaultFps = defaultFps;
        ui.autoSaveEnabled = autoSaveEnabled;
        ui.autoSaveIntervalSeconds = autoSaveIntervalSeconds;
        if (saveConfig()) {
            return true;
        }

        ui.defaultFps = previousFps;
        ui.autoSaveEnabled = previousAutoSaveEnabled;
        ui.autoSaveIntervalSeconds = previousInterval;
        return false;
    }

    /**
     * 检查是否启用调试模式
     */
    public static boolean isDebugMode() {
        return currentConfig.debug.enableDebugMode;
    }

    /**
     * 检查是否启用模组兼容性检查
     */
    public static boolean isModCompatibilityEnabled() {
        return currentConfig.compatibility.checkModCompatibility;
    }
    
    /**
     * 获取必需模组列表
     */
    public static String[] getRequiredMods() {
        return currentConfig.compatibility.requiredMods;
    }
    
    /**
     * 获取可选模组列表
     */
    public static String[] getOptionalMods() {
        return currentConfig.compatibility.optionalMods;
    }
}
