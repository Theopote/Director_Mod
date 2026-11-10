package com.director.ui.panels;

import com.director.actor.ActorRegistryManager;
import com.director.core.DirectorServices;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.PanelId;
import com.director.ui.PanelWindows;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyAdapterContext;
import com.director.ui.properties.PropertyAdapterRegistry;
import com.director.ui.properties.PropertyContext;
import com.director.ui.properties.PropertyContextService;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.camera.path.CameraPathType;
import com.director.ui.aware.ICommandsAware;
import com.director.ui.commands.UICommandFacade;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.TimelineSelectionProperties;
import com.director.ui.properties.adapters.ActorProjectPropertyAdapter;
import com.director.ui.properties.adapters.OrbitPathClipPropertyAdapter;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


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
public class PropertiesPanel extends AbstractPanel implements ICommandsAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesPanel.class);
    private static final DateTimeFormatter SAVE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private UICommandFacade commands;
    private OrbitPathClipPropertyAdapter orbitInspector;

    @Override
    public void setCommands(UICommandFacade commands) {
        this.commands = commands;
    }


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
        OrbitPathClipPropertyAdapter previous = orbitInspector;
        orbitInspector = null;
        try {
            renderInspector();
        } finally {
            if (previous != null && previous != orbitInspector) {
                previous.finishEditing();
            }
        }
    }

    private void renderInspector() {
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
        if(com.director.ui.timeline.render.ActorRootKeyPropertyUI.render(DirectorServices.get().containerTimeline().getTimelineModel())) return;
        if(com.director.ui.actor.pose.ActorKeySelectionPropertyUI.render(DirectorServices.get().containerTimeline().getTimelineModel())) return;
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
                    return;
                }
            }
            renderNoToolMessage();
            return;
        }
        if (ctx == PropertyContext.NONE) {
            renderProjectPanel();
            return;
        }
        if (ctx == PropertyContext.TIMELINE || ctx == PropertyContext.WORLD) {
            if (target == null) {
                renderProjectPanel();
                return;
            }
            if (ctx == PropertyContext.TIMELINE && TimelineSelectionProperties.render(
                    DirectorServices.get().containerTimeline().getTimelineModel(), target)) {
                return;
            }
            var ad = PropertyAdapterRegistry.getAdapterFor(ctx, target);
            if (ad == null) {
                currentSelection = target;
                currentAdapter = null;
                renderNoAdapterMessage();
                return;
            }
            currentSelection = target;
            currentAdapter = ad;
            var adapterCtx = PropertyAdapterContext.of(1f, false);
            try {
                if (target instanceof com.director.timeline.model.entity.EntityClip actorClip) {
                    if (renderActorClipContext(actorClip)) return;
                } else if (!(target instanceof com.director.timeline.model.TimelineTrack track
                        && track.getType() == com.director.timeline.model.TrackType.ACTOR)) {
                    renderObjectTypeHeader();
                }
                if ((IPropertyAdapter<?>) ad instanceof OrbitPathClipPropertyAdapter orbit) {
                    orbitInspector = orbit;
                }
                boolean modified = ad.renderProperties(target, adapterCtx);
                if (modified) {
                    var timelineModel = DirectorServices.get().containerTimeline().getTimelineModel();
                    if (timelineModel != null) timelineModel.markDirty();
                }
            } catch (Exception e) {
                LOGGER.error("[PropertiesPanel] 渲染 TIMELINE 属性时出错", e);
                renderErrorMessage(e);
            }
            return;
        }

        renderProjectPanel();
    }

    private void renderProjectPanel() {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        var actors = ActorRegistryManager.getInstance();
        UITheme.title(tr(DirectorLang.Properties.Overview.TITLE));

        if (model == null) {
            ImGui.textDisabled(tr(DirectorLang.Properties.AdapterActor.Project.TIMELINE_NOT_READY));
            return;
        }

        long cameras = model.getTracks().stream().filter(track -> track.getType() == TrackType.CAMERA).count();
        PropertyRow.value(tr(DirectorLang.Properties.Overview.DURATION),
                tr(DirectorLang.Properties.Overview.SECONDS, model.getDuration()));
        PropertyRow.value(tr(DirectorLang.Properties.Overview.CAMERAS), Long.toString(cameras));
        PropertyRow.value(tr(DirectorLang.Properties.Overview.ACTORS), Integer.toString(actors.size()));
        PropertyRow.value(tr(DirectorLang.Properties.Overview.TRACKS), Integer.toString(model.getTrackCount()));
        var lastSaved = model.getLastTimelineSave();
        PropertyRow.value(tr(DirectorLang.Properties.Overview.LAST_SAVE),
                lastSaved == null ? tr(DirectorLang.Properties.Overview.NEVER_SAVED)
                        : SAVE_TIME.format(lastSaved.atZone(ZoneId.systemDefault())));

        UITheme.separator();
        boolean busy = model.isPlaying() || model.isScrubbing();
        var timeline = commands == null ? null : commands.timeline();
        ImGui.beginDisabled(busy || timeline == null);
        if (ImGui.button(tr(DirectorLang.Properties.Overview.NEW_CAMERA))) {
            timeline.addCameraTrack(CameraPathType.CUSTOM);
        }
        ImGui.endDisabled();

        IPropertyAdapter<?> adapter = PropertyAdapterRegistry.getAdapterFor("ACTOR_PROJECT");
        if (adapter instanceof ActorProjectPropertyAdapter actorProject) {
            ImGui.beginDisabled(busy);
            boolean add = ImGui.button(tr(DirectorLang.Properties.AdapterActor.Project.ADD_ACTOR));
            ImGui.endDisabled();
            actorProject.renderCreateActorDialog(add);
        }
    }

    /**
     * 工具上下文下无当前工具时显示
     */
    private void renderNoToolMessage() {
        ImGui.textColored(1.0f, 1.0f, 0.0f, 1.0f, Icons.WARNING + " " + tr(DirectorLang.Properties.NO_TOOL));
        UITheme.label(tr(DirectorLang.Properties.SELECT_TOOL_HINT), UITheme.DarkTheme.TEXT);
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
    private boolean renderActorClipContext(com.director.timeline.model.entity.EntityClip clip) {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        var wrapper = clip.getTimelineClip();
        var track = wrapper == null ? null : wrapper.getParentTrack();
        if (model == null || track == null || !model.getAllTracks().contains(track)
                || !track.getClips().contains(wrapper)) return false;

        UITheme.title(clip.getName());
        if (ImGui.smallButton(tr("gui.director.actor.clip_inspector.open_track", track.getName()))) {
            model.selectItem(track, false);
            DirectorServices.get().uiSelection().selectTrack(track);
            return true;
        }

        ImGui.beginDisabled(track.isLocked() || model.isPlaying() || model.isScrubbing()
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging());
        if (ImGui.checkbox(tr("gui.director.actor.clip_inspector.mute_clip"), clip.isMuted())) {
            boolean before = clip.isMuted();
            com.director.ui.timeline.commands.TimelineCommandBridge.execute(
                    new com.director.ui.timeline.commands.StateChangeCommand(model,
                            tr("gui.director.actor.clip_inspector.mute_clip"),
                            () -> clip.setMuted(before), () -> clip.setMuted(!before)));
        }
        ImGui.endDisabled();
        return false;
    }

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
        finishOrbitInspector();
        com.director.ui.lighting.PointLightParameterEditing.finish();
        // Shared actor drafts remain valid in the timeline inspector.
    }

    @Override
    public void dispose() {
        finishOrbitInspector();
        com.director.ui.lighting.PointLightParameterEditing.finish();
        // Shared actor drafts remain valid in the timeline inspector.
        // 移除监听器
        DirectorServices.get().uiSelection().removeSelectionChangedListener(this::onSelectionChanged);
        super.dispose();
        LOGGER.debug("[PropertiesPanel] 已销毁");
    }

    private void finishOrbitInspector() {
        if (orbitInspector != null) {
            orbitInspector.finishEditing();
            orbitInspector = null;
        }
    }
}
