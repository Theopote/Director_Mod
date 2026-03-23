package com.director.ui.timeline.playback.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WeatherFogController v1.1
 * -----------------------------------------
 * ⚠️ 雾功能已禁用：由于雾效果无法实现理想效果，已注释掉所有雾相关代码
 * 
 * <p>原设计：负责指数雾、雾密度、雾颜色的控制
 * 
 * @author Director Team
 * @version 1.1 - 架构集成完成（已禁用）
 */
public class WeatherFogController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherFogController.class);
    
    private static final WeatherFogController INSTANCE = new WeatherFogController();
    
    public static WeatherFogController getInstance() {
        return INSTANCE;
    }
    
    private WeatherFogController() {}
    
    /**
     * ⚠️ 已禁用：应用天气采样到雾系统
     */
    public void apply(WeatherSample sample) {
        // 雾功能已禁用
        /*
        if (sample == null || !sample.enabled) {
            return;
        }
        */
    }
    
    /**
     * ⚠️ 已禁用：清除雾覆盖，恢复原版雾效果
     */
    public void clear() {
        // 雾功能已禁用
    }
}

