package com.director.ui.panels;

import com.director.core.DirectorServices;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.actor.ActorSelectorPanel;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyAdapterRegistry;
import com.director.ui.PanelId;
import com.director.ui.PanelWindows;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ActorsPanel extends AbstractPanel {

    public static final String PANEL_NAME = PanelId.ACTORS;
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorsPanel.class);
    private static final String ACTOR_PROJECT_MARKER = "ACTOR_PROJECT";

    private ActorSelectorPanel actorSelectorPanel;

    public ActorsPanel() {
        setVisible(false);  // 默认布局不打开，需从菜单「窗口」打开
    }

    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }

    @Override
    public String getWindowName() {
        return PanelWindows.actors();
    }

    @Override
    public int getPriority() {
        return 145;
    }

    @Override
    protected void doRender() {
        renderActorProjectSection();
    }

    private void renderActorProjectSection() {
        IPropertyAdapter<Object> adapter = PropertyAdapterRegistry.getAdapterFor(ACTOR_PROJECT_MARKER);
        if (adapter != null && adapter.supports(ACTOR_PROJECT_MARKER)) {
            try {
                adapter.renderProperties(ACTOR_PROJECT_MARKER);
            } catch (Exception e) {
                LOGGER.error("[ActorsPanel] Failed to render actor panel", e);
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.PANEL_RENDER_ERROR));
            }
            return;
        }

        ensureActorSelectorInitialized();
        if (actorSelectorPanel == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.TIMELINE_NOT_READY));
            return;
        }
        actorSelectorPanel.render();
    }

    private void ensureActorSelectorInitialized() {
        if (actorSelectorPanel != null) {
            return;
        }

        TimelineModel timelineModel = DirectorServices.get().containerTimeline().getTimelineModel();
        if (timelineModel == null) {
            return;
        }

        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        actorSelectorPanel = new ActorSelectorPanel(actorRegistry, timelineModel);
        LOGGER.debug("[ActorsPanel] ActorSelectorPanel initialized");
    }
}
