package com.director.ui.layout;

import com.director.util.WorldIdentifier;
import imgui.ImGui;
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
 * @author Director Team
 * @version 1.0
 */
public class LayoutPersistenceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LayoutPersistenceManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // 布局配置文件路径
    private static final Path LAYOUT_DIR = Paths.get("config", "director");
    
    // 布局版本
    private static final String LAYOUT_VERSION = "1.0";
    
    // 单例实例
    private static LayoutPersistenceManager instance;
    
    // 当前布局数据
    private LayoutData currentLayout;
    
    // 当前世界标识
    private String currentWorldId = null;
    
    // 布局变更检测
    private boolean layoutChanged = false;

    /**
     * 布局数据结构
     */
    public static class LayoutData {
        public String version = LAYOUT_VERSION;
        public long timestamp = System.currentTimeMillis();
        public Map<String, PanelLayout> panels = new HashMap<>();
        public String worldFingerprint;

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
            
            // 检测当前世界并加载对应布局
            checkAndLoadWorldLayout();
            
            LOGGER.info("[LayoutPersistence] 布局持久化管理器初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 布局管理器初始化失败: {}", e.getMessage(), e);
            // 创建默认布局
            currentLayout = new LayoutData();
        }
    }
    
    /**
     * 检查并加载当前世界的布局
     */
    private void checkAndLoadWorldLayout() {
        reloadForCurrentWorld("initialize");
    }

    /**
     * 获取 layout 子目录（确保存在）
     */
    private Path getLayoutsDir() {
        Path layoutsDir = LAYOUT_DIR.resolve("layouts");
        try {
            if (!Files.exists(layoutsDir)) {
                Files.createDirectories(layoutsDir);
                LOGGER.debug("[LayoutPersistence] 创建布局子目录: {}", layoutsDir);
            }
        } catch (IOException e) {
            LOGGER.warn("[LayoutPersistence] 无法创建布局子目录: {}", e.getMessage(), e);
        }
        return layoutsDir;
    }
    
    private String resolveWorldId(String suggestFallback) {
        if (suggestFallback != null && !suggestFallback.isEmpty()) {
            return suggestFallback;
        }
        String detected = WorldIdentifier.getCurrentWorldId();
        return detected != null ? detected : "default";
    }

    private String getWorldFingerprint() {
        try {
            java.nio.file.Path path = com.director.util.WorldIdentifier.getWorldSavePath();
            if (path != null && Files.exists(path)) {
                long modified = Files.getLastModifiedTime(path).toMillis();
                return path.toAbsolutePath() + "#" + modified;
            }
        } catch (Exception e) {
            LOGGER.warn("[LayoutPersistence] 获取世界指纹失败: {}", e.getMessage());
        }
        return resolveWorldId(currentWorldId);
    }
    
    /**
     * 获取指定世界的布局文件路径
     */
    private Path getLayoutFile(String worldId) {
        return getLayoutsDir().resolve(worldId + "_ui_layout.json");
    }
    
    /**
     * 获取当前世界的布局文件路径（公共方法）
     */
    public Path getLayoutFile() {
        return getLayoutFile(resolveWorldId(currentWorldId));
    }
    
    /**
     * 获取ImGui INI文件路径（按世界区分）
     */
    public Path getImGuiIniPath() {
        return getImGuiIniPath(resolveWorldId(currentWorldId));
    }
    
    public Path getImGuiIniPath(String worldId) {
        Path iniFile = getLayoutsDir().resolve(worldId + "_imgui.ini");
        Path parent = iniFile.getParent();
        try {
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            LOGGER.warn("[LayoutPersistence] 无法创建ImGui配置目录: {}", e.getMessage(), e);
        }
        return iniFile;
    }
    
    /**
     * 手动同步当前世界的布局
     * @param reason 触发原因
     */
    public void reloadForCurrentWorld(String reason) {
        String nextWorldId = resolveWorldId(WorldIdentifier.getCurrentWorldId());
        if (currentWorldId == null) {
            currentWorldId = nextWorldId;
            LOGGER.info("[LayoutPersistence] 初始化世界布局: {} (触发: {})", currentWorldId, reason);
            loadLayoutInternal();
            loadImGuiIniIfExists(currentWorldId);
            return;
        }
        
        if (!currentWorldId.equals(nextWorldId)) {
            LOGGER.info("[LayoutPersistence] 检测到世界切换: {} -> {} (触发: {})", currentWorldId, nextWorldId, reason);
            saveLayoutForWorld(currentWorldId);
            saveImGuiIni(currentWorldId);
            currentWorldId = nextWorldId;
            loadLayoutInternal();
            loadImGuiIniIfExists(currentWorldId);
        } else {
            LOGGER.debug("[LayoutPersistence] 世界未变化 ({})，保持当前布局", currentWorldId);
        }
    }
    
    /**
     * 强制重新加载当前世界的布局（即使世界ID未变化）
     * 用于恢复默认布局、载入预设等场景
     */
    public void forceReloadLayout() {
        String worldId = resolveWorldId(currentWorldId);
        LOGGER.info("[LayoutPersistence] 强制重新加载布局 (世界: {})", worldId);
        loadLayoutInternal();
        loadImGuiIniIfExists(worldId);
    }

    private void loadLayoutInternal() {
        String worldId = resolveWorldId(currentWorldId);
        currentWorldId = worldId;
        try {
            Path layoutFile = getLayoutFile(worldId);
            
            // 确保布局目录存在
            if (!Files.exists(layoutFile.getParent())) {
                Files.createDirectories(layoutFile.getParent());
            }
            
            if (Files.exists(layoutFile)) {
                String layoutContent = Files.readString(layoutFile);
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
                        LOGGER.info("[LayoutPersistence] 成功加载世界 {} 的布局配置: {}", 
                            worldId, layoutFile);
                    } else {
                        LOGGER.warn("[LayoutPersistence] 布局配置无效，使用默认布局");
                        currentLayout = new LayoutData();
                    }
                }
            } else {
                LOGGER.info("[LayoutPersistence] 世界 {} 的布局文件不存在，创建默认布局: {}", 
                    worldId, layoutFile);
                currentLayout = new LayoutData();
            }
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 加载布局配置失败: {}", e.getMessage(), e);
            currentLayout = new LayoutData();
        }
    }
    
    /**
     * 保存布局配置（按世界）
     */
    public void saveLayout() {
        saveLayoutForWorld(resolveWorldId(currentWorldId));
    }
    
    private void saveLayoutForWorld(String worldId) {
        if (currentLayout == null) {
            LOGGER.warn("[LayoutPersistence] 当前布局为空，跳过保存");
            return;
        }
        
        try {
            Path layoutFile = getLayoutFile(worldId);
            
            if (!Files.exists(layoutFile.getParent())) {
                Files.createDirectories(layoutFile.getParent());
            }
            
            currentLayout.timestamp = System.currentTimeMillis();
            currentLayout.worldFingerprint = getWorldFingerprint();
            String layoutJson = GSON.toJson(currentLayout);
            Files.writeString(layoutFile, layoutJson);
            LOGGER.debug("[LayoutPersistence] 世界 {} 的布局配置已保存: {}", 
                worldId, layoutFile);
            layoutChanged = false;
        } catch (IOException e) {
            LOGGER.error("[LayoutPersistence] 保存布局配置失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 保存 ImGui ini 布局
     */
    public void saveImGuiIni() {
        saveImGuiIni(resolveWorldId(currentWorldId));
    }
    
    private void saveImGuiIni(String worldId) {
        try {
            Path iniPath = getImGuiIniPath(worldId);
            String iniPathStr = iniPath.toString().replace('\\', '/');
            ImGui.saveIniSettingsToDisk(iniPathStr);
            LOGGER.info("[LayoutPersistence] ImGui 布局已保存: {}", iniPathStr);
        } catch (Exception e) {
            LOGGER.warn("[LayoutPersistence] 保存 ImGui 布局失败: {}", e.getMessage(), e);
        }
    }

    private void loadImGuiIniIfExists(String worldId) {
        try {
            Path iniPath = getImGuiIniPath(worldId);
            if (Files.exists(iniPath)) {
                String iniPathStr = iniPath.toString().replace('\\', '/');
                ImGui.loadIniSettingsFromDisk(iniPathStr);
                LOGGER.info("[LayoutPersistence] 已加载世界 {} 的 ImGui 布局: {}", worldId, iniPathStr);
            } else {
                LOGGER.info("[LayoutPersistence] 世界 {} 尚无 ImGui 布局文件: {}", worldId, iniPath);
            }
        } catch (Exception e) {
            LOGGER.warn("[LayoutPersistence] 加载 ImGui 布局失败: {}", e.getMessage(), e);
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
     * 更新面板可见性状态
     */
    public void updatePanelVisibility(String panelName, boolean visible) {
        if (panelName == null || panelName.isEmpty()) {
            return;
        }
        if (currentLayout == null) {
            currentLayout = new LayoutData();
        }
        if (currentLayout.panels == null) {
            currentLayout.panels = new HashMap<>();
        }
        PanelLayout layout = currentLayout.panels.computeIfAbsent(panelName, n -> new PanelLayout(n, n));
        if (layout.visible != visible) {
            layout.visible = visible;
            markLayoutChanged();
        }
    }
    
    /**
     * 获取存储的面板可见性
     */
    public Boolean getStoredPanelVisibility(String panelName) {
        PanelLayout layout = getPanelLayout(panelName);
        return layout != null ? layout.visible : null;
    }

    /**
     * 重置为默认布局（当前世界）
     */
    public void resetToDefault() {
        LOGGER.info("[LayoutPersistence] 重置世界 {} 为默认布局", WorldIdentifier.getCurrentWorldId());
        currentLayout = new LayoutData();
        markLayoutChanged();
        saveLayout();
    }
    
    /**
     * 仅重置内存中的布局数据为默认值（不保存到文件）
     * 用于恢复默认布局场景，在删除文件后重置内存状态
     */
    public void resetLayoutDataToDefault() {
        LOGGER.info("[LayoutPersistence] 重置内存中的布局数据为默认值（不保存）");
        currentLayout = new LayoutData();
        // 不标记为已更改，也不保存
    }

    /**
     * 保存当前布局为预设
     * 
     * @param presetName 预设名称
     * @return 是否保存成功
     */
    public boolean saveLayoutPreset(String presetName) {
        if (presetName == null || presetName.trim().isEmpty()) {
            LOGGER.warn("[LayoutPersistence] 预设名称不能为空");
            return false;
        }
        
        try {
            // 确保预设目录存在
            Path presetsDir = LAYOUT_DIR.resolve("presets");
            if (!Files.exists(presetsDir)) {
                Files.createDirectories(presetsDir);
                LOGGER.debug("[LayoutPersistence] 创建预设目录: {}", presetsDir);
            }
            
            // 保存当前布局数据
            LayoutData presetLayout = new LayoutData();
            presetLayout.version = LAYOUT_VERSION;
            presetLayout.timestamp = System.currentTimeMillis();
            presetLayout.worldFingerprint = getWorldFingerprint();
            if (currentLayout != null && currentLayout.panels != null) {
                presetLayout.panels = new HashMap<>(currentLayout.panels);
            } else {
                presetLayout.panels = new HashMap<>();
            }
            
            // 保存为 JSON 文件
            Path presetFile = presetsDir.resolve(sanitizePresetName(presetName) + ".json");
            String layoutJson = GSON.toJson(presetLayout);
            Files.writeString(presetFile, layoutJson);
            
            LOGGER.info("[LayoutPersistence] 成功保存布局预设: {} -> {}", presetName, presetFile);
            return true;
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 保存布局预设失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 加载布局预设
     * 
     * @param presetName 预设名称
     */
    public boolean loadLayoutPreset(String presetName) {
        try {
            Path presetFile = LAYOUT_DIR.resolve("presets").resolve(sanitizePresetName(presetName) + ".json");
            
            if (!Files.exists(presetFile)) {
                LOGGER.warn("[LayoutPersistence] 布局预设不存在: {}", presetFile);
                return false;
            }
            
            String layoutContent = Files.readString(presetFile);
            JsonObject layoutJson = JsonParser.parseString(layoutContent).getAsJsonObject();
            
            currentLayout = GSON.fromJson(layoutJson, LayoutData.class);
            if (currentLayout.isValid()) {
                LOGGER.info("[LayoutPersistence] 成功加载布局预设: {}", presetName);
                markLayoutChanged();
                saveLayout(); // 保存到当前世界的布局文件
                saveImGuiIni(); // 保存当前 ImGui 状态到 INI 文件
                return true;
            } else {
                LOGGER.warn("[LayoutPersistence] 布局预设无效");
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 加载布局预设失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 列出所有可用的布局预设
     * 
     * @return 预设名称列表（不包含文件扩展名）
     */
    public java.util.List<String> listLayoutPresets() {
        java.util.List<String> presets = new java.util.ArrayList<>();
        try {
            Path presetsDir = LAYOUT_DIR.resolve("presets");
            if (Files.exists(presetsDir)) {
                try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(presetsDir)) {
                    stream.filter(Files::isRegularFile)
                          .filter(p -> p.toString().endsWith(".json"))
                          .map(p -> {
                              String fileName = p.getFileName().toString();
                              return fileName.substring(0, fileName.length() - 5); // 移除 .json
                          })
                          .sorted()
                          .forEach(presets::add);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 列出布局预设失败: {}", e.getMessage(), e);
        }
        return presets;
    }
    
    /**
     * 删除布局预设
     * 
     * @param presetName 预设名称
     * @return 是否删除成功
     */
    public boolean deleteLayoutPreset(String presetName) {
        try {
            Path presetFile = LAYOUT_DIR.resolve("presets").resolve(sanitizePresetName(presetName) + ".json");
            if (Files.exists(presetFile)) {
                Files.delete(presetFile);
                LOGGER.info("[LayoutPersistence] 已删除布局预设: {}", presetName);
                return true;
            } else {
                LOGGER.warn("[LayoutPersistence] 布局预设不存在: {}", presetFile);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 删除布局预设失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 清理预设名称，使其适合作为文件名
     */
    private String sanitizePresetName(String name) {
        if (name == null || name.isEmpty()) {
            return "default";
        }
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
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
