package com.chronoblocks.i18n;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.client.resource.language.I18n;
import com.chronoblocks.ChronoBlocksMod;

import java.util.IllegalFormatException;

/**
 * ChronoBlocks 国际化工具类
 * <p>
 * 提供便捷的翻译方法，支持格式化字符串和多语言切换。
 * 类似于 Axiom mod 的 AxiomI18n 类，为 ChronoBlocks 提供统一的翻译接口。
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li><strong>翻译获取</strong> - 获取指定键的翻译文本</li>
 *   <li><strong>默认值支持</strong> - 当翻译不存在时返回默认值</li>
 *   <li><strong>格式化字符串</strong> - 支持参数化的翻译字符串</li>
 *   <li><strong>存在性检查</strong> - 检查翻译键是否存在</li>
 *   <li><strong>语言切换</strong> - 动态切换当前语言</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 基本翻译
 * String text = ChronoBlocksI18n.get("key.chronoblocks.open_ui");
 * 
 * // 带默认值的翻译
 * String text = ChronoBlocksI18n.getOrDefault("key.chronoblocks.unknown", "未知");
 * 
 * // 格式化翻译
 * String text = ChronoBlocksI18n.get("key.chronoblocks.welcome", "玩家", 100);
 * 
 * // 检查翻译是否存在
 * boolean exists = ChronoBlocksI18n.exists("key.chronoblocks.some_key");
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 1.0
 */
public class ChronoBlocksI18n {
    
    /** 当前语言管理器 */
    private static volatile LanguageManager currentLanguage = null;
    
    /**
     * 私有构造函数，防止实例化
     */
    private ChronoBlocksI18n() {
    }
    
    /**
     * 初始化国际化系统
     */
    public static void initialize() {
        try {
            // 初始化翻译加载器
            TranslationLoader.initialize();
            
            // 尝试初始化客户端语言管理器（如果可用）
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getLanguageManager() != null) {
                    currentLanguage = new LanguageManager(client.getLanguageManager());
                    ChronoBlocksMod.LOGGER.debug("[ChronoBlocks] 客户端语言管理器已初始化");
                }
            } catch (Exception e) {
                ChronoBlocksMod.LOGGER.debug("[ChronoBlocks] 客户端语言管理器不可用（可能是在服务器端）: {}", e.getMessage());
            }
            
