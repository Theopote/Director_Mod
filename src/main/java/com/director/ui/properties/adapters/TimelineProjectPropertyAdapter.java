package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.i18n.DirectorLang;
import com.director.i18n.TimelinePropertyAdapterI18n;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyReflection;
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

    @Override
    public boolean renderProperties(Object project) {
        if (project == null) {
            UITheme.spacingLarge();
            UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.NULL_PROJECT),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 使用数组包装以支持 lambda 修改
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title(Icons.FILM + " "
                + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.TITLE));
        
        try {
            // ========== 基础信息 ==========
            if (ImGui.collapsingHeader(Icons.INFO + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.SECTION_BASIC),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                // 项目名称
                String currentName = PropertyReflection.getString(project, "name", "getName",
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.DEFAULT_NAME));
                ImString nameBuffer = new ImString(currentName, 256);
                if (ImGui.inputText(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.PROJECT_NAME),
                        nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (PropertyReflection.setString(project, "name", "setName", nameBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 项目名称已更新: {}", nameBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.PROJECT_NAME_TOOLTIP));
                }
                
                // 作者
                String currentAuthor = PropertyReflection.getString(project, "author", "getAuthor", "");
                ImString authorBuffer = new ImString(currentAuthor, 256);
                if (ImGui.inputText(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.AUTHOR),
                        authorBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (PropertyReflection.setString(project, "author", "setAuthor", authorBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 作者已更新: {}", authorBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.AUTHOR_TOOLTIP));
                }
                
                // 描述（多行文本）
                String currentDesc = PropertyReflection.getString(project, "description", "getDescription", "");
                ImString descBuffer = new ImString(currentDesc, 1024);
                if (ImGui.inputTextMultiline(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.DESCRIPTION),
                        descBuffer, 200, 60, ImGuiInputTextFlags.None)) {
                    if (PropertyReflection.setString(project, "description", "setDescription", descBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 描述已更新");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.DESCRIPTION_TOOLTIP));
                }
                
                // 版本号
                String version = PropertyReflection.getString(project, "version", "getVersion", "1.0.0");
                UITheme.spacingSmall();
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.VERSION, version),
                        UITheme.DarkTheme.TEXT_DISABLED);
                
                // UUID 显示
                String id = PropertyReflection.getString(project, "id", "getId", null);
                if (id != null) {
                    UITheme.label(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.PROJECT_ID, id),
                            UITheme.DarkTheme.TEXT_DISABLED);
                }
            }
            
            // ========== 全局设置 ==========
            if (ImGui.collapsingHeader(Icons.SETTINGS + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.SECTION_GLOBAL),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                // 帧率
                float currentFPS = PropertyReflection.getFloat(project, "frameRate", "getFrameRate", 30.0f);
                float[] fps = {currentFPS};
                if (ImGui.dragFloat(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.FRAME_RATE),
                        fps, 1.0f, 1.0f, 240.0f, "%.0f")) {
                    if (PropertyReflection.setFloat(project, "fps", "setFps", fps[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 帧率已更新: {}", fps[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.FRAME_RATE_TOOLTIP));
                }
                
                // 时间单位
                int currentTimeUnit = PropertyReflection.getInt(project, "timeUnit", "getTimeUnit", 0);
                ImInt timeUnitIdx = new ImInt(currentTimeUnit);
                String[] timeUnits = TimelinePropertyAdapterI18n.projectTimeUnits();
                if (ImGui.combo(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.TIME_UNIT),
                        timeUnitIdx, timeUnits)) {
                    if (PropertyReflection.setInt(project, "timeUnit", "setTimeUnit", timeUnitIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 时间单位已更新: {}",
                            timeUnits[timeUnitIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.TIME_UNIT_TOOLTIP));
                }
                
                // 自动保存
                boolean currentAutoSave = PropertyReflection.getBoolean(project, "autoSaveEnabled", "isAutoSaveEnabled", false);
                ImBoolean autoSave = new ImBoolean(currentAutoSave);
                if (ImGui.checkbox(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.AUTO_SAVE),
                        autoSave)) {
                    if (PropertyReflection.setBoolean(project, "autoSaveEnabled", "setAutoSaveEnabled", autoSave.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 自动保存已更新: {}", autoSave.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.AUTO_SAVE_TOOLTIP));
                }
                
                // 循环所有序列
                boolean currentLoopAll = PropertyReflection.getBoolean(project, "loopAll", "isLoopAll", false);
                ImBoolean loopAll = new ImBoolean(currentLoopAll);
                if (ImGui.checkbox(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.LOOP_ALL),
                        loopAll)) {
                    if (PropertyReflection.setBoolean(project, "loopAll", "setLoopAll", loopAll.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 循环所有序列已更新: {}", loopAll.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.LOOP_ALL_TOOLTIP));
                }
            }
            
            // ========== 序列管理 ==========
            if (ImGui.collapsingHeader(Icons.FILM + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.SECTION_SEQUENCES),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                // 获取序列列表
                Object sequencesObj = PropertyReflection.readProperty(project, "sequences", "getSequences");
                
                if (sequencesObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> sequences = (List<Object>) sequencesObj;
                    
                    if (sequences.isEmpty()) {
                        UITheme.label(TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineProject.SEQUENCES_NONE),
                                UITheme.DarkTheme.TEXT_DISABLED);
                        UITheme.label(TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineProject.CREATE_FIRST_SEQUENCE),
                                UITheme.DarkTheme.TEXT_DISABLED);
                    } else {
                        // 序列树形列表
                        for (int i = 0; i < sequences.size(); i++) {
                            Object seq = sequences.get(i);
                            ImGui.pushID(i);
                            
                            String seqName = PropertyReflection.getString(seq, "name", "getName",
                                    TimelinePropertyAdapterI18n.tr(
                                            DirectorLang.Properties.AdapterTimelineProject.DEFAULT_SEQUENCE_NAME, i));
                            float seqDuration = PropertyReflection.getFloat(seq, "duration", "getDuration", 0.0f);
                            boolean seqLoop = PropertyReflection.getBoolean(seq, "loop", "isLoop", false);
                            
                            String loopIcon = seqLoop ? Icons.LOOP : Icons.PLAY;
                            
                            if (ImGui.treeNodeEx(loopIcon + " " + seqName, 
                                    ImGuiTreeNodeFlags.Framed | ImGuiTreeNodeFlags.SpanAvailWidth)) {
                                
                                // 序列简要信息
                                UITheme.label(TimelinePropertyAdapterI18n.tr(
                                        DirectorLang.Properties.AdapterTimelineProject.SEQUENCE_DURATION, seqDuration),
                                    UITheme.DarkTheme.TEXT);
                                
                                // 获取轨道数量
                                Object tracksObj = PropertyReflection.readProperty(seq, "tracks", "getTracks");
                                int trackCount = 0;
                                if (tracksObj instanceof List<?>) {
                                    trackCount = ((List<?>) tracksObj).size();
                                }
                                UITheme.label(TimelinePropertyAdapterI18n.tr(
                                        DirectorLang.Properties.AdapterTimelineProject.SEQUENCE_TRACK_COUNT, trackCount),
                                        UITheme.DarkTheme.TEXT);
                                
                                UITheme.label(TimelinePropertyAdapterI18n.tr(
                                        DirectorLang.Properties.AdapterTimelineProject.SEQUENCE_LOOP,
                                        seqLoop
                                                ? TimelinePropertyAdapterI18n.tr(
                                                        DirectorLang.Properties.AdapterTimelineCommon.YES)
                                                : TimelinePropertyAdapterI18n.tr(
                                                        DirectorLang.Properties.AdapterTimelineCommon.NO)),
                                        UITheme.DarkTheme.TEXT);
                                
                                float timeScale = PropertyReflection.getFloat(seq, "timeScale", "getTimeScale", 1.0f);
                                UITheme.label(TimelinePropertyAdapterI18n.tr(
                                        DirectorLang.Properties.AdapterTimelineProject.SEQUENCE_TIME_SCALE, timeScale),
                                    UITheme.DarkTheme.TEXT);
                                
                                UITheme.spacingSmall();
                                
                                // 操作按钮
                                if (ImGui.button(
                                        TimelinePropertyAdapterI18n.tr(
                                                DirectorLang.Properties.AdapterTimelineProject.EDIT_SEQUENCE),
                                        110, UITheme.Spacing.BUTTON_HEIGHT)) {
                                    // 切换选择到此序列
                                    DirectorServices.get().uiSelection().setCurrentSelection(seq);
                                    LOGGER.debug("[TimelineProjectPropertyAdapter] 跳转编辑序列: {}", seqName);
                                }
                                if (ImGui.isItemHovered()) {
                                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                                            DirectorLang.Properties.AdapterTimelineProject.EDIT_SEQUENCE_TOOLTIP));
                                }
                                
                                ImGui.sameLine();
                                
                                if (ImGui.button(
                                        TimelinePropertyAdapterI18n.tr(DirectorLang.Common.DELETE) + "##seq" + i,
                                        110, UITheme.Spacing.BUTTON_HEIGHT)) {
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
                                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                                            DirectorLang.Properties.AdapterTimelineProject.DELETE_SEQUENCE_TOOLTIP));
                                }
                                
                                ImGui.treePop();
                            }
                            
                            ImGui.popID();
                        }
                        
                        UITheme.spacingSmall();
                        UITheme.label(TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineProject.SEQUENCE_HINT),
                            UITheme.DarkTheme.TEXT_DISABLED);
                    }
                } else {
                    UITheme.label(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.CANNOT_GET_SEQUENCES),
                            UITheme.DarkTheme.TEXT_DISABLED);
                }
                
                UITheme.spacingSmall();
                
                // 添加和清空按钮
                if (ImGui.button(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.ADD_SEQUENCE),
                        120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 添加新序列");
                    // TODO: 创建并添加新序列
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.ADD_SEQUENCE_TOOLTIP));
                }
                
                ImGui.sameLine();
                
                if (ImGui.button(
                        TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineProject.CLEAR_ALL_SEQUENCES),
                        120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.warn("[TimelineProjectPropertyAdapter] 清空所有序列");
                    // TODO: 清空序列列表
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.CLEAR_ALL_SEQUENCES_TOOLTIP));
                }
            }
            
            // ========== 导出设置 ==========
            if (ImGui.collapsingHeader(Icons.EXPORT + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.SECTION_EXPORT))) {
                // 导出格式
                int currentFormat = PropertyReflection.getInt(project, "exportFormat", "getExportFormat", 0);
                ImInt formatIdx = new ImInt(currentFormat);
                String[] exportFormats = TimelinePropertyAdapterI18n.projectExportFormats();
                if (ImGui.combo(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.EXPORT_FORMAT),
                        formatIdx, exportFormats)) {
                    if (PropertyReflection.setInt(project, "exportFormat", "setExportFormat", formatIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 导出格式已更新: {}",
                            exportFormats[formatIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.EXPORT_FORMAT_TOOLTIP));
                }
                
                // 导出路径
                String currentPath = PropertyReflection.getString(project, "exportPath", "getExportPath", "");
                ImString pathBuffer = new ImString(currentPath, 512);
                if (ImGui.inputText(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.EXPORT_PATH),
                        pathBuffer, ImGuiInputTextFlags.None)) {
                    if (PropertyReflection.setString(project, "exportPath", "setExportPath", pathBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineProjectPropertyAdapter] 导出路径已更新: {}", pathBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.EXPORT_PATH_TOOLTIP));
                }
                
                UITheme.spacingSmall();
                
                // 浏览路径按钮
                if (ImGui.button(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.BROWSE_PATH),
                        120, UITheme.Spacing.BUTTON_HEIGHT)) {
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
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.BROWSE_PATH_TOOLTIP));
                }
                
                // 导出选项
                UITheme.spacingSmall();
                
                boolean includeResources = PropertyReflection.getBoolean(project, "includeResources", "isIncludeResources", true);
                ImBoolean includeRes = new ImBoolean(includeResources);
                if (ImGui.checkbox(
                        TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineProject.INCLUDE_RESOURCES),
                        includeRes)) {
                    if (PropertyReflection.setBoolean(project, "includeResources", "setIncludeResources", includeRes.get())) {
                        modifiedRef[0] = true;
                    }
                }
                
                boolean compress = PropertyReflection.getBoolean(project, "compress", "isCompress", false);
                ImBoolean compressBool = new ImBoolean(compress);
                if (ImGui.checkbox(
                        TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineProject.COMPRESS_EXPORT),
                        compressBool)) {
                    if (PropertyReflection.setBoolean(project, "compress", "setCompress", compressBool.get())) {
                        modifiedRef[0] = true;
                    }
                }
            }
            
            // ========== 统计信息 ==========
            if (ImGui.collapsingHeader(Icons.STATS + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.SECTION_STATS))) {
                // 序列统计
                Object sequencesObj = PropertyReflection.readProperty(project, "sequences", "getSequences");
                int sequenceCount = 0;
                int totalTracks = 0;
                int totalKeyframes = 0;
                
                if (sequencesObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> sequences = (List<Object>) sequencesObj;
                    sequenceCount = sequences.size();
                    
                    // 统计总轨道数和关键帧数
                    for (Object seq : sequences) {
                        Object tracksObj = PropertyReflection.readProperty(seq, "tracks", "getTracks");
                        if (tracksObj instanceof List<?>) {
                            @SuppressWarnings("unchecked")
                            List<Object> tracks = (List<Object>) tracksObj;
                            totalTracks += tracks.size();
                            
                            // 统计关键帧
                            for (Object track : tracks) {
                                int count = PropertyReflection.getInt(track, "keyframeCount", "getKeyframeCount", 0);
                                totalKeyframes += count;
                            }
                        }
                    }
                }
                
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.SEQUENCE_TOTAL, sequenceCount),
                        UITheme.DarkTheme.TEXT);
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.TOTAL_TRACKS, totalTracks),
                        UITheme.DarkTheme.TEXT);
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.TOTAL_KEYFRAMES, totalKeyframes),
                        UITheme.DarkTheme.TEXT);
                
                // 文件统计
                UITheme.spacingSmall();
                String projectPath = PropertyReflection.getString(project, "projectPath", "getProjectPath",
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.NOT_SAVED));
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.PROJECT_PATH, projectPath),
                        UITheme.DarkTheme.TEXT_DISABLED);
                
                // 创建时间
                long createdTime = PropertyReflection.getLong(project, "createdTime", "getCreatedTime", 0L);
                if (createdTime > 0) {
                    UITheme.label(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.CREATED_AT,
                            DATE_FORMAT.format(new Date(createdTime))),
                        UITheme.DarkTheme.TEXT_DISABLED);
                }
                
                // 修改时间
                long modifiedTime = PropertyReflection.getLong(project, "modifiedTime", "getModifiedTime", 0L);
                if (modifiedTime > 0) {
                    UITheme.label(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineProject.MODIFIED_AT,
                            DATE_FORMAT.format(new Date(modifiedTime))),
                        UITheme.DarkTheme.TEXT_DISABLED);
                }
            }
            
            UITheme.separator();
            
            // ========== 项目控制 ==========
            UITheme.beginGroup(TimelinePropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterTimelineProject.SECTION_CONTROLS));
            
            // 第一行 - 文件操作
            if (ImGui.button(
                    TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.SAVE_PROJECT),
                    110, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(project, "save")) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 项目已保存");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.SAVE_PROJECT_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(
                    TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.EXPORT_PROJECT),
                    110, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(project, "export") || PropertyReflection.invokeVoid(project, "exportProject")) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 项目已导出");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.EXPORT_PROJECT_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(Icons.REFRESH + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.RELOAD),
                    110, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(project, "reload")) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 项目已重新加载");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.RELOAD_TOOLTIP));
            }
            
            // 第二行 - 项目操作
            if (ImGui.button(
                    TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.NEW_PROJECT),
                    110, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineProjectPropertyAdapter] 新建项目");
                // TODO: 创建新项目逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.NEW_PROJECT_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(
                    TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.COPY_PROJECT),
                    110, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineProjectPropertyAdapter] 复制项目");
                // TODO: 复制项目逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.COPY_PROJECT_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(
                    TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.CLEAR_CACHE),
                    110, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(project, "clearCache")) {
                    LOGGER.info("[TimelineProjectPropertyAdapter] 缓存已清理");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineProject.CLEAR_CACHE_TOOLTIP));
            }
            
            UITheme.endGroup();
            
            UITheme.spacingSmall();
            UITheme.label(TimelinePropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterTimelineProject.FOOTER_HINT),
                UITheme.DarkTheme.TEXT_DISABLED);
            
        } catch (Exception e) {
            LOGGER.error("[TimelineProjectPropertyAdapter] 渲染项目属性时出错", e);
            UITheme.label(TimelinePropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterTimelineCommon.RENDER_ERROR, e.getMessage()),
                    UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    @Override
    public Class<Object> getTargetType() {
        // 使用 Object 以支持各种项目类型
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineProject.ADAPTER_NAME);
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


