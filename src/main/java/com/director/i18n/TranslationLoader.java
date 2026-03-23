package com.director.i18n;

import com.director.DirectorMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Director 翻译加载器
 * <p>
 * 负责加载和管理翻译文件，提供简化的国际化支持。
 * 支持从资源包和本地文件加载翻译，并提供翻译缓存功能。
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li><strong>翻译文件加载</strong> - 从资源包和本地文件加载翻译</li>
 *   <li><strong>翻译缓存</strong> - 缓存已加载的翻译以提高性能</li>
 *   <li><strong>语言检测</strong> - 自动检测当前游戏语言</li>
 *   <li><strong>回退机制</strong> - 翻译缺失时回退到英语</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 * @since 1.0
 */
public class TranslationLoader {
    
    /** JSON 解析器 */
    private static final Gson GSON = new GsonBuilder().create();
    
    /** 翻译缓存 */
    private static final Map<String, Map<String, String>> translationCache = new HashMap<>();
    
    /** 当前语言 */
    private static String currentLanguage = "en_us";
    
    /** 是否已初始化 */
    private static boolean initialized = false;
    
    /**
     * 私有构造函数，防止实例化
     */
    private TranslationLoader() {
    }
    
    /**
     * 初始化翻译加载器
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            // 检测当前语言
            currentLanguage = detectCurrentLanguage();
            DirectorMod.LOGGER.info("[Director] 检测到当前语言: {}", currentLanguage);
            
            // 加载翻译文件
            loadTranslations(currentLanguage);
            
            initialized = true;
            DirectorMod.LOGGER.info("[Director] 翻译加载器初始化完成");
            
        } catch (Exception e) {
            DirectorMod.LOGGER.error("[Director] 翻译加载器初始化失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 重新加载当前语言的翻译
     * 用于语言切换时刷新翻译
     */
    public static void reloadLanguage() {
        try {
            String newLanguage = detectCurrentLanguage();
            if (!newLanguage.equals(currentLanguage)) {
                DirectorMod.LOGGER.info("[Director] 语言已改变: {} -> {}", currentLanguage, newLanguage);
                currentLanguage = newLanguage;
                loadTranslations(currentLanguage);
                DirectorMod.LOGGER.info("[Director] 翻译已重新加载");
            }
        } catch (Exception e) {
            DirectorMod.LOGGER.error("[Director] 重新加载语言失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取翻译文本
     * 
     * @param key 翻译键
     * @return 翻译文本
     */
    public static String getTranslation(String key) {
        if (!initialized) {
            return key;
        }
        
        // 尝试当前语言
        String translation = getTranslationForLanguage(key, currentLanguage);
        if (!translation.equals(key)) {
            return translation;
        }
        
        // 回退到英语
        if (!currentLanguage.equals("en_us")) {
            translation = getTranslationForLanguage(key, "en_us");
            if (!translation.equals(key)) {
                return translation;
            }
        }
        
        // 如果都没有找到，返回键本身
        return key;
    }
    
    /**
     * 获取指定语言的翻译文本
     * 
     * @param key 翻译键
     * @param language 语言代码
     * @return 翻译文本
     */
    public static String getTranslationForLanguage(String key, String language) {
        Map<String, String> languageTranslations = translationCache.get(language);
        if (languageTranslations == null) {
            return key;
        }
        
        return languageTranslations.getOrDefault(key, key);
    }
    
    /**
     * 检查翻译键是否存在
     * 
     * @param key 翻译键
     * @return 如果存在则返回 true
     */
    public static boolean hasTranslation(String key) {
        if (!initialized) {
            return false;
        }
        
        // 检查当前语言
        Map<String, String> languageTranslations = translationCache.get(currentLanguage);
        if (languageTranslations != null && languageTranslations.containsKey(key)) {
            return true;
        }
        
        // 检查英语
        if (!currentLanguage.equals("en_us")) {
            languageTranslations = translationCache.get("en_us");
            return languageTranslations != null && languageTranslations.containsKey(key);
        }
        
        return false;
    }
    
    /**
     * 获取当前语言
     * 
     * @return 当前语言代码
     */
    public static String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * 获取支持的语言列表
     * 
     * @return 支持的语言集合
     */
    public static Set<String> getSupportedLanguages() {
        return translationCache.keySet();
    }
    
    /**
     * 重新加载翻译
     */
    public static void reloadTranslations() {
        DirectorMod.LOGGER.info("[Director] 重新加载翻译...");
        
        // 清空缓存
        translationCache.clear();
        
        // 重新初始化
        initialized = false;
        initialize();
    }
    
    /**
     * 检测当前游戏语言
     * 
     * @return 语言代码
     */
    private static String detectCurrentLanguage() {
        try {
            // 尝试从Minecraft客户端获取语言设置
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getLanguageManager() != null) {
                String language = client.getLanguageManager().getLanguage();
                if (language != null && !language.isEmpty()) {
                    DirectorMod.LOGGER.info("[Director] 从Minecraft客户端检测到语言: {}", language);
                    return language;
                }
            }
        } catch (Exception e) {
            DirectorMod.LOGGER.debug("[Director] 无法从客户端获取语言设置: {}", e.getMessage());
        }
        
        // 回退到系统语言检测
        String systemLanguage = System.getProperty("user.language");
        String systemCountry = System.getProperty("user.country");
        
        DirectorMod.LOGGER.info("[Director] 系统语言: {}, 国家: {}", systemLanguage, systemCountry);
        
        if (systemLanguage != null) {
            if ("zh".equals(systemLanguage)) {
                DirectorMod.LOGGER.info("[Director] 检测到中文系统，使用中文语言包");
                return "zh_cn";
            } else if ("en".equals(systemLanguage)) {
                DirectorMod.LOGGER.info("[Director] 检测到英文系统，使用英文语言包");
                return "en_us";
            }
        }
        
        // 默认使用英文
        DirectorMod.LOGGER.info("[Director] 使用默认语言：英文");
        return "en_us";
    }
    
    /**
     * 加载指定语言的翻译
     * 
     * @param language 语言代码
     */
    private static void loadTranslations(String language) {
        try {
            // 首先尝试从资源包加载
            if (loadFromResourcePack(language)) {
                DirectorMod.LOGGER.debug("[Director] 从资源包加载翻译: {}", language);
                return;
            }
            
            // 然后尝试从本地文件加载
            if (loadFromLocalFile(language)) {
                DirectorMod.LOGGER.debug("[Director] 从本地文件加载翻译: {}", language);
                return;
            }
            
            DirectorMod.LOGGER.warn("[Director] 无法加载翻译文件: {}", language);
            
        } catch (Exception e) {
            DirectorMod.LOGGER.error("[Director] 加载翻译时发生错误: language={}, error={}", 
                language, e.getMessage(), e);
        }
    }
    
    /**
     * 从资源包加载翻译
     * 
     * @param language 语言代码
     * @return 是否加载成功
     */
    private static boolean loadFromResourcePack(String language) {
        try {
            String resourcePath = "assets/director/lang/" + language + ".json";
            
            // 尝试从类路径加载
            InputStream inputStream = TranslationLoader.class.getClassLoader()
                .getResourceAsStream(resourcePath);
            
            if (inputStream != null) {
                String content = new String(inputStream.readAllBytes());
                parseAndCacheTranslations(language, content);
                inputStream.close();
                return true;
            }
            
        } catch (Exception e) {
            DirectorMod.LOGGER.debug("[Director] 从资源包加载翻译失败: language={}, error={}", 
                language, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 从本地文件加载翻译
     * 
     * @param language 语言代码
     * @return 是否加载成功
     */
    private static boolean loadFromLocalFile(String language) {
        try {
            Path translationFile = Paths.get("config/director/lang/" + language + ".json");
            
            if (Files.exists(translationFile)) {
                String content = Files.readString(translationFile);
                parseAndCacheTranslations(language, content);
                return true;
            }
            
        } catch (Exception e) {
            DirectorMod.LOGGER.debug("[Director] 从本地文件加载翻译失败: language={}, error={}", 
                language, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 解析并缓存翻译内容
     * 
     * @param language 语言代码
     * @param content JSON 内容
     */
    private static void parseAndCacheTranslations(String language, String content) {
        try {
            JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
            Map<String, String> translations = new HashMap<>();
            
            for (String key : jsonObject.keySet()) {
                String value = jsonObject.get(key).getAsString();
                translations.put(key, value);
            }
            
            translationCache.put(language, translations);
            DirectorMod.LOGGER.debug("[Director] 缓存了 {} 个翻译: {}", translations.size(), language);
            
        } catch (Exception e) {
            DirectorMod.LOGGER.error("[Director] 解析翻译文件失败: language={}, error={}", 
                language, e.getMessage(), e);
        }
    }
    
    /**
     * 获取翻译统计信息
     * 
     * @return 统计信息字符串
     */
    public static String getStatistics() {
        if (!initialized) {
            return "翻译加载器未初始化";
        }
        
        StringBuilder stats = new StringBuilder();
        stats.append("当前语言: ").append(currentLanguage).append("\n");
        stats.append("支持的语言: ").append(translationCache.size()).append("\n");
        
        for (Map.Entry<String, Map<String, String>> entry : translationCache.entrySet()) {
            stats.append("  ").append(entry.getKey()).append(": ")
                 .append(entry.getValue().size()).append(" 个翻译\n");
        }
        
        return stats.toString();
    }
    
    /**
     * 检查是否已初始化
     * 
     * @return 如果已初始化则返回 true
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
