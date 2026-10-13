package com.director.ui.panels;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorRegistryManager;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.PanelId;
import com.director.ui.PanelWindows;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyAdapterContext;
import com.director.ui.properties.PropertyAdapterRegistry;
import com.director.ui.properties.PropertyContext;
import com.director.ui.properties.PropertyContextService;
import com.director.ui.selection.SelectionManager;
import com.director.timeline.container.ContainerTimelineService;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 🎨 智能动态属性面板 v5.0 (现代化增强版) - Director Properties System
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
 *   <tr><td rowspan="2"><b>核心对象</b></td>
 *       <td>Keyframe</td><td>KeyframePropertyAdapter</td></tr>
 *   <tr><td>Container</td><td>ContainerPropertyAdapter</td></tr>
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
 * DirectorServices.get().uiSelection().setCurrentSelection(kf);
 * // → 自动显示关键帧属性编辑器
 * 
 * // 示例 2: 选择相机
 * Camera camera = ...;
 * DirectorServices.get().uiSelection().setCurrentSelection(camera);
 * // → 自动显示相机属性编辑器（FOV、景深、镜头特效等）
 * 
 * // 示例 3: 选择未知对象
 * MyCustomObject obj = ...;
 * DirectorServices.get().uiSelection().setCurrentSelection(obj);
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
 * @author Director Team
 */
