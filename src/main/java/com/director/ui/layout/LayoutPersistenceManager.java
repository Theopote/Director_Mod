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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    
    // 布局根目录（预设等）；世界布局优先写入世界存档目录
    private static final Path LAYOUT_DIR = Paths.get("config", "director");
    
    private static final String LAYOUT_VERSION = "1.1";
    private static final Set<String> SUPPORTED_LAYOUT_VERSIONS = Set.of("1.0", LAYOUT_VERSION);
    private static final String DEFAULT_LAYOUT_DIR = "layouts";
    private static final String PRESETS_DIR = "presets";

    private static LayoutPersistenceManager instance;

    private final Path presetsDir;
    private final Supplier<String> iniCapture;
    private final Consumer<String> iniRestore;
    private final Map<String, Path> worldPaths = new HashMap<>();

    private LayoutData currentLayout;
    private String currentWorldId;
    private Path currentLayoutDir;
    private boolean layoutChanged;

    /**
     * 布局数据结构
     */
    public static class LayoutData {
        public String version = LAYOUT_VERSION;
        public long timestamp = System.currentTimeMillis();
        public Map<String, PanelLayout> panels = new HashMap<>();
        public String worldFingerprint;
        public String displayName;
        public String imguiIni;

        public LayoutData() {}

        public boolean isValid() {
            return version != null && SUPPORTED_LAYOUT_VERSIONS.contains(version) && panels != null;
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
        this(LAYOUT_DIR.resolve(PRESETS_DIR), ImGui::saveIniSettingsToMemory, ImGui::loadIniSettingsFromMemory);
        initialize();
    }

    LayoutPersistenceManager(Path presetsDir, Supplier<String> iniCapture, Consumer<String> iniRestore) {
        this.presetsDir = presetsDir.toAbsolutePath().normalize();
        this.iniCapture = iniCapture;
        this.iniRestore = iniRestore;
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
    private Path getLayoutsDir(Path worldSavePath) {
        Path layoutsDir = worldSavePath == null
                ? LAYOUT_DIR.resolve(DEFAULT_LAYOUT_DIR)
                : worldSavePath.resolve("director").resolve(DEFAULT_LAYOUT_DIR);
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

    private Path normalizeWorldPath(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }
    
    private String resolveWorldId(String suggestFallback) {
        if (suggestFallback != null && !suggestFallback.isEmpty()) {
            return suggestFallback;
        }
        String detected = WorldIdentifier.getCurrentWorldId();
        return detected != null ? detected : "default";
    }

    private String getWorldFingerprint(Path worldSavePath, String worldId) {
        try {
            if (worldSavePath != null && Files.exists(worldSavePath)) {
                long modified = Files.getLastModifiedTime(worldSavePath).toMillis();
                return worldSavePath.toAbsolutePath() + "#" + modified;
            }
        } catch (Exception e) {
            LOGGER.warn("[LayoutPersistence] 获取世界指纹失败: {}", e.getMessage());
        }
        return worldId == null ? "default" : worldId;
    }

    private Path getLayoutFile(String worldId) {
        Path worldPath = worldPaths.get(worldId);
        Path layoutsDir = worldId.equals(currentWorldId) && currentLayoutDir != null
                ? currentLayoutDir
                : getLayoutsDir(worldPath);
        return layoutsDir.resolve("ui_layout.json");
    }

    public Path getLayoutFile() {
        return getLayoutFile(resolveWorldId(currentWorldId));
    }

    public Path getImGuiIniPath() {
        return getImGuiIniPath(resolveWorldId(currentWorldId));
    }

    public Path getImGuiIniPath(String worldId) {
        Path worldPath = worldPaths.get(worldId);
        Path layoutsDir = worldId.equals(currentWorldId) && currentLayoutDir != null
                ? currentLayoutDir
                : getLayoutsDir(worldPath);
        return layoutsDir.resolve("imgui.ini");
    }
    
    /**
     * 手动同步当前世界的布局
     * @param reason 触发原因
     */
    public void reloadForCurrentWorld(String reason) {
        String nextWorldId = resolveWorldId(WorldIdentifier.getCurrentWorldId());
        Path nextWorldPath = normalizeWorldPath(WorldIdentifier.getWorldSavePath());
        if (currentWorldId == null) {
            activateWorld(nextWorldId, nextWorldPath);
            LOGGER.info("[LayoutPersistence] 初始化世界布局: {} (触发: {})", currentWorldId, reason);
            loadLayoutInternal();
            loadImGuiIniIfExists(currentWorldId);
            return;
        }

        if (!currentWorldId.equals(nextWorldId) || !java.util.Objects.equals(currentLayoutDir, getLayoutsDir(nextWorldPath))) {
            Path previousLayoutDir = currentLayoutDir;
            String previousWorldId = currentWorldId;
            switchWorld(nextWorldId, nextWorldPath, reason);
            saveImGuiIni(previousLayoutDir, previousWorldId);
            loadImGuiIniIfExists(currentWorldId);
        } else {
            LOGGER.debug("[LayoutPersistence] 世界未变化 ({})，保持当前布局", currentWorldId);
        }
    }

    void reloadForWorld(String worldId, Path worldSavePath, String reason) {
        String nextWorldId = resolveWorldId(worldId);
        Path nextWorldPath = normalizeWorldPath(worldSavePath);
        if (currentWorldId == null) {
            activateWorld(nextWorldId, nextWorldPath);
            loadLayoutInternal();
        } else if (!currentWorldId.equals(nextWorldId)
                || !java.util.Objects.equals(currentLayoutDir, getLayoutsDir(nextWorldPath))) {
            switchWorld(nextWorldId, nextWorldPath, reason);
        }
    }

    private void activateWorld(String worldId, Path worldSavePath) {
        currentWorldId = worldId;
        currentLayoutDir = getLayoutsDir(worldSavePath);
        worldPaths.put(worldId, worldSavePath);
    }

    private void switchWorld(String nextWorldId, Path nextWorldPath, String reason) {
        LOGGER.info("[LayoutPersistence] 检测到世界切换: {} -> {} (触发: {})", currentWorldId, nextWorldId, reason);
        saveLayoutForWorld(currentWorldId, currentLayoutDir, getWorldFingerprint(worldPaths.get(currentWorldId), currentWorldId));
        activateWorld(nextWorldId, nextWorldPath);
        loadLayoutInternal();
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
                
                if (!SUPPORTED_LAYOUT_VERSIONS.contains(version)) {
                    LOGGER.warn("[LayoutPersistence] 检测到不支持的布局文件版本 ({}), 将使用默认布局", version);
                    currentLayout = new LayoutData();
                } else {
                    currentLayout = GSON.fromJson(layoutJson, LayoutData.class);
                    if (currentLayout.isValid()) {
                        currentLayout.version = LAYOUT_VERSION;
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
        saveLayoutForWorld(worldId, currentLayoutDir, getWorldFingerprint(worldPaths.get(currentWorldId), currentWorldId));
    }

    private void saveLayoutForWorld(String worldId, Path layoutsDir, String worldFingerprint) {
        if (currentLayout == null) {
            LOGGER.warn("[LayoutPersistence] 当前布局为空，跳过保存");
            return;
        }

        try {
            Path layoutFile = layoutsDir.resolve("ui_layout.json");
            if (!Files.exists(layoutFile.getParent())) {
                Files.createDirectories(layoutFile.getParent());
            }

            currentLayout.timestamp = System.currentTimeMillis();
            currentLayout.worldFingerprint = worldFingerprint;
            currentLayout.version = LAYOUT_VERSION;
            Files.writeString(layoutFile, GSON.toJson(currentLayout));
            LOGGER.debug("[LayoutPersistence] 世界 {} 的布局配置已保存: {}", worldId, layoutFile);
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
        saveImGuiIni(getImGuiIniPath(worldId), worldId);
    }

    private void saveImGuiIni(Path iniPath, String worldId) {
        try {
            Files.createDirectories(iniPath.getParent());
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

        String displayName = presetName.trim();
        try {
            Files.createDirectories(presetsDir);

            LayoutData presetLayout = new LayoutData();
            presetLayout.version = LAYOUT_VERSION;
            presetLayout.timestamp = System.currentTimeMillis();
            presetLayout.worldFingerprint = getWorldFingerprint(worldPaths.get(currentWorldId), currentWorldId);
            presetLayout.displayName = displayName;
            if (currentLayout != null && currentLayout.panels != null) {
                for (Map.Entry<String, PanelLayout> entry : currentLayout.panels.entrySet()) {
                    PanelLayout source = entry.getValue();
                    PanelLayout copy = new PanelLayout(source.panelName, source.windowName);
                    copy.visible = source.visible;
                    presetLayout.panels.put(entry.getKey(), copy);
                }
            }
            String presetIni = iniCapture.get();
            if (presetIni == null || presetIni.isBlank()) {
                throw new IllegalStateException("ImGui 几何信息为空，无法保存完整布局预设");
            }
            presetLayout.imguiIni = presetIni;

            Path presetFile = findPresetFile(displayName);
            if (presetFile == null) {
                presetFile = availablePresetFile(displayName);
            }
            Files.writeString(presetFile, GSON.toJson(presetLayout));
            LOGGER.info("[LayoutPersistence] 成功保存布局预设: {} -> {}", displayName, presetFile);
            return true;
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 保存布局预设失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean loadLayoutPreset(String presetName) {
        if (presetName == null) {
            return false;
        }
        try {
            Path presetFile = findPresetFile(presetName.trim());
            if (presetFile == null) {
                LOGGER.warn("[LayoutPersistence] 布局预设不存在: {}", presetName);
                return false;
            }

            LayoutData loaded = readLayout(presetFile);
            if (loaded == null || !loaded.isValid()) {
                LOGGER.warn("[LayoutPersistence] 布局预设无效: {}", presetFile);
                return false;
            }

            String presetIni = loaded.imguiIni;
            if (LAYOUT_VERSION.equals(loaded.version) && (presetIni == null || presetIni.isBlank())) {
                LOGGER.warn("[LayoutPersistence] 完整布局预设缺少 ImGui 几何信息: {}", presetFile);
                return false;
            }
            if (presetIni != null && !presetIni.isBlank()) {
                Files.writeString(getImGuiIniPath(), presetIni);
                iniRestore.accept(presetIni);
            }

            loaded.version = LAYOUT_VERSION;
            loaded.displayName = null;
            currentLayout = loaded;
            LOGGER.info("[LayoutPersistence] 成功加载布局预设: {}", presetName);
            markLayoutChanged();
            saveLayout();
            return true;
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 加载布局预设失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public List<String> listLayoutPresets() {
        List<String> presets = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try {
            if (Files.exists(presetsDir)) {
                try (var stream = Files.list(presetsDir)) {
                    for (Path file : stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".json"))
                            .toList()) {
                        LayoutData layout = readLayout(file);
                        String name = layout != null && layout.displayName != null && !layout.displayName.isBlank()
                                ? layout.displayName
                                : file.getFileName().toString().substring(0, file.getFileName().toString().length() - 5);
                        if (names.add(name)) {
                            presets.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 列出布局预设失败: {}", e.getMessage(), e);
        }
        presets.sort(Comparator.comparing((String name) -> name.toLowerCase(java.util.Locale.ROOT))
                .thenComparing(Comparator.naturalOrder()));
        return presets;
    }

    public boolean deleteLayoutPreset(String presetName) {
        if (presetName == null) {
            return false;
        }
        try {
            Path presetFile = findPresetFile(presetName.trim());
            if (presetFile != null) {
                Files.delete(presetFile);
                LOGGER.info("[LayoutPersistence] 已删除布局预设: {}", presetName);
                return true;
            }
            LOGGER.warn("[LayoutPersistence] 布局预设不存在: {}", presetName);
            return false;
        } catch (Exception e) {
            LOGGER.error("[LayoutPersistence] 删除布局预设失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private LayoutData readLayout(Path file) {
        try {
            return GSON.fromJson(Files.readString(file), LayoutData.class);
        } catch (Exception e) {
            LOGGER.warn("[LayoutPersistence] 无法读取布局文件: {} ({})", file, e.getMessage());
            return null;
        }
    }

    private Path findPresetFile(String displayName) {
        Path encoded = presetsDir.resolve(encodePresetName(displayName) + ".json");
        if (Files.isRegularFile(encoded)) {
            LayoutData encodedLayout = readLayout(encoded);
            if (encodedLayout != null && displayName.equals(encodedLayout.displayName)) {
                return encoded;
            }
        }
        Path legacy = presetsDir.resolve(sanitizePresetName(displayName) + ".json");
        if (Files.isRegularFile(legacy)) {
            LayoutData legacyLayout = readLayout(legacy);
            if (legacyLayout != null && (legacyLayout.displayName == null
                    || legacyLayout.displayName.isBlank()
                    || displayName.equals(legacyLayout.displayName))) {
                return legacy;
            }
        }
        if (!Files.isDirectory(presetsDir)) {
            return null;
        }
        try (var files = Files.list(presetsDir)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .toList()) {
                LayoutData layout = readLayout(file);
                if (layout != null && displayName.equals(layout.displayName)) {
                    return file;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[LayoutPersistence] 查找布局预设失败: {}", e.getMessage(), e);
        }
        return null;
    }

    private Path availablePresetFile(String displayName) {
        Path base = presetsDir.resolve(encodePresetName(displayName) + ".json");
        if (!Files.exists(base)) {
            return base;
        }
        int suffix = 1;
        Path candidate;
        do {
            candidate = presetsDir.resolve(encodePresetName(displayName) + "_" + suffix++ + ".json");
        } while (Files.exists(candidate));
        return candidate;
    }

    private String encodePresetName(String name) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(name.getBytes(StandardCharsets.UTF_8));
        if (encoded.length() <= 120) {
            return "p_" + encoded;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(name.getBytes(StandardCharsets.UTF_8));
            String hash = java.util.HexFormat.of().formatHex(digest).substring(0, 16);
            return "p_" + encoded.substring(0, 80) + "_" + hash;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

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
