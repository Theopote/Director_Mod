package com.chronoblocks.ui.panels;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRegistryManager;
import com.chronoblocks.ui.actor.ActorSelectorPanel;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.properties.PropertyAdapterRegistry;
import com.chronoblocks.ui.selection.SelectionManager;
import com.chronoblocks.ui.selection.SelectionType;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 🎨 智能动态属性面板 v5.0 (现代化增强版) - ChronoBlocks Properties System
 * <p>
 * 根据当前选择的对象类型，自动切换到对应的属性编辑器。
 * 支持 <b>17种专用适配器</b> + 通用反射适配器。
 * </p>
 * 
 * <h3>✨ 核心功能</h3>
 * <ul>
 *   <li>🎯 <strong>上下文驱动</strong> - 根据选中对象类型动态渲染</li>
 *   <li>🔄 <strong>事件驱动更新</strong> - 监听选择变化事件</li>
 *   <li>🧩 <strong>智能类型适配</strong> - 4级查找机制（精确→继承→supports→fallback）</li>
 *   <li>↔️ <strong>双向绑定</strong> - 属性修改即时反映到对象</li>
 *   <li>📊 <strong>实时统计</strong> - 显示适配器信息和性能指标</li>
 *   <li>🛠️ <strong>调试模式</strong> - 详细的日志和诊断信息</li>
 *   <li>🔍 <strong>搜索功能</strong> - 快速查找属性（v5.0新增）</li>
 *   <li>📈 <strong>统计面板</strong> - 详细的性能监控（v5.0新增）</li>
 *   <li>💾 <strong>保存状态</strong> - Dirty标记和保存指示（v5.0新增）</li>
 *   <li>🧰 <strong>工具栏</strong> - 刷新、展开/折叠控制（v5.0新增）</li>
 * </ul>
 * 
 * <h3>📋 支持的对象类型（16+1种）</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 *   <tr><th>类别</th><th>对象类型</th><th>适配器</th></tr>
 *   <tr><td rowspan="4"><b>时间线系统</b></td>
 *       <td>TimelineProject</td><td>TimelineProjectPropertyAdapter</td></tr>
 *   <tr><td>TimelineSequence</td><td>TimelineSequencePropertyAdapter</td></tr>
 *   <tr><td>TimelineTrack</td><td>TimelineTrackPropertyAdapter</td></tr>
 *   <tr><td>TimelineClip</td><td>TimelineClipPropertyAdapter</td></tr>
 *   <tr><td rowspan="3"><b>核心对象</b></td>
 *       <td>Keyframe</td><td>KeyframePropertyAdapter</td></tr>
 *   <tr><td>Container</td><td>ContainerPropertyAdapter</td></tr>
 *   <tr><td>AnimationClip</td><td>ClipPropertyAdapter</td></tr>
 *   <tr><td rowspan="6"><b>扩展系统</b></td>
 *       <td>Effect</td><td>EffectPropertyAdapter</td></tr>
 *   <tr><td>ProjectEnvironment</td><td>ProjectEnvironmentPropertyAdapter</td></tr>
 *   <tr><td>MaterialLibrary</td><td>MaterialLibraryPropertyAdapter</td></tr>
 *   <tr><td>Camera</td><td>CameraPropertyAdapter</td></tr>
 *   <tr><td>PostProcessing</td><td>PostProcessingPropertyAdapter</td></tr>
 *   <tr><td>PhysicsEnvironment</td><td>PhysicsEnvironmentPropertyAdapter</td></tr>
 *   <tr><td rowspan="3"><b>扩展类型</b></td>
 *       <td>Vector3f</td><td>Vector3fPropertyAdapter</td></tr>
 *   <tr><td>Vector4f (Color)</td><td>ColorPropertyAdapter</td></tr>
 *   <tr><td>List</td><td>ListPropertyAdapter</td></tr>
 *   <tr><td><b>通用回退</b></td>
 *       <td>Object (任意)</td><td>GenericPropertyAdapter</td></tr>
 * </table>
 * 
 * <h3>🧠 使用示例</h3>
 * <pre>{@code
 * // 示例 1: 选择关键帧
 * Keyframe kf = ...;
 * SelectionManager.get().setCurrentSelection(kf);
 * // → 自动显示关键帧属性编辑器
 * 
 * // 示例 2: 选择相机
 * Camera camera = ...;
 * SelectionManager.get().setCurrentSelection(camera);
 * // → 自动显示相机属性编辑器（FOV、景深、镜头特效等）
 * 
 * // 示例 3: 选择未知对象
 * MyCustomObject obj = ...;
 * SelectionManager.get().setCurrentSelection(obj);
 * // → 自动使用通用反射适配器显示所有字段
 * }</pre>
 * 
 * <h3>🆕 v5.0 新增特性（合并 ModernPropertiesPanel）</h3>
 * <ul>
 *   <li>搜索功能 - 快速查找属性</li>
 *   <li>工具栏 - 展开/折叠、刷新、统计开关</li>
 *   <li>状态栏 - 适配器信息显示</li>
 *   <li>统计面板 - 详细的性能指标</li>
 *   <li>保存状态指示 - Dirty标记和保存按钮</li>
 *   <li>多选提示 - 显示多选对象数量</li>
 * </ul>
 * 
 * @version 5.0
 * @since 2025-10-16
 * @author ChronoBlocks Team
 */
