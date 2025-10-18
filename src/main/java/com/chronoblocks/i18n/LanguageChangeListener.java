package com.chronoblocks.i18n;

import com.chronoblocks.ChronoBlocksMod;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * 语言切换监听器
 * <p>
 * 监听Minecraft的资源重载事件，当玩家在设置中切换语言时自动重新加载翻译。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class LanguageChangeListener implements SimpleSynchronousResourceReloadListener {
    
    /** 监听器标识符 */
    private static final Identifier ID = Identifier.of("chronoblocks", "language_change");
    
    /** 上一次的语言代码 */
    private String lastLanguage = "";
    
    @Override
    public Identifier getFabricId() {
        return ID;
    }
    
    @Override
    public void reload(ResourceManager manager) {
        try {
            // 获取当前Minecraft语言设置
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getLanguageManager() == null) {
                return;
            }
            
            String currentLanguage = client.getLanguageManager().getLanguage();
            
            // 检查语言是否改变
            if (!currentLanguage.equals(lastLanguage)) {
                ChronoBlocksMod.LOGGER.info("[ChronoBlocks] 检测到语言切换: {} -> {}", 
                    lastLanguage.isEmpty() ? "初始化" : lastLanguage, currentLanguage);
                
                lastLanguage = currentLanguage;
                
                // 重新加载翻译
                TranslationLoader.reloadLanguage();
                
                ChronoBlocksMod.LOGGER.info("[ChronoBlocks] 语言切换完成，当前语言: {}", currentLanguage);
            }
            
        } catch (Exception e) {
            ChronoBlocksMod.LOGGER.error("[ChronoBlocks] 处理语言切换时发生错误", e);
        }
    }
}

