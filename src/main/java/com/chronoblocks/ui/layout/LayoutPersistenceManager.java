package com.chronoblocks.ui.layout;

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
import java.util.HashMap;
import java.util.Map;

/**
 * 布局持久化管理器
 * <p>
 * 负责保存和恢复 ImGui DockSpace 布局，包括：
 * - 面板的位置和大小
 * - 面板的可见性状态
 * - 停靠节点的配置
 * - 布局的版本管理
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class LayoutPersistenceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayoutPersistenceManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 布局配置文件路径
    private static final Path LAYOUT_DIR = Paths.get("config", "chronoblocks");
    private static final Path LAYOUT_FILE = LAYOUT_DIR.resolve("ui_layout.json");
    
    // 布局版本
    private static final String LAYOUT_VERSION = "1.0";
    
    // 单例实例
    private static LayoutPersistenceManager instance;
    
    // 当前布局数据
    private LayoutData currentLayout;
    
    // 布局变更检测
    private boolean layoutChanged = false;
    private long lastSaveTime = 0;
    private static final long AUTO_SAVE_INTERVAL = 5000; // 5秒自动保存
    
    /**
     * 布局数据结构
     */
    public static class LayoutData {
        public String version = LAYOUT_VERSION;
        public long timestamp = System.currentTimeMillis();
        public Map<String, PanelLayout> panels = new HashMap<>();
        public String dockspaceConfig = "";
        public boolean hasCustomLayout = false;
        
        public LayoutData() {}
        
        public boolean isValid() {
            return version != null && !version.isEmpty() && 
                   panels != null && 
                   LAYOUT_VERSION.equals(version);
        }
    }
    
    /**
     * 面板布局信息
     */
    public static class PanelLayout {
        public String panelName;
        public String windowName;
        public boolean visible = true;
        public boolean docked = true;
        public float posX = 0;
        public float posY = 0;
        public float sizeX = 0;
        public float sizeY = 0;
        public String dockNode = "";
        
        public PanelLayout() {}
        
        public PanelLayout(String panelName, String windowName) {
            this.panelName = panelName;
            this.windowName = windowName;
        }
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized LayoutPersistenceManager getInstance() {
        if (instance == null) {
            instance = new LayoutPersistenceManager();
        }
        return instance;
    }
    
    /**
     * 私有构造函数
     */
    private LayoutPersistenceManager() {
        initialize();
    }
    
    /**
     * 初始化布局管理器
     */
    private void initialize() {
        LOGGER.info("[LayoutPersistence] 初始化布局持久化管理器...");
        
        try {
            // 确保布局目录存在
            if (!Files.exists(LAYOUT_DIR)) {
                Files.createDirectories(LAYOUT_DIR);
                LOGGER.debug("[LayoutPersistence] 创建布局目录: {}", LAYOUT_DIR);
            }
            
            // 加载现有布局或创建默认布局
            loadLayout();
            
            LOGGER.info("[LayoutPersistence] 布局持久化管理器初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 布局管理器初始化失败: {}", e.getMessage(), e);
            // 创建默认布局
            currentLayout = new LayoutData();
        }
    }
    
    /**
     * 加载布局配置
     */
    public void loadLayout() {
        try {
            if (Files.exists(LAYOUT_FILE)) {
                String layoutContent = Files.readString(LAYOUT_FILE);
                JsonObject layoutJson = JsonParser.parseString(layoutContent).getAsJsonObject();
                
                // 检查布局版本
                String version = layoutJson.has("version") ? 
                    layoutJson.get("version").getAsString() : "unknown";
                
                if (!LAYOUT_VERSION.equals(version)) {
                    LOGGER.warn("[LayoutPersistence] 检测到旧版本布局文件 ({}), 将使用默认布局", version);
                    currentLayout = new LayoutData();
                } else {
                    currentLayout = GSON.fromJson(layoutJson, LayoutData.class);
                    if (currentLayout.isValid()) {
                        LOGGER.info("[LayoutPersistence] 成功加载布局配置: {}", LAYOUT_FILE);
                    } else {
                        LOGGER.warn("[LayoutPersistence] 布局配置无效，使用默认布局");
                        currentLayout = new LayoutData();
                    }
                }
            } else {
                LOGGER.info("[LayoutPersistence] 布局文件不存在，创建默认布局: {}", LAYOUT_FILE);
                currentLayout = new LayoutData();
            }
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 加载布局配置失败: {}", e.getMessage(), e);
            currentLayout = new LayoutData();
        }
    }
    
    /**
     * 保存布局配置
     */
    public void saveLayout() {
        if (currentLayout == null) {
            LOGGER.warn("[LayoutPersistence] 当前布局为空，跳过保存");
            return;
        }
        
        try {
            currentLayout.timestamp = System.currentTimeMillis();
            String layoutJson = GSON.toJson(currentLayout);
            Files.writeString(LAYOUT_FILE, layoutJson);
            LOGGER.debug("[LayoutPersistence] 布局配置已保存: {}", LAYOUT_FILE);
            layoutChanged = false;
            lastSaveTime = System.currentTimeMillis();
        } catch (IOException e) {
            LOGGER.error("[LayoutPersistence] 保存布局配置失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 自动保存布局（如果已更改）
     */
    public void autoSaveIfChanged() {
        if (layoutChanged && (System.currentTimeMillis() - lastSaveTime) > AUTO_SAVE_INTERVAL) {
            saveLayout();
        }
    }
    
    /**
     * 标记布局已更改
     */
    public void markLayoutChanged() {
        layoutChanged = true;
    }
    
    /**
     * 获取面板布局信息
     */
    public PanelLayout getPanelLayout(String panelName) {
        if (currentLayout == null || currentLayout.panels == null) {
            return null;
        }
        return currentLayout.panels.get(panelName);
    }
    
    /**
     * 设置面板布局信息
     */
    public void setPanelLayout(String panelName, PanelLayout layout) {
        if (currentLayout == null) {
            currentLayout = new LayoutData();
        }
        if (currentLayout.panels == null) {
            currentLayout.panels = new HashMap<>();
        }
        currentLayout.panels.put(panelName, layout);
        markLayoutChanged();
    }
    
    /**
     * 检查是否有自定义布局
     */
    public boolean hasCustomLayout() {
        return currentLayout != null && currentLayout.hasCustomLayout;
    }
    
    /**
     * 设置自定义布局标志
     */
    public void setCustomLayout(boolean hasCustom) {
        if (currentLayout != null) {
            currentLayout.hasCustomLayout = hasCustom;
            markLayoutChanged();
        }
    }
    
    /**
     * 重置为默认布局
     */
    public void resetToDefault() {
        LOGGER.info("[LayoutPersistence] 重置为默认布局");
        currentLayout = new LayoutData();
        markLayoutChanged();
        saveLayout();
    }
    
    /**
     * 获取当前布局数据
     */
    public LayoutData getCurrentLayout() {
        return currentLayout;
    }
    
    /**
     * 设置DockSpace配置
     */
    public void setDockspaceConfig(String config) {
        if (currentLayout != null) {
            currentLayout.dockspaceConfig = config;
            markLayoutChanged();
        }
    }
    
    /**
     * 获取DockSpace配置
     */
    public String getDockspaceConfig() {
        return currentLayout != null ? currentLayout.dockspaceConfig : "";
    }
    
    /**
     * 清理资源
     */
    public void dispose() {
        if (layoutChanged) {
            saveLayout();
        }
        LOGGER.info("[LayoutPersistence] 布局持久化管理器已释放");
    }
}