public class PropertiesPanel extends AbstractPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesPanel.class);
    
    // ==================== 当前选择 ====================
    
    /** 当前选择的对象 */
    private Object currentSelection;
    
    /** 当前适配器 */
    private IPropertyAdapter<Object> currentAdapter;
    
    /** 上次更新时间（用于检测选择变化） */
    private long lastUpdateTime = 0;
    
    /** 更新间隔（毫秒） */
    private static final long UPDATE_INTERVAL = 100;
    
    /** Actor Selector Panel（演员管理器） */
    private ActorSelectorPanel actorSelectorPanel;
    
    // ==================== UI 状态（精简版） ====================
    
    // ==================== 面板配置 ====================

    @Override
    public String getPanelName() {
        return "Properties";
    }
    
    @Override
    public String getWindowName() {
        return Icons.CLIPBOARD + " 属性###PropertiesPanel";
    }
    
    @Override
    protected int getWindowFlags() {
        return super.getWindowFlags();
    }

    @Override
    public int getPriority() {
        return 150;
    }
    
    @Override
    public boolean shouldReserveDockSpaceWhenHidden() {
        return super.shouldReserveDockSpaceWhenHidden();
    }
    
    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }
    
    // ==================== 初始化 ====================
    
    /**
     * 构造函数
     */
    public PropertiesPanel() {
        super();
        
        // 注册选择变化监听器
        SelectionManager.get().addSelectionChangedListener(this::onSelectionChanged);
        
        // 初始化 Actor Selector Panel（延迟初始化，需要 TimelineModel）
        // actorSelectorPanel 将在首次渲染时初始化
        
        LOGGER.info("[PropertiesPanel] 已初始化智能属性面板");
    }
    
    /**
     * 初始化 Actor Selector Panel（延迟初始化）
     */
    private void ensureActorSelectorInitialized() {
        if (actorSelectorPanel != null) {
            return;
        }
        
        TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        if (timelineModel == null) {
            return; // TimelineModel 尚未初始化，稍后重试
        }
        
        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        actorSelectorPanel = new ActorSelectorPanel(actorRegistry, timelineModel);
        LOGGER.debug("[PropertiesPanel] Actor Selector Panel 已初始化");
    }

    // ==================== 主渲染方法 ====================

    @Override
    protected void doRender() {
        // 定期检查选择变化（备用机制）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            checkSelectionChange();
            lastUpdateTime = currentTime;
        }
        
        // =========================
        // SelectionType 驱动的精确切换（v5.0）
        // =========================
        SelectionManager selectionManager = SelectionManager.get();
        SelectionType selectionType = selectionManager.getSelectionType();
        
        switch (selectionType) {
            case PROJECT -> // Project 面板（含 Actor 列表）
                    renderProjectPanel();
            
            case ACTOR -> // Actor 面板（ActorProjectPropertyAdapter）
                    renderActorPanel(selectionManager);
            
            case TRACK -> // Track 面板（TimelineTrackPropertyAdapter）
                    renderTrackPanel(selectionManager);
            
            case CLIP -> // Clip 面板（Entity*ClipPropertyAdapter）
                    renderClipPanel(selectionManager);
        }
        
        // 多选提示
        var multiSelection = selectionManager.getMultiSelection();
        if (multiSelection.size() > 1) {
            UITheme.separator();
            ImGui.textDisabled(String.format("多选 (%d 个对象)", multiSelection.size()));
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("当前选择了多个对象，只显示第一个对象的属性");
            }
        }
    }
    
    /**
     * 渲染 Project 面板（含 Actor 列表）
     */
    private void renderProjectPanel() {
        renderNoSelectionMessage();
    }
    
    /**
     * 渲染 Actor 面板
     */
    private void renderActorPanel(SelectionManager selectionManager) {
        UUID actorId = selectionManager.getSelectedActorId();
        if (actorId == null) {
            // 如果没有 actorId，回退到 Project 面板
            renderProjectPanel();
            return;
        }
        
        // 获取 Actor 对象
        var actorRegistry = ActorRegistryManager.getInstance();

        var actorOpt = actorRegistry.getActor(actorId);
        if (actorOpt.isEmpty()) {
            // Actor 不存在，回退到 Project 面板
            selectionManager.clearSelection();
            renderProjectPanel();
            return;
        }
        
        Actor actor = actorOpt.get();
        
        // 使用 ActorProjectPropertyAdapter
        var adapter = PropertyAdapterRegistry.getAdapterFor(actor);
        if (adapter != null) {
            try {
                UITheme.title("🎭 Actor Properties");
                UITheme.separator();
                boolean modified = adapter.renderProperties(actor);
                if (modified) {
                    var timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
                    if (timelineModel != null) {
                        timelineModel.markDirty();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[PropertiesPanel] 渲染 Actor 面板时出错", e);
                renderErrorMessage(e);
            }
        } else {
            UITheme.label("No adapter for Actor", UITheme.DarkTheme.TEXT_DISABLED);
        }
    }
    
    /**
     * 渲染 Track 面板
     */
    private void renderTrackPanel(SelectionManager selectionManager) {
        var track = selectionManager.getSelectedTrack();
        if (track == null) {
            renderProjectPanel();
            return;
        }
        
        // 使用 TimelineTrackPropertyAdapter
        var adapter = PropertyAdapterRegistry.getAdapterFor(track);
        if (adapter != null) {
            try {
                renderObjectTypeHeader();
                UITheme.separator();
                boolean modified = adapter.renderProperties(track);
                if (modified) {
                    var timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
                    if (timelineModel != null) {
                        timelineModel.markDirty();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[PropertiesPanel] 渲染 Track 面板时出错", e);
                renderErrorMessage(e);
            }
        } else {
            renderNoAdapterMessage();
        }
    }
    
    /**
     * 渲染 Clip 面板
     */
    private void renderClipPanel(SelectionManager selectionManager) {
        var clip = selectionManager.getSelectedClip();
        if (clip == null) {
            renderProjectPanel();
            return;
        }
        
        // 使用对应的 Entity*ClipPropertyAdapter
        var adapter = PropertyAdapterRegistry.getAdapterFor(clip);
        if (adapter != null) {
            try {
                renderObjectTypeHeader();
                UITheme.separator();
                boolean modified = adapter.renderProperties(clip);
                if (modified) {
                    var timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
                    if (timelineModel != null) {
                        timelineModel.markDirty();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[PropertiesPanel] 渲染 Clip 面板时出错", e);
                renderErrorMessage(e);
            }
        } else {
            renderNoAdapterMessage();
        }
    }
    
    /**
     * 选择变化回调
     */
    private void onSelectionChanged(Object newSelection) {
        LOGGER.debug("[PropertiesPanel] Selection changed event: {} → {}",
            currentSelection != null ? currentSelection.getClass().getSimpleName() : "null",
            newSelection != null ? newSelection.getClass().getSimpleName() : "null");
        
        updateSelection(newSelection);
    }
    
    /**
     * 检查选择是否变化
     */
    private void checkSelectionChange() {
        Object newSelection = SelectionManager.get().getCurrentSelection();
        if (newSelection != currentSelection) {
            updateSelection(newSelection);
        }
    }
    
    /**
     * 更新当前选择和适配器
     * <p>注意：v5.0 后，此方法主要用于向后兼容。
     * 新的 SelectionType 系统在 doRender() 中直接处理。
     */
    private void updateSelection(Object newSelection) {
        currentSelection = newSelection;
        currentAdapter = PropertyAdapterRegistry.getAdapterFor(newSelection);
        
        if (newSelection != null) {
            LOGGER.debug("[PropertiesPanel] 选中对象: {} ({})", 
                newSelection.getClass().getSimpleName(),
                currentAdapter != null ? "有适配器" : "无适配器");
        } else {
            LOGGER.debug("[PropertiesPanel] 清除选择");
        }
    }
    
    /**
     * 渲染对象类型标题
     */
    private void renderObjectTypeHeader() {
        if (currentAdapter != null) {
            // 适配器名称（带图标）
            String adapterName = currentAdapter.getAdapterName();
            UITheme.title(adapterName);
        } else if (currentSelection != null) {
            String typeName = currentSelection.getClass().getSimpleName();
            UITheme.label("对象: " + typeName, UITheme.DarkTheme.TEXT);
        }
    }
    
    /**
     * 渲染无选择消息
     */
    private void renderNoSelectionMessage() {
        UITheme.spacingLarge();
        UITheme.title(Icons.CLIPBOARD + " 属性面板");
        //UITheme.spacingSmall();
        
        UITheme.label("未选择对象", UITheme.DarkTheme.TEXT_DISABLED);
        //UITheme.spacingSmall();
        
        ImGui.textWrapped("在时间线或场景中选择一个对象以查看和编辑其属性");
        
        //UITheme.spacing();
        UITheme.separator();
        
        // ========== 🎭 Actors 区块（使用 ActorProjectPropertyAdapter）==========
        // 使用 PropertyAdapter 系统，而不是直接使用 ActorSelectorPanel
        Object actorProjectMarker = "ACTOR_PROJECT";
        var actorAdapter = com.chronoblocks.ui.properties.PropertyAdapterRegistry.getAdapterFor(actorProjectMarker);
        if (actorAdapter != null && actorAdapter.supports(actorProjectMarker)) {
            UITheme.spacing();
            try {
                actorAdapter.renderProperties(actorProjectMarker);
            } catch (Exception e) {
                LOGGER.error("[PropertiesPanel] 渲染 Actor 面板时出错", e);
            }
            UITheme.separator();
        } else {
            // 回退到旧的 ActorSelectorPanel（兼容性）
            ensureActorSelectorInitialized();
            if (actorSelectorPanel != null) {
                UITheme.spacing();
                actorSelectorPanel.render();
                UITheme.separator();
            }
        }
        //UITheme.spacing();
        
        // ========== 支持的对象类型（16+1种）==========
        UITheme.label(Icons.STAR + " 支持的对象类型（17种）", UITheme.DarkTheme.TEXT);
        //UITheme.spacingSmall();
        
        // 时间线系统（3个）
        if (ImGui.collapsingHeader(Icons.ANIMATION + " 时间线系统（3个）")) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text("时间线项目 (TimelineProject)");
            ImGui.bullet(); ImGui.text("时间线序列 (TimelineSequence)");
            ImGui.bullet(); ImGui.text("时间线轨道 (TimelineTrack)");
            ImGui.unindent();
        }
        
        // 核心对象（3个）
        if (ImGui.collapsingHeader(Icons.TARGET + " 核心对象（3个）")) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text("关键帧 (Keyframe)");
            ImGui.bullet(); ImGui.text("容器 (Container)");
            ImGui.bullet(); ImGui.text("动画片段 (AnimationClip)");
            ImGui.unindent();
        }
        
        // 扩展系统（6个）
        if (ImGui.collapsingHeader(Icons.STAR + " 扩展系统（6个）")) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text("特效 (Effect)");
            ImGui.bullet(); ImGui.text("项目环境 (ProjectEnvironment)");
            ImGui.bullet(); ImGui.text("材质库 (MaterialLibrary)");
            ImGui.bullet(); ImGui.text("相机 (Camera)");
            ImGui.bullet(); ImGui.text("后处理 (PostProcessing)");
            ImGui.bullet(); ImGui.text("物理环境 (PhysicsEnvironment)");
            ImGui.unindent();
        }
        
        // 扩展类型（3个）
        if (ImGui.collapsingHeader(Icons.WRENCH + " 扩展类型（3个）")) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text("三维向量 (Vector3f)");
            ImGui.bullet(); ImGui.text("颜色 (Vector4f)");
            ImGui.bullet(); ImGui.text("列表 (List)");
            ImGui.unindent();
        }
        
        // 通用回退（1个）
        if (ImGui.collapsingHeader(Icons.SHIELD + " 通用回退（1个）")) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text("任意对象 (GenericPropertyAdapter)");
            ImGui.unindent();
        }
        
        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();
        
        // 统计信息
        int adapterCount = PropertyAdapterRegistry.getAdapterCount();
        boolean hasFallback = PropertyAdapterRegistry.hasFallbackAdapter();
        
        UITheme.label(String.format(Icons.STATS + " 已注册适配器: %d 个", adapterCount), UITheme.DarkTheme.TEXT);
        
        // 回退适配器状态（使用颜色）
        if (hasFallback) {
            ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f, Icons.SHIELD + " 回退适配器: 已启用");
        } else {
            UITheme.label(Icons.SHIELD + " 回退适配器: 未启用", UITheme.DarkTheme.TEXT_DISABLED);
        }
    }
    
    /**
     * 渲染无适配器消息
     */
    private void renderNoAdapterMessage() {
        //UITheme.spacingLarge();
        UITheme.title(Icons.WARNING + " 无可用的属性编辑器");
        //UITheme.spacingSmall();
        
        String typeName = currentSelection.getClass().getSimpleName();
        String fullClassName = currentSelection.getClass().getName();
        
        // 警告消息（黄色）
        ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, 
            String.format("对象类型 \"%s\" 没有注册属性适配器", typeName));
        
        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();
        
        // 对象信息
        ImGui.text(Icons.PACKAGE + " 对象信息:");
        ImGui.indent();
        ImGui.bullet(); ImGui.text(String.format("类名: %s", typeName));
        ImGui.bullet(); ImGui.text(String.format("完整路径: %s", fullClassName));
        ImGui.bullet(); ImGui.text(String.format("包名: %s", currentSelection.getClass().getPackageName()));
        ImGui.unindent();
        
        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();
        
        // 解决方案
        ImGui.text(Icons.LIGHTBULB + " 解决方案:");
        ImGui.indent();
        ImGui.text(Icons.PLUS + " 创建专用适配器:");
        ImGui.indent();
        ImGui.bulletText("实现 IPropertyAdapter<YourType> 接口");
        ImGui.bulletText("在 PropertyAdapterBootstrap 中注册");
        ImGui.unindent();
        
        //UITheme.spacingSmall();
        
        ImGui.text(Icons.SETTINGS + " 使用通用适配器:");
        ImGui.indent();
        ImGui.bulletText("确保 GenericPropertyAdapter 已注册为回退适配器");
        ImGui.bulletText("检查 PropertyAdapterBootstrap.initialize() 是否已调用");
        ImGui.unindent();
        ImGui.unindent();
        
        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();
        
        // 调试按钮
        if (ImGui.button(Icons.SEARCH + " 查看注册表详情", 200, 25)) {
            LOGGER.info("[PropertiesPanel] 注册表状态:\n{}", 
                PropertyAdapterRegistry.getDebugInfo());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("输出所有已注册适配器的详细信息到日志");
        }
    }
    
    /**
     * 渲染错误消息
     */
    private void renderErrorMessage(Exception e) {
        //UITheme.spacingLarge();
        UITheme.title(Icons.ERROR + " 渲染错误");
        //UITheme.spacingSmall();
        
        // 错误消息（红色）
        ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, "渲染属性时发生错误");
        
        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();
        
        // 错误详情
        ImGui.text(Icons.CLIPBOARD + " 错误详情:");
        ImGui.indent();
        ImGui.bullet(); ImGui.text(String.format("异常类型: %s", e.getClass().getSimpleName()));
        ImGui.bullet(); ImGui.text(String.format("错误消息: %s", e.getMessage() != null ? e.getMessage() : "(无)"));
        ImGui.unindent();
        
        //UITheme.spacing();
        
        // 堆栈跟踪（折叠）
        if (ImGui.collapsingHeader(Icons.SEARCH + " 堆栈跟踪")) {
            ImGui.indent();
            StackTraceElement[] stackTrace = e.getStackTrace();
            for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
                ImGui.bulletText(stackTrace[i].toString());
            }
            if (stackTrace.length > 10) {
                ImGui.text(String.format("... 还有 %d 行", stackTrace.length - 10));
            }
            ImGui.unindent();
        }
        
        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();
        
        // 建议操作
        ImGui.text(Icons.LIGHTBULB + " 建议操作:");
        ImGui.indent();
        ImGui.bulletText("检查对象是否包含所有必需的属性");
        ImGui.bulletText("查看日志获取完整的错误堆栈");
        ImGui.bulletText("确认适配器实现是否正确");
        ImGui.bulletText("尝试重新选择对象");
        ImGui.unindent();
        
        //UITheme.spacing();
        
        // 重新选择按钮
        if (ImGui.button(Icons.REFRESH + " 清除选择并重试", 150, 25)) {
            SelectionManager.get().clearSelection();
            updateSelection(null);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("清除当前选择，尝试解决错误");
        }
    }
    
    // ==================== 生命周期 ====================
    
    @Override
    public void dispose() {
        // 移除监听器
        SelectionManager.get().removeSelectionChangedListener(this::onSelectionChanged);
        super.dispose();
        LOGGER.debug("[PropertiesPanel] 已销毁");
    }
}
