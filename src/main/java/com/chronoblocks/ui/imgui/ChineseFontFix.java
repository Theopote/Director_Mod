package com.chronoblocks.ui.imgui;

import imgui.ImFont;
import imgui.ImFontConfig;
import imgui.ImGuiIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 中文字体修复工具
 * 专门用于解决ImGui中文字符显示为问号的问题
 */
public class ChineseFontFix {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChineseFontFix.class);

    // 保存加载的中文字体引用
    private static ImFont chineseFont = null;

    /**
     * 强制加载中文字体
     */
    public static boolean forceLoadChineseFont(ImGuiIO io) {
        try {
            // 添加默认字体作为基础
            io.getFonts().addFontDefault();

            // 创建字体配置 - 使用合并模式
            ImFontConfig fontConfig = new ImFontConfig();
            fontConfig.setMergeMode(true);
            fontConfig.setPixelSnapH(true);
            fontConfig.setOversampleH(2);
            fontConfig.setOversampleV(2);

            // 使用Windows系统字体（微软雅黑）
            String systemFont = "C:/Windows/Fonts/msyh.ttc";

            try {
                if (new java.io.File(systemFont).exists()) {
                    // 手动指定字符范围
                    short[] glyphRanges = new short[] {
                            (short)0x0020, (short)0x00FF,  // 基本拉丁字符
                            (short)0x2000, (short)0x206F,  // 通用标点符号（包含•等）
                            (short)0x3000, (short)0x303F,  // CJK符号和标点
                            (short)0x4E00, (short)0x9FFF,  // CJK统一汉字
                            0,
                    };

                    chineseFont = io.getFonts().addFontFromFileTTF(
                            systemFont,
                            20.0f,
                            fontConfig,
                            glyphRanges
                    );

                    // 构建字体图集
                    boolean success = io.getFonts().build();
                    if (success) {
                        LOGGER.debug("[ChronoBlocks] 中文字体加载成功");
                        return true;
                    } else {
                        LOGGER.warn("[ChronoBlocks] 字体图集构建失败");
                    }
                } else {
                    LOGGER.warn("[ChronoBlocks] 系统字体不存在: {}", systemFont);
                }
            } catch (Exception e) {
                LOGGER.error("[ChronoBlocks] 加载系统字体失败", e);
            }

            // 如果失败，使用默认字体
            LOGGER.warn("[ChronoBlocks] 中文字体加载失败，使用默认字体");
            io.getFonts().addFontDefault();
            io.getFonts().build();
            return false;

        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 字体加载时发生错误", e);
            return false;
        }
    }
}