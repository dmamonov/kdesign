# Kdesign design

Kdesign is a local authoring tool for side-scrolling 2D action adventures. It combines high-level game design notes with
basic pixel art and spatial composition. The primary output is a structured project that can be inspected, reused, and
versioned on disk. It is not currently a playable game engine.

This document describes the implemented design and its constraints. Future directions are listed separately.
See [README.md](README.md) for setup and commands.

## Design principles

- **Files are the source of truth.** Store the project under `data/`, with a small server providing read, write, and
  directory-list operations. No database is required.
- **Reuse preserves identity.** Place references to definitions instead of copying their contents. Changing a mushroom
  definition changes every occurrence of that mushroom.
- **Backtracing is part of the model.** Every reference-bearing feature must have a clear owner and a way to find its
  incoming usages, including occurrences nested across levels.
- **Text participates in the graph.** Titles and other authored text can refer to objects by stable ID.
- **Valid edits save automatically.** Keep invalid drafts visible without replacing the last valid project. Make save
  failures and pending work visible.
- **Graphics support design work.** Provide tiles, sparse compositions, area editing, and fast brush selection without
  requiring a separate graphics application.

## Runtime architecture

```text
Browser
  web: Kotlin/Wasm application
    editor state, views, commands, undo, autosave
    small JavaScript bridge: DOM, input, HTTP, timers
                      |
               same-origin HTTP
                      |
  app: Kotlin/JVM local server
    static browser assets, filesystem API, generation proxy
             |                         |
       data/project.json          OpenAI API

  model: shared Kotlin code used by both web and app
    schema, validation, references, usages, area operations
```

The `model` module has no browser or server dependency. Both ends validate the same serialized model. The browser owns
the editing session; the server owns access to disk and the API key.

The `web` module compiles to WebAssembly. Kotlin constructs the UI and SVG graphics; the JavaScript bridge handles
browser-specific operations. Pointer geometry and keyboard events are translated into Kotlin editor actions. Model
changes remain in Kotlin.

The `app` module uses the JDK HTTP server and serves both browser assets and API endpoints from `127.0.0.1:8080` by
default. `:app:run` builds the browser distribution before starting the server. The starter `utils` module is not used
by the designer.

## Project model

The project is a single JSON aggregate with schema `version: 1`. It contains a title, tiles, blocks, levels, concepts,
and asset hotkey assignments.

| Entity      | Main fields                                                                                | Responsibility                                                     |
|-------------|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `Tile`      | `id`, `name`, `size`, `pixels`                                                             | Smallest graphic definition                                        |
| `Block`     | `id`, `name`, `kind`, `width`, `height`, `children`, `properties`, `conceptIds`, `portals` | Reusable spatial composition and semantics                         |
| `Placement` | `id`, `assetId`, `x`, `y`                                                                  | An instance of an asset in a parent composition                    |
| `Level`     | `id`, `title`, `rootId`, `parentId`, `notes`                                               | Independent space and its design brief                             |
| `Concept`   | `id`, `name`, `category`, `type`, `description`                                            | Named design concept attached to definitions or referenced in text |
| `Portal`    | `label`, `targetLevelId`, `targetPath`                                                     | A connection to a particular meta-block instance in a level        |

Entity IDs are globally unique within the project and independent of display names. Placement IDs are unique within
their parent block. Renaming an object does not change its identity.

### Tiles and palette

A tile is square, either 8×8 or 16×16 pixels; the model default is 16×16. Pixels are stored in row-major order as
integer palette indices. Values `0..63` address a fixed NES-style palette; `-1` means transparency.

The tile editor draws directly into this representation. Generated tiles use the same representation and remain
editable. The palette is a visual starting point, not an implementation of NES hardware restrictions.

### Blocks and meta blocks

Both use `Block`, distinguished by `kind`:

- `block`: children reference tiles only.
- `meta`: children reference basic blocks or other meta blocks.

Positions and dimensions are measured in pixels in the parent's coordinate system. Empty regions are represented by
omitted placements. Children must fit inside the parent; current block dimensions range from 8 to 4096 pixels on each
axis. General composition permits overlapping placements; area fill deliberately avoids adding overlaps.

