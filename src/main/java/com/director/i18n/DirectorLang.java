package com.director.i18n;

/**
 * Director lang 键常量（对应 {@code assets/director/lang/*.json}）。
 */
public final class DirectorLang {

    private DirectorLang() {
    }

    public static final class Menu {
        public static final String FILE = "gui.director.menu.file";
        public static final String FILE_NEW_PROJECT = "gui.director.menu.file.new_project";
        public static final String FILE_RELOAD_PROJECT = "gui.director.menu.file.reload_project";
        public static final String FILE_SAVE = "gui.director.menu.file.save";
        public static final String FILE_SAVE_AS = "gui.director.menu.file.save_as";
        public static final String FILE_EXPORT_VIDEO = "gui.director.menu.file.export_video";
        public static final String FILE_EXIT = "gui.director.menu.file.exit";

        public static final String EDIT = "gui.director.menu.edit";
        public static final String EDIT_UNDO = "gui.director.menu.edit.undo";
        public static final String EDIT_REDO = "gui.director.menu.edit.redo";
        public static final String EDIT_DELETE_SELECTED = "gui.director.menu.edit.delete_selected";

        public static final String TIMELINE = "gui.director.menu.timeline";
        public static final String TIMELINE_SETTINGS = "gui.director.menu.timeline.settings";
        public static final String TIMELINE_ADD_TRACK = "gui.director.menu.timeline.add_track";
        public static final String TIMELINE_TRACK_CAMERA = "gui.director.menu.timeline.track.camera";
        public static final String TIMELINE_TRACK_ACTOR = "gui.director.menu.timeline.track.actor";
        public static final String TIMELINE_TRACK_WEATHER = "gui.director.menu.timeline.track.weather";
        public static final String TIMELINE_TRACK_AUDIO = "gui.director.menu.timeline.track.audio";
        public static final String TIMELINE_SCROLL_TO_PLAYHEAD = "gui.director.menu.timeline.scroll_to_playhead";
        public static final String TIMELINE_ZOOM_TO_FIT = "gui.director.menu.timeline.zoom_to_fit";

        public static final String PLAYBACK = "gui.director.menu.playback";
        public static final String PLAYBACK_PLAY = "gui.director.menu.playback.play";
        public static final String PLAYBACK_PAUSE = "gui.director.menu.playback.pause";
        public static final String PLAYBACK_STOP = "gui.director.menu.playback.stop";
        public static final String PLAYBACK_SPEED = "gui.director.menu.playback.speed";
        public static final String PLAYBACK_TOGGLE_LOOP = "gui.director.menu.playback.toggle_loop";
        public static final String PLAYBACK_SET_IN = "gui.director.menu.playback.set_in";
        public static final String PLAYBACK_SET_OUT = "gui.director.menu.playback.set_out";
        public static final String PLAYBACK_RANGE = "gui.director.menu.playback.range";
        public static final String PLAYBACK_RANGE_FULL = "gui.director.menu.playback.range_full";
        public static final String PLAYBACK_CLEAR_IN_OUT = "gui.director.menu.playback.clear_in_out";
        public static final String PLAYBACK_GO_TO_START = "gui.director.menu.playback.go_to_start";
        public static final String PLAYBACK_GO_TO_END = "gui.director.menu.playback.go_to_end";

        public static final String WINDOW = "gui.director.menu.window";
        public static final String WINDOW_SHOW_ALL = "gui.director.menu.window.show_all";
        public static final String WINDOW_HIDE_ALL = "gui.director.menu.window.hide_all";
        public static final String WINDOW_TOGGLE_PANELS = "gui.director.menu.window.toggle_panels";
        public static final String WINDOW_EXTERNAL = "gui.director.menu.window.external";
        public static final String WINDOW_SAVE_LAYOUT = "gui.director.menu.window.save_layout";
        public static final String WINDOW_LOAD_LAYOUT = "gui.director.menu.window.load_layout";
        public static final String WINDOW_RESTORE_LAYOUT = "gui.director.menu.window.restore_layout";
        public static final String WINDOW_THEME = "gui.director.menu.window.theme";
        public static final String WINDOW_THEME_DARK = "gui.director.menu.window.theme.dark";
        public static final String WINDOW_THEME_LIGHT = "gui.director.menu.window.theme.light";

        public static final String HELP = "gui.director.menu.help";
        public static final String HELP_SHORTCUTS = "gui.director.menu.help.shortcuts";
        public static final String HELP_MANUAL = "gui.director.menu.help.manual";
        public static final String HELP_VIDEO = "gui.director.menu.help.video";
        public static final String HELP_REPORT_BUG = "gui.director.menu.help.report_bug";
        public static final String HELP_ABOUT = "gui.director.menu.help.about";

        private Menu() {
        }
    }

    public static final class Timeline {
        public static final String NEW_TRACK = "gui.director.timeline.new_track";
        public static final String CAMERA_PATH = "gui.director.timeline.camera_path";
        public static final String CAMERA_PATH_TOOLTIP = "gui.director.timeline.camera_path_tooltip";
        public static final String TIME_LABEL = "gui.director.timeline.time_label";
        public static final String DURATION_TOOLTIP_TITLE = "gui.director.timeline.duration_tooltip_title";
        public static final String DURATION_TOOLTIP_HINT = "gui.director.timeline.duration_tooltip_hint";
        public static final String JUMP_LABEL = "gui.director.timeline.jump_label";
        public static final String SNAP = "gui.director.timeline.snap";
        public static final String SNAP_TOOLTIP = "gui.director.timeline.snap_tooltip";
        public static final String ZOOM_LABEL = "gui.director.timeline.zoom_label";
        public static final String FIT_VIEW = "gui.director.timeline.fit_view";
        public static final String FIT_VIEW_TOOLTIP = "gui.director.timeline.fit_view_tooltip";
        public static final String SCRUB_LABEL = "gui.director.timeline.scrub_label";
        public static final String SCRUB_TOOLTIP = "gui.director.timeline.scrub_tooltip";
        public static final String SPEED_LABEL = "gui.director.timeline.speed_label";
        public static final String SPEED_TOOLTIP = "gui.director.timeline.speed_tooltip";

        private Timeline() {
        }
    }

    public static final class Toolbar {
        public static final String TOOL_DISPLAY_TITLE = "gui.director.toolbar.tool_display_title";
        public static final String TOOL_DISPLAY_ICONS_TEXT = "gui.director.toolbar.tool_display_icons_text";
        public static final String TOOL_DISPLAY_ICONS_ONLY = "gui.director.toolbar.tool_display_icons_only";
        public static final String TOOL_DISPLAY_TOGGLE_HINT = "gui.director.toolbar.tool_display_toggle_hint";
        public static final String SHORTCUT = "gui.director.toolbar.shortcut";

        private Toolbar() {
        }
    }

    public static final class ToolGroupKeys {
        public static final String SELECTION = "gui.director.tool.group.selection";
        public static final String WORLD = "gui.director.tool.group.world";
        public static final String TIMELINE = "gui.director.tool.group.timeline";

        private ToolGroupKeys() {
        }
    }

    public static final class Common {
        public static final String DELETE = "gui.director.common.delete";

        private Common() {
        }
    }
}
