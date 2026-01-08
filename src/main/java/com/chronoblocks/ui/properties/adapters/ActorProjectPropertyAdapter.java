package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRegistryManager;
import com.chronoblocks.actor.ActorType;
import com.chronoblocks.actor.query.ActorUsageQuery;
import com.chronoblocks.ui.actor.selection.ActorSelectionHub;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.selection.SelectionManager;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.entity.EntitySpawnClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTimelineClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ActorProjectPropertyAdapter v1.0
 *
 * <p>Project-level 属性面板：管理项目中的所有 Actor（演员）
 *
 * <p>职责边界：
 * <ul>
 *   <li>✅ 显示 Actor 列表</li>
 *   <li>✅ 新建 Actor</li>
 *   <li>✅ 删除 Actor</li>
 *   <li>✅ 编辑 Actor 的基础属性（name / type）</li>
 *   <li>✅ 将 Actor 设为 Selection</li>
 *   <li>❌ 不编辑 Transform / LookAt / Animation（这些在 Clip 面板）</li>
 *   <li>❌ 不操作 Timeline 播放</li>
 *   <li>❌ 不直接创建 Clip（SpawnClip 已在创建 Actor 时由上层处理）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>Actor 是 Project 级资产，生命周期 > Timeline</li>
 *   <li>不污染 Track / Clip 编辑逻辑</li>
 *   <li>不与 Gizmo 强耦合</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ActorProjectPropertyAdapter implements IPropertyAdapter<Object> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorProjectPropertyAdapter.class);
    private static final float DEFAULT_CLIP_DURATION = 5.0f;
    
    // UI state
    private UUID selectedActorId;
    
    // Create popup state
    private boolean createPopupOpen = false;
    private final ImString newActorName = new ImString("Actor", 256);
    private ActorType newActorType = ActorType.VILLAGER;
    
    // Edit state
    private final ImString editingActorName = new ImString("", 256);
    
    // Delete confirm state
    private UUID deletingActorId = null;
    private boolean deleteConfirmOpen = false;
    
    // =========================================================
    // IPropertyAdapter implementation
    // =========================================================
    
    @Override
    public Class<Object> getTargetType() {
        return Object.class;
    }
    
    @Override
    public boolean supports(Object obj) {
        // 支持两种情况：
        // 1. 特定标记对象 "ACTOR_PROJECT"（用于无选择时显示）
        // 2. Actor 对象（用于选中 Actor 时显示）
        if (obj == null) {
            return false; // null 由 PropertiesPanel 的 renderNoSelectionMessage 处理
        }
        if ("ACTOR_PROJECT".equals(obj)) {
            return true;
        }
        return obj instanceof Actor;
    }
    
    @Override
    public boolean renderProperties(Object obj) {
        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();

        boolean modified = false;
        
        // 如果传入的是 Actor 对象，直接选中它
        if (obj instanceof Actor actor) {
            selectedActorId = actor.getActorId();
        }
        
        // 标题
        UITheme.title("🎭 Actors");
        
        // Actor 列表
        modified |= renderActorList(actorRegistry);
        
        // 创建 Actor 按钮
        ImGui.spacing();
        if (ImGui.button("+ Add Actor")) {
            openCreateActorPopup();
        }
        
        // 创建 Actor 弹窗
        renderCreateActorPopup(actorRegistry);
        
        // 选中的 Actor 编辑器
        if (selectedActorId != null) {
            modified |= renderSelectedActorEditor(actorRegistry);
        }
        
        // 删除确认弹窗
        renderDeleteConfirmPopup(actorRegistry);
        
        return modified;
    }
    
    @Override
    public String getAdapterName() {
        return "Actor Project Adapter";
    }
    
    // =========================================================
    // Actor list
    // =========================================================
    
    private boolean renderActorList(ActorRegistry actorRegistry) {
        // 使用 UITheme 的方式显示分隔符文本
        UITheme.label("Actor List", UITheme.DarkTheme.TEXT);
        UITheme.separator();
        
        List<Actor> actors = actorRegistry.getAllActors();
        if (actors.isEmpty()) {
            ImGui.textDisabled("No actors in project.");
            return false;
        }
        
        boolean modified = false;
        
        for (Actor actor : actors) {
            boolean selected = actor.getActorId().equals(selectedActorId);
            
            int flags = ImGuiTreeNodeFlags.Leaf
                      | ImGuiTreeNodeFlags.NoTreePushOnOpen
                      | (selected ? ImGuiTreeNodeFlags.Selected : 0);
            
            String displayText = String.format("%s  (%s)", actor.getName(), actor.getType().name());
            
            ImGui.treeNodeEx(actor.getActorId().toString(), flags, displayText);
            
            if (ImGui.isItemClicked()) {
                selectActor(actor);
                modified = true;
            }
            
            // 右键菜单：编辑/删除
            if (ImGui.beginPopupContextItem("ActorContextMenu_" + actor.getActorId())) {
                if (ImGui.selectable("Edit")) {
                    selectActor(actor);
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.selectable("Delete")) {
                    openDeleteConfirm(actor.getActorId());
                    ImGui.closeCurrentPopup();
                }
                ImGui.endPopup();
            }
        }
        
        return modified;
    }
    
    // =========================================================
    // Selected actor editor
    // =========================================================
    
    private boolean renderSelectedActorEditor(ActorRegistry actorRegistry) {
        Optional<Actor> actorOpt = actorRegistry.getActor(selectedActorId);
        if (actorOpt.isEmpty()) {
            selectedActorId = null;
            return false;
        }
        
        Actor actor = actorOpt.get();
        boolean modified = false;
        
        UITheme.separator();
        UITheme.label("Actor Properties", UITheme.DarkTheme.TEXT);
        
        // ---- Name ----
        editingActorName.set(actor.getName());
        if (ImGui.inputText("Name", editingActorName, ImGuiInputTextFlags.EnterReturnsTrue)) {
            String newName = editingActorName.get();
            if (newName != null && !newName.isBlank() && !newName.equals(actor.getName())) {
                actor.setName(newName);
                modified = true;
                LOGGER.debug("[ActorProjectPropertyAdapter] Actor name changed: {}", newName);
            }
        }
        
        // ---- Type ----
        String currentTypeName = actor.getType().name();
        if (ImGui.beginCombo("Type", currentTypeName)) {
            for (ActorType type : ActorType.values()) {
                boolean isSelected = type == actor.getType();
                if (ImGui.selectable(type.name(), isSelected)) {
                    actor.setType(type);
                    modified = true;
                    LOGGER.debug("[ActorProjectPropertyAdapter] Actor type changed: {}", type);
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
        
        ImGui.spacing();
        
        // ---- Actions ----
        if (ImGui.button("Focus in World")) {
            ActorSelectionHub.selectActor(actor.getActorId());
            LOGGER.debug("[ActorProjectPropertyAdapter] Focus actor in world: {}", actor.getActorId());
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("Place at Camera")) {
            placeActorAtCamera(actor);
            LOGGER.debug("[ActorProjectPropertyAdapter] Place actor at camera: {}", actor.getActorId());
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("Delete Actor")) {
            openDeleteConfirm(actor.getActorId());
        }
        
        // 提示信息
        ImGui.textDisabled("💡 Tip: Right-click in world to place actor");
        
        // ---- Usage Information (只读) ----
        renderActorUsage(actor);
        
        return modified;
    }
    
    // =========================================================
    // Selection handling
    // =========================================================
    
    private void selectActor(Actor actor) {
        if (actor == null) {
            selectedActorId = null;
            return;
        }
        
        this.selectedActorId = actor.getActorId();
        
        // 通知全局 Selection（明确设置为 ACTOR 类型）
        SelectionManager selectionManager = SelectionManager.get();
        // ⭐ 关键：使用 selectActor() 明确设置 SelectionType.ACTOR
        selectionManager.selectActor(actor.getActorId());

        LOGGER.debug("[ActorProjectPropertyAdapter] Selected actor: {}", actor.getActorId());
    }
    
    // =========================================================
    // Create actor
    // =========================================================
    
    private void openCreateActorPopup() {
        newActorName.set("Actor");
        newActorType = ActorType.VILLAGER;
        createPopupOpen = true;
        ImGui.openPopup("Create Actor");
    }
    
    private void renderCreateActorPopup(ActorRegistry actorRegistry) {
        if (!createPopupOpen) {
            return;
        }
        
        if (ImGui.beginPopupModal("Create Actor")) {
            ImGui.text("Create New Actor");
            ImGui.separator();
            
            // Name input
            if (ImGui.inputText("Name", newActorName, ImGuiInputTextFlags.EnterReturnsTrue)) {
                // Enter key pressed, but we'll handle it in the Create button
            }
            
            // Type combo
            String currentTypeName = newActorType.name();
            if (ImGui.beginCombo("Type", currentTypeName)) {
                for (ActorType type : ActorType.values()) {
                    boolean selected = type == newActorType;
                    if (ImGui.selectable(type.name(), selected)) {
                        newActorType = type;
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus();
                    }
                }
                ImGui.endCombo();
            }
            
            ImGui.separator();
            
            // Buttons
            if (ImGui.button("Create")) {
                String name = newActorName.get();
                if (name != null && !name.isBlank()) {
                    // 使用新的放置系统
                    com.chronoblocks.ui.actor.placement.ActorPlacementController placementController =
                            com.chronoblocks.ui.actor.placement.ActorPlacementController.getInstance();
                    
                    if (placementController.startPlacement(name, newActorType)) {
                        LOGGER.info("[ActorProjectPropertyAdapter] 开始放置 Actor: {} ({})", name, newActorType);
                    } else {
                        LOGGER.warn("[ActorProjectPropertyAdapter] 无法开始放置 Actor");
                    }
                }
                
                ImGui.closeCurrentPopup();
                createPopupOpen = false;
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
                createPopupOpen = false;
            }
            
            ImGui.endPopup();
        }
    }
    
    // =========================================================
    // Create Actor and Track（原子性操作）
    // =========================================================
    
    /**
     * 创建 Actor 和 Track（原子性操作）
     * <p>与 ActorSelectorPanel.createActorAndTrack() 保持一致
     *
     * @param actorName Actor 名称
     * @param actorType Actor 类型
     */
    private void createActorAndTrack(String actorName, ActorType actorType) {
        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();

        try {
            // 1️⃣ Create Actor
            Actor actor = actorRegistry.createActor(actorName.trim(), actorType);
            ActorRef ref = new ActorRef(actor.getActorId());

            // 2️⃣ Create EntityTrack
            EntityTrack entityTrack = new EntityTrack();
            entityTrack.setActorRef(ref);
            entityTrack.setDisplayName(actor.getName());

            // 3️⃣ Create TimelineTrack with EntityTrack payload
            TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
            if (timelineModel == null) {
                LOGGER.warn("[ActorProjectPropertyAdapter] TimelineModel 未初始化，无法创建 Track");
                // 即使没有 TimelineModel，也选中 Actor
                selectActor(actor);
                return;
            }

            TimelineTrack timelineTrack = new TimelineTrack(actor.getName(), TrackType.ENTITY);
            timelineTrack.setEntityTrackPayload(entityTrack);

            // 4️⃣ Add to TimelineModel
            timelineModel.addTrack(timelineTrack);

            // 5️⃣ Insert SpawnClip at current time
            float currentTime = timelineModel.getCurrentTime();
            EntitySpawnClip spawnClip = new EntitySpawnClip(
                    "Spawn",
                    currentTime,
                    currentTime + DEFAULT_CLIP_DURATION
            );
            spawnClip.setVisibleOnSpawn(true);
            entityTrack.addClip(spawnClip);
            EntityTimelineClip timelineClip = new EntityTimelineClip(spawnClip, entityTrack.getColorRgba());
            timelineTrack.addClip(timelineClip);

            // 6️⃣ Mark model as dirty (trigger save)
            timelineModel.markDirty();

            // 7️⃣ Select Actor（这会触发 PropertiesPanel 切换到 Actor 面板）
            selectActor(actor);

            LOGGER.info("[ActorProjectPropertyAdapter] 成功创建 Actor: {} ({}), EntityTrack 已添加到时间线",
                    actor.getName(), actorType);
        } catch (Exception e) {
            LOGGER.error("[ActorProjectPropertyAdapter] 创建 Actor 失败", e);
            // TODO: 如果实现了撤销/重做系统，这里应该回滚所有操作
        }
    }
    
    // =========================================================
    // Actor Usage (只读查询)
    // =========================================================
    
    /**
     * 渲染 Actor 使用情况（只读）
     * <p>显示该 Actor 在哪些 Timeline/Track 中被使用
     *
     * @param actor Actor 实例
     */
    private void renderActorUsage(Actor actor) {
        if (actor == null) {
            return;
        }
        
        UITheme.separator();
        UITheme.label("Used In", UITheme.DarkTheme.TEXT);
        
        TimelineModel timeline = ContainerTimelineService.getInstance().getTimelineModel();
        if (timeline == null) {
            ImGui.textDisabled("No active timeline.");
            return;
        }
        
        // 查询使用该 Actor 的所有 EntityTrack
        List<EntityTrack> entityTracks = ActorUsageQuery.findTracksUsingActor(
                timeline,
                actor.getActorId()
        );
        
        if (entityTracks.isEmpty()) {
            ImGui.textDisabled("This actor is not used in the current timeline.");
            ImGui.sameLine();
            if (ImGui.smallButton("Delete Actor##unused")) {
                openDeleteConfirm(actor.getActorId());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("This actor is not used in any track. Click to delete it.");
            }
            return;
        }
        
        // 查询对应的 TimelineTrack（用于 Focus Track 功能）
        List<TimelineTrack> timelineTracks = ActorUsageQuery.findTimelineTracksUsingActor(
                timeline,
                actor.getActorId()
        );
        
        // 显示 Timeline 名称（目前只有一个 Timeline，未来可扩展）
        String timelineName = "Current Timeline";
        // 如果 TimelineModel 有 getName() 方法，可以使用它
        // timelineName = timeline.getName() != null ? timeline.getName() : "Unnamed Timeline";
        
        ImGui.bulletText(String.format("Timeline: %s", timelineName));
        
        // 显示每个 Track
        ImGui.indent();
        for (int i = 0; i < entityTracks.size(); i++) {
            EntityTrack entityTrack = entityTracks.get(i);
            TimelineTrack timelineTrack = i < timelineTracks.size() ? timelineTracks.get(i) : null;
            
            String trackDisplayName = entityTrack.getDisplayName();
            if (trackDisplayName == null || trackDisplayName.isBlank()) {
                trackDisplayName = timelineTrack != null && timelineTrack.getName() != null
                        ? timelineTrack.getName()
                        : "Entity Track";
            }
            
            ImGui.text(String.format("- Track: %s", trackDisplayName));
            
            // Focus Track 按钮
            if (timelineTrack != null) {
                // 使用 track 的 hashCode 作为唯一标识符
                String buttonId = "Focus Track##" + timelineTrack.hashCode();
                if (ImGui.smallButton(buttonId)) {
                    focusTrack(timelineTrack);
                }
            }
        }
        ImGui.unindent();
    }
    
    /**
     * 聚焦到指定的 Track
     * <p>选中该 Track，并切换到 Track 面板
     *
     * @param track TimelineTrack 实例
     */
    private void focusTrack(TimelineTrack track) {
        if (track == null) {
            return;
        }
        
        // 使用 SelectionManager 选中 Track
        SelectionManager selectionManager = SelectionManager.get();
        selectionManager.selectTrack(track);
        
        LOGGER.debug("[ActorProjectPropertyAdapter] Focused track: {}", track.getName());
    }
    
    // =========================================================
    // Place Actor at Camera
    // =========================================================
    
    /**
     * 将 Actor 放置到当前摄像机位置
     * <p>创建或更新 EntitySpawnClip，将位置设置为当前摄像机位置
     *
     * @param actor Actor 实例
     */
    private void placeActorAtCamera(Actor actor) {
        if (actor == null) {
            return;
        }
        
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] MinecraftClient 未初始化");
            return;
        }
        
        // 获取当前摄像机位置
        var cameraSample = com.chronoblocks.ui.timeline.playback.camera.CameraAccessor.sampleFromGameCamera(client);
        if (cameraSample == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] 无法获取摄像机位置");
            return;
        }
        
        var timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        if (timelineModel == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] TimelineModel 未初始化");
            return;
        }
        
        // 查找该 Actor 的 EntityTrack
        var timelineTracks = ActorUsageQuery.findTimelineTracksUsingActor(timelineModel, actor.getActorId());
        if (timelineTracks.isEmpty()) {
            LOGGER.warn("[ActorProjectPropertyAdapter] 未找到 Actor 对应的 Track");
            return;
        }
        
        // 使用第一个 Track（通常只有一个）
        TimelineTrack timelineTrack = timelineTracks.getFirst();
        EntityTrack entityTrack = timelineTrack.getEntityTrackPayload();
        if (entityTrack == null) {
            LOGGER.warn("[ActorProjectPropertyAdapter] EntityTrack payload 为空");
            return;
        }
        
        float currentTime = timelineModel.getCurrentTime();
        org.joml.Vector3f cameraPos = new org.joml.Vector3f(
                (float) cameraSample.position().x,
                (float) cameraSample.position().y,
                (float) cameraSample.position().z
        );
        
        // 查找当前时间是否有 EntitySpawnClip
        var clipsAtTime = entityTrack.findClipsAt(currentTime);
        EntitySpawnClip spawnClip = null;
        for (var clip : clipsAtTime) {
            if (clip instanceof EntitySpawnClip) {
                spawnClip = (EntitySpawnClip) clip;
                break;
            }
        }
        
        if (spawnClip != null) {
            // 更新现有的 SpawnClip
            spawnClip.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
            spawnClip.setYawDeg(cameraSample.yaw());
            spawnClip.setPitchDeg(cameraSample.pitch());
            LOGGER.info("[ActorProjectPropertyAdapter] 更新 SpawnClip 位置到摄像机: ({}, {}, {})",
                    cameraPos.x, cameraPos.y, cameraPos.z);
        } else {
            // 创建新的 SpawnClip
            spawnClip = new EntitySpawnClip("Spawn", currentTime, currentTime + DEFAULT_CLIP_DURATION);
            spawnClip.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
            spawnClip.setYawDeg(cameraSample.yaw());
            spawnClip.setPitchDeg(cameraSample.pitch());
            spawnClip.setVisibleOnSpawn(true);
            entityTrack.addClip(spawnClip);
            LOGGER.info("[ActorProjectPropertyAdapter] 创建 SpawnClip 在摄像机位置: ({}, {}, {})",
                    cameraPos.x, cameraPos.y, cameraPos.z);
        }
        
        if (spawnClip.getTimelineClip() == null) {
            EntityTimelineClip timelineClip = new EntityTimelineClip(spawnClip, entityTrack.getColorRgba());
            timelineTrack.addClip(timelineClip);
        }
        timelineModel.markDirty();
        
        // 选中 Actor，激活 Gizmo
        ActorSelectionHub.selectActor(actor.getActorId());
    }
    
    // =========================================================
    // Delete actor
    // =========================================================
    
    private void openDeleteConfirm(UUID actorId) {
        deletingActorId = actorId;
        deleteConfirmOpen = true;
        ImGui.openPopup("Delete Actor");
    }
    
    private void renderDeleteConfirmPopup(ActorRegistry actorRegistry) {
        if (!deleteConfirmOpen || deletingActorId == null) {
            return;
        }
        
        if (ImGui.beginPopupModal("Delete Actor")) {
            Optional<Actor> actorOpt = actorRegistry.getActor(deletingActorId);
            if (actorOpt.isEmpty()) {
                ImGui.closeCurrentPopup();
                deleteConfirmOpen = false;
                deletingActorId = null;
                ImGui.endPopup();
                return;
            }
            
            Actor actor = actorOpt.get();
            
            ImGui.text("Are you sure you want to delete this actor?");
            ImGui.textColored(1.0f, 0.5f, 0.5f, 1.0f, 
                String.format("Actor: %s (%s)", actor.getName(), actor.getType().name()));
            ImGui.textDisabled("This action cannot be undone.");
            
            ImGui.separator();
            
            if (ImGui.button("Delete")) {
                deleteActor(actorRegistry, deletingActorId);
                
                ImGui.closeCurrentPopup();
                deleteConfirmOpen = false;
                deletingActorId = null;
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
                deleteConfirmOpen = false;
                deletingActorId = null;
            }
            
            ImGui.endPopup();
        }
    }
    
    private void deleteActor(ActorRegistry actorRegistry, UUID actorId) {
        boolean removed = actorRegistry.removeActor(actorId);
        
        if (removed) {
            // 如果删除的是当前选中的 Actor，清空选择
            if (actorId.equals(selectedActorId)) {
                selectedActorId = null;
            }
            
            // 清空全局 Selection
            SelectionManager selectionManager = SelectionManager.get();
            selectionManager.clearSelection();

            // 标记需要保存（通过 TimelineModel）
            var timelineModel = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
            if (timelineModel != null) {
                timelineModel.markDirty();
            }
            
            LOGGER.info("[ActorProjectPropertyAdapter] Deleted actor: {}", actorId);
        }
    }
}