Composition is an acyclic graph, rather than a tree: one definition can appear under multiple parents or multiple times
under the same parent. Self-reference and indirect composition cycles are rejected. Editing a shared definition affects
all of its occurrences; there are no per-instance overrides or automatic copies.

`properties` stores semantic `key=value` text. `conceptIds` attaches design concepts to a block. Together these describe
the role of a graphic, such as a mushroom, locked door, or checkpoint. They do not currently execute behavior or enforce
a gameplay type system. The schema supports multiple concept links; the current properties editor selects one.

### Levels and sublevels

Each level references a root meta block. An optional `parentId` expresses a sublevel relationship. Every level has its
own coordinate space; a cave sublevel is not implicitly positioned inside its parent forest. Travel between spaces is
explicit through portals.

Level ancestry must be acyclic. Roots refer to reusable definitions, so two levels may share a root; changes to that
definition then affect both levels.

The level overview is directly editable and presents these columns:

| Field                  | Stored as               | Purpose                                                    |
|------------------------|-------------------------|------------------------------------------------------------|
| Title                  | `title`                 | Name of the level                                          |
| Description            | `notes.description`     | Setting and overview                                       |
| Player mechanics       | `notes.playerMechanics` | Actions and abilities used here                            |
| Player goals           | `notes.playerGoals`     | What the player is trying to achieve                       |
| Design goals           | `notes.designGoals`     | What the designer wants the space to accomplish            |
| Emotional baits        | `notes.emotionalBaits`  | Curiosity, tension, anticipation, and other intended draws |
| Characters encountered | `notes.characters`      | Characters involved in the space                           |
| Enemies encountered    | `notes.enemies`         | Opposition encountered                                     |
| Objects encountered    | `notes.objects`         | Relevant items and map objects                             |

These are reference-capable text fields, not separate typed encounter lists. The table and the individual design brief
edit the same underlying level.

### Concept taxonomy

Concepts provide a shared vocabulary for design notes and graphic definitions.

| Category          | Current types                                               |
|-------------------|-------------------------------------------------------------|
| Gates             | Lock and key, Inventory object, Ability, Puzzle, Difficulty |
| Mechanics         | Movement, Combat, Interaction                               |
| Switches          | Puzzle, Tool based, Environment based, Key switches         |
| Quests            | Kill, Fetch, Delivery, Collect, Escort                      |
| Puzzles           | Logic, Spatial, Sequence                                    |
| Inventory objects | Key, Tool, Collectible                                      |
| Map objects       | Landmark, Entrance, Checkpoint                              |

Category/type pairs are validated. Concepts are descriptive authoring objects; quest execution, puzzle simulation, and
inventory state are outside the current model.

## References and backtracing

### Definition references versus instance references

An `assetId` identifies a reusable definition. A path of placement IDs identifies an occurrence in a level. These are
deliberately different forms of identity.

For example, if `block-mushroom` is placed twice in a clearing, searching for its usages returns both occurrences. If
that clearing is reused in two levels, both paths in each level remain distinct. A global visited set must not collapse
repeated occurrences during traversal; cycle protection is scoped to the current ancestry.

A portal belongs to a meta-block definition and targets:

```json
{
  "label": "Into the cave",
  "targetLevelId": "level-cave",
  "targetPath": [
    "cave-room",
    "room-entrance"
  ]
}
```

The path is resolved from the target level's root through placement IDs and must end at a meta block. An empty path
addresses the root itself. Portals can form cycles, including a return path to the forest, because navigation cycles are
different from recursive composition.

Portal data is shared with its owning definition: reusing that definition repeats the same configured destination.
Removing or replacing a placement used by a portal destination is rejected if it would leave the path invalid.

### References in authored text

References are embedded in ordinary strings using `[[object-id]]`. The UI provides an `@` picker and reference buttons,
resolves names for display, and exposes clickable links and field previews. This includes project and level titles,
asset names, briefs, concept descriptions, property keys and values, portal labels, and generation prompts. Numeric
inputs and structural IDs/paths keep their own formats.