            ChronoBlocksMod.LOGGER.info("[ChronoBlocks] 国际化系统初始化完成");
            
        } catch (Exception e) {
            ChronoBlocksMod.LOGGER.error("[ChronoBlocks] 初始化国际化系统时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 设置当前语言
     * 
     * @param languageManager 语言管理器
     */
    public static void setLanguage(LanguageManager languageManager) {
        currentLanguage = languageManager;
        ChronoBlocksMod.LOGGER.debug("[ChronoBlocks] 语言已切换");
    }
    
    /**
     * 获取翻译文本
     * 
     * @param key 翻译键
     * @return 翻译文本，如果不存在则返回键本身
     */
    public static String get(String key) {
        // 首先尝试使用客户端语言管理器
        if (currentLanguage != null) {
            String translation = currentLanguage.get(key);
            if (!translation.equals(key)) {
                return translation;
            }
        }
        
        // 回退到翻译加载器
        return TranslationLoader.getTranslation(key);
    }
    
    /**
     * 获取翻译文本，如果不存在则返回默认值
     * 
     * @param key 翻译键
     * @param defaultValue 默认值
     * @return 翻译文本或默认值
     */
    public static String getOrDefault(String key, String defaultValue) {
        // 首先尝试使用客户端语言管理器
        if (currentLanguage != null) {
            String translation = currentLanguage.getOrDefault(key, defaultValue);
            if (!translation.equals(key)) {
                return translation;
            }
        }
        
        // 回退到翻译加载器
        String translation = TranslationLoader.getTranslation(key);
        if (!translation.equals(key)) {
            return translation;
        }
        
        return defaultValue;
    }
    
    /**
     * 获取格式化的翻译文本
     * 
     * @param key 翻译键
     * @param args 格式化参数
     * @return 格式化后的翻译文本
     */
    public static String get(String key, Object... args) {
        if (currentLanguage == null) {
            ChronoBlocksMod.LOGGER.debug("[ChronoBlocks] 语言管理器未初始化，返回键: {}", key);
            return key;
        }
        
        String template = currentLanguage.get(key);
        
        try {
            return String.format(template, args);
        } catch (IllegalFormatException e) {
            ChronoBlocksMod.LOGGER.warn("[ChronoBlocks] 翻译格式化错误: key={}, template={}, error={}", 
                key, template, e.getMessage());
            return "格式化错误: " + template;
        }
    }
    
    /**
     * 检查翻译键是否存在
     * 
     * @param key 翻译键
     * @return 如果存在则返回 true
     */
    public static boolean exists(String key) {
        // 首先检查客户端语言管理器
        if (currentLanguage != null && currentLanguage.exists(key)) {
            return true;
        }
        
        // 然后检查翻译加载器
        return TranslationLoader.hasTranslation(key);
    }
    
    /**
     * 获取翻译文本作为 Text 对象
     * 
     * @param key 翻译键
     * @return Text 对象
     */
    public static Text getText(String key) {
        return Text.literal(get(key));
    }
    
    /**
     * 获取格式化的翻译文本作为 Text 对象
     * 
     * @param key 翻译键
     * @param args 格式化参数
     * @return Text 对象
     */
    public static Text getText(String key, Object... args) {
        return Text.literal(get(key, args));
    }
    
    /**
     * 获取当前语言代码
     * 
     * @return 当前语言代码，如果未初始化则返回 "unknown"
     */
    public static String getCurrentLanguage() {
        // 优先使用翻译加载器的语言
        if (TranslationLoader.isInitialized()) {
            return TranslationLoader.getCurrentLanguage();
        }
        
        // 回退到客户端语言管理器
        if (currentLanguage != null) {
            return currentLanguage.getCurrentLanguage();
        }
        
        return "unknown";
    }
    
    /**
     * 检查国际化系统是否已初始化
     * 
     * @return 如果已初始化则返回 true
     */
    public static boolean isInitialized() {
        return TranslationLoader.isInitialized() || currentLanguage != null;
    }
    
    /**
     * 获取国际化系统状态信息
     * 
     * @return 状态信息字符串
     */
    public static String getStatus() {
        if (currentLanguage == null) {
            return "未初始化";
        }
        
        return String.format("已初始化 (语言: %s)", currentLanguage.getCurrentLanguage());
    }
    
    /**
     * 语言管理器包装类
     * <p>
     * 包装 Minecraft 的 LanguageManager，提供统一的翻译接口。
     */
    public static class LanguageManager {
        
        /** Minecraft 语言管理器 */
        private final net.minecraft.client.resource.language.LanguageManager minecraftLanguageManager;
        
        /**
         * 创建语言管理器
         * 
         * @param minecraftLanguageManager Minecraft 语言管理器
         */
        public LanguageManager(net.minecraft.client.resource.language.LanguageManager minecraftLanguageManager) {
            this.minecraftLanguageManager = minecraftLanguageManager;
        }
        
        /**
         * 获取翻译文本
         * 
         * @param key 翻译键
         * @return 翻译文本
         */
        public String get(String key) {
            try {
                // 使用 Minecraft 的 I18n 类进行翻译
                return I18n.translate(key);
            } catch (Exception e) {
                ChronoBlocksMod.LOGGER.debug("[ChronoBlocks] 获取翻译时发生错误: key={}, error={}", key, e.getMessage());
                return key;
            }
        }
        
        /**
         * 获取翻译文本，如果不存在则返回默认值
         * 
         * @param key 翻译键
         * @param defaultValue 默认值
         * @return 翻译文本或默认值
         */
        public String getOrDefault(String key, String defaultValue) {
            try {
                // 尝试获取翻译
                String translation = I18n.translate(key);
                
                // 如果翻译与键相同，说明翻译不存在
                if (translation.equals(key)) {
                    return defaultValue;
                }
                
                return translation;
            } catch (Exception e) {
                ChronoBlocksMod.LOGGER.debug("[ChronoBlocks] 获取翻译时发生错误: key={}, error={}", key, e.getMessage());
                return defaultValue;
            }
        }
        
        /**
         * 检查翻译键是否存在
         * 
         * @param key 翻译键
         * @return 如果存在则返回 true
         */
        public boolean exists(String key) {
            try {
                String translation = I18n.translate(key);
                // 如果翻译与键不同，说明翻译存在
                return !translation.equals(key);
            } catch (Exception e) {
                ChronoBlocksMod.LOGGER.debug("[ChronoBlocks] 检查翻译存在性时发生错误: key={}, error={}", key, e.getMessage());
                return false;
            }
        }
        
        /**
         * 获取当前语言代码
         * 
         * @return 当前语言代码
         */
        public String getCurrentLanguage() {
            try {
                if (minecraftLanguageManager.getLanguage() != null) {
                    return minecraftLanguageManager.getLanguage();
                }
            } catch (Exception e) {
                ChronoBlocksMod.LOGGER.debug("[ChronoBlocks] 获取当前语言时发生错误: {}", e.getMessage());
            }
            return "unknown";
        }
        
        /**
         * 获取底层 Minecraft 语言管理器
         * 
         * @return Minecraft 语言管理器
         */
        public net.minecraft.client.resource.language.LanguageManager getMinecraftLanguageManager() {
            return minecraftLanguageManager;
        }
    }
    
    /**
     * 翻译键常量
     * <p>
     * 定义 ChronoBlocks 使用的所有翻译键，便于维护和避免硬编码。
     */
    public static class Keys {
        
        // UI 相关
        public static final String OPEN_UI = "key.chronoblocks.open_ui";
        public static final String CLOSE_UI = "key.chronoblocks.close_ui";
        public static final String TOGGLE_UI = "key.chronoblocks.toggle_ui";
        
        // 类别
        public static final String CATEGORY_GENERAL = "category.chronoblocks.general";
        public static final String CATEGORY_UI = "category.chronoblocks.ui";
        public static final String CATEGORY_TOOLS = "category.chronoblocks.tools";
        
        // 工具提示
        public static final String TOOLTIP_CHRONOBLOCKS_TOOL = "tooltip.chronoblocks.tool";
        public static final String TOOLTIP_CHRONOBLOCKS_TOOL_DESCRIPTION = "tooltip.chronoblocks.tool.description";
        
        // 消息
        public static final String MESSAGE_UI_OPENED = "message.chronoblocks.ui.opened";
        public static final String MESSAGE_UI_CLOSED = "message.chronoblocks.ui.closed";
        public static final String MESSAGE_CONFIG_SAVED = "message.chronoblocks.config.saved";
        public static final String MESSAGE_CONFIG_LOADED = "message.chronoblocks.config.loaded";
        
        // 错误消息
        public static final String ERROR_CONFIG_LOAD_FAILED = "error.chronoblocks.config.load_failed";
        public static final String ERROR_CONFIG_SAVE_FAILED = "error.chronoblocks.config.save_failed";
        public static final String ERROR_TRANSLATION_LOAD_FAILED = "error.chronoblocks.translation.load_failed";
        
        // 状态消息
        public static final String STATUS_INITIALIZING = "status.chronoblocks.initializing";
        public static final String STATUS_INITIALIZED = "status.chronoblocks.initialized";
        public static final String STATUS_READY = "status.chronoblocks.ready";
        
        // 命令
        public static final String COMMAND_CHRONOBLOCKS_DESCRIPTION = "command.chronoblocks.description";
        public static final String COMMAND_CHRONOBLOCKS_USAGE = "command.chronoblocks.usage";
        public static final String COMMAND_CHRONOBLOCKS_RELOAD_DESCRIPTION = "command.chronoblocks.reload.description";
        public static final String COMMAND_CHRONOBLOCKS_RELOAD_SUCCESS = "command.chronoblocks.reload.success";
        
        // 配置
        public static final String CONFIG_GENERAL_TITLE = "config.chronoblocks.general.title";
        public static final String CONFIG_UI_TITLE = "config.chronoblocks.ui.title";
        public static final String CONFIG_PERFORMANCE_TITLE = "config.chronoblocks.performance.title";
        
        /**
         * 私有构造函数，防止实例化
         */
        private Keys() {
        }
    }
}
