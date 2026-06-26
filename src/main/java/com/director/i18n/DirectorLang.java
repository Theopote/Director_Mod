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
        public static final String PANEL_TITLE = DirectorLang.Panel.TIMELINE;
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
            public static final String ACTOR_RELATION = "gui.director.timeline.context.actor_relation";
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
            public static final String ACTOR_RELATION = "gui.director.timeline.default.actor_relation";

            private DefaultNames() {
            }
        }

        private Timeline() {
        }
    }

    public static final class Toolbar {
        public static final String PANEL_TITLE = DirectorLang.Panel.TOOLBAR;
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
        public static final String ACTOR = "gui.director.tool.group.actor";
        public static final String WORLD = "gui.director.tool.group.world";
        public static final String TIMELINE = "gui.director.tool.group.timeline";

        private ToolGroupKeys() {
        }
    }

    public static final class Panel {
        public static final String TIMELINE = "gui.director.panel.timeline";
        public static final String TOOLBAR = "gui.director.panel.toolbar";
        public static final String MENU_BAR = "gui.director.panel.menu_bar";
        public static final String STATUS_BAR = "gui.director.panel.status_bar";
        public static final String PROPERTIES = "gui.director.panel.properties";
        public static final String TOOL_PROPERTIES = "gui.director.panel.tool_properties";
        public static final String CONTAINER = "gui.director.panel.container";
        public static final String ACTORS = "gui.director.panel.actors";
        public static final String SELECTION_FILTER = "gui.director.panel.selection_filter";
        public static final String SELECTION_HISTORY = "gui.director.panel.selection_history";
        public static final String RENDER_SETTINGS = "gui.director.panel.render_settings";
        public static final String HIDDEN = "gui.director.panel.hidden";

        private Panel() {
        }
    }

    public static final class Properties {
        public static final String MULTI_SELECT = "gui.director.properties.multi_select";
        public static final String MULTI_SELECT_TOOLTIP = "gui.director.properties.multi_select_tooltip";
        public static final String NO_TOOL = "gui.director.properties.no_tool";
        public static final String SELECT_TOOL_HINT = "gui.director.properties.select_tool_hint";
        public static final String OBJECT_LABEL = "gui.director.properties.object_label";
        public static final String NO_SELECTION = "gui.director.properties.no_selection";
        public static final String SELECT_OBJECT_HINT = "gui.director.properties.select_object_hint";
        public static final String SUPPORTED_TYPES_TITLE = "gui.director.properties.supported_types_title";
        public static final String GROUP_TIMELINE = "gui.director.properties.group.timeline";
        public static final String GROUP_CORE = "gui.director.properties.group.core";
        public static final String GROUP_EXTENDED = "gui.director.properties.group.extended";
        public static final String GROUP_TYPES = "gui.director.properties.group.types";
        public static final String GROUP_FALLBACK = "gui.director.properties.group.fallback";
        public static final String TYPE_TIMELINE_PROJECT = "gui.director.properties.type.timeline_project";
        public static final String TYPE_TIMELINE_SEQUENCE = "gui.director.properties.type.timeline_sequence";
        public static final String TYPE_TIMELINE_TRACK = "gui.director.properties.type.timeline_track";
        public static final String TYPE_KEYFRAME = "gui.director.properties.type.keyframe";
        public static final String TYPE_CONTAINER = "gui.director.properties.type.container";
        public static final String TYPE_EFFECT = "gui.director.properties.type.effect";
        public static final String TYPE_PROJECT_ENV = "gui.director.properties.type.project_env";
        public static final String TYPE_MATERIAL_LIBRARY = "gui.director.properties.type.material_library";
        public static final String TYPE_CAMERA = "gui.director.properties.type.camera";
        public static final String TYPE_POST_PROCESSING = "gui.director.properties.type.post_processing";
        public static final String TYPE_PHYSICS_ENV = "gui.director.properties.type.physics_env";
        public static final String TYPE_VECTOR3F = "gui.director.properties.type.vector3f";
        public static final String TYPE_COLOR = "gui.director.properties.type.color";
        public static final String TYPE_LIST = "gui.director.properties.type.list";
        public static final String TYPE_GENERIC = "gui.director.properties.type.generic";
        public static final String ADAPTERS_REGISTERED = "gui.director.properties.adapters_registered";
        public static final String FALLBACK_ENABLED = "gui.director.properties.fallback_enabled";
        public static final String FALLBACK_DISABLED = "gui.director.properties.fallback_disabled";
        public static final String NO_ADAPTER_TITLE = "gui.director.properties.no_adapter_title";
        public static final String NO_ADAPTER_MESSAGE = "gui.director.properties.no_adapter_message";
        public static final String OBJECT_INFO = "gui.director.properties.object_info";
        public static final String CLASS_NAME = "gui.director.properties.class_name";
        public static final String FULL_CLASS_NAME = "gui.director.properties.full_class_name";
        public static final String PACKAGE_NAME = "gui.director.properties.package_name";
        public static final String SOLUTION = "gui.director.properties.solution";
        public static final String CREATE_ADAPTER = "gui.director.properties.create_adapter";
        public static final String CREATE_ADAPTER_IMPL = "gui.director.properties.create_adapter_impl";
        public static final String CREATE_ADAPTER_REGISTER = "gui.director.properties.create_adapter_register";
        public static final String USE_GENERIC_ADAPTER = "gui.director.properties.use_generic_adapter";
        public static final String USE_GENERIC_ENSURE = "gui.director.properties.use_generic_ensure";
        public static final String USE_GENERIC_INIT = "gui.director.properties.use_generic_init";
        public static final String VIEW_REGISTRY = "gui.director.properties.view_registry";
        public static final String VIEW_REGISTRY_TOOLTIP = "gui.director.properties.view_registry_tooltip";
        public static final String RENDER_ERROR_TITLE = "gui.director.properties.render_error_title";
        public static final String RENDER_ERROR_MESSAGE = "gui.director.properties.render_error_message";
        public static final String ERROR_DETAILS = "gui.director.properties.error_details";
        public static final String EXCEPTION_TYPE = "gui.director.properties.exception_type";
        public static final String ERROR_MESSAGE = "gui.director.properties.error_message";
        public static final String STACK_TRACE = "gui.director.properties.stack_trace";
        public static final String STACK_TRACE_MORE = "gui.director.properties.stack_trace_more";
        public static final String SUGGESTIONS = "gui.director.properties.suggestions";
        public static final String SUGGEST_CHECK_PROPERTIES = "gui.director.properties.suggest.check_properties";
        public static final String SUGGEST_CHECK_LOG = "gui.director.properties.suggest.check_log";
        public static final String SUGGEST_CHECK_ADAPTER = "gui.director.properties.suggest.check_adapter";
        public static final String SUGGEST_RESELECT = "gui.director.properties.suggest.reselect";
        public static final String CLEAR_SELECTION_RETRY = "gui.director.properties.clear_selection_retry";
        public static final String CLEAR_SELECTION_TOOLTIP = "gui.director.properties.clear_selection_tooltip";
        public static final String NO_MESSAGE = "gui.director.properties.no_message";
        public static final String ACTOR_TITLE = "gui.director.properties.actor_title";
        public static final String NO_ACTOR_ADAPTER = "gui.director.properties.no_actor_adapter";

        public static final class AdapterContainer {
            public static final String ADAPTER_NAME = "gui.director.properties.adapter.container.name";
            public static final String NULL_CONTAINER = "gui.director.properties.adapter.container.null";
            public static final String TIMELINE_READONLY = "gui.director.properties.adapter.container.timeline_readonly";
            public static final String GROUP_TITLE = "gui.director.properties.adapter.container.group_title";
            public static final String CONTAINER_TITLE = "gui.director.properties.adapter.container.container_title";
            public static final String SECTION_BASIC = "gui.director.properties.adapter.container.section_basic";
            public static final String SECTION_DISPLAY = "gui.director.properties.adapter.container.section_display";
            public static final String SECTION_VISUAL = "gui.director.properties.adapter.container.section_visual";
            public static final String SECTION_ANIMATION = "gui.director.properties.adapter.container.section_animation";
            public static final String SECTION_BOUNDS = "gui.director.properties.adapter.container.section_bounds";
            public static final String SECTION_STATS = "gui.director.properties.adapter.container.section_stats";
            public static final String SECTION_HIERARCHY = "gui.director.properties.adapter.container.section_hierarchy";
            public static final String SECTION_METADATA = "gui.director.properties.adapter.container.section_metadata";
            public static final String SECTION_CONTENT_STATS = "gui.director.properties.adapter.container.section_content_stats";
            public static final String NAME = "gui.director.properties.adapter.container.name_field";
            public static final String NAME_TOOLTIP = "gui.director.properties.adapter.container.name_tooltip";
            public static final String CONTAINER_TYPE = "gui.director.properties.adapter.container.type";
            public static final String CONTAINER_TYPE_TOOLTIP = "gui.director.properties.adapter.container.type_tooltip";
            public static final String TYPE_LABEL = "gui.director.properties.adapter.container.type_label";
            public static final String VISIBLE = "gui.director.properties.adapter.container.visible";
            public static final String VISIBLE_TOOLTIP = "gui.director.properties.adapter.container.visible_tooltip";
            public static final String LOCKED = "gui.director.properties.adapter.container.locked";
            public static final String LOCKED_TOOLTIP = "gui.director.properties.adapter.container.locked_tooltip";
            public static final String OPACITY = "gui.director.properties.adapter.container.opacity";
            public static final String OPACITY_TOOLTIP = "gui.director.properties.adapter.container.opacity_tooltip";
            public static final String MARKER_COLOR = "gui.director.properties.adapter.container.marker_color";
            public static final String MARKER_COLOR_TOOLTIP = "gui.director.properties.adapter.container.marker_color_tooltip";
            public static final String DIRECT_CHILDREN = "gui.director.properties.adapter.container.direct_children";
            public static final String CHILD_CONTAINERS = "gui.director.properties.adapter.container.child_containers";
            public static final String CHILD_GROUPS = "gui.director.properties.adapter.container.child_groups";
            public static final String DESCENDANT_COUNT = "gui.director.properties.adapter.container.descendant_count";
            public static final String PARENT = "gui.director.properties.adapter.container.parent";
            public static final String PARENT_NONE = "gui.director.properties.adapter.container.parent_none";
            public static final String PARENT_JUMP_TOOLTIP = "gui.director.properties.adapter.container.parent_jump_tooltip";
            public static final String CHILDREN_NONE = "gui.director.properties.adapter.container.children_none";
            public static final String CHILDREN_TREE = "gui.director.properties.adapter.container.children_tree";
            public static final String CHILD_TOOLTIP = "gui.director.properties.adapter.container.child_tooltip";
            public static final String DEPTH = "gui.director.properties.adapter.container.depth";
            public static final String CREATED_AT = "gui.director.properties.adapter.container.created_at";
            public static final String MODIFIED_AT = "gui.director.properties.adapter.container.modified_at";
            public static final String AGE = "gui.director.properties.adapter.container.age";
            public static final String SINCE_MODIFIED = "gui.director.properties.adapter.container.since_modified";
            public static final String DEFAULT_ANIMATION = "gui.director.properties.adapter.container.default_animation";
            public static final String ANIMATION_DEMOLITION_TOOLTIP = "gui.director.properties.adapter.container.animation_demolition_tooltip";
            public static final String ANIMATION_BUILD_TOOLTIP = "gui.director.properties.adapter.container.animation_build_tooltip";
            public static final String LEGACY_MODE_SUFFIX = "gui.director.properties.adapter.container.legacy_mode_suffix";
            public static final String FRAGMENT_SCATTER = "gui.director.properties.adapter.container.fragment_scatter";
            public static final String LEGACY_MODE_HINT = "gui.director.properties.adapter.container.legacy_mode_hint";
            public static final String ANIMATION_PARAMS = "gui.director.properties.adapter.container.animation_params";
            public static final String RENDER_ERROR = "gui.director.properties.adapter.container.render_error";
            public static final String SUGGESTED_DURATION = "gui.director.properties.adapter.container.suggested_duration";
            public static final String SUGGESTED_DURATION_TOOLTIP = "gui.director.properties.adapter.container.suggested_duration_tooltip";
            public static final String DEFAULT_DELAY = "gui.director.properties.adapter.container.default_delay";
            public static final String CLIP_OVERRIDE_HINT = "gui.director.properties.adapter.container.clip_override_hint";
            public static final String MIN_POINT = "gui.director.properties.adapter.container.min_point";
            public static final String MAX_POINT = "gui.director.properties.adapter.container.max_point";
            public static final String CENTER_POINT = "gui.director.properties.adapter.container.center_point";
            public static final String DIMENSIONS = "gui.director.properties.adapter.container.dimensions";
            public static final String DIMENSIONS_TOOLTIP = "gui.director.properties.adapter.container.dimensions_tooltip";
            public static final String BLOCK_COUNT = "gui.director.properties.adapter.container.block_count";
            public static final String TOTAL_BLOCKS = "gui.director.properties.adapter.container.total_blocks";
            public static final String VOLUME = "gui.director.properties.adapter.container.volume";
            public static final String FILL_RATE = "gui.director.properties.adapter.container.fill_rate";
            public static final String FILL_RATE_TOOLTIP = "gui.director.properties.adapter.container.fill_rate_tooltip";
            public static final String QUICK_ACTIONS = "gui.director.properties.adapter.container.quick_actions";
            public static final String CLONE = "gui.director.properties.adapter.container.clone";
            public static final String CLONE_TOOLTIP = "gui.director.properties.adapter.container.clone_tooltip";
            public static final String CLEAR_BLOCKS = "gui.director.properties.adapter.container.clear_blocks";
            public static final String CLEAR_BLOCKS_TOOLTIP = "gui.director.properties.adapter.container.clear_blocks_tooltip";
            public static final String SYNC_WORLD = "gui.director.properties.adapter.container.sync_world";
            public static final String SYNC_WORLD_TOOLTIP = "gui.director.properties.adapter.container.sync_world_tooltip";
            public static final String REFRESH_STATS = "gui.director.properties.adapter.container.refresh_stats";
            public static final String REFRESH_STATS_TOOLTIP = "gui.director.properties.adapter.container.refresh_stats_tooltip";
            public static final String RESET_CENTER = "gui.director.properties.adapter.container.reset_center";
            public static final String RESET_CENTER_TOOLTIP = "gui.director.properties.adapter.container.reset_center_tooltip";
            public static final String DURATION_SECONDS = "gui.director.properties.adapter.container.duration_seconds";
            public static final String DURATION_MINUTES = "gui.director.properties.adapter.container.duration_minutes";
            public static final String DURATION_HOURS = "gui.director.properties.adapter.container.duration_hours";
            public static final String DURATION_DAYS = "gui.director.properties.adapter.container.duration_days";

            private AdapterContainer() {
            }
        }

        public static final class AdapterKeyframe {
            public static final String NO_KEYFRAME = "gui.director.properties.adapter.keyframe.no_keyframe";
            public static final String TIME_GROUP = "gui.director.properties.adapter.keyframe.time_group";
            public static final String TIME_SECONDS = "gui.director.properties.adapter.keyframe.time_seconds";
            public static final String VALUE_GROUP = "gui.director.properties.adapter.keyframe.value_group";
            public static final String VECTOR = "gui.director.properties.adapter.keyframe.vector";
            public static final String SCALAR = "gui.director.properties.adapter.keyframe.scalar";
            public static final String VALUE_LABEL = "gui.director.properties.adapter.keyframe.value_label";
            public static final String INTERPOLATION_GROUP = "gui.director.properties.adapter.keyframe.interpolation_group";
            public static final String MODE = "gui.director.properties.adapter.keyframe.mode";

            private AdapterKeyframe() {
            }
        }

        public static final class EmbeddedContainer {
            public static final String EMPTY_NO_SELECTION = "gui.director.properties.embedded.container.empty_no_selection";
            public static final String EMPTY_HINT = "gui.director.properties.embedded.container.empty_hint";
            public static final String BASIC_PROPERTIES = "gui.director.properties.embedded.container.basic_properties";
            public static final String NAME = "gui.director.properties.embedded.container.name";
            public static final String TYPE = "gui.director.properties.embedded.container.type";
            public static final String TYPE_TOOLTIP = "gui.director.properties.embedded.container.type_tooltip";
            public static final String DESCRIPTION = "gui.director.properties.embedded.container.description";
            public static final String ANIMATION_PRESET = "gui.director.properties.embedded.container.animation_preset";
            public static final String APPEAR_MODE = "gui.director.properties.embedded.container.appear_mode";
            public static final String SUGGESTED_DURATION = "gui.director.properties.embedded.container.suggested_duration";
            public static final String SUGGESTED_DURATION_TOOLTIP = "gui.director.properties.embedded.container.suggested_duration_tooltip";
            public static final String DEFAULT_DELAY = "gui.director.properties.embedded.container.default_delay";
            public static final String EASING_CURVE = "gui.director.properties.embedded.container.easing_curve";
            public static final String PLAY_ORDERING = "gui.director.properties.embedded.container.play_ordering";
            public static final String PRESET_HINT = "gui.director.properties.embedded.container.preset_hint";
            public static final String CONTAINER_PROPERTIES = "gui.director.properties.embedded.container.container_properties";
            public static final String UI_TAG_COLOR = "gui.director.properties.embedded.container.ui_tag_color";
            public static final String UI_TAG_COLOR_TOOLTIP = "gui.director.properties.embedded.container.ui_tag_color_tooltip";
            public static final String TAG_COLOR_PICKER = "gui.director.properties.embedded.container.tag_color_picker";
            public static final String CLEAR = "gui.director.properties.embedded.container.clear";
            public static final String CLEAR_TAG_TOOLTIP = "gui.director.properties.embedded.container.clear_tag_tooltip";
            public static final String LOCKED = "gui.director.properties.embedded.container.locked";
            public static final String LOCKED_TOOLTIP = "gui.director.properties.embedded.container.locked_tooltip";
            public static final String VISIBLE = "gui.director.properties.embedded.container.visible";
            public static final String HIDE_TOOLTIP = "gui.director.properties.embedded.container.hide_tooltip";
            public static final String SHOW_TOOLTIP = "gui.director.properties.embedded.container.show_tooltip";
            public static final String STATS = "gui.director.properties.embedded.container.stats";
            public static final String BLOCK_COUNT = "gui.director.properties.embedded.container.block_count";
            public static final String CHILD_COUNT = "gui.director.properties.embedded.container.child_count";
            public static final String TOTAL_BLOCKS = "gui.director.properties.embedded.container.total_blocks";
            public static final String SIZE_VOLUME = "gui.director.properties.embedded.container.size_volume";
            public static final String CENTER = "gui.director.properties.embedded.container.center";
            public static final String CREATED_AT = "gui.director.properties.embedded.container.created_at";
            public static final String MODIFIED_AT = "gui.director.properties.embedded.container.modified_at";

            private EmbeddedContainer() {
            }
        }

        public static final class EmbeddedGroup {
            public static final String TITLE = "gui.director.properties.embedded.group.title";
            public static final String BASIC_INFO = "gui.director.properties.embedded.group.basic_info";
            public static final String GROUP_NAME = "gui.director.properties.embedded.group.group_name";
            public static final String NOTES = "gui.director.properties.embedded.group.notes";
            public static final String STATS_HEADER = "gui.director.properties.embedded.group.stats_header";
            public static final String CHILD_CONTAINERS = "gui.director.properties.embedded.group.child_containers";
            public static final String TOTAL_BLOCKS = "gui.director.properties.embedded.group.total_blocks";
            public static final String INCLUDED_CONTAINERS = "gui.director.properties.embedded.group.included_containers";
            public static final String NO_CHILDREN = "gui.director.properties.embedded.group.no_children";
            public static final String CHILD_ITEM = "gui.director.properties.embedded.group.child_item";
            public static final String MORE_CHILDREN = "gui.director.properties.embedded.group.more_children";
            public static final String NO_GROUP_SELECTED = "gui.director.properties.embedded.group.no_group_selected";
            public static final String SELECT_GROUP_HINT = "gui.director.properties.embedded.group.select_group_hint";

            private EmbeddedGroup() {
            }
        }

        public static final class AdapterTimelineCommon {
            public static final String YES = "gui.director.properties.adapter.timeline.common.yes";
            public static final String NO = "gui.director.properties.adapter.timeline.common.no";
            public static final String UNKNOWN = "gui.director.properties.adapter.timeline.common.unknown";
            public static final String RENDER_ERROR = "gui.director.properties.adapter.timeline.common.render_error";
            public static final String QUICK_ACTIONS = "gui.director.properties.adapter.timeline.common.quick_actions";
            public static final String UNBOUND = "gui.director.properties.adapter.timeline.common.unbound";
            public static final String NOT_SAVED = "gui.director.properties.adapter.timeline.common.not_saved";

            private AdapterTimelineCommon() {
            }
        }

        public static final class AdapterTimelineProject {
            public static final String ADAPTER_NAME = "gui.director.properties.adapter.timeline.project.name";
            public static final String NULL_PROJECT = "gui.director.properties.adapter.timeline.project.null";
            public static final String TITLE = "gui.director.properties.adapter.timeline.project.title";
            public static final String DEFAULT_NAME = "gui.director.properties.adapter.timeline.project.default_name";
            public static final String SECTION_BASIC = "gui.director.properties.adapter.timeline.project.section_basic";
            public static final String PROJECT_NAME = "gui.director.properties.adapter.timeline.project.project_name";
            public static final String PROJECT_NAME_TOOLTIP = "gui.director.properties.adapter.timeline.project.project_name_tooltip";
            public static final String AUTHOR = "gui.director.properties.adapter.timeline.project.author";
            public static final String AUTHOR_TOOLTIP = "gui.director.properties.adapter.timeline.project.author_tooltip";
            public static final String DESCRIPTION = "gui.director.properties.adapter.timeline.project.description";
            public static final String DESCRIPTION_TOOLTIP = "gui.director.properties.adapter.timeline.project.description_tooltip";
            public static final String VERSION = "gui.director.properties.adapter.timeline.project.version";
            public static final String PROJECT_ID = "gui.director.properties.adapter.timeline.project.project_id";
            public static final String SECTION_GLOBAL = "gui.director.properties.adapter.timeline.project.section_global";
            public static final String FRAME_RATE = "gui.director.properties.adapter.timeline.project.frame_rate";
            public static final String FRAME_RATE_TOOLTIP = "gui.director.properties.adapter.timeline.project.frame_rate_tooltip";
            public static final String TIME_UNIT = "gui.director.properties.adapter.timeline.project.time_unit";
            public static final String TIME_UNIT_TOOLTIP = "gui.director.properties.adapter.timeline.project.time_unit_tooltip";
            public static final String AUTO_SAVE = "gui.director.properties.adapter.timeline.project.auto_save";
            public static final String AUTO_SAVE_TOOLTIP = "gui.director.properties.adapter.timeline.project.auto_save_tooltip";
            public static final String LOOP_ALL = "gui.director.properties.adapter.timeline.project.loop_all";
            public static final String LOOP_ALL_TOOLTIP = "gui.director.properties.adapter.timeline.project.loop_all_tooltip";
            public static final String SECTION_SEQUENCES = "gui.director.properties.adapter.timeline.project.section_sequences";
            public static final String SEQUENCES_NONE = "gui.director.properties.adapter.timeline.project.sequences_none";
            public static final String CREATE_FIRST_SEQUENCE = "gui.director.properties.adapter.timeline.project.create_first_sequence";
            public static final String DEFAULT_SEQUENCE_NAME = "gui.director.properties.adapter.timeline.project.default_sequence_name";
            public static final String SEQUENCE_DURATION = "gui.director.properties.adapter.timeline.project.sequence_duration";
            public static final String SEQUENCE_TRACK_COUNT = "gui.director.properties.adapter.timeline.project.sequence_track_count";
            public static final String SEQUENCE_LOOP = "gui.director.properties.adapter.timeline.project.sequence_loop";
            public static final String SEQUENCE_TIME_SCALE = "gui.director.properties.adapter.timeline.project.sequence_time_scale";
            public static final String EDIT_SEQUENCE = "gui.director.properties.adapter.timeline.project.edit_sequence";
            public static final String EDIT_SEQUENCE_TOOLTIP = "gui.director.properties.adapter.timeline.project.edit_sequence_tooltip";
            public static final String DELETE_SEQUENCE_TOOLTIP = "gui.director.properties.adapter.timeline.project.delete_sequence_tooltip";
            public static final String SEQUENCE_HINT = "gui.director.properties.adapter.timeline.project.sequence_hint";
            public static final String CANNOT_GET_SEQUENCES = "gui.director.properties.adapter.timeline.project.cannot_get_sequences";
            public static final String ADD_SEQUENCE = "gui.director.properties.adapter.timeline.project.add_sequence";
            public static final String ADD_SEQUENCE_TOOLTIP = "gui.director.properties.adapter.timeline.project.add_sequence_tooltip";
            public static final String CLEAR_ALL_SEQUENCES = "gui.director.properties.adapter.timeline.project.clear_all_sequences";
            public static final String CLEAR_ALL_SEQUENCES_TOOLTIP = "gui.director.properties.adapter.timeline.project.clear_all_sequences_tooltip";
            public static final String SECTION_EXPORT = "gui.director.properties.adapter.timeline.project.section_export";
            public static final String EXPORT_FORMAT = "gui.director.properties.adapter.timeline.project.export_format";
            public static final String EXPORT_FORMAT_TOOLTIP = "gui.director.properties.adapter.timeline.project.export_format_tooltip";
            public static final String EXPORT_PATH = "gui.director.properties.adapter.timeline.project.export_path";
            public static final String EXPORT_PATH_TOOLTIP = "gui.director.properties.adapter.timeline.project.export_path_tooltip";
            public static final String BROWSE_PATH = "gui.director.properties.adapter.timeline.project.browse_path";
            public static final String BROWSE_PATH_TOOLTIP = "gui.director.properties.adapter.timeline.project.browse_path_tooltip";
            public static final String INCLUDE_RESOURCES = "gui.director.properties.adapter.timeline.project.include_resources";
            public static final String COMPRESS_EXPORT = "gui.director.properties.adapter.timeline.project.compress_export";
            public static final String SECTION_STATS = "gui.director.properties.adapter.timeline.project.section_stats";
            public static final String SEQUENCE_TOTAL = "gui.director.properties.adapter.timeline.project.sequence_total";
            public static final String TOTAL_TRACKS = "gui.director.properties.adapter.timeline.project.total_tracks";
            public static final String TOTAL_KEYFRAMES = "gui.director.properties.adapter.timeline.project.total_keyframes";
            public static final String PROJECT_PATH = "gui.director.properties.adapter.timeline.project.project_path";
            public static final String CREATED_AT = "gui.director.properties.adapter.timeline.project.created_at";
            public static final String MODIFIED_AT = "gui.director.properties.adapter.timeline.project.modified_at";
            public static final String SECTION_CONTROLS = "gui.director.properties.adapter.timeline.project.section_controls";
            public static final String SAVE_PROJECT = "gui.director.properties.adapter.timeline.project.save_project";
            public static final String SAVE_PROJECT_TOOLTIP = "gui.director.properties.adapter.timeline.project.save_project_tooltip";
            public static final String EXPORT_PROJECT = "gui.director.properties.adapter.timeline.project.export_project";
            public static final String EXPORT_PROJECT_TOOLTIP = "gui.director.properties.adapter.timeline.project.export_project_tooltip";
            public static final String RELOAD = "gui.director.properties.adapter.timeline.project.reload";
            public static final String RELOAD_TOOLTIP = "gui.director.properties.adapter.timeline.project.reload_tooltip";
            public static final String NEW_PROJECT = "gui.director.properties.adapter.timeline.project.new_project";
            public static final String NEW_PROJECT_TOOLTIP = "gui.director.properties.adapter.timeline.project.new_project_tooltip";
            public static final String COPY_PROJECT = "gui.director.properties.adapter.timeline.project.copy_project";
            public static final String COPY_PROJECT_TOOLTIP = "gui.director.properties.adapter.timeline.project.copy_project_tooltip";
            public static final String CLEAR_CACHE = "gui.director.properties.adapter.timeline.project.clear_cache";
            public static final String CLEAR_CACHE_TOOLTIP = "gui.director.properties.adapter.timeline.project.clear_cache_tooltip";
            public static final String FOOTER_HINT = "gui.director.properties.adapter.timeline.project.footer_hint";
            public static final String EXPORT_FORMAT_JSON = "gui.director.properties.adapter.timeline.project.export_format_json";
            public static final String EXPORT_FORMAT_NBT = "gui.director.properties.adapter.timeline.project.export_format_nbt";
            public static final String EXPORT_FORMAT_BINARY = "gui.director.properties.adapter.timeline.project.export_format_binary";
            public static final String EXPORT_FORMAT_SCENEPACK = "gui.director.properties.adapter.timeline.project.export_format_scenepack";
            public static final String TIME_UNIT_SECONDS = "gui.director.properties.adapter.timeline.project.time_unit_seconds";
            public static final String TIME_UNIT_FRAMES = "gui.director.properties.adapter.timeline.project.time_unit_frames";
            public static final String TIME_UNIT_TICKS = "gui.director.properties.adapter.timeline.project.time_unit_ticks";

            private AdapterTimelineProject() {
            }
        }

        public static final class AdapterTimelineTrack {
            public static final String ADAPTER_NAME = "gui.director.properties.adapter.timeline.track.name";
            public static final String NULL_TRACK = "gui.director.properties.adapter.timeline.track.null";
            public static final String TITLE = "gui.director.properties.adapter.timeline.track.title";
            public static final String DEFAULT_NAME = "gui.director.properties.adapter.timeline.track.default_name";
            public static final String SECTION_BASIC = "gui.director.properties.adapter.timeline.track.section_basic";
            public static final String TRACK_NAME = "gui.director.properties.adapter.timeline.track.track_name";
            public static final String TRACK_NAME_TOOLTIP = "gui.director.properties.adapter.timeline.track.track_name_tooltip";
            public static final String ENABLE_TRACK = "gui.director.properties.adapter.timeline.track.enable_track";
            public static final String ENABLE_TRACK_TOOLTIP = "gui.director.properties.adapter.timeline.track.enable_track_tooltip";
            public static final String TRACK_TYPE = "gui.director.properties.adapter.timeline.track.track_type";
            public static final String TRACK_ID = "gui.director.properties.adapter.timeline.track.track_id";
            public static final String SECTION_CONTAINER_BINDING = "gui.director.properties.adapter.timeline.track.section_container_binding";
            public static final String BOUND_CONTAINER = "gui.director.properties.adapter.timeline.track.bound_container";
            public static final String BOUND_CONTAINER_TOOLTIP = "gui.director.properties.adapter.timeline.track.bound_container_tooltip";
            public static final String SELECT_CONTAINER = "gui.director.properties.adapter.timeline.track.select_container";
            public static final String SELECT_CONTAINER_TOOLTIP = "gui.director.properties.adapter.timeline.track.select_container_tooltip";
            public static final String CLEAR_BINDING = "gui.director.properties.adapter.timeline.track.clear_binding";
            public static final String CLEAR_BINDING_TOOLTIP = "gui.director.properties.adapter.timeline.track.clear_binding_tooltip";
            public static final String SECTION_BLEND = "gui.director.properties.adapter.timeline.track.section_blend";
            public static final String BLEND_MODE = "gui.director.properties.adapter.timeline.track.blend_mode";
            public static final String BLEND_MODE_TOOLTIP = "gui.director.properties.adapter.timeline.track.blend_mode_tooltip";
            public static final String WEIGHT = "gui.director.properties.adapter.timeline.track.weight";
            public static final String WEIGHT_TOOLTIP = "gui.director.properties.adapter.timeline.track.weight_tooltip";
            public static final String ADDITIVE_MODE = "gui.director.properties.adapter.timeline.track.additive_mode";
            public static final String ADDITIVE_MODE_TOOLTIP = "gui.director.properties.adapter.timeline.track.additive_mode_tooltip";
            public static final String SECTION_ALIGNMENT = "gui.director.properties.adapter.timeline.track.section_alignment";
            public static final String ALIGNMENT_MODE = "gui.director.properties.adapter.timeline.track.alignment_mode";
            public static final String ALIGNMENT_MODE_TOOLTIP = "gui.director.properties.adapter.timeline.track.alignment_mode_tooltip";
            public static final String START_TIME = "gui.director.properties.adapter.timeline.track.start_time";
            public static final String START_TIME_TOOLTIP = "gui.director.properties.adapter.timeline.track.start_time_tooltip";
            public static final String END_TIME = "gui.director.properties.adapter.timeline.track.end_time";
            public static final String END_TIME_TOOLTIP = "gui.director.properties.adapter.timeline.track.end_time_tooltip";
            public static final String DURATION = "gui.director.properties.adapter.timeline.track.duration";
            public static final String SECTION_VISUAL = "gui.director.properties.adapter.timeline.track.section_visual";
            public static final String TRACK_COLOR = "gui.director.properties.adapter.timeline.track.track_color";
            public static final String TRACK_COLOR_TOOLTIP = "gui.director.properties.adapter.timeline.track.track_color_tooltip";
            public static final String VISUAL_OFFSET = "gui.director.properties.adapter.timeline.track.visual_offset";
            public static final String SECTION_STATS = "gui.director.properties.adapter.timeline.track.section_stats";
            public static final String KEYFRAME_TOTAL = "gui.director.properties.adapter.timeline.track.keyframe_total";
            public static final String ENABLED_KEYFRAMES = "gui.director.properties.adapter.timeline.track.enabled_keyframes";
            public static final String TIME_RANGE = "gui.director.properties.adapter.timeline.track.time_range";
            public static final String DURATION_STATS = "gui.director.properties.adapter.timeline.track.duration_stats";
            public static final String CURRENT_WEIGHT = "gui.director.properties.adapter.timeline.track.current_weight";
            public static final String SECTION_ACTOR_CLIPS = "gui.director.properties.adapter.timeline.track.section_actor_clips";
            public static final String ACTOR_CLIP_HINT = "gui.director.properties.adapter.timeline.track.actor_clip_hint";
            public static final String ACTOR_CLIP_TRANSFORM = "gui.director.properties.adapter.timeline.track.actor_clip_transform";
            public static final String ACTOR_CLIP_TRANSFORM_TOOLTIP = "gui.director.properties.adapter.timeline.track.actor_clip_transform_tooltip";
            public static final String ACTOR_CLIP_LOOK_AT = "gui.director.properties.adapter.timeline.track.actor_clip_look_at";
            public static final String ACTOR_CLIP_LOOK_AT_TOOLTIP = "gui.director.properties.adapter.timeline.track.actor_clip_look_at_tooltip";
            public static final String ACTOR_CLIP_ACTION = "gui.director.properties.adapter.timeline.track.actor_clip_action";
            public static final String ACTOR_CLIP_ACTION_TOOLTIP = "gui.director.properties.adapter.timeline.track.actor_clip_action_tooltip";
            public static final String ACTOR_CLIP_POSE = "gui.director.properties.adapter.timeline.track.actor_clip_pose";
            public static final String ACTOR_CLIP_POSE_TOOLTIP = "gui.director.properties.adapter.timeline.track.actor_clip_pose_tooltip";
            public static final String ACTOR_CLIP_STATE = "gui.director.properties.adapter.timeline.track.actor_clip_state";
            public static final String ACTOR_CLIP_STATE_TOOLTIP = "gui.director.properties.adapter.timeline.track.actor_clip_state_tooltip";
            public static final String DUPLICATE_TRACK = "gui.director.properties.adapter.timeline.track.duplicate_track";
            public static final String DUPLICATE_TRACK_TOOLTIP = "gui.director.properties.adapter.timeline.track.duplicate_track_tooltip";
            public static final String CLEAR_KEYFRAMES = "gui.director.properties.adapter.timeline.track.clear_keyframes";
            public static final String CLEAR_KEYFRAMES_TOOLTIP = "gui.director.properties.adapter.timeline.track.clear_keyframes_tooltip";
            public static final String LOCK_TRACK = "gui.director.properties.adapter.timeline.track.lock_track";
            public static final String UNLOCK_TRACK = "gui.director.properties.adapter.timeline.track.unlock_track";
            public static final String LOCK_TRACK_TOOLTIP = "gui.director.properties.adapter.timeline.track.lock_track_tooltip";
            public static final String UNLOCK_TRACK_TOOLTIP = "gui.director.properties.adapter.timeline.track.unlock_track_tooltip";
            public static final String RESET_WEIGHT = "gui.director.properties.adapter.timeline.track.reset_weight";
            public static final String RESET_WEIGHT_TOOLTIP = "gui.director.properties.adapter.timeline.track.reset_weight_tooltip";
            public static final String RESET_TIME = "gui.director.properties.adapter.timeline.track.reset_time";
            public static final String RESET_TIME_TOOLTIP = "gui.director.properties.adapter.timeline.track.reset_time_tooltip";
            public static final String BLEND_OVERRIDE = "gui.director.properties.adapter.timeline.track.blend_override";
            public static final String BLEND_ADDITIVE = "gui.director.properties.adapter.timeline.track.blend_additive";
            public static final String BLEND_MULTIPLY = "gui.director.properties.adapter.timeline.track.blend_multiply";
            public static final String BLEND_SUBTRACT = "gui.director.properties.adapter.timeline.track.blend_subtract";
            public static final String BLEND_CUSTOM = "gui.director.properties.adapter.timeline.track.blend_custom";
            public static final String ALIGN_GLOBAL = "gui.director.properties.adapter.timeline.track.align_global";
            public static final String ALIGN_CONTAINER = "gui.director.properties.adapter.timeline.track.align_container";
            public static final String ALIGN_LOCAL = "gui.director.properties.adapter.timeline.track.align_local";

            private AdapterTimelineTrack() {
            }
        }

        public static final class AdapterTimelineSequence {
            public static final String ADAPTER_NAME = "gui.director.properties.adapter.timeline.sequence.name";
            public static final String NULL_SEQUENCE = "gui.director.properties.adapter.timeline.sequence.null";
            public static final String TITLE = "gui.director.properties.adapter.timeline.sequence.title";
            public static final String DEFAULT_NAME = "gui.director.properties.adapter.timeline.sequence.default_name";
            public static final String SECTION_BASIC = "gui.director.properties.adapter.timeline.sequence.section_basic";
            public static final String SEQUENCE_NAME = "gui.director.properties.adapter.timeline.sequence.sequence_name";
            public static final String SEQUENCE_NAME_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.sequence_name_tooltip";
            public static final String TOTAL_DURATION = "gui.director.properties.adapter.timeline.sequence.total_duration";
            public static final String TOTAL_DURATION_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.total_duration_tooltip";
            public static final String TIME_SCALE = "gui.director.properties.adapter.timeline.sequence.time_scale";
            public static final String TIME_SCALE_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.time_scale_tooltip";
            public static final String SEQUENCE_ID = "gui.director.properties.adapter.timeline.sequence.sequence_id";
            public static final String SECTION_PLAYBACK = "gui.director.properties.adapter.timeline.sequence.section_playback";
            public static final String LOOP_PLAYBACK = "gui.director.properties.adapter.timeline.sequence.loop_playback";
            public static final String LOOP_PLAYBACK_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.loop_playback_tooltip";
            public static final String PLAY_MODE = "gui.director.properties.adapter.timeline.sequence.play_mode";
            public static final String PLAY_MODE_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.play_mode_tooltip";
            public static final String CURRENT_TIME = "gui.director.properties.adapter.timeline.sequence.current_time";
            public static final String PLAYING_STATUS = "gui.director.properties.adapter.timeline.sequence.playing_status";
            public static final String PAUSED_STATUS = "gui.director.properties.adapter.timeline.sequence.paused_status";
            public static final String SECTION_TRACKS = "gui.director.properties.adapter.timeline.sequence.section_tracks";
            public static final String TRACKS_NONE = "gui.director.properties.adapter.timeline.sequence.tracks_none";
            public static final String ADD_TRACK_HINT = "gui.director.properties.adapter.timeline.sequence.add_track_hint";
            public static final String DEFAULT_TRACK_NAME = "gui.director.properties.adapter.timeline.sequence.default_track_name";
            public static final String TRACK_TYPE = "gui.director.properties.adapter.timeline.sequence.track_type";
            public static final String TRACK_WEIGHT = "gui.director.properties.adapter.timeline.sequence.track_weight";
            public static final String TRACK_KEYFRAMES = "gui.director.properties.adapter.timeline.sequence.track_keyframes";
            public static final String EDIT_TRACK = "gui.director.properties.adapter.timeline.sequence.edit_track";
            public static final String EDIT_TRACK_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.edit_track_tooltip";
            public static final String DELETE_TRACK_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.delete_track_tooltip";
            public static final String TRACK_HINT = "gui.director.properties.adapter.timeline.sequence.track_hint";
            public static final String CANNOT_GET_TRACKS = "gui.director.properties.adapter.timeline.sequence.cannot_get_tracks";
            public static final String ADD_TRACK = "gui.director.properties.adapter.timeline.sequence.add_track";
            public static final String ADD_TRACK_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.add_track_tooltip";
            public static final String CLEAR_ALL_TRACKS = "gui.director.properties.adapter.timeline.sequence.clear_all_tracks";
            public static final String CLEAR_ALL_TRACKS_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.clear_all_tracks_tooltip";
            public static final String SECTION_STATS = "gui.director.properties.adapter.timeline.sequence.section_stats";
            public static final String TRACK_TOTAL = "gui.director.properties.adapter.timeline.sequence.track_total";
            public static final String TOTAL_KEYFRAMES = "gui.director.properties.adapter.timeline.sequence.total_keyframes";
            public static final String DURATION_LABEL = "gui.director.properties.adapter.timeline.sequence.duration_label";
            public static final String ACTUAL_DURATION = "gui.director.properties.adapter.timeline.sequence.actual_duration";
            public static final String PLAYBACK_CONTROL = "gui.director.properties.adapter.timeline.sequence.playback_control";
            public static final String PLAY = "gui.director.properties.adapter.timeline.sequence.play";
            public static final String PLAY_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.play_tooltip";
            public static final String PAUSE = "gui.director.properties.adapter.timeline.sequence.pause";
            public static final String PAUSE_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.pause_tooltip";
            public static final String STOP = "gui.director.properties.adapter.timeline.sequence.stop";
            public static final String STOP_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.stop_tooltip";
            public static final String SEQUENCE_OPS = "gui.director.properties.adapter.timeline.sequence.sequence_ops";
            public static final String DUPLICATE_SEQUENCE = "gui.director.properties.adapter.timeline.sequence.duplicate_sequence";
            public static final String DUPLICATE_SEQUENCE_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.duplicate_sequence_tooltip";
            public static final String RESET_SEQUENCE = "gui.director.properties.adapter.timeline.sequence.reset_sequence";
            public static final String RESET_SEQUENCE_TOOLTIP = "gui.director.properties.adapter.timeline.sequence.reset_sequence_tooltip";
            public static final String PLAY_SEQUENTIAL = "gui.director.properties.adapter.timeline.sequence.play_sequential";
            public static final String PLAY_LOOP = "gui.director.properties.adapter.timeline.sequence.play_loop";
            public static final String PLAY_PINGPONG = "gui.director.properties.adapter.timeline.sequence.play_pingpong";
            public static final String PLAY_RANDOM = "gui.director.properties.adapter.timeline.sequence.play_random";

            private AdapterTimelineSequence() {
            }
        }

        public static final class AdapterTimelineClip {
            public static final String ADAPTER_NAME = "gui.director.properties.adapter.timeline.clip.name";
            public static final String NO_SELECTION = "gui.director.properties.adapter.timeline.clip.no_selection";
            public static final String VIEW_DETAILS = "gui.director.properties.adapter.timeline.clip.view_details";
            public static final String SECTION_BASIC = "gui.director.properties.adapter.timeline.clip.section_basic";
            public static final String SECTION_SEQUENCE = "gui.director.properties.adapter.timeline.clip.section_sequence";
            public static final String SECTION_ANIMATION = "gui.director.properties.adapter.timeline.clip.section_animation";
            public static final String EFFECTS_HEADER = "gui.director.properties.adapter.timeline.clip.effects_header";
            public static final String EFFECTS_HEADER_COUNT = "gui.director.properties.adapter.timeline.clip.effects_header_count";

            private AdapterTimelineClip() {
            }
        }

        public static final class AdapterTimelineClipUi {
            // basic
            public static final String BASIC_START_TIME = "gui.director.properties.clip_ui.basic.start_time";
            public static final String BASIC_START_TIME_TOOLTIP = "gui.director.properties.clip_ui.basic.start_time_tooltip";
            public static final String BASIC_DURATION = "gui.director.properties.clip_ui.basic.duration";
            public static final String BASIC_DURATION_TOOLTIP_BUILD = "gui.director.properties.clip_ui.basic.duration_tooltip_build";
            public static final String BASIC_DURATION_TOOLTIP_DEMOLITION = "gui.director.properties.clip_ui.basic.duration_tooltip_demolition";
            public static final String BASIC_CLIP_COLOR = "gui.director.properties.clip_ui.basic.clip_color";
            public static final String BASIC_AUTO_FADE_PREVIOUS = "gui.director.properties.clip_ui.basic.auto_fade_previous";
            public static final String BASIC_FADE_OPACITY = "gui.director.properties.clip_ui.basic.fade_opacity";
            public static final String BASIC_OCCLUSION_TRANSPARENCY = "gui.director.properties.clip_ui.basic.occlusion_transparency";
            public static final String BASIC_POST_ANIMATION_FADE_HEADER = "gui.director.properties.clip_ui.basic.post_animation_fade_header";
            public static final String BASIC_POST_ANIMATION_FADE_HINT = "gui.director.properties.clip_ui.basic.post_animation_fade_hint";
            public static final String BASIC_FADE_START_SECONDS = "gui.director.properties.clip_ui.basic.fade_start_seconds";
            public static final String BASIC_FADE_END_SECONDS = "gui.director.properties.clip_ui.basic.fade_end_seconds";
            public static final String BASIC_FADE_DURATION_INFO = "gui.director.properties.clip_ui.basic.fade_duration_info";
            public static final String BASIC_FADE_INSTANT_WARNING = "gui.director.properties.clip_ui.basic.fade_instant_warning";
            public static final String BASIC_FADE_DISABLED = "gui.director.properties.clip_ui.basic.fade_disabled";

            // animation
            public static final String ANIMATION_CONFIG_EMPTY = "gui.director.properties.clip_ui.animation.config_empty";
            public static final String ANIMATION_DURATION_SECONDS = "gui.director.properties.clip_ui.animation.duration_seconds";
            public static final String ANIMATION_EASING_HEADER = "gui.director.properties.clip_ui.animation.easing_header";
            public static final String ANIMATION_ADVANCED_HEADER = "gui.director.properties.clip_ui.animation.advanced_header";
            public static final String ANIMATION_FADE_INTENSITY_FORMAT = "gui.director.properties.clip_ui.animation.fade_intensity_format";
            public static final String ANIMATION_FADE_INTENSITY_TOOLTIP = "gui.director.properties.clip_ui.animation.fade_intensity_tooltip";
            public static final String ANIMATION_FLASH_WHITE_HINT = "gui.director.properties.clip_ui.animation.flash_white_hint";
            public static final String ANIMATION_RESTORE_DEFAULTS = "gui.director.properties.clip_ui.animation.restore_defaults";
            public static final String ANIMATION_RESTORE_DEFAULTS_TOOLTIP = "gui.director.properties.clip_ui.animation.restore_defaults_tooltip";
            public static final String ANIMATION_NO_TRANSFORMER = "gui.director.properties.clip_ui.animation.no_transformer";
            public static final String ANIMATION_POLICY_CONTAINER_SECTION = "gui.director.properties.clip_ui.animation.policy_container_section";
            public static final String ANIMATION_POLICY_CONTAINER_MODE_TOOLTIP = "gui.director.properties.clip_ui.animation.policy_container_mode_tooltip";
            public static final String ANIMATION_POLICY_CONTAINER_DURATION_TOOLTIP = "gui.director.properties.clip_ui.animation.policy_container_duration_tooltip";
            public static final String ANIMATION_POLICY_CONTAINER_OPACITY_FADE = "gui.director.properties.clip_ui.animation.policy_container_opacity_fade";
            public static final String ANIMATION_POLICY_CONTAINER_OPACITY_FADE_TOOLTIP = "gui.director.properties.clip_ui.animation.policy_container_opacity_fade_tooltip";
            public static final String ANIMATION_POLICY_DEMOLITION_SECTION = "gui.director.properties.clip_ui.animation.policy_demolition_section";
            public static final String ANIMATION_POLICY_DEMOLITION_HINT = "gui.director.properties.clip_ui.animation.policy_demolition_hint";
            public static final String ANIMATION_POLICY_DEMOLITION_MODE_TOOLTIP = "gui.director.properties.clip_ui.animation.policy_demolition_mode_tooltip";
            public static final String ANIMATION_POLICY_DEMOLITION_DURATION_TOOLTIP = "gui.director.properties.clip_ui.animation.policy_demolition_duration_tooltip";
            public static final String ANIMATION_POLICY_DEMOLITION_OPACITY_FADE = "gui.director.properties.clip_ui.animation.policy_demolition_opacity_fade";
            public static final String ANIMATION_POLICY_DEMOLITION_OPACITY_FADE_TOOLTIP = "gui.director.properties.clip_ui.animation.policy_demolition_opacity_fade_tooltip";
            public static final String ANIMATION_POLICY_ASSEMBLE_EXIT_DESC = "gui.director.properties.clip_ui.animation.policy_assemble_exit_desc";

            // easing
            public static final String EASING_LINEAR_FALLBACK = "gui.director.properties.clip_ui.easing.linear_fallback";
            public static final String EASING_PREVIEW_EMPTY = "gui.director.properties.clip_ui.easing.preview_empty";
            public static final String EASING_PREVIEW_TOOLTIP = "gui.director.properties.clip_ui.easing.preview_tooltip";

            // sequence
            public static final String SEQUENCE_CONFIG_EMPTY = "gui.director.properties.clip_ui.sequence.config_empty";
            public static final String SEQUENCE_PARAMS_EMPTY = "gui.director.properties.clip_ui.sequence.params_empty";
            public static final String SEQUENCE_PRESET_HEADER = "gui.director.properties.clip_ui.sequence.preset_header";
            public static final String SEQUENCE_PRESET_ORDER = "gui.director.properties.clip_ui.sequence.preset_order";
            public static final String SEQUENCE_PRESET_RANDOM = "gui.director.properties.clip_ui.sequence.preset_random";
            public static final String SEQUENCE_PRESET_RADIAL = "gui.director.properties.clip_ui.sequence.preset_radial";
            public static final String SEQUENCE_PRESET_SPIRAL = "gui.director.properties.clip_ui.sequence.preset_spiral";
            public static final String SEQUENCE_PRESET_SLICE = "gui.director.properties.clip_ui.sequence.preset_slice";
            public static final String SEQUENCE_PRESET_WAVE = "gui.director.properties.clip_ui.sequence.preset_wave";
            public static final String SEQUENCE_PRESET_TOOLTIP = "gui.director.properties.clip_ui.sequence.preset_tooltip";
            public static final String SEQUENCE_TIMING_HEADER = "gui.director.properties.clip_ui.sequence.timing_header";
            public static final String SEQUENCE_CONCURRENCY_MODE = "gui.director.properties.clip_ui.sequence.concurrency_mode";
            public static final String SEQUENCE_TIME_MAPPING = "gui.director.properties.clip_ui.sequence.time_mapping";
            public static final String SEQUENCE_GLOBAL_DELAY = "gui.director.properties.clip_ui.sequence.global_delay";
            public static final String SEQUENCE_GLOBAL_DELAY_TOOLTIP = "gui.director.properties.clip_ui.sequence.global_delay_tooltip";
            public static final String SEQUENCE_BATCH_SIZE = "gui.director.properties.clip_ui.sequence.batch_size";
            public static final String SEQUENCE_BATCH_INTERVAL = "gui.director.properties.clip_ui.sequence.batch_interval";
            public static final String SEQUENCE_BATCH_INTERVAL_TOOLTIP = "gui.director.properties.clip_ui.sequence.batch_interval_tooltip";
            public static final String SEQUENCE_GROUP_INTERVAL = "gui.director.properties.clip_ui.sequence.group_interval";
            public static final String SEQUENCE_GROUP_INTERVAL_TOOLTIP = "gui.director.properties.clip_ui.sequence.group_interval_tooltip";
            public static final String SEQUENCE_TIMING_SUMMARY = "gui.director.properties.clip_ui.sequence.timing_summary";
            public static final String SEQUENCE_FLOW_HEADER = "gui.director.properties.clip_ui.sequence.flow_header";
            public static final String SEQUENCE_FLOW_TOOLTIP = "gui.director.properties.clip_ui.sequence.flow_tooltip";
            public static final String SEQUENCE_PLANE_DIRECTION = "gui.director.properties.clip_ui.sequence.plane_direction";
            public static final String SEQUENCE_QUICK_DIRECTIONS = "gui.director.properties.clip_ui.sequence.quick_directions";
            public static final String SEQUENCE_AXIS_X = "gui.director.properties.clip_ui.sequence.axis_x";
            public static final String SEQUENCE_AXIS_Y = "gui.director.properties.clip_ui.sequence.axis_y";
            public static final String SEQUENCE_AXIS_Y_DOWN = "gui.director.properties.clip_ui.sequence.axis_y_down";
            public static final String SEQUENCE_AXIS_Z = "gui.director.properties.clip_ui.sequence.axis_z";
            public static final String SEQUENCE_CUSTOM_ANGLE = "gui.director.properties.clip_ui.sequence.custom_angle";
            public static final String SEQUENCE_CUSTOM_ANGLE_TOOLTIP = "gui.director.properties.clip_ui.sequence.custom_angle_tooltip";
            public static final String SEQUENCE_PLANE_ANGLES = "gui.director.properties.clip_ui.sequence.plane_angles";
            public static final String SEQUENCE_PLANE_ANGLES_TOOLTIP = "gui.director.properties.clip_ui.sequence.plane_angles_tooltip";
            public static final String SEQUENCE_LAYER_STEP = "gui.director.properties.clip_ui.sequence.layer_step";
            public static final String SEQUENCE_LAYER_STEP_TOOLTIP = "gui.director.properties.clip_ui.sequence.layer_step_tooltip";
            public static final String SEQUENCE_ALIGN_CAMERA_NEAR_FAR = "gui.director.properties.clip_ui.sequence.align_camera_near_far";
            public static final String SEQUENCE_ALIGN_CAMERA_FAR_NEAR = "gui.director.properties.clip_ui.sequence.align_camera_far_near";
            public static final String SEQUENCE_ALIGN_CAMERA_TOOLTIP_DEMOLITION = "gui.director.properties.clip_ui.sequence.align_camera_tooltip_demolition";
            public static final String SEQUENCE_ALIGN_CAMERA_TOOLTIP_BUILD = "gui.director.properties.clip_ui.sequence.align_camera_tooltip_build";
            public static final String SEQUENCE_ORIGIN_HEADER = "gui.director.properties.clip_ui.sequence.origin_header";
            public static final String SEQUENCE_ORIGIN_HEADER_DEMOLITION = "gui.director.properties.clip_ui.sequence.origin_header_demolition";
            public static final String SEQUENCE_ORIGIN_DEMOLITION_HINT = "gui.director.properties.clip_ui.sequence.origin_demolition_hint";
            public static final String SEQUENCE_SPHERICAL_ORIGIN_FIXED = "gui.director.properties.clip_ui.sequence.spherical_origin_fixed";
            public static final String SEQUENCE_SPHERICAL_ORIGIN_TOOLTIP = "gui.director.properties.clip_ui.sequence.spherical_origin_tooltip";
            public static final String SEQUENCE_ORIGIN_TOOLTIP_CYLINDRICAL_DEMOLITION = "gui.director.properties.clip_ui.sequence.origin_tooltip_cylindrical_demolition";
            public static final String SEQUENCE_ORIGIN_TOOLTIP_CYLINDRICAL_BUILD = "gui.director.properties.clip_ui.sequence.origin_tooltip_cylindrical_build";
            public static final String SEQUENCE_ORIGIN_TOOLTIP_DEMOLITION = "gui.director.properties.clip_ui.sequence.origin_tooltip_demolition";
            public static final String SEQUENCE_ORIGIN_TOOLTIP_BUILD = "gui.director.properties.clip_ui.sequence.origin_tooltip_build";
            public static final String SEQUENCE_CUSTOM_COORDS = "gui.director.properties.clip_ui.sequence.custom_coords";
            public static final String SEQUENCE_FILL_HEADER = "gui.director.properties.clip_ui.sequence.fill_header";
            public static final String SEQUENCE_FILL_TOOLTIP = "gui.director.properties.clip_ui.sequence.fill_tooltip";
            public static final String SEQUENCE_RASTER_SETTINGS = "gui.director.properties.clip_ui.sequence.raster_settings";
            public static final String SEQUENCE_SCAN_AXIS = "gui.director.properties.clip_ui.sequence.scan_axis";
            public static final String SEQUENCE_RASTER_SCAN_TOOLTIP = "gui.director.properties.clip_ui.sequence.raster_scan_tooltip";
            public static final String SEQUENCE_ROW_STEP = "gui.director.properties.clip_ui.sequence.row_step";
            public static final String SEQUENCE_ROW_STEP_TOOLTIP = "gui.director.properties.clip_ui.sequence.row_step_tooltip";
            public static final String SEQUENCE_RASTER_ZIGZAG = "gui.director.properties.clip_ui.sequence.raster_zigzag";
            public static final String SEQUENCE_RASTER_ZIGZAG_TOOLTIP = "gui.director.properties.clip_ui.sequence.raster_zigzag_tooltip";
            public static final String SEQUENCE_FLOOD_SEED_HEADER = "gui.director.properties.clip_ui.sequence.flood_seed_header";
            public static final String SEQUENCE_FLOOD_SEED_TOOLTIP = "gui.director.properties.clip_ui.sequence.flood_seed_tooltip";
            public static final String SEQUENCE_WEIGHT_HEADER = "gui.director.properties.clip_ui.sequence.weight_header";
            public static final String SEQUENCE_WEIGHT_LABEL = "gui.director.properties.clip_ui.sequence.weight_label";
            public static final String SEQUENCE_WEIGHT_TOOLTIP = "gui.director.properties.clip_ui.sequence.weight_tooltip";
            public static final String SEQUENCE_WEIGHT_BAR_HINT = "gui.director.properties.clip_ui.sequence.weight_bar_hint";
            public static final String SEQUENCE_MODIFIERS_LABEL = "gui.director.properties.clip_ui.sequence.modifiers_label";
            public static final String SEQUENCE_REVERSE = "gui.director.properties.clip_ui.sequence.reverse";
            public static final String SEQUENCE_REVERSE_TOOLTIP = "gui.director.properties.clip_ui.sequence.reverse_tooltip";
            public static final String SEQUENCE_ALTERNATING = "gui.director.properties.clip_ui.sequence.alternating";
            public static final String SEQUENCE_ALTERNATING_TOOLTIP = "gui.director.properties.clip_ui.sequence.alternating_tooltip";

            // effects
            public static final String EFFECTS_CONFIG_EMPTY = "gui.director.properties.clip_ui.effects.config_empty";
            public static final String EFFECTS_QUICK_ADD_HEADER = "gui.director.properties.clip_ui.effects.quick_add_header";
            public static final String EFFECTS_FLASH = "gui.director.properties.clip_ui.effects.flash";
            public static final String EFFECTS_DUST = "gui.director.properties.clip_ui.effects.dust";
            public static final String EFFECTS_RIPPLE = "gui.director.properties.clip_ui.effects.ripple";
            public static final String EFFECTS_TRAIL = "gui.director.properties.clip_ui.effects.trail";
            public static final String EFFECTS_LIBRARY_HEADER = "gui.director.properties.clip_ui.effects.library_header";
            public static final String EFFECTS_CLEAR_ALL = "gui.director.properties.clip_ui.effects.clear_all";
            public static final String EFFECTS_CLEAR_ALL_TOOLTIP = "gui.director.properties.clip_ui.effects.clear_all_tooltip";
            public static final String EFFECTS_PASTE = "gui.director.properties.clip_ui.effects.paste";
            public static final String EFFECTS_PRESETS_LABEL = "gui.director.properties.clip_ui.effects.presets_label";
            public static final String EFFECTS_INDIVIDUAL_LABEL = "gui.director.properties.clip_ui.effects.individual_label";
            public static final String EFFECTS_ADD = "gui.director.properties.clip_ui.effects.add";
            public static final String EFFECTS_NONE = "gui.director.properties.clip_ui.effects.none";
            public static final String EFFECTS_CURRENT = "gui.director.properties.clip_ui.effects.current";
            public static final String EFFECTS_ENABLED = "gui.director.properties.clip_ui.effects.enabled";
            public static final String EFFECTS_COPY_TOOLTIP = "gui.director.properties.clip_ui.effects.copy_tooltip";
            public static final String EFFECTS_DELETE_TOOLTIP = "gui.director.properties.clip_ui.effects.delete_tooltip";
            public static final String EFFECTS_PARAMS_HEADER = "gui.director.properties.clip_ui.effects.params_header";
            public static final String EFFECTS_TIMELINE_HEADER = "gui.director.properties.clip_ui.effects.timeline_header";
            public static final String EFFECTS_PHASE_LABEL = "gui.director.properties.clip_ui.effects.phase_label";
            public static final String EFFECTS_PHASE_ENTER = "gui.director.properties.clip_ui.effects.phase_enter";
            public static final String EFFECTS_PHASE_EXIT = "gui.director.properties.clip_ui.effects.phase_exit";
            public static final String EFFECTS_PHASE_BOTH = "gui.director.properties.clip_ui.effects.phase_both";
            public static final String EFFECTS_PHASE_TOOLTIP = "gui.director.properties.clip_ui.effects.phase_tooltip";
            public static final String EFFECTS_PHASE_MISMATCH_WARNING = "gui.director.properties.clip_ui.effects.phase_mismatch_warning";
            public static final String EFFECTS_PHASE_MISMATCH_TOOLTIP = "gui.director.properties.clip_ui.effects.phase_mismatch_tooltip";
            public static final String EFFECTS_TRIGGER_LABEL = "gui.director.properties.clip_ui.effects.trigger_label";
            public static final String EFFECTS_TRIGGER_CONTINUOUS = "gui.director.properties.clip_ui.effects.trigger_continuous";
            public static final String EFFECTS_TRIGGER_ONE_SHOT = "gui.director.properties.clip_ui.effects.trigger_one_shot";
            public static final String EFFECTS_TRIGGER_TOOLTIP = "gui.director.properties.clip_ui.effects.trigger_tooltip";
            public static final String EFFECTS_START_SECONDS = "gui.director.properties.clip_ui.effects.start_seconds";
            public static final String EFFECTS_START_TOOLTIP = "gui.director.properties.clip_ui.effects.start_tooltip";
            public static final String EFFECTS_DURATION_SECONDS = "gui.director.properties.clip_ui.effects.duration_seconds";
            public static final String EFFECTS_DURATION_TOOLTIP = "gui.director.properties.clip_ui.effects.duration_tooltip";
            public static final String EFFECTS_WINDOW_HINT = "gui.director.properties.clip_ui.effects.window_hint";
            public static final String EFFECTS_WINDOW_SUMMARY = "gui.director.properties.clip_ui.effects.window_summary";
            public static final String EFFECTS_TIMELINE_PREVIEW = "gui.director.properties.clip_ui.effects.timeline_preview";
            public static final String EFFECTS_IMPL_NOT_FOUND = "gui.director.properties.clip_ui.effects.impl_not_found";

            // transformer
            public static final String TRANSFORMER_FLY_DIRECTION = "gui.director.properties.clip_ui.transformer.fly_direction";
            public static final String TRANSFORMER_FLY_DISTANCE = "gui.director.properties.clip_ui.transformer.fly_distance";
            public static final String TRANSFORMER_FALL_HEIGHT = "gui.director.properties.clip_ui.transformer.fall_height";
            public static final String TRANSFORMER_BOUNCINESS = "gui.director.properties.clip_ui.transformer.bounciness";
            public static final String TRANSFORMER_FALL_SPEED = "gui.director.properties.clip_ui.transformer.fall_speed";
            public static final String TRANSFORMER_TUMBLE = "gui.director.properties.clip_ui.transformer.tumble";
            public static final String TRANSFORMER_TUMBLE_INTENSITY = "gui.director.properties.clip_ui.transformer.tumble_intensity";
            public static final String TRANSFORMER_GROW_AXIS = "gui.director.properties.clip_ui.transformer.grow_axis";
            public static final String TRANSFORMER_SCALE_PIVOT = "gui.director.properties.clip_ui.transformer.scale_pivot";
            public static final String TRANSFORMER_RISE_DISTANCE = "gui.director.properties.clip_ui.transformer.rise_distance";
            public static final String TRANSFORMER_SHAKE_INTENSITY = "gui.director.properties.clip_ui.transformer.shake_intensity";
            public static final String TRANSFORMER_DECAY_SPEED = "gui.director.properties.clip_ui.transformer.decay_speed";
            public static final String TRANSFORMER_TARGET_SCALE = "gui.director.properties.clip_ui.transformer.target_scale";
            public static final String TRANSFORMER_EXPAND_SPEED = "gui.director.properties.clip_ui.transformer.expand_speed";
            public static final String TRANSFORMER_EXPAND_SPEED_TOOLTIP = "gui.director.properties.clip_ui.transformer.expand_speed_tooltip";
            public static final String TRANSFORMER_USE_DISTANCE_SCALE = "gui.director.properties.clip_ui.transformer.use_distance_scale";
            public static final String TRANSFORMER_USE_DISTANCE_SCALE_TOOLTIP = "gui.director.properties.clip_ui.transformer.use_distance_scale_tooltip";
            public static final String TRANSFORMER_SPREAD_RADIUS = "gui.director.properties.clip_ui.transformer.spread_radius";
            public static final String TRANSFORMER_SCATTER_RADIUS = "gui.director.properties.clip_ui.transformer.scatter_radius";
            public static final String TRANSFORMER_UP_BIAS = "gui.director.properties.clip_ui.transformer.up_bias";
            public static final String TRANSFORMER_SCALE_MIN = "gui.director.properties.clip_ui.transformer.scale_min";
            public static final String TRANSFORMER_START_OPACITY = "gui.director.properties.clip_ui.transformer.start_opacity";
            public static final String TRANSFORMER_END_OPACITY = "gui.director.properties.clip_ui.transformer.end_opacity";
            public static final String TRANSFORMER_INITIAL_SCALE = "gui.director.properties.clip_ui.transformer.initial_scale";
            public static final String TRANSFORMER_ELASTICITY = "gui.director.properties.clip_ui.transformer.elasticity";
            public static final String TRANSFORMER_BLOCKS = "gui.director.properties.clip_ui.transformer.blocks";
            public static final String TRANSFORMER_BLOCKS_PER_SECOND = "gui.director.properties.clip_ui.transformer.blocks_per_second";

            // error
            public static final String ERROR_RENDER_ANIMATION = "gui.director.properties.clip_ui.error.render_animation";
            public static final String ERROR_RENDER_PARAMS = "gui.director.properties.clip_ui.error.render_params";
            public static final String ERROR_GENERIC = "gui.director.properties.clip_ui.error.generic";
            public static final String ERROR_RESTORE = "gui.director.properties.clip_ui.error.restore";
            public static final String ERROR_EFFECT_PARAMS = "gui.director.properties.clip_ui.error.effect_params";
            public static final String ERROR_EFFECT_IMPL = "gui.director.properties.clip_ui.error.effect_impl";

            private AdapterTimelineClipUi() {
            }
        }

        /** Transformer renderUI 区块标题与 clip_ui 未覆盖的字段 */
        public static final class TransformerUi {
            public static final String SECTION_GENERIC = "gui.director.animation.transformer.section.generic";
            public static final String SECTION_FADE_IN = "gui.director.animation.transformer.section.fade_in";
            public static final String SECTION_FADE_OUT = "gui.director.animation.transformer.section.fade_out";
            public static final String SECTION_FLY_IN = "gui.director.animation.transformer.section.fly_in";
            public static final String SECTION_FLY_OUT = "gui.director.animation.transformer.section.fly_out";
            public static final String SECTION_FALL_DOWN = "gui.director.animation.transformer.section.fall_down";
            public static final String SECTION_GRAVITY_FALL = "gui.director.animation.transformer.section.gravity_fall";
            public static final String SECTION_GROW_UP = "gui.director.animation.transformer.section.grow_up";
            public static final String SECTION_RISE_UP = "gui.director.animation.transformer.section.rise_up";
            public static final String SECTION_SHAKER = "gui.director.animation.transformer.section.shaker";
            public static final String SECTION_POP_IN = "gui.director.animation.transformer.section.pop_in";
            public static final String SECTION_SHRINK_OUT = "gui.director.animation.transformer.section.shrink_out";
            public static final String SECTION_IMPLODE = "gui.director.animation.transformer.section.implode";
            public static final String SECTION_EXPLODE = "gui.director.animation.transformer.section.explode";
            public static final String SECTION_EXPAND = "gui.director.animation.transformer.section.expand";
            public static final String SECTION_ASSEMBLE = "gui.director.animation.transformer.section.assemble";

            public static final String FLY_OUT_DIRECTION = "gui.director.animation.transformer.fly_out_direction";
            public static final String START_DEPTH = "gui.director.animation.transformer.start_depth";
            public static final String SINK_DISTANCE = "gui.director.animation.transformer.sink_distance";
            public static final String SHAKE_DURATION = "gui.director.animation.transformer.shake_duration";
            public static final String VERTICAL_WEIGHT = "gui.director.animation.transformer.vertical_weight";
            public static final String ENABLE_TUMBLE = "gui.director.animation.transformer.enable_tumble";
            public static final String USE_DISTANCE = "gui.director.animation.transformer.use_distance";
            public static final String USE_DISTANCE_TOOLTIP = "gui.director.animation.transformer.use_distance_tooltip";
            public static final String PIVOT_BOTTOM = "gui.director.animation.transformer.pivot.bottom";
            public static final String PIVOT_CENTER = "gui.director.animation.transformer.pivot.center";

            private TransformerUi() {
            }
        }

        /** 特效 renderUI 区块标题与参数字段 */
        public static final class EffectUi {
            public static final String SECTION_FLASH_WHITE = "gui.director.animation.effect.ui.section.flash_white";
            public static final String SECTION_DUST_BURST = "gui.director.animation.effect.ui.section.dust_burst";
            public static final String SECTION_MAGIC_FLARE = "gui.director.animation.effect.ui.section.magic_flare";
            public static final String SECTION_GLOW_PULSE = "gui.director.animation.effect.ui.section.glow_pulse";
            public static final String SECTION_IMPACT_RIPPLE = "gui.director.animation.effect.ui.section.impact_ripple";
            public static final String SECTION_MOTION_TRAIL = "gui.director.animation.effect.ui.section.motion_trail";

            public static final String INTENSITY = "gui.director.animation.effect.ui.param.intensity";
            public static final String RADIUS = "gui.director.animation.effect.ui.param.radius";
            public static final String SCATTER_RADIUS = "gui.director.animation.effect.ui.param.scatter_radius";
            public static final String SPREAD_RADIUS = "gui.director.animation.effect.ui.param.spread_radius";
            public static final String DENSITY = "gui.director.animation.effect.ui.param.density";
            public static final String HEIGHT_JITTER = "gui.director.animation.effect.ui.param.height_jitter";
            public static final String SPIN_SPEED = "gui.director.animation.effect.ui.param.spin_speed";
            public static final String PULSE_COUNT = "gui.director.animation.effect.ui.param.pulse_count";
            public static final String CLOUD = "gui.director.animation.effect.ui.param.cloud";
            public static final String Y_OFFSET = "gui.director.animation.effect.ui.param.y_offset";

            public static final String FLASH_INTENSITY_TOOLTIP = "gui.director.animation.effect.ui.tooltip.flash_intensity";
            public static final String DUST_INTENSITY_TOOLTIP = "gui.director.animation.effect.ui.tooltip.dust_intensity";
            public static final String DUST_RADIUS_TOOLTIP = "gui.director.animation.effect.ui.tooltip.dust_radius";

            private EffectUi() {
            }
        }

        public static final class AdapterTimelineMarker {
            public static final String ADAPTER_NAME = "gui.director.properties.adapter.timeline.marker.name";
            public static final String TITLE = "gui.director.properties.adapter.timeline.marker.title";
            public static final String NO_SELECTION = "gui.director.properties.adapter.timeline.marker.no_selection";
            public static final String SECTION_BASIC = "gui.director.properties.adapter.timeline.marker.section_basic";
            public static final String TIME_POSITION = "gui.director.properties.adapter.timeline.marker.time_position";
            public static final String TIME_POSITION_TOOLTIP = "gui.director.properties.adapter.timeline.marker.time_position_tooltip";
            public static final String MARKER_NAME = "gui.director.properties.adapter.timeline.marker.marker_name";
            public static final String MARKER_NAME_TOOLTIP = "gui.director.properties.adapter.timeline.marker.marker_name_tooltip";
            public static final String SECTION_APPEARANCE = "gui.director.properties.adapter.timeline.marker.section_appearance";
            public static final String PRESET_COLORS = "gui.director.properties.adapter.timeline.marker.preset_colors";
            public static final String CUSTOM_COLOR = "gui.director.properties.adapter.timeline.marker.custom_color";
            public static final String MARKER_COLOR = "gui.director.properties.adapter.timeline.marker.marker_color";
            public static final String MARKER_COLOR_TOOLTIP = "gui.director.properties.adapter.timeline.marker.marker_color_tooltip";
            public static final String SECTION_DESCRIPTION = "gui.director.properties.adapter.timeline.marker.section_description";
            public static final String DESCRIPTION = "gui.director.properties.adapter.timeline.marker.description";
            public static final String DESCRIPTION_TOOLTIP = "gui.director.properties.adapter.timeline.marker.description_tooltip";
            public static final String COLOR_RED = "gui.director.properties.adapter.timeline.marker.color_red";
            public static final String COLOR_ORANGE = "gui.director.properties.adapter.timeline.marker.color_orange";
            public static final String COLOR_YELLOW = "gui.director.properties.adapter.timeline.marker.color_yellow";
            public static final String COLOR_GREEN = "gui.director.properties.adapter.timeline.marker.color_green";
            public static final String COLOR_CYAN = "gui.director.properties.adapter.timeline.marker.color_cyan";
            public static final String COLOR_BLUE = "gui.director.properties.adapter.timeline.marker.color_blue";
            public static final String COLOR_MAGENTA = "gui.director.properties.adapter.timeline.marker.color_magenta";
            public static final String COLOR_WHITE = "gui.director.properties.adapter.timeline.marker.color_white";
            public static final String COLOR_GRAY = "gui.director.properties.adapter.timeline.marker.color_gray";
            public static final String COLOR_DARK_GRAY = "gui.director.properties.adapter.timeline.marker.color_dark_gray";
            public static final String COLOR_LIGHT_RED = "gui.director.properties.adapter.timeline.marker.color_light_red";
            public static final String COLOR_TEAL = "gui.director.properties.adapter.timeline.marker.color_teal";

            private AdapterTimelineMarker() {
            }
        }

        public static final class AdapterTimelineKeyframeMulti {
            public static final String ADAPTER_NAME = "gui.director.properties.adapter.timeline.keyframe_multi.name";
            public static final String TITLE = "gui.director.properties.adapter.timeline.keyframe_multi.title";
            public static final String SELECTED_COUNT = "gui.director.properties.adapter.timeline.keyframe_multi.selected_count";
            public static final String CLIP_KEYFRAMES_COUNT = "gui.director.properties.adapter.timeline.keyframe_multi.clip_keyframes_count";
            public static final String TRACK_KEYFRAMES_COUNT = "gui.director.properties.adapter.timeline.keyframe_multi.track_keyframes_count";
            public static final String HINT_MULTI_MODE = "gui.director.properties.adapter.timeline.keyframe_multi.hint_multi_mode";
            public static final String HINT_DELETE = "gui.director.properties.adapter.timeline.keyframe_multi.hint_delete";

            private AdapterTimelineKeyframeMulti() {
            }
        }

        public static final class AdapterCamera {

            public static final class Common {
                public static final String PATH_HEADER = "gui.director.properties.adapter.camera.common.path_header";
                public static final String TIMING_SECTION = "gui.director.properties.adapter.camera.common.timing_section";
                public static final String TIMING_START = "gui.director.properties.adapter.camera.common.timing_start";
                public static final String TIMING_END = "gui.director.properties.adapter.camera.common.timing_end";
                public static final String TIMING_DURATION = "gui.director.properties.adapter.camera.common.timing_duration";
                public static final String TIMING_HINT = "gui.director.properties.adapter.camera.common.timing_hint";
                public static final String MOTION_SECTION = "gui.director.properties.adapter.camera.common.motion_section";
                public static final String ADVANCED_SECTION = "gui.director.properties.adapter.camera.common.advanced_section";
                public static final String DEBUG_INFO_SECTION = "gui.director.properties.adapter.camera.common.debug_info_section";
                public static final String ALIGN_START = "gui.director.properties.adapter.camera.common.align_start";
                public static final String ALIGN_START_TOOLTIP = "gui.director.properties.adapter.camera.common.align_start_tooltip";
                public static final String ALIGN_FROM_CAMERA = "gui.director.properties.adapter.camera.common.align_from_camera";
                public static final String DEBUG_NOT_INITIALIZED = "gui.director.properties.adapter.camera.common.debug_not_initialized";
                public static final String START_POSITION_FMT = "gui.director.properties.adapter.camera.common.start_position_fmt";
                public static final String DOLLY_DIRECTION_FMT = "gui.director.properties.adapter.camera.common.dolly_direction_fmt";
                public static final String TIP_CTRL_FINE = "gui.director.properties.adapter.camera.common.tip_ctrl_fine";
                public static final String CAPTURE_FROM_VIEW = "gui.director.properties.adapter.camera.common.capture_from_view";

                private Common() {
                }
            }

            public static final class Keyframe {
                public static final String TITLE = "gui.director.properties.adapter.camera.keyframe.title";
                public static final String NO_SELECTION = "gui.director.properties.adapter.camera.keyframe.no_selection";
                public static final String SECTION_BASIC = "gui.director.properties.adapter.camera.keyframe.section_basic";
                public static final String SECTION_INTERPOLATION = "gui.director.properties.adapter.camera.keyframe.section_interpolation";
                public static final String SECTION_TRANSFORM = "gui.director.properties.adapter.camera.keyframe.section_transform";
                public static final String SECTION_BEZIER = "gui.director.properties.adapter.camera.keyframe.section_bezier";
                public static final String SECTION_PROJECTION = "gui.director.properties.adapter.camera.keyframe.section_projection";
                public static final String SECTION_DOF = "gui.director.properties.adapter.camera.keyframe.section_dof";
                public static final String TIME = "gui.director.properties.adapter.camera.keyframe.time";
                public static final String TIME_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.time_tooltip";
                public static final String KEY_TYPE = "gui.director.properties.adapter.camera.keyframe.key_type";
                public static final String KEY_TYPE_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.key_type_tooltip";
                public static final String POSITION_CURVE = "gui.director.properties.adapter.camera.keyframe.position_curve";
                public static final String POSITION_CURVE_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.position_curve_tooltip";
                public static final String ROTATION_CURVE = "gui.director.properties.adapter.camera.keyframe.rotation_curve";
                public static final String ROTATION_CURVE_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.rotation_curve_tooltip";
                public static final String ROTATION_DEFAULT = "gui.director.properties.adapter.camera.keyframe.rotation_default";
                public static final String ROTATION_LINEAR = "gui.director.properties.adapter.camera.keyframe.rotation_linear";
                public static final String ROTATION_BEZIER = "gui.director.properties.adapter.camera.keyframe.rotation_bezier";
                public static final String ROTATION_CATMULL_ROM = "gui.director.properties.adapter.camera.keyframe.rotation_catmull_rom";
                public static final String ROTATION_STEP = "gui.director.properties.adapter.camera.keyframe.rotation_step";
                public static final String CAPTURE_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.capture_tooltip";
                public static final String POSITION = "gui.director.properties.adapter.camera.keyframe.position";
                public static final String POSITION_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.position_tooltip";
                public static final String ROTATION = "gui.director.properties.adapter.camera.keyframe.rotation";
                public static final String ROTATION_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.rotation_tooltip";
                public static final String HANDLE_MODE = "gui.director.properties.adapter.camera.keyframe.handle_mode";
                public static final String HANDLE_MODE_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.handle_mode_tooltip";
                public static final String HANDLE_AUTO_HINT = "gui.director.properties.adapter.camera.keyframe.handle_auto_hint";
                public static final String HANDLE_IN = "gui.director.properties.adapter.camera.keyframe.handle_in";
                public static final String HANDLE_OUT = "gui.director.properties.adapter.camera.keyframe.handle_out";
                public static final String HANDLE_RELATIVE_HINT = "gui.director.properties.adapter.camera.keyframe.handle_relative_hint";
                public static final String HANDLE_AUTO_GENERATED_HINT = "gui.director.properties.adapter.camera.keyframe.handle_auto_generated_hint";
                public static final String FOV = "gui.director.properties.adapter.camera.keyframe.fov";
                public static final String FOV_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.fov_tooltip";
                public static final String NEAR = "gui.director.properties.adapter.camera.keyframe.near";
                public static final String NEAR_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.near_tooltip";
                public static final String FAR = "gui.director.properties.adapter.camera.keyframe.far";
                public static final String FAR_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.far_tooltip";
                public static final String NEAR_FAR_WARNING = "gui.director.properties.adapter.camera.keyframe.near_far_warning";
                public static final String DOF_ENABLED = "gui.director.properties.adapter.camera.keyframe.dof_enabled";
                public static final String DOF_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.dof_tooltip";
                public static final String FOCUS = "gui.director.properties.adapter.camera.keyframe.focus";
                public static final String FOCUS_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.focus_tooltip";
                public static final String APERTURE = "gui.director.properties.adapter.camera.keyframe.aperture";
                public static final String APERTURE_TOOLTIP = "gui.director.properties.adapter.camera.keyframe.aperture_tooltip";

                private Keyframe() {
                }
            }

            public static final class Orbit {
                public static final String TITLE = "gui.director.properties.adapter.camera.orbit.title";
                public static final String NO_CLIP = "gui.director.properties.adapter.camera.orbit.no_clip";
                public static final String DURATION_FMT = "gui.director.properties.adapter.camera.orbit.duration_fmt";
                public static final String START_FMT = "gui.director.properties.adapter.camera.orbit.start_fmt";
                public static final String END_FMT = "gui.director.properties.adapter.camera.orbit.end_fmt";
                public static final String TIMING_HINT = "gui.director.properties.adapter.camera.orbit.timing_hint";
                public static final String CAPTURE_TOOLTIP = "gui.director.properties.adapter.camera.orbit.capture_tooltip";
                public static final String SECTION_CENTER = "gui.director.properties.adapter.camera.orbit.section_center";
                public static final String CENTER_LABEL = "gui.director.properties.adapter.camera.orbit.center_label";
                public static final String PICK_FROM_WORLD = "gui.director.properties.adapter.camera.orbit.pick_from_world";
                public static final String CANCEL_PICK = "gui.director.properties.adapter.camera.orbit.cancel_pick";
                public static final String PICK_HINT = "gui.director.properties.adapter.camera.orbit.pick_hint";
                public static final String LOOK_AT_LABEL = "gui.director.properties.adapter.camera.orbit.look_at_label";
                public static final String SAME_AS_CENTER = "gui.director.properties.adapter.camera.orbit.same_as_center";
                public static final String CUSTOM_POINT = "gui.director.properties.adapter.camera.orbit.custom_point";
                public static final String SECTION_MOTION = "gui.director.properties.adapter.camera.orbit.section_motion";
                public static final String RADIUS_LABEL = "gui.director.properties.adapter.camera.orbit.radius_label";
                public static final String FROM_CURRENT_RADIUS = "gui.director.properties.adapter.camera.orbit.from_current_radius";
                public static final String CUSTOM_RADIUS = "gui.director.properties.adapter.camera.orbit.custom_radius";
                public static final String RADIUS_FROM_CURRENT_FMT = "gui.director.properties.adapter.camera.orbit.radius_from_current_fmt";
                public static final String RADIUS_FROM_CURRENT_TOOLTIP = "gui.director.properties.adapter.camera.orbit.radius_from_current_tooltip";
                public static final String HEIGHT_LABEL = "gui.director.properties.adapter.camera.orbit.height_label";
                public static final String KEEP_CURRENT_HEIGHT = "gui.director.properties.adapter.camera.orbit.keep_current_height";
                public static final String CUSTOM_HEIGHT = "gui.director.properties.adapter.camera.orbit.custom_height";
                public static final String HEIGHT_CACHED_FMT = "gui.director.properties.adapter.camera.orbit.height_cached_fmt";
                public static final String HEIGHT_AUTO_HINT = "gui.director.properties.adapter.camera.orbit.height_auto_hint";
                public static final String DIRECTION_LABEL = "gui.director.properties.adapter.camera.orbit.direction_label";
                public static final String CLOCKWISE = "gui.director.properties.adapter.camera.orbit.clockwise";
                public static final String COUNTER_CLOCKWISE = "gui.director.properties.adapter.camera.orbit.counter_clockwise";
                public static final String SECTION_ANGLE = "gui.director.properties.adapter.camera.orbit.section_angle";
                public static final String FULL_ORBIT_BTN = "gui.director.properties.adapter.camera.orbit.full_orbit_btn";
                public static final String SWEEP_90 = "gui.director.properties.adapter.camera.orbit.sweep_90";
                public static final String SWEEP_180 = "gui.director.properties.adapter.camera.orbit.sweep_180";
                public static final String START_ANGLE_LABEL = "gui.director.properties.adapter.camera.orbit.start_angle_label";
                public static final String FROM_CURRENT_ANGLE = "gui.director.properties.adapter.camera.orbit.from_current_angle";
                public static final String CUSTOM_ANGLE = "gui.director.properties.adapter.camera.orbit.custom_angle";
                public static final String CURRENT_START_ANGLE_FMT = "gui.director.properties.adapter.camera.orbit.current_start_angle_fmt";
                public static final String START_ANGLE_TOOLTIP = "gui.director.properties.adapter.camera.orbit.start_angle_tooltip";
                public static final String START_ANGLE_SLIDER = "gui.director.properties.adapter.camera.orbit.start_angle_slider";
                public static final String END_ANGLE_SLIDER = "gui.director.properties.adapter.camera.orbit.end_angle_slider";
                public static final String ANGLE_RANGE_FMT = "gui.director.properties.adapter.camera.orbit.angle_range_fmt";

                private Orbit() {
                }
            }

            public static final class Dolly {
                public static final String TYPE_LABEL = "gui.director.properties.adapter.camera.dolly.type_label";
                public static final String SUBTITLE = "gui.director.properties.adapter.camera.dolly.subtitle";
                public static final String NO_CLIP = "gui.director.properties.adapter.camera.dolly.no_clip";
                public static final String DISTANCE_LABEL = "gui.director.properties.adapter.camera.dolly.distance_label";
                public static final String PULL_BACK = "gui.director.properties.adapter.camera.dolly.pull_back";
                public static final String PUSH_IN = "gui.director.properties.adapter.camera.dolly.push_in";
                public static final String DIRECTION_SECTION = "gui.director.properties.adapter.camera.dolly.direction_section";
                public static final String DIRECTION_ALONG_VIEW = "gui.director.properties.adapter.camera.dolly.direction_along_view";
                public static final String DIRECTION_ALONG_VIEW_TOOLTIP = "gui.director.properties.adapter.camera.dolly.direction_along_view_tooltip";
                public static final String DIRECTION_TOWARDS_FOCUS = "gui.director.properties.adapter.camera.dolly.direction_towards_focus";
                public static final String DIRECTION_TOWARDS_FOCUS_TOOLTIP = "gui.director.properties.adapter.camera.dolly.direction_towards_focus_tooltip";
                public static final String DIRECTION_CUSTOM = "gui.director.properties.adapter.camera.dolly.direction_custom";
                public static final String DIRECTION_CUSTOM_TOOLTIP = "gui.director.properties.adapter.camera.dolly.direction_custom_tooltip";
                public static final String CUSTOM_DIRECTION_VECTOR = "gui.director.properties.adapter.camera.dolly.custom_direction_vector";
                public static final String NORMALIZE_HINT = "gui.director.properties.adapter.camera.dolly.normalize_hint";

                private Dolly() {
                }
            }

            public static final class Crane {
                public static final String TYPE_LABEL = "gui.director.properties.adapter.camera.crane.type_label";
                public static final String SUBTITLE = "gui.director.properties.adapter.camera.crane.subtitle";
                public static final String NO_CLIP = "gui.director.properties.adapter.camera.crane.no_clip";
                public static final String HEIGHT_LABEL = "gui.director.properties.adapter.camera.crane.height_label";
                public static final String DOWN = "gui.director.properties.adapter.camera.crane.down";
                public static final String UP = "gui.director.properties.adapter.camera.crane.up";
                public static final String FRAMING_STABLE_HINT = "gui.director.properties.adapter.camera.crane.framing_stable_hint";

                private Crane() {
                }
            }

            public static final class Custom {
                public static final String TYPE_LABEL = "gui.director.properties.adapter.camera.custom.type_label";
                public static final String SUBTITLE = "gui.director.properties.adapter.camera.custom.subtitle";
                public static final String NO_CLIP = "gui.director.properties.adapter.camera.custom.no_clip";
                public static final String BASIC_SECTION = "gui.director.properties.adapter.camera.custom.basic_section";
                public static final String NAME = "gui.director.properties.adapter.camera.custom.name";
                public static final String ENABLED = "gui.director.properties.adapter.camera.custom.enabled";
                public static final String KEYFRAMES_SECTION = "gui.director.properties.adapter.camera.custom.keyframes_section";
                public static final String KEYFRAME_COUNT = "gui.director.properties.adapter.camera.custom.keyframe_count";
                public static final String KEYFRAME_EMPTY_HINT = "gui.director.properties.adapter.camera.custom.keyframe_empty_hint";
                public static final String KEYFRAME_SELECT_HINT = "gui.director.properties.adapter.camera.custom.keyframe_select_hint";

                private Custom() {
                }
            }

            public static final class Object {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.camera.object.name";
                public static final String NULL_CAMERA = "gui.director.properties.adapter.camera.object.null";
                public static final String TITLE = "gui.director.properties.adapter.camera.object.title";
                public static final String SECTION_TRANSFORM = "gui.director.properties.adapter.camera.object.section_transform";
                public static final String POSITION = "gui.director.properties.adapter.camera.object.position";
                public static final String ROTATION = "gui.director.properties.adapter.camera.object.rotation";
                public static final String SECTION_PROJECTION = "gui.director.properties.adapter.camera.object.section_projection";
                public static final String FOV = "gui.director.properties.adapter.camera.object.fov";
                public static final String FOV_TOOLTIP = "gui.director.properties.adapter.camera.object.fov_tooltip";
                public static final String FOV_PRESETS = "gui.director.properties.adapter.camera.object.fov_presets";
                public static final String FOV_PRESET_60 = "gui.director.properties.adapter.camera.object.fov_preset_60";
                public static final String FOV_PRESET_70 = "gui.director.properties.adapter.camera.object.fov_preset_70";
                public static final String FOV_PRESET_90 = "gui.director.properties.adapter.camera.object.fov_preset_90";
                public static final String FOV_PRESET_110 = "gui.director.properties.adapter.camera.object.fov_preset_110";
                public static final String NEAR = "gui.director.properties.adapter.camera.object.near";
                public static final String NEAR_TOOLTIP = "gui.director.properties.adapter.camera.object.near_tooltip";
                public static final String FAR = "gui.director.properties.adapter.camera.object.far";
                public static final String FAR_TOOLTIP = "gui.director.properties.adapter.camera.object.far_tooltip";
                public static final String SECTION_LENS = "gui.director.properties.adapter.camera.object.section_lens";
                public static final String DOF_ENABLED = "gui.director.properties.adapter.camera.object.dof_enabled";
                public static final String DOF_TOOLTIP = "gui.director.properties.adapter.camera.object.dof_tooltip";
                public static final String FOCUS = "gui.director.properties.adapter.camera.object.focus";
                public static final String FOCUS_TOOLTIP = "gui.director.properties.adapter.camera.object.focus_tooltip";
                public static final String APERTURE = "gui.director.properties.adapter.camera.object.aperture";
                public static final String APERTURE_TOOLTIP = "gui.director.properties.adapter.camera.object.aperture_tooltip";
                public static final String BOKEH = "gui.director.properties.adapter.camera.object.bokeh";
                public static final String BOKEH_TOOLTIP = "gui.director.properties.adapter.camera.object.bokeh_tooltip";
                public static final String SECTION_ANIMATION = "gui.director.properties.adapter.camera.object.section_animation";
                public static final String ANIMATION_NAME = "gui.director.properties.adapter.camera.object.animation_name";
                public static final String ANIMATION_DURATION = "gui.director.properties.adapter.camera.object.animation_duration";
                public static final String ANIMATION_DURATION_TOOLTIP = "gui.director.properties.adapter.camera.object.animation_duration_tooltip";
                public static final String LOOP = "gui.director.properties.adapter.camera.object.loop";
                public static final String SMOOTH = "gui.director.properties.adapter.camera.object.smooth";
                public static final String SMOOTH_TOOLTIP = "gui.director.properties.adapter.camera.object.smooth_tooltip";
                public static final String RESET = "gui.director.properties.adapter.camera.object.reset";
                public static final String RESET_TOOLTIP = "gui.director.properties.adapter.camera.object.reset_tooltip";
                public static final String COPY = "gui.director.properties.adapter.camera.object.copy";
                public static final String COPY_TOOLTIP = "gui.director.properties.adapter.camera.object.copy_tooltip";
                public static final String ALIGN_PLAYER = "gui.director.properties.adapter.camera.object.align_player";
                public static final String ALIGN_PLAYER_TOOLTIP = "gui.director.properties.adapter.camera.object.align_player_tooltip";

                private Object() {
                }
            }

            public static final class Track {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.camera.track.name";
                public static final String NULL_TRACK = "gui.director.properties.adapter.camera.track.null_track";
                public static final String NOT_CAMERA_TRACK = "gui.director.properties.adapter.camera.track.not_camera_track";
                public static final String TITLE = "gui.director.properties.adapter.camera.track.title";
                public static final String SECTION_ASPECT = "gui.director.properties.adapter.camera.track.section_aspect";
                public static final String SECTION_SAFE_AREA = "gui.director.properties.adapter.camera.track.section_safe_area";
                public static final String SECTION_PREVIEW_RES = "gui.director.properties.adapter.camera.track.section_preview_res";
                public static final String SECTION_PREVIEW_OPTS = "gui.director.properties.adapter.camera.track.section_preview_opts";
                public static final String SECTION_GUIDES = "gui.director.properties.adapter.camera.track.section_guides";
                public static final String ASPECT_RATIO = "gui.director.properties.adapter.camera.track.aspect_ratio";
                public static final String ASPECT_RATIO_TOOLTIP = "gui.director.properties.adapter.camera.track.aspect_ratio_tooltip";
                public static final String PRESET_16_9 = "gui.director.properties.adapter.camera.track.preset_16_9";
                public static final String PRESET_9_16 = "gui.director.properties.adapter.camera.track.preset_9_16";
                public static final String PRESET_1_1 = "gui.director.properties.adapter.camera.track.preset_1_1";
                public static final String CUSTOM_RATIO = "gui.director.properties.adapter.camera.track.custom_ratio";
                public static final String CUSTOM_RATIO_TOOLTIP = "gui.director.properties.adapter.camera.track.custom_ratio_tooltip";
                public static final String CURRENT_RATIO_FMT = "gui.director.properties.adapter.camera.track.current_ratio_fmt";
                public static final String EXPORT_NOTE = "gui.director.properties.adapter.camera.track.export_note";
                public static final String SHOW_SAFE_AREAS = "gui.director.properties.adapter.camera.track.show_safe_areas";
                public static final String SHOW_SAFE_AREAS_TOOLTIP = "gui.director.properties.adapter.camera.track.show_safe_areas_tooltip";
                public static final String ACTION_SAFE = "gui.director.properties.adapter.camera.track.action_safe";
                public static final String ACTION_SAFE_TOOLTIP = "gui.director.properties.adapter.camera.track.action_safe_tooltip";
                public static final String TITLE_SAFE = "gui.director.properties.adapter.camera.track.title_safe";
                public static final String TITLE_SAFE_TOOLTIP = "gui.director.properties.adapter.camera.track.title_safe_tooltip";
                public static final String SAFE_AREA_NOTE = "gui.director.properties.adapter.camera.track.safe_area_note";
                public static final String PREVIEW_RES = "gui.director.properties.adapter.camera.track.preview_res";
                public static final String PREVIEW_RES_TOOLTIP = "gui.director.properties.adapter.camera.track.preview_res_tooltip";
                public static final String PRESET_1080P = "gui.director.properties.adapter.camera.track.preset_1080p";
                public static final String PRESET_720P = "gui.director.properties.adapter.camera.track.preset_720p";
                public static final String PRESET_4K = "gui.director.properties.adapter.camera.track.preset_4k";
                public static final String SCALE_POLICY = "gui.director.properties.adapter.camera.track.scale_policy";
                public static final String SCALE_POLICY_TOOLTIP = "gui.director.properties.adapter.camera.track.scale_policy_tooltip";
                public static final String PREVIEW_RES_NOTE = "gui.director.properties.adapter.camera.track.preview_res_note";
                public static final String SHOW_LETTERBOX = "gui.director.properties.adapter.camera.track.show_letterbox";
                public static final String SHOW_LETTERBOX_TOOLTIP = "gui.director.properties.adapter.camera.track.show_letterbox_tooltip";
                public static final String MASK_ALPHA = "gui.director.properties.adapter.camera.track.mask_alpha";
                public static final String LETTERBOX_NOTE = "gui.director.properties.adapter.camera.track.letterbox_note";
                public static final String SHOW_CENTER_CROSS = "gui.director.properties.adapter.camera.track.show_center_cross";
                public static final String SHOW_CENTER_CROSS_TOOLTIP = "gui.director.properties.adapter.camera.track.show_center_cross_tooltip";
                public static final String SHOW_RULE_OF_THIRDS = "gui.director.properties.adapter.camera.track.show_rule_of_thirds";
                public static final String SHOW_RULE_OF_THIRDS_TOOLTIP = "gui.director.properties.adapter.camera.track.show_rule_of_thirds_tooltip";
                public static final String GUIDES_NOTE = "gui.director.properties.adapter.camera.track.guides_note";
                public static final String ASPECT_16_9 = "gui.director.properties.adapter.camera.track.aspect_16_9";
                public static final String ASPECT_9_16 = "gui.director.properties.adapter.camera.track.aspect_9_16";
                public static final String ASPECT_1_1 = "gui.director.properties.adapter.camera.track.aspect_1_1";
                public static final String ASPECT_4_3 = "gui.director.properties.adapter.camera.track.aspect_4_3";
                public static final String ASPECT_3_2 = "gui.director.properties.adapter.camera.track.aspect_3_2";
                public static final String ASPECT_21_9 = "gui.director.properties.adapter.camera.track.aspect_21_9";
                public static final String ASPECT_CUSTOM = "gui.director.properties.adapter.camera.track.aspect_custom";
                public static final String SCALE_FIT = "gui.director.properties.adapter.camera.track.scale_fit";
                public static final String SCALE_FILL = "gui.director.properties.adapter.camera.track.scale_fill";

                private Track() {
                }
            }

            private AdapterCamera() {
            }
        }

        public static final class AdapterActor {

            public static final class Common {
                public static final String NO_CLIP = "gui.director.properties.adapter.actor.common.no_clip";
                public static final String START_FMT = "gui.director.properties.adapter.actor.common.start_fmt";
                public static final String END_FMT = "gui.director.properties.adapter.actor.common.end_fmt";
                public static final String DURATION_FMT = "gui.director.properties.adapter.actor.common.duration_fmt";
                public static final String WAYPOINT_COUNT_FMT = "gui.director.properties.adapter.actor.common.waypoint_count_fmt";
                public static final String SECTION_BASIC = "gui.director.properties.adapter.actor.common.section_basic";
                public static final String TIME_RANGE = "gui.director.properties.adapter.actor.common.time_range";
                public static final String START_TIME = "gui.director.properties.adapter.actor.common.start_time";
                public static final String END_TIME = "gui.director.properties.adapter.actor.common.end_time";
                public static final String NAME = "gui.director.properties.adapter.actor.common.name";
                public static final String TYPE = "gui.director.properties.adapter.actor.common.type";
                public static final String ID_FMT = "gui.director.properties.adapter.actor.common.id_fmt";
                public static final String MEMBER_COUNT_FMT = "gui.director.properties.adapter.actor.common.member_count_fmt";
                public static final String INTENSITY = "gui.director.properties.adapter.actor.common.intensity";
                public static final String INTENSITY_STYLE_HINT = "gui.director.properties.adapter.actor.common.intensity_style_hint";
                public static final String LOOP = "gui.director.properties.adapter.actor.common.loop";
                public static final String DELETE = "gui.director.properties.adapter.actor.common.delete";
                public static final String CANCEL = "gui.director.properties.adapter.actor.common.cancel";
                public static final String CREATE = "gui.director.properties.adapter.actor.common.create";
                public static final String SAVE = "gui.director.properties.adapter.actor.common.save";
                public static final String EDIT = "gui.director.properties.adapter.actor.common.edit";
                public static final String PLACE = "gui.director.properties.adapter.actor.common.place";
                public static final String NONE = "gui.director.properties.adapter.actor.common.none";
                public static final String DURATION = "gui.director.properties.adapter.actor.common.duration";

                private Common() {
                }
            }

            public static final class ActorType {
                public static final String PLAYER = "gui.director.properties.adapter.actor.type.player";
                public static final String VILLAGER = "gui.director.properties.adapter.actor.type.villager";
                public static final String COW = "gui.director.properties.adapter.actor.type.cow";
                public static final String SHEEP = "gui.director.properties.adapter.actor.type.sheep";
                public static final String ZOMBIE = "gui.director.properties.adapter.actor.type.zombie";
                public static final String SKELETON = "gui.director.properties.adapter.actor.type.skeleton";
                public static final String CUSTOM = "gui.director.properties.adapter.actor.type.custom";

                private ActorType() {
                }
            }

            public static final class Formation {
                public static final String FREE = "gui.director.properties.adapter.actor.formation.free";
                public static final String LINE = "gui.director.properties.adapter.actor.formation.line";
                public static final String CIRCLE = "gui.director.properties.adapter.actor.formation.circle";
                public static final String GRID = "gui.director.properties.adapter.actor.formation.grid";
                public static final String FOLLOW_LEADER = "gui.director.properties.adapter.actor.formation.follow_leader";

                private Formation() {
                }
            }

            public static final class DelayPolicy {
                public static final String NONE = "gui.director.properties.adapter.actor.delay.none";
                public static final String SEQUENTIAL = "gui.director.properties.adapter.actor.delay.sequential";
                public static final String WAVE = "gui.director.properties.adapter.actor.delay.wave";
                public static final String RANDOM = "gui.director.properties.adapter.actor.delay.random";

                private DelayPolicy() {
                }
            }

            public static final class Action {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.actor.action.name";
                public static final String TITLE = "gui.director.properties.adapter.actor.action.title";
                public static final String SECTION_SEMANTICS = "gui.director.properties.adapter.actor.action.section_semantics";
                public static final String SECTION_INTENSITY = "gui.director.properties.adapter.actor.action.section_intensity";
                public static final String SECTION_LOOP = "gui.director.properties.adapter.actor.action.section_loop";
                public static final String SECTION_TIME_EDIT = "gui.director.properties.adapter.actor.action.section_time_edit";
                public static final String ACTION_TYPE = "gui.director.properties.adapter.actor.action.action_type";
                public static final String ACTION_TYPE_TOOLTIP = "gui.director.properties.adapter.actor.action.action_type_tooltip";
                public static final String NOT_SUPPORTED_SUFFIX = "gui.director.properties.adapter.actor.action.not_supported_suffix";
                public static final String NOT_SUPPORTED_TOOLTIP = "gui.director.properties.adapter.actor.action.not_supported_tooltip";
                public static final String NOT_SUPPORTED_RIG_FMT = "gui.director.properties.adapter.actor.action.not_supported_rig_fmt";
                public static final String RIG_WARNING = "gui.director.properties.adapter.actor.action.rig_warning";
                public static final String RIG_WARNING_FMT = "gui.director.properties.adapter.actor.action.rig_warning_fmt";
                public static final String RIG_WARNING_HINT = "gui.director.properties.adapter.actor.action.rig_warning_hint";
                public static final String INTENSITY_TOOLTIP = "gui.director.properties.adapter.actor.action.intensity_tooltip";
                public static final String LOOP_TOOLTIP = "gui.director.properties.adapter.actor.action.loop_tooltip";

                private Action() {
                }
            }

            public static final class Pose {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.actor.pose.name";
                public static final String SECTION_OVERRIDE = "gui.director.properties.adapter.actor.pose.section_override";
                public static final String BODY = "gui.director.properties.adapter.actor.pose.body";
                public static final String HEAD = "gui.director.properties.adapter.actor.pose.head";
                public static final String ARMS = "gui.director.properties.adapter.actor.pose.arms";
                public static final String LEGS = "gui.director.properties.adapter.actor.pose.legs";
                public static final String SECTION_HEAD = "gui.director.properties.adapter.actor.pose.section_head";
                public static final String SECTION_BODY = "gui.director.properties.adapter.actor.pose.section_body";
                public static final String SECTION_ARMS = "gui.director.properties.adapter.actor.pose.section_arms";
                public static final String SECTION_LEGS = "gui.director.properties.adapter.actor.pose.section_legs";
                public static final String HEAD_PITCH = "gui.director.properties.adapter.actor.pose.head_pitch";
                public static final String HEAD_YAW = "gui.director.properties.adapter.actor.pose.head_yaw";
                public static final String BODY_PITCH = "gui.director.properties.adapter.actor.pose.body_pitch";
                public static final String BODY_YAW = "gui.director.properties.adapter.actor.pose.body_yaw";
                public static final String BODY_ROLL = "gui.director.properties.adapter.actor.pose.body_roll";
                public static final String LEFT_ARM = "gui.director.properties.adapter.actor.pose.left_arm";
                public static final String RIGHT_ARM = "gui.director.properties.adapter.actor.pose.right_arm";
                public static final String LEFT_LEG = "gui.director.properties.adapter.actor.pose.left_leg";
                public static final String RIGHT_LEG = "gui.director.properties.adapter.actor.pose.right_leg";

                private Pose() {
                }
            }

            public static final class Route {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.actor.route.name";
                public static final String SECTION_SETTINGS = "gui.director.properties.adapter.actor.route.section_settings";
                public static final String SPEED = "gui.director.properties.adapter.actor.route.speed";
                public static final String INTERPOLATION = "gui.director.properties.adapter.actor.route.interpolation";
                public static final String LOOP_MODE = "gui.director.properties.adapter.actor.route.loop_mode";
                public static final String ALIGN_ROTATION = "gui.director.properties.adapter.actor.route.align_rotation";
                public static final String SECTION_WAYPOINTS = "gui.director.properties.adapter.actor.route.section_waypoints";
                public static final String WAYPOINT_FMT = "gui.director.properties.adapter.actor.route.waypoint_fmt";
                public static final String ADD_WAYPOINT = "gui.director.properties.adapter.actor.route.add_waypoint";
                public static final String INTERP_LINEAR = "gui.director.properties.adapter.actor.route.interp_linear";
                public static final String INTERP_SMOOTH = "gui.director.properties.adapter.actor.route.interp_smooth";
                public static final String INTERP_CATMULL_ROM = "gui.director.properties.adapter.actor.route.interp_catmull_rom";
                public static final String LOOP_ONCE = "gui.director.properties.adapter.actor.route.loop_once";
                public static final String LOOP_LOOP = "gui.director.properties.adapter.actor.route.loop_loop";
                public static final String LOOP_PING_PONG = "gui.director.properties.adapter.actor.route.loop_ping_pong";

                private Route() {
                }
            }

            public static final class PathLibrary {
                public static final String HEADER = "gui.director.properties.adapter.actor.path_library.header";
                public static final String HINT = "gui.director.properties.adapter.actor.path_library.hint";
                public static final String EMPTY = "gui.director.properties.adapter.actor.path_library.empty";
                public static final String WAYPOINT_COUNT_FMT = "gui.director.properties.adapter.actor.path_library.waypoint_count_fmt";
                public static final String LOCK = "gui.director.properties.adapter.actor.path_library.lock";
                public static final String UNLOCK = "gui.director.properties.adapter.actor.path_library.unlock";
                public static final String SHOW = "gui.director.properties.adapter.actor.path_library.show";
                public static final String HIDE = "gui.director.properties.adapter.actor.path_library.hide";
                public static final String DELETE = "gui.director.properties.adapter.actor.path_library.delete";
                public static final String RENAME = "gui.director.properties.adapter.actor.path_library.rename";
                public static final String LOCKED_HINT = "gui.director.properties.adapter.actor.path_library.locked_hint";
                public static final String POPUP_DELETE = "gui.director.properties.adapter.actor.path_library.popup_delete";
                public static final String DELETE_CONFIRM = "gui.director.properties.adapter.actor.path_library.delete_confirm";
                public static final String DELETE_NAME_FMT = "gui.director.properties.adapter.actor.path_library.delete_name_fmt";
                public static final String DELETE_WARNING = "gui.director.properties.adapter.actor.path_library.delete_warning";

                private PathLibrary() {
                }
            }

            public static final class Relation {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.actor.relation.name";
                public static final String NO_CLIP = "gui.director.properties.adapter.actor.relation.no_clip";
                public static final String SECTION_RELATION = "gui.director.properties.adapter.actor.relation.section_relation";
                public static final String SECTION_TARGET = "gui.director.properties.adapter.actor.relation.section_target";
                public static final String RELATION_TYPE = "gui.director.properties.adapter.actor.relation.relation_type";
                public static final String DISTANCE = "gui.director.properties.adapter.actor.relation.distance";
                public static final String ANGLE = "gui.director.properties.adapter.actor.relation.angle";
                public static final String HEIGHT_OFFSET = "gui.director.properties.adapter.actor.relation.height_offset";
                public static final String MAINTAIN_LOOK_AT = "gui.director.properties.adapter.actor.relation.maintain_look_at";
                public static final String TARGET_ACTOR = "gui.director.properties.adapter.actor.relation.target_actor";
                public static final String TARGET_ACTOR_NONE = "gui.director.properties.adapter.actor.relation.target_actor_none";
                public static final String TARGET_ACTOR_UUID_FMT = "gui.director.properties.adapter.actor.relation.target_actor_uuid_fmt";
                public static final String RECENT_TARGETS = "gui.director.properties.adapter.actor.relation.recent_targets";
                public static final String TARGET_RECENT_PREFIX = "gui.director.properties.adapter.actor.relation.target_recent_prefix";
                public static final String TYPE_FACE_TO_FACE = "gui.director.properties.adapter.actor.relation.type_face_to_face";
                public static final String TYPE_FOLLOW = "gui.director.properties.adapter.actor.relation.type_follow";
                public static final String TYPE_CIRCLE_AROUND = "gui.director.properties.adapter.actor.relation.type_circle_around";
                public static final String TYPE_MIRROR = "gui.director.properties.adapter.actor.relation.type_mirror";
                public static final String TYPE_PASS_OBJECT = "gui.director.properties.adapter.actor.relation.type_pass_object";
                public static final String TYPE_RELATIVE_POSITION = "gui.director.properties.adapter.actor.relation.type_relative_position";

                private Relation() {
                }
            }

            public static final class Group {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.actor.group.name";
                public static final String NO_GROUP = "gui.director.properties.adapter.actor.group.no_group";
                public static final String SECTION_FORMATION = "gui.director.properties.adapter.actor.group.section_formation";
                public static final String FORMATION_TYPE = "gui.director.properties.adapter.actor.group.formation_type";
                public static final String SPACING = "gui.director.properties.adapter.actor.group.spacing";
                public static final String SECTION_DELAY = "gui.director.properties.adapter.actor.group.section_delay";
                public static final String DELAY_POLICY = "gui.director.properties.adapter.actor.group.delay_policy";
                public static final String DELAY_STEP = "gui.director.properties.adapter.actor.group.delay_step";
                public static final String SECTION_MEMBERS = "gui.director.properties.adapter.actor.group.section_members";

                private Group() {
                }
            }

            public static final class LookAt {
                public static final String TITLE = "gui.director.properties.adapter.actor.look_at.title";
                public static final String SECTION_TARGET_MODE = "gui.director.properties.adapter.actor.look_at.section_target_mode";
                public static final String SECTION_TARGET = "gui.director.properties.adapter.actor.look_at.section_target";
                public static final String SECTION_STRENGTH = "gui.director.properties.adapter.actor.look_at.section_strength";
                public static final String TARGET_MODE = "gui.director.properties.adapter.actor.look_at.target_mode";
                public static final String WORLD_COORDS = "gui.director.properties.adapter.actor.look_at.world_coords";
                public static final String TARGET_ENTITY_UUID_FMT = "gui.director.properties.adapter.actor.look_at.target_entity_uuid_fmt";
                public static final String NO_TARGET_ENTITY = "gui.director.properties.adapter.actor.look_at.no_target_entity";
                public static final String LOOK_AT_CAMERA = "gui.director.properties.adapter.actor.look_at.look_at_camera";
                public static final String STRENGTH_TOOLTIP = "gui.director.properties.adapter.actor.look_at.strength_tooltip";
                public static final String MODE_WORLD_POINT = "gui.director.properties.adapter.actor.look_at.mode_world_point";
                public static final String MODE_ENTITY = "gui.director.properties.adapter.actor.look_at.mode_entity";
                public static final String MODE_CAMERA = "gui.director.properties.adapter.actor.look_at.mode_camera";

                private LookAt() {
                }
            }

            public static final class RigStyle {
                public static final String NO_STYLE = "gui.director.properties.adapter.actor.rig_style.no_style";
                public static final String SECTION = "gui.director.properties.adapter.actor.rig_style.section";
                public static final String MOTION_SCALE = "gui.director.properties.adapter.actor.rig_style.motion_scale";
                public static final String MOTION_SCALE_TOOLTIP = "gui.director.properties.adapter.actor.rig_style.motion_scale_tooltip";
                public static final String TEMPO = "gui.director.properties.adapter.actor.rig_style.tempo";
                public static final String TEMPO_TOOLTIP = "gui.director.properties.adapter.actor.rig_style.tempo_tooltip";
                public static final String LEAN_BIAS = "gui.director.properties.adapter.actor.rig_style.lean_bias";
                public static final String LEAN_BIAS_TOOLTIP = "gui.director.properties.adapter.actor.rig_style.lean_bias_tooltip";
                public static final String ARM_EXPRESSIVENESS = "gui.director.properties.adapter.actor.rig_style.arm_expressiveness";
                public static final String ARM_EXPRESSIVENESS_TOOLTIP = "gui.director.properties.adapter.actor.rig_style.arm_expressiveness_tooltip";
                public static final String LEG_EXPRESSIVENESS = "gui.director.properties.adapter.actor.rig_style.leg_expressiveness";
                public static final String LEG_EXPRESSIVENESS_TOOLTIP = "gui.director.properties.adapter.actor.rig_style.leg_expressiveness_tooltip";
                public static final String GLOBAL_HINT = "gui.director.properties.adapter.actor.rig_style.global_hint";
                public static final String GLOBAL_TOOLTIP = "gui.director.properties.adapter.actor.rig_style.global_tooltip";

                private RigStyle() {
                }
            }

            public static final class Selector {
                public static final String ADD_ACTOR = "gui.director.properties.adapter.actor.selector.add_actor";
                public static final String NO_ACTORS = "gui.director.properties.adapter.actor.selector.no_actors";
                public static final String EDIT_NAME = "gui.director.properties.adapter.actor.selector.edit_name";
                public static final String DELETE = "gui.director.properties.adapter.actor.selector.delete";
                public static final String POPUP_CREATE = "gui.director.properties.adapter.actor.selector.popup_create";
                public static final String POPUP_EDIT_NAME = "gui.director.properties.adapter.actor.selector.popup_edit_name";
                public static final String POPUP_DELETE = "gui.director.properties.adapter.actor.selector.popup_delete";
                public static final String NAME_LABEL = "gui.director.properties.adapter.actor.selector.name_label";
                public static final String TYPE_LABEL = "gui.director.properties.adapter.actor.selector.type_label";
                public static final String PLACE_HINT = "gui.director.properties.adapter.actor.selector.place_hint";
                public static final String ACTOR_NAME_LABEL = "gui.director.properties.adapter.actor.selector.actor_name_label";
                public static final String DELETE_CONFIRM = "gui.director.properties.adapter.actor.selector.delete_confirm";
                public static final String DELETE_NAME_FMT = "gui.director.properties.adapter.actor.selector.delete_name_fmt";
                public static final String DELETE_TYPE_FMT = "gui.director.properties.adapter.actor.selector.delete_type_fmt";
                public static final String DELETE_WARNING = "gui.director.properties.adapter.actor.selector.delete_warning";

                private Selector() {
                }
            }

            public static final class Project {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.actor.project.name";
                public static final String ACTOR_LIST = "gui.director.properties.adapter.actor.project.actor_list";
                public static final String NO_ACTORS = "gui.director.properties.adapter.actor.project.no_actors";
                public static final String ACTOR_GROUPS = "gui.director.properties.adapter.actor.project.actor_groups";
                public static final String NO_GROUPS = "gui.director.properties.adapter.actor.project.no_groups";
                public static final String GROUP_LABEL_FMT = "gui.director.properties.adapter.actor.project.group_label_fmt";
                public static final String ADD_ACTOR = "gui.director.properties.adapter.actor.project.add_actor";
                public static final String ADD_GROUP = "gui.director.properties.adapter.actor.project.add_group";
                public static final String PLACE_TOOLTIP = "gui.director.properties.adapter.actor.project.place_tooltip";
                public static final String GROUP_ACTION = "gui.director.properties.adapter.actor.project.group_action";
                public static final String GROUP_ROUTE = "gui.director.properties.adapter.actor.project.group_route";
                public static final String GROUP_PLACEMENT = "gui.director.properties.adapter.actor.project.group_placement";
                public static final String GROUP_ACTION_TOOLTIP = "gui.director.properties.adapter.actor.project.group_action_tooltip";
                public static final String GROUP_ROUTE_TOOLTIP = "gui.director.properties.adapter.actor.project.group_route_tooltip";
                public static final String GROUP_PLACEMENT_TOOLTIP = "gui.director.properties.adapter.actor.project.group_placement_tooltip";
                public static final String SOURCE_FMT = "gui.director.properties.adapter.actor.project.source_fmt";
                public static final String SOURCE_NONE = "gui.director.properties.adapter.actor.project.source_none";
                public static final String CREATE_ACTION_CLIPS = "gui.director.properties.adapter.actor.project.create_action_clips";
                public static final String COPY_SELECTED_ROUTE = "gui.director.properties.adapter.actor.project.copy_selected_route";
                public static final String PLACE_GROUP_AT_CAMERA = "gui.director.properties.adapter.actor.project.place_group_at_camera";
                public static final String DELETE_GROUP = "gui.director.properties.adapter.actor.project.delete_group";
                public static final String ACTOR_PROPERTIES = "gui.director.properties.adapter.actor.project.actor_properties";
                public static final String FOCUS_IN_WORLD = "gui.director.properties.adapter.actor.project.focus_in_world";
                public static final String PLACE_AT_CAMERA = "gui.director.properties.adapter.actor.project.place_at_camera";
                public static final String DELETE_ACTOR = "gui.director.properties.adapter.actor.project.delete_actor";
                public static final String PLACE_TIP = "gui.director.properties.adapter.actor.project.place_tip";
                public static final String GROUPS_SECTION = "gui.director.properties.adapter.actor.project.groups_section";
                public static final String NO_GROUPS_HINT = "gui.director.properties.adapter.actor.project.no_groups_hint";
                public static final String POPUP_CREATE = "gui.director.properties.adapter.actor.project.popup_create";
                public static final String CREATE_NEW_ACTOR = "gui.director.properties.adapter.actor.project.create_new_actor";
                public static final String USED_IN = "gui.director.properties.adapter.actor.project.used_in";
                public static final String NO_TIMELINE = "gui.director.properties.adapter.actor.project.no_timeline";
                public static final String NOT_USED_IN_TIMELINE = "gui.director.properties.adapter.actor.project.not_used_in_timeline";
                public static final String DELETE_UNUSED_TOOLTIP = "gui.director.properties.adapter.actor.project.delete_unused_tooltip";
                public static final String TIMELINE_FMT = "gui.director.properties.adapter.actor.project.timeline_fmt";
                public static final String TRACK_FMT = "gui.director.properties.adapter.actor.project.track_fmt";
                public static final String FOCUS_TRACK = "gui.director.properties.adapter.actor.project.focus_track";
                public static final String CURRENT_TIMELINE = "gui.director.properties.adapter.actor.project.current_timeline";
                public static final String DEFAULT_TRACK_NAME = "gui.director.properties.adapter.actor.project.default_track_name";
                public static final String POPUP_DELETE = "gui.director.properties.adapter.actor.project.popup_delete";
                public static final String DELETE_CONFIRM = "gui.director.properties.adapter.actor.project.delete_confirm";
                public static final String DELETE_ACTOR_FMT = "gui.director.properties.adapter.actor.project.delete_actor_fmt";
                public static final String DELETE_UNDO_WARNING = "gui.director.properties.adapter.actor.project.delete_undo_warning";
                public static final String ACTION_BRUSH = "gui.director.properties.adapter.actor.project.action_brush";
                public static final String CURRENT_BRUSH = "gui.director.properties.adapter.actor.project.current_brush";
                public static final String BRUSH_TOOLTIP = "gui.director.properties.adapter.actor.project.brush_tooltip";
                public static final String BRUSH_HINT_FMT = "gui.director.properties.adapter.actor.project.brush_hint_fmt";
                public static final String ACTOR_DISPLAY_FMT = "gui.director.properties.adapter.actor.project.actor_display_fmt";
                public static final String FORMATION = "gui.director.properties.adapter.actor.project.formation";
                public static final String FORMATION_SPACING = "gui.director.properties.adapter.actor.project.formation_spacing";
                public static final String DELAY = "gui.director.properties.adapter.actor.project.delay";
                public static final String DELAY_STEP = "gui.director.properties.adapter.actor.project.delay_step";
                public static final String ACTION = "gui.director.properties.adapter.actor.project.action";
                public static final String PANEL_RENDER_ERROR = "gui.director.properties.adapter.actor.project.panel_render_error";
                public static final String TIMELINE_NOT_READY = "gui.director.properties.adapter.actor.project.timeline_not_ready";

                private Project() {
                }
            }

            public static final class Semantic {
                public static final String IDLE = "gui.director.properties.adapter.actor.semantic.idle";
                public static final String WALK = "gui.director.properties.adapter.actor.semantic.walk";
                public static final String RUN = "gui.director.properties.adapter.actor.semantic.run";
                public static final String DANCE = "gui.director.properties.adapter.actor.semantic.dance";
                public static final String CELEBRATE = "gui.director.properties.adapter.actor.semantic.celebrate";
                public static final String SIT = "gui.director.properties.adapter.actor.semantic.sit";
                public static final String MEDITATE = "gui.director.properties.adapter.actor.semantic.meditate";
                public static final String CARRY = "gui.director.properties.adapter.actor.semantic.carry";
                public static final String THROW_PREPARE = "gui.director.properties.adapter.actor.semantic.throw_prepare";
                public static final String ATTACK = "gui.director.properties.adapter.actor.semantic.attack";
                public static final String GRAZE = "gui.director.properties.adapter.actor.semantic.graze";
                public static final String ROAR = "gui.director.properties.adapter.actor.semantic.roar";
                public static final String CUSTOM = "gui.director.properties.adapter.actor.semantic.custom";

                private Semantic() {
                }
            }

            public static final class LiveTransform {
                public static final String SECTION_TRANSFORM = "gui.director.properties.adapter.actor.live_transform.section_transform";
                public static final String SECTION_LOOK_AT = "gui.director.properties.adapter.actor.live_transform.section_look_at";
                public static final String TIMELINE_NOT_LOADED = "gui.director.properties.adapter.actor.live_transform.timeline_not_loaded";
                public static final String TIME_HINT_FMT = "gui.director.properties.adapter.actor.live_transform.time_hint_fmt";
                public static final String POSITION = "gui.director.properties.adapter.actor.live_transform.position";
                public static final String ROTATION = "gui.director.properties.adapter.actor.live_transform.rotation";
                public static final String SCALE = "gui.director.properties.adapter.actor.live_transform.scale";
                public static final String YAW = "gui.director.properties.adapter.actor.live_transform.yaw";
                public static final String PITCH = "gui.director.properties.adapter.actor.live_transform.pitch";
                public static final String ROLL = "gui.director.properties.adapter.actor.live_transform.roll";
                public static final String TARGET_MODE = "gui.director.properties.adapter.actor.live_transform.target_mode";
                public static final String STRENGTH = "gui.director.properties.adapter.actor.live_transform.strength";
                public static final String APPLY_LOOK_AT_CAMERA = "gui.director.properties.adapter.actor.live_transform.apply_look_at_camera";
                public static final String GIZMO_HINT = "gui.director.properties.adapter.actor.live_transform.gizmo_hint";

                private LiveTransform() {
                }
            }

            public static final class Skin {
                public static final String SECTION = "gui.director.properties.adapter.actor.skin.section";
                public static final String DEFAULT = "gui.director.properties.adapter.actor.skin.default";
                public static final String CHOOSE_PNG = "gui.director.properties.adapter.actor.skin.choose_png";
                public static final String CLEAR = "gui.director.properties.adapter.actor.skin.clear";
                public static final String DIALOG_TITLE = "gui.director.properties.adapter.actor.skin.dialog_title";
                public static final String DIALOG_FILTER = "gui.director.properties.adapter.actor.skin.dialog_filter";

                private Skin() {
                }
            }

            public static final class State {
                public static final String ADAPTER_NAME = "gui.director.properties.adapter.actor.state.name";
                public static final String SECTION_BEHAVIOR = "gui.director.properties.adapter.actor.state.section_behavior";
                public static final String SECTION_VISUAL = "gui.director.properties.adapter.actor.state.section_visual";
                public static final String SECTION_COMBAT = "gui.director.properties.adapter.actor.state.section_combat";
                public static final String SNEAKING = "gui.director.properties.adapter.actor.state.sneaking";
                public static final String SPRINTING = "gui.director.properties.adapter.actor.state.sprinting";
                public static final String GLOWING = "gui.director.properties.adapter.actor.state.glowing";
                public static final String VISIBILITY = "gui.director.properties.adapter.actor.state.visibility";
                public static final String INVULNERABLE = "gui.director.properties.adapter.actor.state.invulnerable";

                private State() {
                }
            }

            public static final class ToolEditor {
                public static final String CURRENT_ACTOR = "gui.director.properties.adapter.actor.tool_editor.current_actor";
                public static final String NO_ACTOR_SELECTED = "gui.director.properties.adapter.actor.tool_editor.no_actor_selected";
                public static final String SELECT_ACTOR_HINT = "gui.director.properties.adapter.actor.tool_editor.select_actor_hint";
                public static final String NAME_FMT = "gui.director.properties.adapter.actor.tool_editor.name_fmt";
                public static final String TYPE_FMT = "gui.director.properties.adapter.actor.tool_editor.type_fmt";
                public static final String RIG_FMT = "gui.director.properties.adapter.actor.tool_editor.rig_fmt";
                public static final String RIG_NONE = "gui.director.properties.adapter.actor.tool_editor.rig_none";
                public static final String SELECT_ACTOR_FIRST = "gui.director.properties.adapter.actor.tool_editor.select_actor_first";
                public static final String LOCAL_POSE_OVERRIDE = "gui.director.properties.adapter.actor.tool_editor.local_pose_override";
                public static final String CREATE_LOCAL_POSE_CLIP = "gui.director.properties.adapter.actor.tool_editor.create_local_pose_clip";
                public static final String CREATE_LOCAL_POSE_TOOLTIP = "gui.director.properties.adapter.actor.tool_editor.create_local_pose_tooltip";
                public static final String POSE_CLIP = "gui.director.properties.adapter.actor.tool_editor.pose_clip";
                public static final String ACTION_SEMANTIC = "gui.director.properties.adapter.actor.tool_editor.action_semantic";
                public static final String RIG_NOT_SUPPORTED_SUFFIX = "gui.director.properties.adapter.actor.tool_editor.rig_not_supported_suffix";
                public static final String CREATE_POSE_CLIP = "gui.director.properties.adapter.actor.tool_editor.create_pose_clip";
                public static final String CREATE_POSE_TOOLTIP = "gui.director.properties.adapter.actor.tool_editor.create_pose_tooltip";
                public static final String PATH_DRAWING = "gui.director.properties.adapter.actor.tool_editor.path_drawing";
                public static final String PATH_DRAW_HINT = "gui.director.properties.adapter.actor.tool_editor.path_draw_hint";
                public static final String CURVE_TYPE = "gui.director.properties.adapter.actor.tool_editor.curve_type";
                public static final String ANCHOR_COUNT_FMT = "gui.director.properties.adapter.actor.tool_editor.anchor_count_fmt";
                public static final String DRAWING_IN_PROGRESS = "gui.director.properties.adapter.actor.tool_editor.drawing_in_progress";
                public static final String APPLY_TO_ACTOR = "gui.director.properties.adapter.actor.tool_editor.apply_to_actor";
                public static final String APPLY_PATH_TOOLTIP = "gui.director.properties.adapter.actor.tool_editor.apply_path_tooltip";
                public static final String DELETE_SELECTED_ANCHOR = "gui.director.properties.adapter.actor.tool_editor.delete_selected_anchor";
                public static final String TRANSFORM_AND_LOOK_AT = "gui.director.properties.adapter.actor.tool_editor.transform_and_look_at";
                public static final String TRANSFORM_HINT_1 = "gui.director.properties.adapter.actor.tool_editor.transform_hint_1";
                public static final String TRANSFORM_HINT_2 = "gui.director.properties.adapter.actor.tool_editor.transform_hint_2";
                public static final String TRANSFORM_HINT_3 = "gui.director.properties.adapter.actor.tool_editor.transform_hint_3";
                public static final String TRANSFORM_HINT_4 = "gui.director.properties.adapter.actor.tool_editor.transform_hint_4";

                private ToolEditor() {
                }
            }

            private AdapterActor() {
            }
        }

        private Properties() {
        }
    }

    public static final class ContainerPanel {
        public static final String HEADER_TITLE = "gui.director.container.header.title";
        public static final String HEADER_COUNT = "gui.director.container.header.count";
        public static final String PROPERTIES_COLLAPSED = "gui.director.container.header.properties_collapsed";
        public static final String PROPERTIES_EXPANDED = "gui.director.container.header.properties_expanded";
        public static final String LAYOUT_TOOLTIP_TITLE = "gui.director.container.header.layout_tooltip_title";
        public static final String LAYOUT_LEFT_WIDTH = "gui.director.container.header.layout_left_width";
        public static final String LAYOUT_TOGGLE_HINT = "gui.director.container.header.layout_toggle_hint";

        public static final String DEFAULT_CONTAINER_NAME = "gui.director.container.default.container";
        public static final String DEFAULT_GROUP_NAME = "gui.director.container.default.group";

        public static final class Toolbar {
            public static final String CREATE_CONTAINER = "gui.director.container.toolbar.create_container";
            public static final String CREATE_CONTAINER_TOOLTIP = "gui.director.container.toolbar.create_container_tooltip";
            public static final String CREATE_GROUP = "gui.director.container.toolbar.create_group";
            public static final String CREATE_GROUP_TOOLTIP = "gui.director.container.toolbar.create_group_tooltip";
            public static final String GENERATE_CONTAINER = "gui.director.container.toolbar.generate_container";
            public static final String GENERATE_CONTAINER_TOOLTIP = "gui.director.container.toolbar.generate_container_tooltip";
            public static final String GENERATE_DEMOLITION = "gui.director.container.toolbar.generate_demolition";
            public static final String GENERATE_DEMOLITION_TOOLTIP = "gui.director.container.toolbar.generate_demolition_tooltip";
            public static final String INTAKE_SELECTION = "gui.director.container.toolbar.intake_selection";
            public static final String INTAKE_SELECTION_TOOLTIP = "gui.director.container.toolbar.intake_selection_tooltip";
            public static final String STOP_INTAKE = "gui.director.container.toolbar.stop_intake";
            public static final String STOP_INTAKE_TOOLTIP = "gui.director.container.toolbar.stop_intake_tooltip";
            public static final String LIVE_INTAKE = "gui.director.container.toolbar.live_intake";
            public static final String LIVE_INTAKE_TOOLTIP = "gui.director.container.toolbar.live_intake_tooltip";
            public static final String TOOLTIP_SELECT_CONTAINER_FIRST = "gui.director.container.toolbar.tooltip_select_container_first";
            public static final String TOOLTIP_GROUP_NO_INTAKE = "gui.director.container.toolbar.tooltip_group_no_intake";
            public static final String TOOLTIP_TIMELINE_NO_INTAKE = "gui.director.container.toolbar.tooltip_timeline_no_intake";
            public static final String TOOLTIP_SELECT_BLOCKS_MC = "gui.director.container.toolbar.tooltip_select_blocks_mc";
            public static final String TOOLTIP_GROUP_NO_LIVE_INTAKE = "gui.director.container.toolbar.tooltip_group_no_live_intake";
            public static final String TOOLTIP_TIMELINE_NO_LIVE_INTAKE = "gui.director.container.toolbar.tooltip_timeline_no_live_intake";
            public static final String NO_SELECTION = "gui.director.container.toolbar.no_selection";
            public static final String SELECTED_COUNT = "gui.director.container.toolbar.selected_count";
            public static final String SELECTED_SINGLE = "gui.director.container.toolbar.selected_single";

            private Toolbar() {
            }
        }

        public static final class ContextMenu {
            public static final String PANEL_TITLE = "gui.director.container.context.panel_title";
            public static final String NEW_CONTAINER = "gui.director.container.context.new_container";
            public static final String GROUP_SELECTED = "gui.director.container.context.group_selected";
            public static final String UNGROUP_SELECTED = "gui.director.container.context.ungroup_selected";
            public static final String MERGE_SELECTED = "gui.director.container.context.merge_selected";
            public static final String PASTE_AS_ROOT = "gui.director.container.context.paste_as_root";
            public static final String COLLAPSE_ALL = "gui.director.container.context.collapse_all";
            public static final String EXPAND_ALL = "gui.director.container.context.expand_all";
            public static final String CLEAR_ALL = "gui.director.container.context.clear_all";
            public static final String SELECTED_COUNT = "gui.director.container.context.selected_count";
            public static final String TIMELINE_CONTROLLED = "gui.director.container.context.timeline_controlled";
            public static final String PASTE_AS_CHILD = "gui.director.container.context.paste_as_child";
            public static final String NEW_CHILD_CONTAINER = "gui.director.container.context.new_child_container";
            public static final String STOP_LIVE_INTAKE = "gui.director.container.context.stop_live_intake";
            public static final String START_LIVE_INTAKE = "gui.director.container.context.start_live_intake";
            public static final String RELEASE_CONTAINER = "gui.director.container.context.release_container";
            public static final String HIDE = "gui.director.container.context.hide";
            public static final String HIDE_BLOCKS = "gui.director.container.context.hide_blocks";
            public static final String RENAME = "gui.director.container.context.rename";
            public static final String DELETE = "gui.director.container.context.delete";
            public static final String MOVE_TO_GROUP = "gui.director.container.context.move_to_group";
            public static final String MOVE_OUT_OF_GROUP = "gui.director.container.context.move_out_of_group";
            public static final String MOVE_UP = "gui.director.container.context.move_up";
            public static final String MOVE_DOWN = "gui.director.container.context.move_down";
            public static final String BATCH_VISIBILITY = "gui.director.container.context.batch_visibility";
            public static final String BATCH_VISIBILITY_TARGET = "gui.director.container.context.batch_visibility_target";
            public static final String SHOW_ALL = "gui.director.container.context.show_all";
            public static final String HIDE_ALL = "gui.director.container.context.hide_all";
            public static final String BATCH_LOCK = "gui.director.container.context.batch_lock";
            public static final String BATCH_LOCK_TARGET = "gui.director.container.context.batch_lock_target";
            public static final String LOCK_ALL = "gui.director.container.context.lock_all";
            public static final String UNLOCK_ALL = "gui.director.container.context.unlock_all";
            public static final String SHOW = "gui.director.container.context.show";
            public static final String LOCK = "gui.director.container.context.lock";
            public static final String UNLOCK = "gui.director.container.context.unlock";
            public static final String SET_TAG_COLOR = "gui.director.container.context.set_tag_color";
            public static final String TAG_COLOR_SELECTED = "gui.director.container.context.tag_color_selected";
            public static final String TAG_COLOR_LABEL = "gui.director.container.context.tag_color_label";
            public static final String RESET_TAG_COLOR = "gui.director.container.context.reset_tag_color";
            public static final String DRAG_MULTIPLE = "gui.director.container.context.drag_multiple";
            public static final String DRAG_SINGLE = "gui.director.container.context.drag_single";

            private ContextMenu() {
            }
        }

        public static final class Tree {
            public static final String EMPTY = "gui.director.container.tree.empty";
            public static final String LOCK_TIMELINE_TOOLTIP = "gui.director.container.tree.lock_timeline_tooltip";
            public static final String UNLOCK_CONTAINER = "gui.director.container.tree.unlock_container";
            public static final String LOCK_CONTAINER = "gui.director.container.tree.lock_container";
            public static final String VISIBILITY_TIMELINE_TOOLTIP = "gui.director.container.tree.visibility_timeline_tooltip";
            public static final String SHOW_CONTAINER_TOOLTIP = "gui.director.container.tree.show_container_tooltip";
            public static final String HIDE_CONTAINER_TOOLTIP = "gui.director.container.tree.hide_container_tooltip";
            public static final String TOOLTIP_NAME = "gui.director.container.tree.tooltip_name";
            public static final String TOOLTIP_TYPE = "gui.director.container.tree.tooltip_type";
            public static final String TOOLTIP_BLOCK_COUNT = "gui.director.container.tree.tooltip_block_count";
            public static final String TOOLTIP_CHILD_COUNT = "gui.director.container.tree.tooltip_child_count";
            public static final String TOOLTIP_TOTAL_BLOCKS = "gui.director.container.tree.tooltip_total_blocks";
            public static final String TOOLTIP_CENTER = "gui.director.container.tree.tooltip_center";
            public static final String TOOLTIP_SIZE = "gui.director.container.tree.tooltip_size";
            public static final String TOOLTIP_ON_TIMELINE = "gui.director.container.tree.tooltip_on_timeline";
            public static final String TOOLTIP_READONLY = "gui.director.container.tree.tooltip_readonly";
            public static final String TOOLTIP_RIGHT_CLICK = "gui.director.container.tree.tooltip_right_click";
            public static final String TOOLTIP_DRAG_TO_TIMELINE = "gui.director.container.tree.tooltip_drag_to_timeline";

            private Tree() {
            }
        }

        public static final class Dialog {
            public static final String CLEAR_ALL_CONFIRM = "gui.director.container.dialog.clear_all_confirm";
            public static final String DELETE_CONFIRM = "gui.director.container.dialog.delete_confirm";
            public static final String CONFIRM_DELETE = "gui.director.container.dialog.confirm_delete";
            public static final String RELEASE_TITLE = "gui.director.container.dialog.release_title";
            public static final String RELEASE_MESSAGE = "gui.director.container.dialog.release_message";
            public static final String CONFIRM_RELEASE = "gui.director.container.dialog.confirm_release";

            private Dialog() {
            }
        }

        private ContainerPanel() {
        }
    }

    public static final class SelectionFilterPanel {
        public static final String WAITING_COMMANDS = "gui.director.selection_filter.waiting_commands";
        public static final String SECTION_BASIC = "gui.director.selection_filter.section.basic";
        public static final String SECTION_MODES = "gui.director.selection_filter.section.modes";
        public static final String SECTION_ADVANCED = "gui.director.selection_filter.section.advanced";
        public static final String BASIC_EXCLUDE_AIR = "gui.director.selection_filter.basic.exclude_air";
        public static final String BASIC_EXCLUDE_WATER = "gui.director.selection_filter.basic.exclude_water";
        public static final String BASIC_EXCLUDE_LAVA = "gui.director.selection_filter.basic.exclude_lava";
        public static final String BASIC_SOLID_ONLY = "gui.director.selection_filter.basic.solid_only";
        public static final String BASIC_PLANT_ONLY = "gui.director.selection_filter.basic.plant_only";
        public static final String BASIC_REPLACEABLE_ONLY = "gui.director.selection_filter.basic.replaceable_only";
        public static final String BASIC_TRANSPARENT_ONLY = "gui.director.selection_filter.basic.transparent_only";
        public static final String CONFLICT_REPLACEABLE_SHORT = "gui.director.selection_filter.conflict.replaceable_short";
        public static final String CONFLICT_HINT = "gui.director.selection_filter.conflict.hint";
        public static final String CONFLICT_DELIMITER = "gui.director.selection_filter.conflict.delimiter";
        public static final String MODE_CUSTOM = "gui.director.selection_filter.mode.custom";
        public static final String MODE_CURRENT_LABEL = "gui.director.selection_filter.mode.current_label";
        public static final String MODE_SAVE_LABEL = "gui.director.selection_filter.mode.save_label";
        public static final String MODE_SAVE_BUTTON = "gui.director.selection_filter.mode.save_button";
        public static final String MODE_SAVE_BUILTIN_BLOCKED = "gui.director.selection_filter.mode.save_builtin_blocked";
        public static final String ADV_RULE_PLACEHOLDER = "gui.director.selection_filter.advanced.rule_placeholder";
        public static final String ADV_ADD_RULE_LABEL = "gui.director.selection_filter.advanced.add_rule_label";
        public static final String ADV_ADD_RULE_BUTTON = "gui.director.selection_filter.advanced.add_rule_button";
        public static final String ADV_EMPTY = "gui.director.selection_filter.advanced.empty";
        public static final String ADV_CLEAR_ALL = "gui.director.selection_filter.advanced.clear_all";
        public static final String ADV_UNREGISTERED_FILTER = "gui.director.selection_filter.advanced.unregistered_filter";
        public static final String PRESET_DEFAULT = "gui.director.selection_filter.preset.default";
        public static final String PRESET_TERRAIN_EDIT = "gui.director.selection_filter.preset.terrain_edit";
        public static final String PRESET_PLANT_EDIT = "gui.director.selection_filter.preset.plant_edit";
        public static final String PRESET_FLUID_EDIT = "gui.director.selection_filter.preset.fluid_edit";
        public static final String PRESET_REPLACEABLE_EDIT = "gui.director.selection_filter.preset.replaceable_edit";
        public static final String FILTER_EXCLUDE_AIR = "gui.director.selection_filter.filter.exclude_air";
        public static final String FILTER_EXCLUDE_WATER = "gui.director.selection_filter.filter.exclude_water";
        public static final String FILTER_EXCLUDE_LAVA = "gui.director.selection_filter.filter.exclude_lava";
        public static final String FILTER_SOLID_ONLY = "gui.director.selection_filter.filter.solid_only";
        public static final String FILTER_PLANT_ONLY = "gui.director.selection_filter.filter.plant_only";
        public static final String FILTER_REPLACEABLE_ONLY = "gui.director.selection_filter.filter.replaceable_only";
        public static final String FILTER_TRANSPARENT_ONLY = "gui.director.selection_filter.filter.transparent_only";
        public static final String FILTER_SAME_BLOCK_TYPE = "gui.director.selection_filter.filter.same_block_type";
        public static final String FILTER_SURFACE_ONLY = "gui.director.selection_filter.filter.surface_only";
        public static final String FILTER_VISIBLE_ONLY = "gui.director.selection_filter.filter.visible_only";
        public static final String FILTER_CONNECTED_ONLY = "gui.director.selection_filter.filter.connected_only";
        public static final String FILTER_MAX_DEPTH_LIMIT = "gui.director.selection_filter.filter.max_depth_limit";
        public static final String FILTER_WITHIN_BBOX = "gui.director.selection_filter.filter.within_bbox";
        public static final String FILTER_WITHIN_CHUNK = "gui.director.selection_filter.filter.within_chunk";
        public static final String FILTER_WITHIN_RADIUS = "gui.director.selection_filter.filter.within_radius";
        public static final String FILTER_MAX_BLOCK_COUNT = "gui.director.selection_filter.filter.max_block_count";
        public static final String FILTER_DEBUG_LOG_REJECT = "gui.director.selection_filter.filter.debug_log_reject";
        public static final String FILTER_PROFILING_ONLY = "gui.director.selection_filter.filter.profiling_only";
        public static final String FILTER_DRY_RUN = "gui.director.selection_filter.filter.dry_run";
        public static final String FILTER_ALL = "gui.director.selection_filter.filter.all";
        public static final String FILTER_NONE = "gui.director.selection_filter.filter.none";
        public static final String FILTER_LUMINOUS = "gui.director.selection_filter.filter.luminous";
        public static final String FILTER_LUMINOUS_5 = "gui.director.selection_filter.filter.luminous_5";
        public static final String FILTER_LUMINOUS_10 = "gui.director.selection_filter.filter.luminous_10";

        private SelectionFilterPanel() {
        }
    }

    public static final class ToolPropertiesPanel {
        public static final String EMBEDDED_TITLE = "gui.director.tool_properties.embedded_title";
        public static final String EMBEDDED_HINT = "gui.director.tool_properties.embedded_hint";

        private ToolPropertiesPanel() {
        }
    }

    public static final class Common {
        public static final String DELETE = "gui.director.common.delete";
        public static final String OK = "gui.director.common.ok";
        public static final String CANCEL = "gui.director.common.cancel";
        public static final String CLOSE = "gui.director.common.close";
        public static final String BIND = "gui.director.common.bind";
        public static final String UNBIND = "gui.director.common.unbind";
        public static final String SAVE = "gui.director.common.save";
        public static final String CONFIRM = "gui.director.common.confirm";

        private Common() {
        }
    }

    /**
     * 系统级对话框（{@link com.director.ui.dialogs.DialogManager}）。
     */
    public static final class SystemDialog {
        public static final String SETTINGS_TITLE = "gui.director.dialog.settings.title";
        public static final String SETTINGS_GENERAL = "gui.director.dialog.settings.general";
        public static final String SETTINGS_AUTO_SAVE = "gui.director.dialog.settings.auto_save";
        public static final String SETTINGS_DEFAULT_FPS = "gui.director.dialog.settings.default_fps";
        public static final String SETTINGS_THEME = "gui.director.dialog.settings.theme";
        public static final String SETTINGS_THEME_CURRENT = "gui.director.dialog.settings.theme_current";

        public static final String ABOUT_VERSION_INFO = "gui.director.dialog.about.version_info";
        public static final String ABOUT_DESCRIPTION = "gui.director.dialog.about.description";
        public static final String ABOUT_DESCRIPTION_DETAIL = "gui.director.dialog.about.description_detail";
        public static final String ABOUT_CREDITS = "gui.director.dialog.about.credits";
        public static final String ABOUT_COPYRIGHT = "gui.director.dialog.about.copyright";
        public static final String ABOUT_BUILT_WITH = "gui.director.dialog.about.built_with";
        public static final String ABOUT_VERSION = "gui.director.dialog.about.version";
        public static final String ABOUT_MINECRAFT = "gui.director.dialog.about.minecraft";
        public static final String ABOUT_JAVA = "gui.director.dialog.about.java";

        public static final String SHORTCUTS_TITLE = "gui.director.dialog.shortcuts.title";

        public static final String RESET_WORKSPACE_TITLE = "gui.director.dialog.reset_workspace.title";
        public static final String RESET_WORKSPACE_WARNING = "gui.director.dialog.reset_workspace.warning";
        public static final String RESET_WORKSPACE_MESSAGE = "gui.director.dialog.reset_workspace.message";
        public static final String RESET_WORKSPACE_CONFIRM = "gui.director.dialog.reset_workspace.confirm";

        public static final String SAVE_LAYOUT_TITLE = "gui.director.dialog.save_layout.title";
        public static final String SAVE_LAYOUT_PROMPT = "gui.director.dialog.save_layout.prompt";
        public static final String SAVE_LAYOUT_NAME_REQUIRED = "gui.director.dialog.save_layout.name_required";

        public static final String LOAD_LAYOUT_TITLE = "gui.director.dialog.load_layout.title";
        public static final String LOAD_LAYOUT_ACCESSOR_UNAVAILABLE = "gui.director.dialog.load_layout.accessor_unavailable";
        public static final String LOAD_LAYOUT_NONE_FOUND = "gui.director.dialog.load_layout.none_found";
        public static final String LOAD_LAYOUT_SAVE_FIRST = "gui.director.dialog.load_layout.save_first";
        public static final String LOAD_LAYOUT_SELECT_PROMPT = "gui.director.dialog.load_layout.select_prompt";

        public static final class TimelineSettings {
            public static final String ACCESS_ERROR = "gui.director.dialog.timeline_settings.access_error";
            public static final String UNAVAILABLE = "gui.director.dialog.timeline_settings.unavailable";
            public static final String PROJECT_GROUP = "gui.director.dialog.timeline_settings.project_group";
            public static final String FRAME_RATE = "gui.director.dialog.timeline_settings.frame_rate";
            public static final String FRAME_RATE_ERROR_TOOLTIP = "gui.director.dialog.timeline_settings.frame_rate_error_tooltip";
            public static final String FRAME_RATE_TOOLTIP = "gui.director.dialog.timeline_settings.frame_rate_tooltip";
            public static final String TIME_UNIT = "gui.director.dialog.timeline_settings.time_unit";
            public static final String TIME_UNIT_SECONDS = "gui.director.dialog.timeline_settings.time_unit.seconds";
            public static final String TIME_UNIT_FRAMES = "gui.director.dialog.timeline_settings.time_unit.frames";
            public static final String TIME_UNIT_TICKS = "gui.director.dialog.timeline_settings.time_unit.ticks";
            public static final String TIME_UNIT_TOOLTIP = "gui.director.dialog.timeline_settings.time_unit_tooltip";
            public static final String SNAP_GROUP = "gui.director.dialog.timeline_settings.snap_group";
            public static final String SNAP_ENABLED = "gui.director.dialog.timeline_settings.snap_enabled";
            public static final String SNAP_ENABLED_TOOLTIP = "gui.director.dialog.timeline_settings.snap_enabled_tooltip";
            public static final String GRID_GROUP = "gui.director.dialog.timeline_settings.grid_group";
            public static final String SNAP_TO_GRID = "gui.director.dialog.timeline_settings.snap_to_grid";
            public static final String GRID_INTERVAL = "gui.director.dialog.timeline_settings.grid_interval";
            public static final String GRID_INTERVAL_TOOLTIP = "gui.director.dialog.timeline_settings.grid_interval_tooltip";
            public static final String OTHER_SNAP_GROUP = "gui.director.dialog.timeline_settings.other_snap_group";
            public static final String SNAP_TO_CLIP_EDGES = "gui.director.dialog.timeline_settings.snap_to_clip_edges";
            public static final String SNAP_TO_CLIP_EDGES_TOOLTIP = "gui.director.dialog.timeline_settings.snap_to_clip_edges_tooltip";
            public static final String SNAP_TO_KEYFRAMES = "gui.director.dialog.timeline_settings.snap_to_keyframes";
            public static final String SNAP_TO_KEYFRAMES_TOOLTIP = "gui.director.dialog.timeline_settings.snap_to_keyframes_tooltip";
            public static final String THRESHOLD_GROUP = "gui.director.dialog.timeline_settings.threshold_group";
            public static final String PIXEL_THRESHOLD = "gui.director.dialog.timeline_settings.pixel_threshold";
            public static final String PIXEL_THRESHOLD_TOOLTIP = "gui.director.dialog.timeline_settings.pixel_threshold_tooltip";

            private TimelineSettings() {
            }
        }

        private SystemDialog() {
        }
    }

    /**
     * 快捷键帮助列表（{@link com.director.ui.input.ShortcutBindings#getAllShortcutsByCategory()}）。
     */
    public static final class Shortcuts {
        public static final String CATEGORY_FILE = "gui.director.shortcuts.category.file";
        public static final String CATEGORY_PLAYBACK = "gui.director.shortcuts.category.playback";
        public static final String CATEGORY_TOOLS = "gui.director.shortcuts.category.tools";
        public static final String CATEGORY_SELECTION_MODE = "gui.director.shortcuts.category.selection_mode";
        public static final String CATEGORY_TIMELINE = "gui.director.shortcuts.category.timeline";
        public static final String CATEGORY_GIZMO = "gui.director.shortcuts.category.gizmo";
        public static final String CATEGORY_UI = "gui.director.shortcuts.category.ui";

        public static final String DESC_PROJECT_NEW = "gui.director.shortcuts.desc.project_new";
        public static final String DESC_PROJECT_RELOAD = "gui.director.shortcuts.desc.project_reload";
        public static final String DESC_PROJECT_SAVE = "gui.director.shortcuts.desc.project_save";
        public static final String DESC_PROJECT_EXIT = "gui.director.shortcuts.desc.project_exit";
        public static final String DESC_PLAY_PAUSE = "gui.director.shortcuts.desc.play_pause";
        public static final String DESC_GO_TO_START = "gui.director.shortcuts.desc.go_to_start";
        public static final String DESC_GO_TO_END = "gui.director.shortcuts.desc.go_to_end";
        public static final String DESC_TOGGLE_LOOP = "gui.director.shortcuts.desc.toggle_loop";
        public static final String DESC_TOOL_SELECT = "gui.director.shortcuts.desc.tool_select";
        public static final String DESC_TOOL_BOX_SELECT = "gui.director.shortcuts.desc.tool_box_select";
        public static final String DESC_TOOL_WAND = "gui.director.shortcuts.desc.tool_wand";
        public static final String DESC_TOOL_BRUSH = "gui.director.shortcuts.desc.tool_brush";
        public static final String DESC_TOOL_FACE = "gui.director.shortcuts.desc.tool_face";
        public static final String DESC_TOOL_MOVE = "gui.director.shortcuts.desc.tool_move";
        public static final String DESC_TOOL_ROTATE = "gui.director.shortcuts.desc.tool_rotate";
        public static final String DESC_TOOL_NEXT = "gui.director.shortcuts.desc.tool_next";
        public static final String DESC_SELECTION_ADD = "gui.director.shortcuts.desc.selection_add";
        public static final String DESC_SELECTION_SUBTRACT = "gui.director.shortcuts.desc.selection_subtract";
        public static final String DESC_SELECTION_INTERSECT = "gui.director.shortcuts.desc.selection_intersect";
        public static final String DESC_TIMELINE_UNDO = "gui.director.shortcuts.desc.timeline_undo";
        public static final String DESC_TIMELINE_REDO = "gui.director.shortcuts.desc.timeline_redo";
        public static final String DESC_TIMELINE_CUT = "gui.director.shortcuts.desc.timeline_cut";
        public static final String DESC_TIMELINE_COPY = "gui.director.shortcuts.desc.timeline_copy";
        public static final String DESC_TIMELINE_PASTE = "gui.director.shortcuts.desc.timeline_paste";
        public static final String DESC_DELETE_FOCUS_ROUTED = "gui.director.shortcuts.desc.delete_focus_routed";
        public static final String DESC_TIMELINE_DELETE_MODIFIED = "gui.director.shortcuts.desc.timeline_delete_modified";
        public static final String DESC_GIZMO_ROTATE = "gui.director.shortcuts.desc.gizmo_rotate";
        public static final String DESC_GIZMO_SCALE = "gui.director.shortcuts.desc.gizmo_scale";
        public static final String DESC_GIZMO_LOOK_AT = "gui.director.shortcuts.desc.gizmo_look_at";
        public static final String DESC_GIZMO_ACTOR_TOOLS = "gui.director.shortcuts.desc.gizmo_actor_tools";
        public static final String DESC_HELP_SHORTCUTS = "gui.director.shortcuts.desc.help_shortcuts";
        public static final String DESC_CANCEL_OR_CLEAR = "gui.director.shortcuts.desc.cancel_or_clear";

        public static final String KEY_CTRL_N = "gui.director.shortcuts.key.ctrl_n";
        public static final String KEY_CTRL_O = "gui.director.shortcuts.key.ctrl_o";
        public static final String KEY_CTRL_S = "gui.director.shortcuts.key.ctrl_s";
        public static final String KEY_ALT_F4 = "gui.director.shortcuts.key.alt_f4";
        public static final String KEY_ENTER = "gui.director.shortcuts.key.enter";
        public static final String KEY_HOME = "gui.director.shortcuts.key.home";
        public static final String KEY_END = "gui.director.shortcuts.key.end";
        public static final String KEY_L = "gui.director.shortcuts.key.l";
        public static final String KEY_1_OR_V = "gui.director.shortcuts.key.1_or_v";
        public static final String KEY_2_OR_B = "gui.director.shortcuts.key.2_or_b";
        public static final String KEY_3 = "gui.director.shortcuts.key.3";
        public static final String KEY_4 = "gui.director.shortcuts.key.4";
        public static final String KEY_5 = "gui.director.shortcuts.key.5";
        public static final String KEY_G = "gui.director.shortcuts.key.g";
        public static final String KEY_R = "gui.director.shortcuts.key.r";
        public static final String KEY_S = "gui.director.shortcuts.key.s";
        public static final String KEY_T = "gui.director.shortcuts.key.t";
        public static final String KEY_TAB = "gui.director.shortcuts.key.tab";
        public static final String KEY_PLUS = "gui.director.shortcuts.key.plus";
        public static final String KEY_MINUS = "gui.director.shortcuts.key.minus";
        public static final String KEY_0 = "gui.director.shortcuts.key.0";
        public static final String KEY_CTRL_Z = "gui.director.shortcuts.key.ctrl_z";
        public static final String KEY_CTRL_Y = "gui.director.shortcuts.key.ctrl_y";
        public static final String KEY_CTRL_X = "gui.director.shortcuts.key.ctrl_x";
        public static final String KEY_CTRL_C = "gui.director.shortcuts.key.ctrl_c";
        public static final String KEY_CTRL_V = "gui.director.shortcuts.key.ctrl_v";
        public static final String KEY_DELETE = "gui.director.shortcuts.key.delete";
        public static final String KEY_CTRL_DELETE = "gui.director.shortcuts.key.ctrl_delete";
        public static final String KEY_E = "gui.director.shortcuts.key.e";
        public static final String KEY_G_R = "gui.director.shortcuts.key.g_r";
        public static final String KEY_F1 = "gui.director.shortcuts.key.f1";
        public static final String KEY_ESC = "gui.director.shortcuts.key.esc";

        private Shortcuts() {
        }
    }

    public static final class Status {
        public static final String VISIBILITY_PROGRESS = "gui.director.status.visibility_progress";
        public static final String VISIBILITY_COMPLETE = "gui.director.status.visibility_complete";
        public static final String LARGE_CONTAINER_ADVISORY = "gui.director.status.large_container_advisory";
        public static final String CONTAINER_BLOCKS_TRUNCATED = "gui.director.status.container_blocks_truncated";
        public static final String SAVE_FAILED_ADVISORY = "gui.director.status.save_failed_advisory";

        private Status() {
        }
    }

    public static final class Memory {
        public static final String LOW_HEAP_ADVISORY = "gui.director.memory.low_heap_advisory";
        public static final String LOW_HEAP_ADVISORY_HINT = "gui.director.memory.low_heap_advisory_hint";

        private Memory() {
        }
    }
}
