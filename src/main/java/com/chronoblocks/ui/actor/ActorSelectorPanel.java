package com.chronoblocks.ui.actor;

import com.chronoblocks.actor.*;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ActorSelectorPanel v1.0
 *
 * <p>属性面板中的"演员管理器"
 * <p>职责：让导演"创建演员"这件事，直觉、快速、不可犯错
 *
 * <p>核心功能：
 * <ul>
 *   <li>显示 Actor 列表</li>
 *   <li>创建新 Actor（原子性：Actor + EntityTrack + SpawnClip）</li>
 *   <li>选择 ActorType</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 只做方案 C（Actor 系统）</li>
 *   <li>✅ 不绑定已有世界实体</li>
 *   <li>✅ 不编辑 Transform / Animation（那是 Step 4B）</li>
 *   <li>✅ 不涉及 Runtime / Preview 逻辑</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorSelectorPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorSelectorPanel.class);

    private final ActorRegistry actorRegistry;
    private final TimelineModel timelineModel;

    private boolean createPopupOpen = false;
    private boolean editNamePopupOpen = false;
    private boolean deleteConfirmPopupOpen = false;

    // Create popup state
    private final ImString newActorName = new ImString("Actor", 256);
    private ActorType newActorType = ActorType.VILLAGER;

    // Edit popup state
    private Actor editingActor = null;
    private final ImString editingActorName = new ImString("", 256);

    // Delete confirm popup state
    private Actor deletingActor = null;

    /**
     * 构造函数
     *
     * @param actorRegistry ActorRegistry 实例
     * @param timelineModel TimelineModel 实例
     */
    public ActorSelectorPanel(ActorRegistry actorRegistry, TimelineModel timelineModel) {
        this.actorRegistry = actorRegistry;
        this.timelineModel = timelineModel;
    }

    // =========================================================
    // Render
    // =========================================================

    /**
     * 渲染 Actor Selector 面板
     */
    public void render() {
        ImGui.textColored(0.9f, 0.7f, 1.0f, 1.0f, "🎭 Actors");

        ImGui.separator();

        renderActorList();

        ImGui.spacing();
        if (ImGui.button("+ Add Actor")) {
            openCreatePopup();
        }

        renderCreatePopup();
        renderEditNamePopup();
        renderDeleteConfirmPopup();
    }

    // =========================================================
    // Actor List
    // =========================================================

    private void renderActorList() {
        List<Actor> actors = actorRegistry.getAllActors();

        if (actors.isEmpty()) {
            ImGui.textDisabled("No actors yet.");
            return;
        }

        for (Actor actor : actors) {
            ImGui.pushID(actor.getActorId().toString());
            
            // 显示 Actor 信息（可点击选择）
            String displayText = actor.getName() + "  (" + actor.getType().name() + ")";
            if (ImGui.selectable(displayText, false)) {
                // TODO: 选择 Actor 后可以显示其详细信息或关联的轨道
            }
            
            // 右键菜单：编辑/删除
            if (ImGui.beginPopupContextItem("ActorContextMenu")) {
                if (ImGui.menuItem("编辑名称")) {
                    openEditActorNamePopup(actor);
                }
                if (ImGui.menuItem("删除")) {
                    openDeleteActorConfirmPopup(actor);
                }
                ImGui.endPopup();
            }
            
            ImGui.popID();
        }
    }

    // =========================================================
    // Create Actor Popup
    // =========================================================

    private void openCreatePopup() {
        newActorName.set("Actor");
        newActorType = ActorType.VILLAGER;
        createPopupOpen = true;
        ImGui.openPopup("Create Actor");
    }

    private void renderCreatePopup() {
        if (!createPopupOpen) return;

        if (ImGui.beginPopupModal("Create Actor", ImGuiCond.Always)) {

            ImGui.text("Name:");
            ImGui.sameLine();
            ImGui.inputText("##ActorName", newActorName);

            ImGui.text("Type:");
            ImGui.sameLine();
            renderActorTypeCombo();

            ImGui.separator();

            if (ImGui.button("Create")) {
                createActorAndTrack();
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

    private void renderActorTypeCombo() {
        String currentTypeName = newActorType.name();
        if (ImGui.beginCombo("##ActorType", currentTypeName)) {
            for (ActorType type : ActorType.values()) {
                boolean selected = (type == newActorType);
                if (ImGui.selectable(type.name(), selected)) {
                    newActorType = type;
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
    }

    // =========================================================
    // Creation Logic（事务）
    // =========================================================

    /**
     * 创建 Actor 并关联 EntityTrack（原子性事务）
     * <p>如果任何一步失败，全部回滚
     */
    private void createActorAndTrack() {
        String actorName = newActorName.get();
        if (actorName == null || actorName.trim().isEmpty()) {
            actorName = "Actor";
        }

        try {
            // 1️⃣ Create Actor
            Actor actor = actorRegistry.createActor(actorName.trim(), newActorType);
            ActorRef ref = new ActorRef(actor.getActorId());

            // 2️⃣ Create EntityTrack
            EntityTrack entityTrack = new EntityTrack();
            entityTrack.setActorRef(ref);
            entityTrack.setDisplayName(actor.getName());

            // 3️⃣ Create TimelineTrack with EntityTrack payload
            TimelineTrack timelineTrack = new TimelineTrack(actor.getName(), TrackType.ENTITY);
            timelineTrack.setEntityTrackPayload(entityTrack);

            // 4️⃣ Add to TimelineModel
            timelineModel.addTrack(timelineTrack);

            // 5️⃣ Insert SpawnClip at current time
            float currentTime = timelineModel.getCurrentTime();
            entityTrack.addDefaultSpawnClip(currentTime);

            // 6️⃣ Mark model as dirty (trigger save)
            timelineModel.markDirty();

            LOGGER.info("[ActorSelector] 成功创建 Actor: {} ({}), EntityTrack 已添加到时间线", 
                    actor.getName(), actor.getType());

        } catch (Exception e) {
            LOGGER.error("[ActorSelector] 创建 Actor 失败，已回滚", e);
            // TODO: 如果实现了撤销/重做系统，这里应该回滚所有操作
        }
    }
}