Stored text keeps the target ID; renaming the target updates its displayed name without rewriting referring fields.
References can point to tiles, blocks, meta blocks, levels, and concepts. They identify definitions or levels, not
individual placements.

Completed reference tokens with unknown IDs fail project validation. Incomplete tokens remain ordinary draft text. Name
references may form cycles; display expansion is bounded and falls back to IDs at a cycle or depth limit. Generation
prompts resolve references to names before submission and are not persisted as project fields.

### Derived usage index

`Project.usages(id)` computes usages from the current model rather than persisting a reverse index. Results include:

- Direct child placements in reusable definitions.
- Transitive instance paths under every level, including sublevels and repeated instances.
- Concept attachments and their level occurrences.
- Incoming portal destinations.
- Text references with their owning object and field.

This keeps usage information consistent with edits and undo. Results navigate to the owning editor or level. Nested
occurrence results retain the full path; the current UI highlights the first placement when opening a level occurrence.

Hotkeys are editor configuration references and are validated, but are not currently included in Find usages. Any future
cached reverse index must remain derived and be invalidated with model changes.

## Map editing

The level canvas edits its root meta block. Basic-block and meta-block canvases use the same composition tools with
different allowed assets. Candidate assets must fit the parent and must not introduce a composition cycle.

The canvas uses an 8- or 16-pixel placement grid. Its current tool, selection, brush, and picker position are transient
editor state.

| Action                              | Control                                       |
|-------------------------------------|-----------------------------------------------|
| Place the selected asset            | `B`, then click a grid cell                   |
| Remove placements at a point        | `E`, then click                               |
| Select a rectangular area           | `V`, then drag; or Shift-drag                 |
| Fill the selection                  | `F` or Fill area                              |
| Clear fully contained placements    | Delete/Backspace or Clear                     |
| Deselect / close the picker         | Escape                                        |
| Open the around-cursor block picker | Right-click the canvas or Space               |
| Select an assigned asset            | Digits `0`–`9`                                |
| Assign the current brush            | Shift + digit, or the asset's hotkey selector |

Keyboard map commands are suppressed in text inputs and dialogs. The radial picker shows up to eight compatible assets
per page, with thumbnails and assigned keys, and stays within the viewport.

Hotkeys are stored in `Project.hotkeys` as digit-to-asset-ID mappings. Assignments are project-wide and autosaved; each
asset has at most one key, and assigning an occupied key replaces its previous binding. A binding only selects an asset
when it is compatible with the current canvas.

### Area operations

Selections are rectangles in parent pixel coordinates, snapped to the grid and clipped to the canvas. A fill repeats
whole assets from the selection origin. Each axis advances by the asset dimension rounded up to the grid size; no asset
is cropped to fit a leftover edge.

- **Fill empty space:** retain existing placements and skip candidate positions that overlap them.
- **Replace contained blocks:** remove fully contained placements of other assets, retain existing placements of the
  chosen asset, then fill available positions.
- **Clear:** remove only placements wholly contained within the selection.

Partially intersecting placements remain intact in all three operations. Preserving existing same-asset placements also
preserves their instance IDs. Fill and clear each produce one validated model change and one undo step. If a change
breaks a portal path or another model invariant, the whole operation is rejected.

## Editing, undo, and autosave

The browser holds an immutable-style `Project` value and up to 50 previous snapshots for Undo. Commands construct a
candidate project, validate it, and only then replace the current value. No-op changes do not create a new revision.
Undo restores a prior project and is itself autosaved. History is session-local; redo is not implemented.

Existing-object text fields and overview cells update the model while typing when valid. Invalid drafts stay in their
fields with errors; navigation that would discard them is blocked until they are corrected or undone. New-object dialogs
use Create to commit a complete object. API generation starts only after an explicit Generate action.

Autosave follows these rules:

1. A valid edit increments the local revision and schedules a save after 650 ms of inactivity.
2. A save serializes a snapshot of the project. Only one write is in flight at a time.
3. Success acknowledges only that snapshot's revision. Edits made during the request remain pending and trigger another
   save.
