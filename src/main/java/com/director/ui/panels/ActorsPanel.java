package com.director.ui.panels;

import com.director.core.DirectorServices;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.ui.actor.ActorSelectorPanel;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyAdapterRegistry;
import com.director.ui.PanelId;
import com.director.ui.PanelWindows;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ActorsPanel extends AbstractPanel {

    public static final String PANEL_NAME = PanelId.ACTORS;
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorsPanel.class);
    private static final String ACTOR_PROJECT_MARKER = "ACTOR_PROJECT";

    private ActorSelectorPanel actorSelectorPanel;

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
        UITheme.title(Icons.TARGET + " Actors");
        renderActorProjectSection();
    }

    private void renderActorProjectSection() {
        IPropertyAdapter<Object> adapter = PropertyAdapterRegistry.getAdapterFor(ACTOR_PROJECT_MARKER);
        if (adapter != null && adapter.supports(ACTOR_PROJECT_MARKER)) {
            try {
                adapter.renderProperties(ACTOR_PROJECT_MARKER);
            } catch (Exception e) {
                LOGGER.error("[ActorsPanel] Failed to render actor panel", e);
                ImGui.textDisabled("Failed to render actor panel.");
            }
            return;
        }

        ensureActorSelectorInitialized();
        if (actorSelectorPanel == null) {
            ImGui.textDisabled("Timeline model not ready.");
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
