# Actors System Development Plan

## Goal

Actors is Director's creature choreography system. It turns Minecraft living entities into controllable performers that can be staged, rehearsed, grouped, routed, and animated through Director's timeline.

The design priority is:

1. Director control
2. Visual stability
3. Timeline reproducibility
4. AI autonomy

Native Minecraft AI and navigation are useful execution helpers, but they must not become the source of truth for a performance.

## Core Concepts

### Actor

An Actor is a Director-level identity, not the Minecraft entity itself.

An Actor owns:

- `actorId`
- display name
- source entity type
- optional bound entity UUID
- rig profile
- tags
- group membership
- performance mode

Timeline data references `ActorRef`, not live Minecraft entities. Runtime resolves the current entity or preview entity from that reference.

### Performance Mode

Performance mode determines how much Director controls the entity.

- `RELEASED`: normal Minecraft behavior
- `CONTROLLED`: Director controls transform, look direction, state, action, pose, and route
- `HYBRID`: future mode where Minecraft navigation may assist local obstacle avoidance

The first stable implementation should focus on `CONTROLLED` and `RELEASED`.

### Actor Group

Groups are Director objects for crowd and troupe workflows.

A group should support:

- batch selection
- batch placement
- shared commands
- delay policies
- formation policies
- route policies
- action policies

Initial useful commands:

- move/route
- look at
- play action
- pose override

### Route

Route is a timeline-controlled movement plan.

Route data contains:

- waypoints
- interpolation mode
- loop mode
- speed
- optional facing alignment
- future navigation policy

Route playback must be deterministic. Minecraft `PathNavigation` can later be used as a local path solver, but timeline sampling should remain reproducible.

### Action, Animation, Pose

These are separate layers:

- Action: director intent, such as `WALK`, `DANCE`, `BOW`
- Animation: concrete asset or procedural animation
- Pose: sampled body-part result

`ActorActionClip` stores semantic action intent. `ActorPoseClip` stores local body-part overrides. `RigProfile` interprets action into pose for each creature type.

## Timeline Clip Roadmap

Current and planned Entity Track clips:

- `EntitySpawnClip`: actor entry/binding
- `EntityTransformClip`: precise transform
- `ActorRouteClip`: waypoint route movement
- `EntityLookAtClip`: look target/camera/world point
- `ActorActionClip`: semantic action state
- `ActorPoseClip`: local pose override
- `EntityStateClip`: visibility/state flags
- `EntityDespawnClip`: exit/removal
- future `ActorFormationClip`: group formation state

## Tool Roadmap

Toolbar World tools should evolve toward:

- Actor Select
- Actor Move
- Actor Rotate
- Actor Scale
- Actor Look At
- Actor Pose
- Actor Route
- Actor Group
- Actor Formation
- Actor Action

Each tool should activate a clear editing context and expose its settings in Properties.

## Development Phases

### Phase 1: Single Actor Control

- Actor selection and binding
- performance mode foundation
- transform gizmo
- look-at authoring
- semantic action clip
- pose override clip

### Phase 2: Route System

- `ActorRouteClip`
- waypoint model
- route sampling
- route property adapter
- path tool creates RouteClip instead of many TransformClips
- loop and ping-pong playback

Status:

- `ActorRouteClip` model, sampling, persistence, and property editing are implemented.
- Actor Route tool currently creates deterministic waypoint RouteClips.
- Minecraft `PathNavigation` integration remains future work and should be added as an optional local navigation policy.

### Phase 3: Groups

- `ActorGroup` model
- batch select/place
- group commands
- delay policies
- group route/action authoring

Status:

- `ActorGroup` / `ActorGroupRef` project models are implemented.
- ActorRegistry save/load supports groups.
- Actor project properties include basic group creation, editing, and actor membership toggles.
- Group action authoring is implemented: the Actor project properties can create per-member
  `ActorActionClip`s from the current playhead, using the group's delay policy.

### Phase 4: Formation

- line/circle/grid formations
- face center/camera/leader
- route offsets per member
- formation timeline clip

### Phase 5: Animation Library

- action preset registry
- creature-specific animation profiles
- imported/custom animation assets
- procedural fallback poses

### Phase 6: Navigation Assistance

- optional PathNavigation local solver
- obstacle avoidance
- crowd steering
- formation-preserving route following

## Implementation Rules

- Timeline data is the source of truth.
- Runtime may use Minecraft AI/navigation only as an execution helper.
- All authored behavior must save/load through timeline persistence.
- Group behavior must decompose to actor-level deterministic samples.
- Tools create or edit clips; they should not directly mutate runtime-only state as the final result.
