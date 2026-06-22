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
        public static final String PANEL_TITLE = "gui.director.timeline.panel.title";
        public static final String TRACKS_HEADER = "gui.director.timeline.tracks_header";
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

        public static final class Dialog {
            public static final String CLIP_LENGTH_TITLE = "gui.director.timeline.dialog.clip_length.title";
            public static final String CLIP_LENGTH_PROMPT = "gui.director.timeline.dialog.clip_length.prompt";
            public static final String CLIP_RENAME_TITLE = "gui.director.timeline.dialog.clip_rename.title";
            public static final String CLIP_RENAME_PROMPT = "gui.director.timeline.dialog.clip_rename.prompt";
            public static final String TRACK_RENAME_TITLE = "gui.director.timeline.dialog.track_rename.title";
            public static final String TRACK_RENAME_PROMPT = "gui.director.timeline.dialog.track_rename.prompt";
            public static final String BIND_CONTAINER_TITLE = "gui.director.timeline.dialog.bind_container.title";
            public static final String BIND_CONTAINER_NO_CONTAINERS = "gui.director.timeline.dialog.bind_container.no_containers";
            public static final String ADD_TRACK_TITLE = "gui.director.timeline.dialog.add_track.title";
            public static final String ADD_TRACK_CHOOSE_TYPE = "gui.director.timeline.dialog.add_track.choose_type";

            private Dialog() {
            }
        }

        public static final class ContextMenu {
            public static final String NO_CLIP = "gui.director.timeline.context.no_clip";
            public static final String NO_KEYFRAME = "gui.director.timeline.context.no_keyframe";
            public static final String NO_TRACK = "gui.director.timeline.context.no_track";
            public static final String CLIP_LABEL = "gui.director.timeline.context.clip_label";
            public static final String TIME_RANGE = "gui.director.timeline.context.time_range";
            public static final String DUPLICATE_CLIP = "gui.director.timeline.context.duplicate_clip";
            public static final String DELETE_CLIP = "gui.director.timeline.context.delete_clip";
            public static final String SET_DURATION_1S = "gui.director.timeline.context.set_duration_1s";
            public static final String SET_DURATION_2S = "gui.director.timeline.context.set_duration_2s";
            public static final String SET_DURATION_5S = "gui.director.timeline.context.set_duration_5s";
            public static final String RENAME_CLIP = "gui.director.timeline.context.rename_clip";
            public static final String BIND_CONTAINER = "gui.director.timeline.context.bind_container";
            public static final String UNBIND_CONTAINER = "gui.director.timeline.context.unbind_container";
            public static final String KEYFRAME_AT = "gui.director.timeline.context.keyframe_at";
            public static final String KEYFRAME_AT_NOT_FOUND = "gui.director.timeline.context.keyframe_at_not_found";
            public static final String DELETE_SELECTED = "gui.director.timeline.context.delete_selected";
            public static final String DELETE_KEYFRAME = "gui.director.timeline.context.delete_keyframe";
            public static final String JUMP_TO_TIME = "gui.director.timeline.context.jump_to_time";
            public static final String INTERPOLATION_SUBMENU = "gui.director.timeline.context.interpolation_submenu";
            public static final String INTERP_LINEAR = "gui.director.timeline.context.interp_linear";
            public static final String INTERP_SMOOTH = "gui.director.timeline.context.interp_smooth";
            public static final String INTERP_STEP = "gui.director.timeline.context.interp_step";
            public static final String RESET_INTERP_LINEAR = "gui.director.timeline.context.reset_interp_linear";
            public static final String BATCH_INTERP_SUBMENU = "gui.director.timeline.context.batch_interp_submenu";
            public static final String BATCH_INTERP_LINEAR = "gui.director.timeline.context.batch_interp_linear";
            public static final String BATCH_INTERP_SMOOTH = "gui.director.timeline.context.batch_interp_smooth";
            public static final String BATCH_INTERP_STEP = "gui.director.timeline.context.batch_interp_step";
            public static final String BATCH_INTERP_HINT = "gui.director.timeline.context.batch_interp_hint";
            public static final String AUDIO_EVENT_AT = "gui.director.timeline.context.audio_event_at";
            public static final String LINKED_TO_CLIP = "gui.director.timeline.context.linked_to_clip";
            public static final String LINKED_TO_CLIP_GENERIC = "gui.director.timeline.context.linked_to_clip_generic";
            public static final String UNLINKED_EVENT = "gui.director.timeline.context.unlinked_event";
            public static final String UNLINK_FROM_CLIP = "gui.director.timeline.context.unlink_from_clip";
            public static final String UNLINK_ALREADY_INDEPENDENT = "gui.director.timeline.context.unlink_already_independent";
            public static final String DELETE_AUDIO_EVENT = "gui.director.timeline.context.delete_audio_event";
            public static final String WEATHER_KEYFRAME = "gui.director.timeline.context.weather_keyframe";
            public static final String MARKER = "gui.director.timeline.context.marker";
            public static final String CAMERA_KEYFRAME_AT = "gui.director.timeline.context.camera_keyframe_at";
            public static final String TRACK_LOCKED = "gui.director.timeline.context.track_locked";
            public static final String TRACK_INACTIVE = "gui.director.timeline.context.track_inactive";
            public static final String TRACK_LABEL = "gui.director.timeline.context.track_label";
            public static final String TRACK_TYPE = "gui.director.timeline.context.track_type";
            public static final String UNLOCK_TRACK = "gui.director.timeline.context.unlock_track";
            public static final String LOCK_TRACK = "gui.director.timeline.context.lock_track";
            public static final String RENAME_TRACK = "gui.director.timeline.context.rename_track";
            public static final String MOVE_TRACK_UP = "gui.director.timeline.context.move_track_up";
            public static final String MOVE_TRACK_DOWN = "gui.director.timeline.context.move_track_down";
            public static final String DELETE_TRACK = "gui.director.timeline.context.delete_track";
            public static final String TIME_AT = "gui.director.timeline.context.time_at";
            public static final String NEW_TRACK_SUBMENU = "gui.director.timeline.context.new_track_submenu";
            public static final String ADD_MARKER_AT_PLAYHEAD = "gui.director.timeline.context.add_marker_at_playhead";
            public static final String CAMERA_PROPERTIES = "gui.director.timeline.context.camera_properties";
            public static final String CAMERA_PATH_SUBMENU = "gui.director.timeline.context.camera_path_submenu";
            public static final String CREATE_CUSTOM_PATH = "gui.director.timeline.context.create_custom_path";
            public static final String CREATE_ORBIT_PATH = "gui.director.timeline.context.create_orbit_path";
            public static final String CREATE_DOLLY_PATH = "gui.director.timeline.context.create_dolly_path";
            public static final String CREATE_CRANE_PATH = "gui.director.timeline.context.create_crane_path";
            public static final String CREATE_PATH_INACTIVE_HINT = "gui.director.timeline.context.create_path_inactive_hint";
            public static final String WEATHER_KEYFRAME_SUBMENU = "gui.director.timeline.context.weather_keyframe_submenu";
            public static final String ADD_WEATHER_KEYFRAME = "gui.director.timeline.context.add_weather_keyframe";
            public static final String CREATE_ENTITY_CLIP_SUBMENU = "gui.director.timeline.context.create_entity_clip_submenu";
            public static final String ENTITY_SPAWN = "gui.director.timeline.context.entity_spawn";
            public static final String ENTITY_TRANSFORM = "gui.director.timeline.context.entity_transform";
            public static final String ENTITY_ANIMATION = "gui.director.timeline.context.entity_animation";
            public static final String ENTITY_ACTION = "gui.director.timeline.context.entity_action";
            public static final String ENTITY_STATE = "gui.director.timeline.context.entity_state";
            public static final String ENTITY_LOOK_AT = "gui.director.timeline.context.entity_look_at";
            public static final String ENTITY_DESPAWN = "gui.director.timeline.context.entity_despawn";
            public static final String NO_PATH_CLIP = "gui.director.timeline.context.no_path_clip";
            public static final String PATH_CLIP_LABEL = "gui.director.timeline.context.path_clip_label";
            public static final String PATH_PROPERTIES = "gui.director.timeline.context.path_properties";
            public static final String ADD_CAMERA_KEYFRAME = "gui.director.timeline.context.add_camera_keyframe";
            public static final String DELETE_PATH_CLIP = "gui.director.timeline.context.delete_path_clip";
            public static final String UNNAMED = "gui.director.timeline.context.unnamed";

            private ContextMenu() {
            }
        }

        public static final class TrackControl {
            public static final String CAMERA_ACTIVE = "gui.director.timeline.track_control.camera_active";
            public static final String CAMERA_SET_ACTIVE = "gui.director.timeline.track_control.camera_set_active";
            public static final String TIME_ACTIVE = "gui.director.timeline.track_control.time_active";
            public static final String TIME_SET_ACTIVE = "gui.director.timeline.track_control.time_set_active";
            public static final String SEASON_ACTIVE = "gui.director.timeline.track_control.season_active";
            public static final String SEASON_SET_ACTIVE = "gui.director.timeline.track_control.season_set_active";
            public static final String WEATHER_ACTIVE = "gui.director.timeline.track_control.weather_active";
            public static final String WEATHER_SET_ACTIVE = "gui.director.timeline.track_control.weather_set_active";
            public static final String CONTAINER_NO_TRACK_KEYFRAME = "gui.director.timeline.track_control.container_no_track_keyframe";
            public static final String CAMERA_ADD_KEYFRAME_HINT = "gui.director.timeline.track_control.camera_add_keyframe_hint";
            public static final String ADD_KEYFRAME = "gui.director.timeline.track_control.add_keyframe";
            public static final String UNLOCK_TRACK = "gui.director.timeline.track_control.unlock_track";
            public static final String LOCK_TRACK = "gui.director.timeline.track_control.lock_track";
            public static final String HIDE_TRACK = "gui.director.timeline.track_control.hide_track";
            public static final String SHOW_TRACK = "gui.director.timeline.track_control.show_track";

            private TrackControl() {
            }
        }

        public static final class CameraKeyframeMenu {
            public static final String KEYFRAME_AT = "gui.director.timeline.camera_keyframe.keyframe_at";
            public static final String MULTI_SELECT = "gui.director.timeline.camera_keyframe.multi_select";
            public static final String TRACK_LOCKED = "gui.director.timeline.camera_keyframe.track_locked";
            public static final String TRACK_INACTIVE = "gui.director.timeline.camera_keyframe.track_inactive";
            public static final String KEY_TYPE_SUBMENU = "gui.director.timeline.camera_keyframe.key_type_submenu";
            public static final String KEY_TYPE_STANDARD = "gui.director.timeline.camera_keyframe.key_type_standard";
            public static final String KEY_TYPE_CORNER = "gui.director.timeline.camera_keyframe.key_type_corner";
            public static final String KEY_TYPE_HOLD = "gui.director.timeline.camera_keyframe.key_type_hold";
            public static final String KEY_TYPE_SMOOTH = "gui.director.timeline.camera_keyframe.key_type_smooth";
            public static final String KEY_TYPE_MANUAL_BEZIER = "gui.director.timeline.camera_keyframe.key_type_manual_bezier";
            public static final String SEGMENT_TYPE_SUBMENU = "gui.director.timeline.camera_keyframe.segment_type_submenu";
            public static final String SEGMENT_DEFAULT = "gui.director.timeline.camera_keyframe.segment_default";
            public static final String SEGMENT_LINEAR = "gui.director.timeline.camera_keyframe.segment_linear";
            public static final String SEGMENT_CATMULL_ROM = "gui.director.timeline.camera_keyframe.segment_catmull_rom";
            public static final String SEGMENT_BEZIER = "gui.director.timeline.camera_keyframe.segment_bezier";
            public static final String SEGMENT_STEP = "gui.director.timeline.camera_keyframe.segment_step";
            public static final String SEGMENT_HOLD = "gui.director.timeline.camera_keyframe.segment_hold";
            public static final String ROTATION_TYPE_SUBMENU = "gui.director.timeline.camera_keyframe.rotation_type_submenu";
            public static final String ROTATION_DEFAULT = "gui.director.timeline.camera_keyframe.rotation_default";
            public static final String ROTATION_LINEAR = "gui.director.timeline.camera_keyframe.rotation_linear";
            public static final String ROTATION_BEZIER = "gui.director.timeline.camera_keyframe.rotation_bezier";
            public static final String ROTATION_CATMULL_ROM = "gui.director.timeline.camera_keyframe.rotation_catmull_rom";
            public static final String ROTATION_STEP = "gui.director.timeline.camera_keyframe.rotation_step";
            public static final String HANDLE_SUBMENU = "gui.director.timeline.camera_keyframe.handle_submenu";
            public static final String HANDLE_AUTO = "gui.director.timeline.camera_keyframe.handle_auto";
            public static final String HANDLE_FREE = "gui.director.timeline.camera_keyframe.handle_free";
            public static final String HANDLE_ALIGNED = "gui.director.timeline.camera_keyframe.handle_aligned";
            public static final String CLEAR_HANDLES = "gui.director.timeline.camera_keyframe.clear_handles";
            public static final String JUMP_TO_KEYFRAME = "gui.director.timeline.camera_keyframe.jump_to_keyframe";
            public static final String DELETE_SELECTED = "gui.director.timeline.camera_keyframe.delete_selected";
            public static final String DELETE_KEYFRAME = "gui.director.timeline.camera_keyframe.delete_keyframe";

            private CameraKeyframeMenu() {
            }
        }

        public static final class DefaultNames {
            public static final String MARKER_AT = "gui.director.timeline.default.marker_at";
            public static final String WEATHER_AT = "gui.director.timeline.default.weather_at";
            public static final String CUSTOM_PATH_AT = "gui.director.timeline.default.custom_path_at";
            public static final String ORBIT_PATH_AT = "gui.director.timeline.default.orbit_path_at";
            public static final String DOLLY_PATH_AT = "gui.director.timeline.default.dolly_path_at";
            public static final String CRANE_PATH_AT = "gui.director.timeline.default.crane_path_at";
            public static final String CAMERA_AT = "gui.director.timeline.default.camera_at";
            public static final String ENTITY_SPAWN = "gui.director.timeline.default.entity_spawn";
            public static final String ENTITY_TRANSFORM = "gui.director.timeline.default.entity_transform";
            public static final String ENTITY_ANIMATION = "gui.director.timeline.default.entity_animation";
            public static final String ENTITY_ACTION = "gui.director.timeline.default.entity_action";
            public static final String ENTITY_STATE = "gui.director.timeline.default.entity_state";
            public static final String ENTITY_LOOK_AT = "gui.director.timeline.default.entity_look_at";
            public static final String ENTITY_DESPAWN = "gui.director.timeline.default.entity_despawn";

            private DefaultNames() {
            }
        }

        private Timeline() {
        }
    }

    public static final class Toolbar {
        public static final String PANEL_TITLE = "gui.director.toolbar.panel.title";
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
        public static final String OK = "gui.director.common.ok";
        public static final String CANCEL = "gui.director.common.cancel";
        public static final String CLOSE = "gui.director.common.close";
        public static final String BIND = "gui.director.common.bind";
        public static final String UNBIND = "gui.director.common.unbind";

        private Common() {
        }
    }
}