public class PropertiesPanel extends AbstractPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesPanel.class);

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }
    
    // ==================== 当前选择 ====================
    
    /** 当前选择的对象 */
    private Object currentSelection;
    
    /** 当前适配器 */
    private IPropertyAdapter<Object> currentAdapter;
    
    /** 上次更新时间（用于检测选择变化） */
    private long lastUpdateTime = 0;
    
    /** 更新间隔（毫秒） */
    private static final long UPDATE_INTERVAL = 100;
    
    
    // ==================== UI 状态（精简版） ====================
    
    // ==================== 面板配置 ====================

    @Override
    public String getPanelName() {
        return PanelId.PROPERTIES;
    }
    
    @Override
    public String getWindowName() {
        return PanelWindows.properties();
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
        DirectorServices.get().uiSelection().addSelectionChangedListener(this::onSelectionChanged);
        
        
        LOGGER.info("[PropertiesPanel] 已初始化智能属性面板");
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
        // PropertyContext 仲裁：只消费 getCurrentContext / getContextTarget
        // =========================
        PropertyContextService svc = PropertyContextService.get();
        Object actorInspection=svc.getActorInspection();
        if(actorInspection!=null) {
            var adapter=PropertyAdapterRegistry.getAdapterFor(null,actorInspection);
            if(adapter!=null) {
                try {
                    UITheme.title(adapter.getAdapterName());
                    if (adapter.renderProperties(actorInspection, PropertyAdapterContext.of(1f, false))) {
                        var model = DirectorServices.get().containerTimeline().getTimelineModel();
                        if (model != null) model.markDirty();
                    }
                } catch (Exception e) {
                    LOGGER.error("[PropertiesPanel] 渲染演员属性时出错", e);
                    renderErrorMessage(e);
                }
                return;
            }
        }
        PropertyContext ctx = svc.getCurrentContext();
        Object target = svc.getContextTarget();

        if (ctx == PropertyContext.TOOL) {
            if (target != null) {
                var ad = PropertyAdapterRegistry.getAdapterFor(ctx, target);
                if (ad != null) {
                    currentSelection = target;
                    currentAdapter = ad;
                    UITheme.title(ad.getAdapterName());
                    ad.renderProperties(target, PropertyAdapterContext.of(1f, false));
                    renderMultiSelectHint(DirectorServices.get().uiSelection());
                    return;
                }
            }
            renderNoToolMessage();
            return;
        }
        if (ctx == PropertyContext.NONE) {
            renderProjectPanel();
            renderMultiSelectHint(DirectorServices.get().uiSelection());
            return;
        }
        if (ctx == PropertyContext.TIMELINE || ctx == PropertyContext.WORLD) {
            if (target == null || svc.isTimelineTargetRenderedAsProject(target)) {
                renderProjectPanel();
                renderMultiSelectHint(DirectorServices.get().uiSelection());
                return;
            }
            var ad = PropertyAdapterRegistry.getAdapterFor(ctx, target);
            if (ad == null) {
                currentSelection = target;
                currentAdapter = null;
                renderNoAdapterMessage();
                renderMultiSelectHint(DirectorServices.get().uiSelection());
                return;
            }
            currentSelection = target;
            currentAdapter = ad;
            var adapterCtx = PropertyAdapterContext.of(1f, false);
            try {
                renderObjectTypeHeader();
                boolean modified = ad.renderProperties(target, adapterCtx);
                if (modified) {
                    var timelineModel = DirectorServices.get().containerTimeline().getTimelineModel();
                    if (timelineModel != null) timelineModel.markDirty();
                }
            } catch (Exception e) {
                LOGGER.error("[PropertiesPanel] 渲染 TIMELINE 属性时出错", e);
                renderErrorMessage(e);
            }
            renderMultiSelectHint(DirectorServices.get().uiSelection());
            return;
        }

        renderProjectPanel();
        renderMultiSelectHint(DirectorServices.get().uiSelection());
    }

    /** 多选时在底部显示提示 */
    private void renderMultiSelectHint(SelectionManager sm) {
        var multi = sm.getMultiSelection();
        if (multi.size() > 1) {
            UITheme.separator();
            ImGui.textDisabled(tr(DirectorLang.Properties.MULTI_SELECT, multi.size()));
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.Properties.MULTI_SELECT_TOOLTIP));
            }
        }
    }
    
    /**
     * Render Project panel
     */
    private void renderProjectPanel() {
        renderNoSelectionMessage();
    }

    /**
     * 工具上下文下无当前工具时显示
     */
    private void renderNoToolMessage() {
        ImGui.textColored(1.0f, 1.0f, 0.0f, 1.0f, Icons.WARNING + " " + tr(DirectorLang.Properties.NO_TOOL));
        UITheme.label(tr(DirectorLang.Properties.SELECT_TOOL_HINT), UITheme.DarkTheme.TEXT);
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
        
        // 使用 ActorProjectPropertyAdapter（Actor 路径暂无 PropertyContext，传 null）
        var adapter = PropertyAdapterRegistry.getAdapterFor(null, actor);
        if (adapter != null) {
            try {
                UITheme.title(tr(DirectorLang.Properties.ACTOR_TITLE));
                boolean modified = adapter.renderProperties(actor, PropertyAdapterContext.of(1f, false));
                if (modified) {
                    var timelineModel = DirectorServices.get().containerTimeline().getTimelineModel();
                    if (timelineModel != null) {
                        timelineModel.markDirty();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[PropertiesPanel] 渲染 Actor 面板时出错", e);
                renderErrorMessage(e);
            }
        } else {
            UITheme.label(tr(DirectorLang.Properties.NO_ACTOR_ADAPTER), UITheme.DarkTheme.TEXT_DISABLED);
        }
    }
    
    /**
     * Selection change callback.
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
        Object newSelection = DirectorServices.get().uiSelection().getCurrentSelection();
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
            UITheme.label(tr(DirectorLang.Properties.OBJECT_LABEL, typeName), UITheme.DarkTheme.TEXT);
            UITheme.separator();
        }
    }
    
    /**
     * 渲染无选择消息
     */
    private void renderNoSelectionMessage() {
        
        UITheme.label(tr(DirectorLang.Properties.NO_SELECTION), UITheme.DarkTheme.TEXT_DISABLED);
        
        ImGui.textWrapped(tr(DirectorLang.Properties.SELECT_OBJECT_HINT));
        
        UITheme.separator();
        
        UITheme.label(Icons.STAR + " " + tr(DirectorLang.Properties.SUPPORTED_TYPES_TITLE), UITheme.DarkTheme.TEXT);
        
        if (ImGui.collapsingHeader(Icons.ANIMATION + " " + tr(DirectorLang.Properties.GROUP_TIMELINE))) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_TIMELINE_PROJECT));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_TIMELINE_SEQUENCE));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_TIMELINE_TRACK));
            ImGui.unindent();
        }
        
        if (ImGui.collapsingHeader(Icons.TARGET + " " + tr(DirectorLang.Properties.GROUP_CORE))) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_KEYFRAME));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_CONTAINER));
            ImGui.unindent();
        }
        
        if (ImGui.collapsingHeader(Icons.STAR + " " + tr(DirectorLang.Properties.GROUP_EXTENDED))) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_EFFECT));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_PROJECT_ENV));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_MATERIAL_LIBRARY));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_CAMERA));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_POST_PROCESSING));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_PHYSICS_ENV));
            ImGui.unindent();
        }
        
        if (ImGui.collapsingHeader(Icons.WRENCH + " " + tr(DirectorLang.Properties.GROUP_TYPES))) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_VECTOR3F));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_COLOR));
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_LIST));
            ImGui.unindent();
        }
        
        if (ImGui.collapsingHeader(Icons.SHIELD + " " + tr(DirectorLang.Properties.GROUP_FALLBACK))) {
            ImGui.indent();
            ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.TYPE_GENERIC));
            ImGui.unindent();
        }
        
        UITheme.separator();
        
        int adapterCount = PropertyAdapterRegistry.getAdapterCount();
        boolean hasFallback = PropertyAdapterRegistry.hasFallbackAdapter();
        
        UITheme.label(Icons.STATS + " " + tr(DirectorLang.Properties.ADAPTERS_REGISTERED, adapterCount), UITheme.DarkTheme.TEXT);
        
        if (hasFallback) {
            ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f, Icons.SHIELD + " " + tr(DirectorLang.Properties.FALLBACK_ENABLED));
        } else {
            UITheme.label(Icons.SHIELD + " " + tr(DirectorLang.Properties.FALLBACK_DISABLED), UITheme.DarkTheme.TEXT_DISABLED);
        }
    }
    
    /**
     * 渲染无适配器消息
     */
    private void renderNoAdapterMessage() {
        //UITheme.spacingLarge();
        UITheme.title(Icons.WARNING + " " + tr(DirectorLang.Properties.NO_ADAPTER_TITLE));
        
        String typeName = currentSelection.getClass().getSimpleName();
        String fullClassName = currentSelection.getClass().getName();
        
        ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f,
            tr(DirectorLang.Properties.NO_ADAPTER_MESSAGE, typeName));
        
        UITheme.separator();
        
        ImGui.text(Icons.PACKAGE + " " + tr(DirectorLang.Properties.OBJECT_INFO));
        ImGui.indent();
        ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.CLASS_NAME, typeName));
        ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.FULL_CLASS_NAME, fullClassName));
        ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.PACKAGE_NAME, currentSelection.getClass().getPackageName()));
        ImGui.unindent();
        
        UITheme.separator();
        
        ImGui.text(Icons.LIGHTBULB + " " + tr(DirectorLang.Properties.SOLUTION));
        ImGui.indent();
        ImGui.text(Icons.PLUS + " " + tr(DirectorLang.Properties.CREATE_ADAPTER));
        ImGui.indent();
        ImGui.bulletText(tr(DirectorLang.Properties.CREATE_ADAPTER_IMPL));
        ImGui.bulletText(tr(DirectorLang.Properties.CREATE_ADAPTER_REGISTER));
        ImGui.unindent();
        
        ImGui.text(Icons.SETTINGS + " " + tr(DirectorLang.Properties.USE_GENERIC_ADAPTER));
        ImGui.indent();
        ImGui.bulletText(tr(DirectorLang.Properties.USE_GENERIC_ENSURE));
        ImGui.bulletText(tr(DirectorLang.Properties.USE_GENERIC_INIT));
        ImGui.unindent();
        ImGui.unindent();
        
        UITheme.separator();
        
        if (ImGui.button(Icons.SEARCH + " " + tr(DirectorLang.Properties.VIEW_REGISTRY), 200, 25)) {
            LOGGER.info("[PropertiesPanel] 注册表状态:\n{}", 
                PropertyAdapterRegistry.getDebugInfo());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.VIEW_REGISTRY_TOOLTIP));
        }
    }
    
    /**
     * 渲染错误消息
     */
    private void renderErrorMessage(Exception e) {
        //UITheme.spacingLarge();
        UITheme.title(Icons.ERROR + " " + tr(DirectorLang.Properties.RENDER_ERROR_TITLE));
        
        ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, tr(DirectorLang.Properties.RENDER_ERROR_MESSAGE));
        
        UITheme.separator();
        
        ImGui.text(Icons.CLIPBOARD + " " + tr(DirectorLang.Properties.ERROR_DETAILS));
        ImGui.indent();
        ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.EXCEPTION_TYPE, e.getClass().getSimpleName()));
        ImGui.bullet(); ImGui.text(tr(DirectorLang.Properties.ERROR_MESSAGE,
                e.getMessage() != null ? e.getMessage() : tr(DirectorLang.Properties.NO_MESSAGE)));
        ImGui.unindent();
        
        if (ImGui.collapsingHeader(Icons.SEARCH + " " + tr(DirectorLang.Properties.STACK_TRACE))) {
            ImGui.indent();
            StackTraceElement[] stackTrace = e.getStackTrace();
            for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
                ImGui.bulletText(stackTrace[i].toString());
            }
            if (stackTrace.length > 10) {
                ImGui.text(tr(DirectorLang.Properties.STACK_TRACE_MORE, stackTrace.length - 10));
            }
            ImGui.unindent();
        }
        
        UITheme.separator();
        
        ImGui.text(Icons.LIGHTBULB + " " + tr(DirectorLang.Properties.SUGGESTIONS));
        ImGui.indent();
        ImGui.bulletText(tr(DirectorLang.Properties.SUGGEST_CHECK_PROPERTIES));
        ImGui.bulletText(tr(DirectorLang.Properties.SUGGEST_CHECK_LOG));
        ImGui.bulletText(tr(DirectorLang.Properties.SUGGEST_CHECK_ADAPTER));
        ImGui.bulletText(tr(DirectorLang.Properties.SUGGEST_RESELECT));
        ImGui.unindent();
        
        if (ImGui.button(Icons.REFRESH + " " + tr(DirectorLang.Properties.CLEAR_SELECTION_RETRY), 150, 25)) {
            DirectorServices.get().uiSelection().clearSelection();
            updateSelection(null);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tr(DirectorLang.Properties.CLEAR_SELECTION_TOOLTIP));
        }
    }
    
    // ==================== 生命周期 ====================
    
    @Override
    protected void onContentUnavailable() {
        com.director.ui.lighting.PointLightParameterEditing.finish();
        com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
    }

    @Override
    public void dispose() {
        com.director.ui.lighting.PointLightParameterEditing.finish();
        com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
        // 移除监听器
        DirectorServices.get().uiSelection().removeSelectionChangedListener(this::onSelectionChanged);
        super.dispose();
        LOGGER.debug("[PropertiesPanel] 已销毁");
    }
}
