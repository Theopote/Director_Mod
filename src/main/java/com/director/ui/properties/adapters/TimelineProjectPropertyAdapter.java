package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.selection.SelectionManager;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import imgui.type.ImInt;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 时间线项目属性适配器 v4.9 - Director Properties System
 * 
 * <p>为 Director 项目对象提供最高层属性编辑器。
 * 
 * <h3>时间线项目（TimelineProject）</h3>
 * <p>TimelineProject 是 Director 的最高层编辑对象，包含：
 * <ul>
 *   <li>多个 TimelineSequence（时间线序列）的集合</li>
 *   <li>全局播放参数（帧率、循环、自动保存）</li>
 *   <li>序列的添加、删除、排序</li>
 *   <li>项目级别的导出控制</li>
 *   <li>项目元数据（作者、描述、版本）</li>
 * </ul>
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>📋 基础信息（项目名称、作者、描述、版本）</li>
 *   <li>⚙️ 全局设置（帧率、时间单位、循环所有、自动保存）</li>
 *   <li>📜 序列管理（序列列表、添加、删除、跳转编辑）</li>
 *   <li>📦 导出设置（格式、路径、选项）</li>
 *   <li>📊 统计信息（序列总数、总轨道数、总关键帧数）</li>
 *   <li>💾 项目控制（保存、导出、重载、新建）</li>
 * </ul>
 * 
 * <h3>导出格式</h3>
 * <ul>
 *   <li>JSON - 人类可读的文本格式</li>
 *   <li>NBT - Minecraft 原生格式</li>
 *   <li>Binary - 二进制压缩格式</li>
 *   <li>ScenePack - 场景打包格式</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>序列树形展开 - 可展开查看每个序列</li>
 *   <li>跳转编辑 - 点击序列跳转到序列编辑器</li>
 *   <li>导出控制 - 路径浏览、格式选择</li>
 *   <li>智能提示 - Tooltip 说明</li>
 *   <li>实时统计 - 项目规模信息</li>
 * </ul>
 * 
 * <h3>与其他适配器的交互</h3>
 * <ul>
 *   <li>点击"编辑序列"按钮 → TimelineSequencePropertyAdapter</li>
 *   <li>完整链路: Project → Sequence → Track → Container → Keyframe</li>
 * </ul>
 * 
 * <h3>注意事项</h3>
 * <p>由于项目系统可能尚未完整实现，此适配器采用通用 Object 类型，
 * 使用反射和类型检查来动态渲染属性。当项目系统完整后，可以
 * 替换为具体的 TimelineProject 类型。
 * 
 * @author Director Team
 * @version 4.9
 * @since 2025-10-16
 */
