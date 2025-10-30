package com.chronoblocks.ui.imgui;

import com.chronoblocks.ui.theme.UITheme;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGuiIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * ChronoBlocks ImGui 字体管理器
 * 
 * 负责加载和管理 ImGui 使用的所有字体，包括：
 * - 中文字体（主字体）- Noto Sans SC
 * - 图标字体（合并模式）- IcoMoon 自定义图标
 * 
 * 解决方案说明：
 * 1. 首先加载中文字体作为基础字体
 * 2. 使用合并模式（MergeMode）加载自定义图标字体，将图标字形添加到字体图集中
 * 3. 完全放弃 Emoji 字体，使用专业的自定义图标
 * 
 * @version 2.0 (移除 Emoji，使用 IcoMoon 图标字体)
 * @since 2025-10-18
 */
public class ImGuiFontManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChronoBlocks-FontManager");
    
    // 字体大小配置（从UITheme获取，保持一致性）
    private static final float DEFAULT_FONT_SIZE = UITheme.FontSize.DEFAULT;  // 18px字体，适配24px按钮
    private static final float ICON_FONT_SIZE = UITheme.FontSize.ICON;        // 图标字体大小（与主字体相同）
    
    // 字体资源路径
    private static final String FONT_PATH_PREFIX = "/assets/chronoblocks/fonts/";
    private static final String CHINESE_FONT = "NotoSansSC-Regular.ttf";
    private static final String ICON_FONT = "icomoon.ttf";  // 自定义图标字体（替代 Emoji）
    
    /**
     * 初始化所有字体
     * 
     * @param io ImGuiIO 实例
     */
    public static void initializeFonts(ImGuiIO io) {
        try {
            final ImFontAtlas fontAtlas = io.getFonts();
            
            // 第一步：加载中文字体作为主字体
            boolean chineseFontLoaded = loadChineseFont(fontAtlas);
            
            if (!chineseFontLoaded) {
                LOGGER.warn("[ChronoBlocks] 中文字体加载失败，使用 ImGui 默认字体");
                io.getFonts().addFontDefault();
            }
            
            // 第二步：以合并模式加载自定义图标字体（替代 Emoji）
            boolean iconFontLoaded = loadIconFont(fontAtlas);
            
            if (!iconFontLoaded) {
                LOGGER.error("[ChronoBlocks] ❌ 图标字体加载失败！自定义图标将无法显示");
                LOGGER.error("[ChronoBlocks] 请确认 icomoon.ttf 文件位于: {}", FONT_PATH_PREFIX + ICON_FONT);
            } else {
                LOGGER.info("[ChronoBlocks] ✅ 图标字体加载成功！自定义图标系统已启用");
            }
            
            // 第三步：构建字体图集
            if (!io.getFonts().isBuilt()) {
                io.getFonts().build();
                LOGGER.info("[ChronoBlocks] 字体图集构建完成");
            }
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 字体初始化过程中发生错误", e);
            // 降级方案：使用默认字体
            io.getFonts().addFontDefault();
            io.getFonts().build();
        }
    }
    
    /**
     * 加载中文字体（主字体）
     * 
     * @param fontAtlas 字体图集
     * @return 是否加载成功
     */
    private static boolean loadChineseFont(ImFontAtlas fontAtlas) {
        final ImFontConfig fontConfig = new ImFontConfig();
        
        // 中文字符范围
        final short[] chineseGlyphRanges = new short[]{
            (short) 0x0020, (short) 0x00FF,  // 基本拉丁字符 (ASCII)
            (short) 0x2000, (short) 0x206F,  // 常规标点符号
            (short) 0x3000, (short) 0x303F,  // CJK 符号和标点
            (short) 0x4E00, (short) 0x9FFF,  // CJK 统一汉字（常用汉字）
            (short) 0xFF00, (short) 0xFFEF,  // 全角 ASCII、全角标点
            0,  // 结束标记
        };
        
        return loadFontFromResources(
            fontAtlas, 
            FONT_PATH_PREFIX + CHINESE_FONT, 
            DEFAULT_FONT_SIZE, 
            fontConfig, 
            chineseGlyphRanges,
            "中文字体"
        );
    }
    
    // ==================== Emoji 字体已移除 ====================
    // 
    // 我们现在完全使用自定义的 IcoMoon 图标字体
    // 不再加载 Emoji 字体，原因：
    // 1. 自定义图标更专业、更统一
    // 2. 减少字体图集大小（~10MB Emoji → ~100KB 图标）
    // 3. 跨平台一致性更好
    // 4. 性能更佳
    //
    // 如需 Emoji 支持，可以取消注释下方的 loadEmojiFont() 方法
    
    /**
     * 加载自定义图标字体（合并模式）
     * 
     * <p>IcoMoon 生成的图标字体，包含 100+ 个专业图标
     * 
     * @param fontAtlas 字体图集
     * @return 是否加载成功
     */
    private static boolean loadIconFont(ImFontAtlas fontAtlas) {
        final ImFontConfig iconConfig = new ImFontConfig();
        
        // 关键：设置为合并模式，将图标字形合并到中文字体中
        iconConfig.setMergeMode(true);
        iconConfig.setPixelSnapH(true);  // 像素对齐，确保清晰显示
        
        // 图标字体范围 - IcoMoon 实际使用范围
        // 从 style.css 可以看到: U+E900 - U+E9A2 (共 161 个图标)
        // 精确指定范围以优化性能和内存使用
        final short[] iconGlyphRanges = new short[]{
            (short) 0xE900,  // 图标起始码位
            (short) 0xE9A2,  // 图标结束码位（zoom-out 是最后一个）
            0,               // 结束标记
        };
        
        boolean result = loadFontFromResources(
            fontAtlas, 
            FONT_PATH_PREFIX + ICON_FONT, 
            ICON_FONT_SIZE,  // 使用与主字体相同的大小
            iconConfig, 
            iconGlyphRanges,
            "IcoMoon 图标字体"
        );
        
        if (result) {
            LOGGER.info("[ChronoBlocks] ✅ IcoMoon 图标字体加载成功!");
            LOGGER.info("[ChronoBlocks] 图标范围: U+E900 - U+E9A2 (共 161 个图标)");
            LOGGER.info("[ChronoBlocks] 已完全替代 Emoji 字体，使用专业自定义图标");
        } else {
            LOGGER.error("[ChronoBlocks] ❌ 图标字体加载失败!");
            LOGGER.error("[ChronoBlocks] 请确认 icomoon.ttf 文件存在于: {}", FONT_PATH_PREFIX + ICON_FONT);
            LOGGER.error("[ChronoBlocks] UI 图标将无法正常显示");
        }
        
        return result;
    }
    
    // ==================== Emoji 相关方法已移除 ====================
    // 
    // addEmojiRange() 方法已不再需要
    // 我们现在使用简单的 short[] 范围来加载图标字体
    
    /**
     * 从资源文件加载字体的通用方法
     * 
     * @param fontAtlas 字体图集
     * @param resourcePath 字体资源路径
     * @param fontSize 字体大小
     * @param fontConfig 字体配置
     * @param glyphRanges 字符范围
     * @param fontName 字体名称（用于日志）
     * @return 是否加载成功
     */
    private static boolean loadFontFromResources(
            ImFontAtlas fontAtlas, 
            String resourcePath, 
            float fontSize, 
            ImFontConfig fontConfig, 
            short[] glyphRanges,
            String fontName) {
        
        try (InputStream fontStream = ImGuiFontManager.class.getResourceAsStream(resourcePath)) {
            if (fontStream == null) {
                LOGGER.error("[ChronoBlocks] 找不到字体资源: {} ({})", resourcePath, fontName);
                return false;
            }
            
            byte[] fontBytes = fontStream.readAllBytes();
            
            if (fontBytes.length == 0) {
                LOGGER.error("[ChronoBlocks] 字体文件为空: {} ({})", resourcePath, fontName);
                return false;
            }
            
            // 使用 ImGui 的内存加载方式
            fontAtlas.addFontFromMemoryTTF(fontBytes, fontSize, fontConfig, glyphRanges);
            
            LOGGER.info("[ChronoBlocks] 成功加载字体: {} ({}) - {} 字节", 
                fontName, resourcePath, fontBytes.length);
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 加载字体失败: {} ({})", resourcePath, fontName, e);
            return false;
        }
    }
    
    /**
     * 获取默认字体大小
     * @deprecated 建议直接使用 UITheme.FontSize.DEFAULT
     */
    @Deprecated
    public static float getDefaultFontSize() {
        return UITheme.FontSize.DEFAULT;
    }
    
    /**
     * 获取图标字体大小
     * @deprecated 建议直接使用 UITheme.FontSize.ICON
     */
    @Deprecated
    public static float getIconFontSize() {
        return UITheme.FontSize.ICON;
    }
}

