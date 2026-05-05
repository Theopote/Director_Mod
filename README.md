# Director

A professional cinematic toolset mod for Minecraft (Fabric), designed for machinima creators, cinematic filmmakers, and content producers who want fine-grained control over camera movement, actor animation, and scene composition inside Minecraft.

## Features

### Camera System
- **Camera Path Editing** — Create and edit smooth camera trajectories using keyframe-based spline paths. Drag keypoints directly in the 3D world with gizmo handles.
- **Safe Frame Overlay** — Displays composition guides (safe areas, rule-of-thirds grid, center cross, letterbox mask) rendered directly on the Minecraft screen in the correct aspect ratio.
- **Camera Preview** — Preview the active camera view with viewport aspect-ratio fitting/filling.
- **External Window Support** — Detach the Director UI into a separate floating window while keeping all overlays on the Minecraft screen.

### Timeline & Keyframing
- **Non-linear Timeline** — A full-featured timeline panel with track lanes, keyframe editing, and playback controls.
- **Snapping & Shortcuts** — Snap keyframes to grid, use standard edit shortcuts (Ctrl+Z undo, Delete to remove, etc.).
- **Container-based Scenes** — Group cameras, actors, and props into named containers/scenes. Persist and reload scene state.
- **Audio Track Support** — Reference audio tracks alongside camera and actor tracks.

### Actor System
- **Actor Tracks** — Attach keyframed animation data to Minecraft entities (actors).
- **Actor Gizmo** — Select and transform actors in the 3D world using move/rotate gizmos.
- **Path Visualization** — Visualise actor movement paths with cubic spline rendering.
- **Entity Clip Sampler** — Record live entity motion and bake it into keyframe clips.

### Toolset
| Tool | Description |
|------|-------------|
| Camera Path Edit | Edit camera spline control points in 3D space |
| Actor Select | Click or box-select actors in the world |
| Actor Path | Edit actor movement paths |

### UI
- **ImGui-based Interface** — Dockable, resizable panels built on top of imgui-mc.
- **Properties Panel** — Context-sensitive properties for the selected object (camera, actor, container, group).
- **Selection Filter** — Filter what types of objects can be selected in the viewport.
- **Selection History** — Navigate backward and forward through selection history.
- **Render Settings** — Control render resolution, aspect ratio, and export options.
- **Status Bar** — Real-time display of playback position, active tool, and mod state.

## Requirements

| Dependency | Version |
|------------|---------|
| Minecraft | 1.21.10 |
| Fabric Loader | ≥ 0.18.4 |
| Fabric API | 0.138.4+1.21.10 |

> Check [fabricmc.net/develop](https://fabricmc.net/develop) for the latest compatible versions.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.10.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods/` folder.
3. Place the Director `.jar` file in your `mods/` folder.
4. Launch Minecraft with the Fabric profile.

## Building from Source

**Prerequisites:** JDK 21, Git

```bash
git clone https://github.com/your-org/director.git
cd director
./gradlew build
```

The compiled jar will be in `build/libs/`.

To run a development client:

```bash
./gradlew runClient
```

## Usage

### Opening the Director UI

Use the in-game command or assigned keybind to open the Director panel. The UI can be docked, undocked, or detached into an external floating window.

### Camera Path Workflow

1. Open the **Camera Path Edit** tool from the toolbar.
2. **Left-click** in the viewport to add a new keyframe point.
3. **Click** an existing point to select it; **drag** to move it with the axis gizmo.
4. **Box-select** by dragging in empty space to select multiple points.
5. Press **Delete** to remove selected points.
6. Press **Play** in the timeline to preview the camera motion.

### Scene Containers

Scenes are organized as *containers*. Each container holds tracks for cameras, actors, and audio. Use the **Container Panel** to create, rename, and switch between containers.

## Project Structure

```
src/main/java/com/director/
├── actor/          Entity actor system (data, registry, clips)
├── client/         Client-side controllers, gizmos, renderers
│   ├── gizmo/      3D gizmo handles (axis, plane, rotation)
│   └── renderer/   HUD overlay renderers (safe frame, path selection)
├── command/        Minecraft commands
├── config/         Mod configuration
├── core/           Core container and scene state management
├── export/         Video export pipeline
├── mixin/          Fabric mixins (HUD injection, etc.)
├── network/        Client–server packet handlers
├── ui/             ImGui UI system
│   ├── camera/     Camera path edit tool and controller
│   ├── panels/     All editor panel implementations
│   ├── timeline/   Timeline engine (tracks, keyframes, playback)
│   └── tools/      Editor tool registry and implementations
└── world/          World-side persistence and entity helpers
```

## Contributing

Contributions are welcome. Please follow these guidelines:

- Open an issue before starting work on a large feature.
- Keep commits focused and atomic.
- Follow the existing code style (no extra blank lines, concise Javadoc).
- Run `./gradlew check` before submitting a pull request.

## License

This project is licensed under the **Apache License 2.0**. See [LICENSE](LICENSE) for the full text.