public class TimelineProjectPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineProjectPropertyAdapter.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // 导出格式选项
    private static final String[] EXPORT_FORMATS = {
        "JSON（文本格式）",
        "NBT（Minecraft格式）",
        "Binary（二进制格式）",
        "ScenePack（场景包）"
    };
    
    // 时间单位选项
    private static final String[] TIME_UNITS = {
        "秒 (Seconds)",
        "帧 (Frames)",
        "Tick (MC Ticks)"
    };

    @Override
    public boolean renderProperties(Object project) {
        if (project == null) {
            UITheme.spacingLarge();
            UITheme.label("时间线项目为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 使用数组包装以支持 lambda 修改
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("🎬 Director 项目属性编辑器");
        
        try {
            // ========== 基础信息 ==========
            if (ImGui.collapsingHeader("📋 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 项目名称
                String currentName = getStringProperty(project, "name", "getName", "未命名项目");
                ImString nameBuffer = new ImString(currentName, 256);
                if (ImGui.inputText("项目名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (setStringProperty(project, "name", "setName", nameBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 项目名称已更新: {}", nameBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("动画项目的显示名称");
                }
                
                // 作者
                String currentAuthor = getStringProperty(project, "author", "getAuthor", "");
                ImString authorBuffer = new ImString(currentAuthor, 256);
                if (ImGui.inputText("作者", authorBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (setStringProperty(project, "author", "setAuthor", authorBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 作者已更新: {}", authorBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("项目作者或创作团队");
                }
                
                // 描述（多行文本）
                String currentDesc = getStringProperty(project, "description", "getDescription", "");
                ImString descBuffer = new ImString(currentDesc, 1024);
                if (ImGui.inputTextMultiline("描述", descBuffer, 200, 60, ImGuiInputTextFlags.None)) {
                    if (setStringProperty(project, "description", "setDescription", descBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 描述已更新");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("项目的详细描述信息");
                }
                
                // 版本号
                String version = getStringProperty(project, "version", "getVersion", "1.0.0");
                UITheme.spacingSmall();
                UITheme.label("版本: " + version, UITheme.DarkTheme.TEXT_DISABLED);
                
                // UUID 显示
                String id = getStringProperty(project, "id", "getId", null);
                if (id != null) {
                    UITheme.label("项目 ID: " + id, UITheme.DarkTheme.TEXT_DISABLED);
                }
            }
            
            // ========== 全局设置 ==========
            if (ImGui.collapsingHeader("⚙️ 全局设置", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 帧率
                float currentFPS = getFloatProperty(project, "frameRate", "getFrameRate", 30.0f);
                float[] fps = {currentFPS};
                if (ImGui.dragFloat("帧率 (FPS)", fps, 1.0f, 1.0f, 240.0f, "%.0f")) {
                    if (setFloatProperty(project, fps[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 帧率已更新: {}", fps[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("动画播放的每秒帧数（常用: 24, 30, 60）");
                }
                
                // 时间单位
                int currentTimeUnit = getIntProperty(project, "timeUnit", "getTimeUnit");
                ImInt timeUnitIdx = new ImInt(currentTimeUnit);
                if (ImGui.combo("时间单位", timeUnitIdx, TIME_UNITS)) {
                    if (setIntProperty(project, "timeUnit", "setTimeUnit", timeUnitIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 时间单位已更新: {}", 
                            TIME_UNITS[timeUnitIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("时间轴显示的单位");
                }
                
                // 自动保存
                boolean currentAutoSave = getBooleanProperty(project, "autoSaveEnabled", "isAutoSaveEnabled", false);
                ImBoolean autoSave = new ImBoolean(currentAutoSave);
                if (ImGui.checkbox("自动保存", autoSave)) {
                    if (setBooleanProperty(project, "autoSaveEnabled", "setAutoSaveEnabled", autoSave.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 自动保存已更新: {}", autoSave.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("定期自动保存项目");
                }
                
                // 循环所有序列
                boolean currentLoopAll = getBooleanProperty(project, "loopAll", "isLoopAll", false);
                ImBoolean loopAll = new ImBoolean(currentLoopAll);
                if (ImGui.checkbox("循环所有序列", loopAll)) {
                    if (setBooleanProperty(project, "loopAll", "setLoopAll", loopAll.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 循环所有序列已更新: {}", loopAll.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("所有序列播放完毕后自动重新开始");
                }
            }
            
            // ========== 序列管理 ==========
            if (ImGui.collapsingHeader("📜 序列管理", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 获取序列列表
                Object sequencesObj = getObjectProperty(project, "sequences", "getSequences");
                
                if (sequencesObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> sequences = (List<Object>) sequencesObj;
                    
                    if (sequences.isEmpty()) {
                        UITheme.label("序列: 无", UITheme.DarkTheme.TEXT_DISABLED);
                        UITheme.label("使用下方按钮创建第一个序列", UITheme.DarkTheme.TEXT_DISABLED);
                    } else {
                        // 序列树形列表
                        for (int i = 0; i < sequences.size(); i++) {
                            Object seq = sequences.get(i);
                            ImGui.pushID(i);
                            
                            String seqName = getStringProperty(seq, "name", "getName", "序列 " + i);
                            float seqDuration = getFloatProperty(seq, "duration", "getDuration", 0.0f);
                            boolean seqLoop = getBooleanProperty(seq, "loop", "isLoop", false);
                            
                            String loopIcon = seqLoop ? Icons.LOOP : Icons.PLAY;
                            
                            if (ImGui.treeNodeEx(loopIcon + " " + seqName, 
                                    ImGuiTreeNodeFlags.Framed | ImGuiTreeNodeFlags.SpanAvailWidth)) {
                                
                                // 序列简要信息
                                UITheme.label(String.format("时长: %.2f 秒", seqDuration), 
                                    UITheme.DarkTheme.TEXT);
                                
                                // 获取轨道数量
                                Object tracksObj = getObjectProperty(seq, "tracks", "getTracks");
                                int trackCount = 0;
                                if (tracksObj instanceof List<?>) {
                                    trackCount = ((List<?>) tracksObj).size();
                                }
                                UITheme.label("轨道数: " + trackCount, UITheme.DarkTheme.TEXT);
                                
                                UITheme.label("循环: " + (seqLoop ? "是" : "否"), UITheme.DarkTheme.TEXT);
                                
                                float timeScale = getFloatProperty(seq, "timeScale", "getTimeScale", 1.0f);
                                UITheme.label(String.format("时间缩放: %.2fx", timeScale), 
                                    UITheme.DarkTheme.TEXT);
                                
                                UITheme.spacingSmall();
                                
                                // 操作按钮
                                if (ImGui.button("编辑此序列", 110, UITheme.Spacing.BUTTON_HEIGHT)) {
                                    // 切换选择到此序列
                                    DirectorServices.get().uiSelection().setCurrentSelection(seq);
                                    LOGGER.debug("[TimelineProjectPropertyAdapter] 跳转编辑序列: {}", seqName);
                                }
                                if (ImGui.isItemHovered()) {
                                    ImGui.setTooltip("在属性面板中编辑此序列");
                                }
                                
                                ImGui.sameLine();
                                
                                if (ImGui.button("删除##seq" + i, 110, UITheme.Spacing.BUTTON_HEIGHT)) {
                                    // 调用 removeSequence 方法
                                    try {
                                        var removeMethod = project.getClass().getMethod("removeSequence", Object.class);
                                        removeMethod.invoke(project, seq);
                                        modifiedRef[0] = true;
                                        LOGGER.info("[TimelineProjectPropertyAdapter] 删除序列: {}", seqName);
                                    } catch (Exception e) {
                                        LOGGER.error("[TimelineProjectPropertyAdapter] 删除序列失败", e);
                                    }
                                }
                                if (ImGui.isItemHovered()) {
                                    ImGui.setTooltip("从项目中移除此序列");
                                }
                                
                                ImGui.treePop();
                            }
                            
                            ImGui.popID();
                        }
                        
                        UITheme.spacingSmall();
                        UITheme.label("提示: 点击 '编辑此序列' 可在属性面板中深入编辑", 
                            UITheme.DarkTheme.TEXT_DISABLED);
                    }
                } else {
                    UITheme.label("无法获取序列列表", UITheme.DarkTheme.TEXT_DISABLED);
                }
                
                UITheme.spacingSmall();
                
                // 添加和清空按钮
                if (ImGui.button("➕ 添加序列", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 添加新序列");
                    // TODO: 创建并添加新序列
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("创建新的时间线序列");
                }
                
                ImGui.sameLine();
                
                if (ImGui.button("🗑 清空所有序列", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.warn("[TimelineProjectPropertyAdapter] 清空所有序列");
                    // TODO: 清空序列列表
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("移除项目中的所有序列（危险操作）");
                }
            }
            
            // ========== 导出设置 ==========
            if (ImGui.collapsingHeader("📦 导出设置")) {
                // 导出格式
                int currentFormat = getIntProperty(project, "exportFormat", "getExportFormat");
                ImInt formatIdx = new ImInt(currentFormat);
                if (ImGui.combo("导出格式", formatIdx, EXPORT_FORMATS)) {
                    if (setIntProperty(project, "exportFormat", "setExportFormat", formatIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 导出格式已更新: {}", 
                            EXPORT_FORMATS[formatIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("选择项目导出的文件格式");
                }
                
                // 导出路径
                String currentPath = getStringProperty(project, "exportPath", "getExportPath", "");
                ImString pathBuffer = new ImString(currentPath, 512);
                if (ImGui.inputText("导出路径", pathBuffer, ImGuiInputTextFlags.None)) {
                    if (setStringProperty(project, "exportPath", "setExportPath", pathBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 导出路径已更新: {}", pathBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("项目导出的目标路径");
                }
                
                UITheme.spacingSmall();
                
                // 浏览路径按钮
                if (ImGui.button("📁 浏览路径", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    String path = pathBuffer.get();
                    if (!path.isEmpty()) {
                        File file = new File(path);
                        if (file.exists()) {
                            LOGGER.info("[TimelineProjectPropertyAdapter] 打开路径: {}", path);
                        } else {
                            LOGGER.warn("[TimelineProjectPropertyAdapter] 路径不存在: {}", path);
                        }
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("打开文件浏览器选择路径");
                }
                
                // 导出选项
                UITheme.spacingSmall();
                
                boolean includeResources = getBooleanProperty(project, "includeResources", "isIncludeResources", true);
                ImBoolean includeRes = new ImBoolean(includeResources);
                if (ImGui.checkbox("包含资源文件", includeRes)) {
                    if (setBooleanProperty(project, "includeResources", "setIncludeResources", includeRes.get())) {
                        modifiedRef[0] = true;
                    }
                }
                
                boolean compress = getBooleanProperty(project, "compress", "isCompress", false);
                ImBoolean compressBool = new ImBoolean(compress);
                if (ImGui.checkbox("压缩导出", compressBool)) {
                    if (setBooleanProperty(project, "compress", "setCompress", compressBool.get())) {
                        modifiedRef[0] = true;
                    }
                }
            }
            
            // ========== 统计信息 ==========
            if (ImGui.collapsingHeader("📊 统计信息")) {
                // 序列统计
                Object sequencesObj = getObjectProperty(project, "sequences", "getSequences");
                int sequenceCount = 0;
                int totalTracks = 0;
                int totalKeyframes = 0;
                
                if (sequencesObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> sequences = (List<Object>) sequencesObj;
                    sequenceCount = sequences.size();
                    
                    // 统计总轨道数和关键帧数
                    for (Object seq : sequences) {
                        Object tracksObj = getObjectProperty(seq, "tracks", "getTracks");
                        if (tracksObj instanceof List<?>) {
                            @SuppressWarnings("unchecked")
                            List<Object> tracks = (List<Object>) tracksObj;
                            totalTracks += tracks.size();
                            
                            // 统计关键帧
                            for (Object track : tracks) {
                                int count = getIntProperty(track, "keyframeCount", "getKeyframeCount");
                                totalKeyframes += count;
                            }
                        }
                    }
                }
                
                UITheme.label("序列总数: " + sequenceCount, UITheme.DarkTheme.TEXT);
                UITheme.label("总轨道数: " + totalTracks, UITheme.DarkTheme.TEXT);
                UITheme.label("总关键帧数: " + totalKeyframes, UITheme.DarkTheme.TEXT);
                
                // 文件统计
                UITheme.spacingSmall();
                String projectPath = getStringProperty(project, "projectPath", "getProjectPath", "未保存");
                UITheme.label("项目路径: " + projectPath, UITheme.DarkTheme.TEXT_DISABLED);
                
                // 创建时间
                long createdTime = getLongProperty(project, "createdTime", "getCreatedTime");
                if (createdTime > 0) {
                    UITheme.label("创建时间: " + DATE_FORMAT.format(new Date(createdTime)), 
                        UITheme.DarkTheme.TEXT_DISABLED);
                }
                
                // 修改时间
                long modifiedTime = getLongProperty(project, "modifiedTime", "getModifiedTime");
                if (modifiedTime > 0) {
                    UITheme.label("修改时间: " + DATE_FORMAT.format(new Date(modifiedTime)), 
                        UITheme.DarkTheme.TEXT_DISABLED);
                }
            }
            
            UITheme.separator();
            
            // ========== 项目控制 ==========
            UITheme.beginGroup("项目控制");
            
            // 第一行 - 文件操作
            if (ImGui.button("💾 保存项目", 110, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (callMethod(project, "save")) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 项目已保存");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("保存项目到磁盘");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("🚀 导出项目", 110, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (callMethod(project, "export") || callMethod(project, "exportProject")) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 项目已导出");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("导出项目为指定格式");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(Icons.REFRESH + " 重新加载", 110, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (callMethod(project, "reload")) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 项目已重新加载");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("从磁盘重新加载项目");
            }
            
            // 第二行 - 项目操作
            if (ImGui.button("📄 新建项目", 110, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineProjectPropertyAdapter] 新建项目");
                // TODO: 创建新项目逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("创建新的空白项目");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("📋 复制项目", 110, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineProjectPropertyAdapter] 复制项目");
                // TODO: 复制项目逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("创建此项目的副本");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("🧹 清理缓存", 110, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (callMethod(project, "clearCache")) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 缓存已清理");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("清理项目缓存文件");
            }
            
            UITheme.endGroup();
            
            UITheme.spacingSmall();
            UITheme.label("提示: Director 项目可包含多个序列用于不同场景或镜头", 
                UITheme.DarkTheme.TEXT_DISABLED);
            
        } catch (Exception e) {
            LOGGER.error("[TimelineProjectPropertyAdapter] 渲染项目属性时出错", e);
            UITheme.label("渲染出错: " + e.getMessage(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    
    // ========== 反射辅助方法 ==========
    
    private String getStringProperty(Object obj, String fieldName, String getterName, String defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result != null ? result.toString() : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setStringProperty(Object obj, String fieldName, String setterName, String value) {
        try {
            var method = obj.getClass().getMethod(setterName, String.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private boolean getBooleanProperty(Object obj, String fieldName, String getterName, boolean defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof Boolean ? (Boolean) result : defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof Boolean ? (Boolean) result : defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setBooleanProperty(Object obj, String fieldName, String setterName, boolean value) {
        try {
            var method = obj.getClass().getMethod(setterName, boolean.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private int getIntProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof Integer ? (Integer) result : 0;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof Integer ? (Integer) result : 0;
            } catch (Exception ex) {
                return 0;
            }
        }
    }
    
    private boolean setIntProperty(Object obj, String fieldName, String setterName, int value) {
        try {
            var method = obj.getClass().getMethod(setterName, int.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private float getFloatProperty(Object obj, String fieldName, String getterName, float defaultValue) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            if (result instanceof Float) return (Float) result;
            if (result instanceof Double) return ((Double) result).floatValue();
            return defaultValue;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                if (result instanceof Float) return (Float) result;
                if (result instanceof Double) return ((Double) result).floatValue();
                return defaultValue;
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    
    private boolean setFloatProperty(Object obj, float value) {
        try {
            var method = obj.getClass().getMethod("setFrameRate", float.class);
            method.invoke(obj, value);
            return true;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField("frameRate");
                field.setAccessible(true);
                field.set(obj, value);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
    
    private long getLongProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result instanceof Long ? (Long) result : 0L;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result instanceof Long ? (Long) result : 0L;
            } catch (Exception ex) {
                return 0L;
            }
        }
    }
    
    private Object getObjectProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            return method.invoke(obj);
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    /**
     * 调用无参方法
     */
    private boolean callMethod(Object obj, String methodName) {
        try {
            var method = obj.getClass().getMethod(methodName);
            method.invoke(obj);
            return true;
        } catch (Exception e) {
            LOGGER.debug("[TimelineProjectPropertyAdapter] 调用方法失败: {}", methodName);
            return false;
        }
    }

    @Override
    public Class<Object> getTargetType() {
        // 使用 Object 以支持各种项目类型
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return "时间线项目属性编辑器";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        // 检查是否是时间线项目对象
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("project") || 
               className.contains("timelineproject");
    }
}