4. Failures retain pending changes and retry with exponential backoff capped at 30 seconds.
5. Save now or Cmd/Ctrl+S requests an immediate flush. Status updates do not remount the editor or interrupt input
   focus.

The UI distinguishes pending, saving, saved, invalid draft, and retrying states. A browser unload warning is enabled
while edits or invalid drafts remain pending. There is no durable browser-side recovery journal, so unsent edits are not
guaranteed to survive a browser crash.

## Persistence and server boundary

The default storage root is `data/`, configurable with `DATA_DIR`. Startup creates a sample `project.json` only if it
does not exist. The current design keeps the graph in one file so references and their targets can be saved together.
JSON is pretty-printed with default fields encoded.

| Method | Endpoint                | Contract                                                   |
|--------|-------------------------|------------------------------------------------------------|
| GET    | `/api/health`           | Health and whether generation is configured; never the key |
| GET    | `/api/files?path=…`     | Read UTF-8 file contents                                   |
| PUT    | `/api/files?path=…`     | Write UTF-8 contents and create parent directories         |
| GET    | `/api/directory?path=…` | List immediate entries with name and directory flag        |
| POST   | `/api/generate`         | Generate an editable palette-indexed tile                  |

Paths are relative to the storage root. Absolute paths, normalized paths escaping the root, and symlink traversal are
rejected. Request bodies are limited to 4 MiB. Writes to `project.json` additionally decode and validate the complete
graph before replacing the file. Other files are arbitrary UTF-8 documents.

Writes use a temporary file in the destination directory followed by atomic replacement when supported, with a
replacement fallback otherwise. The server validates host and origin and binds to loopback; it is a local service, not
an authenticated public deployment.

The design assumes one active editor. Server writes are serialized, but there is no cross-client revision check, merge,
or lock. Multiple tabs or external writers can overwrite one another using last-save-wins semantics. The browser does
not watch disk changes. Schema version 1 is checked explicitly; there is no migration framework yet. Hotkeys default to
an empty map when reading older files without that field.

## Description-based generation

The browser submits a description and tile size to the local server. The server reads `OPENAI_API_KEY` from its
environment and calls the Responses API with a structured-output schema for a name and pixel array. `OPENAI_MODEL`
selects the model; the current configured default is `gpt-4.1-mini`.

The server validates the returned pixel count and palette range. The browser creates a new tile; when generation is
invoked from a basic block, it also places the tile in the first available cell if one exists. The resulting model edit
participates in validation, undo, and autosave.

Generation produces palette indices directly, rather than a raster image that must be downsampled. Keys stay on the
server. Whole-block or hierarchical level generation is not currently implemented.

## Validation and verification

Shared validation protects entity and placement identity, references, palette data, dimensions, child types, placement
bounds, concept taxonomy, hotkey assignments, level ancestry, composition acyclicity, and portal destinations. It runs
for UI mutations and server-side project writes.

Model tests cover serialization, repeated and nested usages, portals, text references, graph validation, area
operations, and autosave revision handling. Server tests cover filesystem operations and rejection of unsafe paths or
invalid project writes. The WebAssembly distribution build checks the browser compilation boundary. UI interaction
checks are still needed for pointer selection, focus preservation, keyboard shortcuts, radial positioning, and save
feedback.

## Future directions

Potential extensions, not current behavior:

- Safe entity deletion with an incoming-reference review and explicit repair strategy.
- Redo, persistent recovery drafts, and conflict detection for concurrent editors.
- Typed mechanics, quest dependencies, puzzle graphs, and richer concept attachment editing.
- Per-instance properties with explicit inheritance from reusable definitions.
- Animation, collision layers, gameplay preview, and runtime export.
- Whole-block generation and schema migrations as the model grows.
- Derived lookup indexes and incremental rendering if larger projects outgrow full-graph scans and whole-project saves.

Extensions should preserve stable identities and account for reverse navigation, instance paths, validation, undo, and
autosave from the outset.
