package com.chronoblocks.ui.panels;

import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorRegistryManager;
import com.chronoblocks.ui.actor.ActorSelectorPanel;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.properties.PropertyAdapterRegistry;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ActorsPanel extends AbstractPanel {

    public static final String PANEL_NAME = "Actors";
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorsPanel.class);
    private static final String ACTOR_PROJECT_MARKER = "ACTOR_PROJECT";

    private ActorSelectorPanel actorSelectorPanel;

    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }

    @Override
    public String getWindowName() {
        return Icons.TARGET + " Actors###ActorsPanel";
    }

    @Override
    public int getPriority() {
        return 145;
    }

    @Override
    protected void doRender() {
        UITheme.title(Icons.TARGET + " Actors");
        ImGui.separator();
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

        TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        if (timelineModel == null) {
            return;
        }

        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        actorSelectorPanel = new ActorSelectorPanel(actorRegistry, timelineModel);
        LOGGER.debug("[ActorsPanel] ActorSelectorPanel initialized");
    }
}
