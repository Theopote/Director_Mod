package com.director.ui.panels;

import com.director.core.DirectorServices;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.actor.path.ActorPathLibraryPanel;
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

    private final imgui.type.ImString diagnosticSearch = new imgui.type.ImString(128);
    private final imgui.type.ImBoolean hiddenOnly = new imgui.type.ImBoolean(false);
    private java.util.List<com.director.actor.query.ActorProjectDiagnostics.Issue> projectIssues = java.util.List.of();
    private TimelineModel diagnosticModel;
    private boolean projectScanned;
    private ActorSelectorPanel actorSelectorPanel;
    private final ActorPathLibraryPanel actorPathLibraryPanel = new ActorPathLibraryPanel();

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
        if (ImGui.beginTabBar("##actorPanelTabs")) {
            if (ImGui.beginTabItem("演员与分组")) {
                renderActorProjectSection();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("路径库")) {
                actorPathLibraryPanel.render();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(diagnosticText("tab"))) {
                renderSceneDiagnostics();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
    }

    private static String diagnosticText(String key) {
        return ActorPropertyAdapterI18n.tr("gui.director.actor.scene_diagnostics." + key);
    }
    private void renderSceneDiagnostics() {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        ImGui.textWrapped(diagnosticText("scope"));
        if (model == null) { ImGui.textDisabled(diagnosticText("no_model")); return; }
        if (diagnosticModel != model) { diagnosticModel = model; projectIssues = java.util.List.of(); projectScanned = false; }
        if (ImGui.button(diagnosticText("scan"))) {
            projectIssues = com.director.actor.query.ActorProjectDiagnostics.scan(model, ActorRegistryManager.getInstance()); projectScanned = true;
        }
        if (projectScanned) {
            ImGui.textWrapped(diagnosticText("scan_scope"));
            for (var issue : projectIssues) {
                ImGui.pushID("audit:" + System.identityHashCode(issue.track()) + ":" + issue.reason() + ":" + issue.reference());
                ImGui.textWrapped(issue.track().getName() + " · " + diagnosticText(issue.reason().name().toLowerCase(java.util.Locale.ROOT)) + " · " + issue.reference());
                if (model.getAllTracks().contains(issue.track()) && ImGui.smallButton(diagnosticText("inspect_track"))) {
                    model.selectItem(issue.track(), false);
                    DirectorServices.get().uiSelection().setCurrentSelection(issue.track());
                    com.director.ui.properties.PropertyContextService.get().onTimelineSelectionChanged(issue.track());
                }
                ImGui.popID();
            }
            if (projectIssues.isEmpty()) ImGui.textWrapped(diagnosticText("scan_clear"));
            ImGui.separator();
        }
        ImGui.inputText(diagnosticText("search"), diagnosticSearch);
        ImGui.checkbox(diagnosticText("hidden_only"), hiddenOnly);
        String query = diagnosticSearch.get().strip().toLowerCase(java.util.Locale.ROOT);
        var tracks = model.getAllTracks().stream().filter(t -> t.getActorTrackPayload() != null
                && t.getActorTrackPayload().getActorRef() != null && !t.isMuted()).toList();
        int count = 0;
        for (var actor : ActorRegistryManager.getInstance().getAllActors()) {
            var issues = com.director.actor.runtime.ActorRuntimeController.getInstance().diagnostics()
                    .current(actor.getActorId(), model.getCurrentTime(), tracks);
            for (var issue : issues) {
                String reason = issue.reason() == null ? issue.message() : ActorPropertyAdapterI18n.tr(issue.reason().translationKey());
                if (hiddenOnly.get() && !issue.hidden()) continue;
                if (!(actor.getName() + " " + reason).toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                ImGui.pushID(actor.getActorId() + ":" + count++);
                ImGui.separator();
                ImGui.textWrapped(actor.getName() + " · " + ActorPropertyAdapterI18n.tr("gui.director.actor.diagnostics." + (issue.hidden() ? "hidden" : "limited")));
                ImGui.textWrapped(reason);
                if (issue.track() instanceof com.director.timeline.model.TimelineTrack track)
                    ImGui.textWrapped(track.getName() + String.format(" · %.2f s", issue.sampleTime()));
                if (ImGui.smallButton(diagnosticText("inspect"))) {
                    com.director.ui.actor.selection.ActorSelectionHub.selectActor(actor.getActorId());
                    com.director.ui.properties.PropertyContextService.get().inspectActorObject(actor);
                }
                ImGui.popID();
            }
        }
        if (count == 0) ImGui.textWrapped(diagnosticText("empty"));
    }

    private void renderActorProjectSection() {
        IPropertyAdapter<Object> adapter = PropertyAdapterRegistry.getAdapterFor(ACTOR_PROJECT_MARKER);
        if (adapter != null && adapter.supports(ACTOR_PROJECT_MARKER)) {
            try {
                if (adapter.renderProperties(ACTOR_PROJECT_MARKER)) {
                    var model = DirectorServices.get().containerTimeline().getTimelineModel();
                    if (model != null) model.markDirty();
                }
            } catch (Exception e) {
                LOGGER.error("[ActorsPanel] Failed to render actor panel", e);
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.PANEL_RENDER_ERROR));
            }
        } else {
            ensureActorSelectorInitialized();
            if (actorSelectorPanel == null) {
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.TIMELINE_NOT_READY));
            } else {
                actorSelectorPanel.render();
            }
        }


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
