# Kdesign

A local, high-level designer for side-scrolling action adventures. The application is written in **Kotlin/Wasm**, with a
small Kotlin/JVM filesystem server. No database or frontend framework is required.

## Run

Requires JDK 21 and a modern browser with WebAssembly GC support (current Chrome, Firefox, Safari, or Edge). The first
build downloads Gradle, Kotlin, Node and webpack dependencies.

```sh
./gradlew :app:run
```

Open http://localhost:8080. This task builds the browser application and serves it and the API from the same origin. The
server binds to loopback only. Stop with Ctrl+C.

On first run, the server creates `data/project.json` with a sample forest, cave sublevel, reusable assets, concepts, and
bidirectional portals. Existing project files are never replaced at startup. **All valid edits autosave** to disk after
650 ms of inactivity. This includes typing in briefs, titles, table cells and properties, editing concepts, drawing,
placement, creation and undo. **Save now** (or Cmd/Ctrl+S) flushes immediately. New-object dialogs still use Create to
commit the new object; generation only runs when explicitly requested. A reload reads the saved project. Tile strokes
and applied model edits support Undo (up to 50 snapshots); invalid field drafts do not.

Optional environment variables:

| Variable         | Default                                       | Purpose                                           |
|------------------|-----------------------------------------------|---------------------------------------------------|
| `PORT`           | `8080`                                        | Local server port                                 |
| `DATA_DIR`       | `data`                                        | Filesystem storage root                           |
| `WEB_DIR`        | `web/build/dist/wasmJs/developmentExecutable` | Built browser files                               |
| `OPENAI_API_KEY` | unset                                         | Enable tile generation; server only               |
| `OPENAI_MODEL`   | `gpt-4.1-mini`                                | Responses API model supporting structured outputs |

To enable generation, export the key in your shell before starting the server. Do not put credentials in `data`, browser
code, or Git. A prompt produces a **new** tile; it never overwrites an existing tile. When invoked from a basic block,
the generated tile is also placed in the first available cell if one exists. The request uses OpenAI structured outputs
to produce palette-indexed pixel art, rather than generating and downsampling a raster image. Real API calls are made
only when you press Generate; your API account is billed for those calls.

## Workspaces

- **Levels:** compose a side-scrolling space from blocks, inspect placements, open portals, edit the design brief, and
  edit titles and every brief field directly in a horizontally scrolling table. Cell edits autosave while you type.
  Invalid cells show an error and retain their draft in the current editor; autosave writes only the last valid model.
  New levels can be children of existing levels, while retaining independent coordinate spaces.
- **Tiles:** create 8×8 or 16×16 tiles; draw with the NES-style 64-entry palette or transparency; inspect the live
  preview; generate editable pixels from a description.
- **Blocks:** sparse placements of tiles, semantic properties (`key=value`), and a linked design concept.
- **Meta blocks:** recursively compose blocks or other meta blocks; add portal references to a level root or a nested
  meta-block instance.
- **Concepts:** create and edit gates, mechanics, switches, quests, puzzles, inventory objects, and map objects with the
  requested subcategories.
- **Find usages:** direct placements, every transitive occurrence across all levels, and incoming portal references.
  Open a result to navigate to its owner/level and highlight its first placement; the full instance path is shown for
  deeper navigation.

The canvas supports 8- and 16-pixel placement grids. This is an authoring tool, not a playable game engine. Entity
deletion, redo, multi-user merging, animation, collision simulation, and richer visual quest graphs are future work. The
current concept selector attaches one concept per definition; the file model supports multiple.

Autosave status shows pending, saving, saved, validation errors, or retrying after a write failure. Failed writes retry
with backoff (up to 30 seconds). Only one request is in flight; its acknowledgement cannot mark newer edits saved. Save
callbacks update the status in place, preserving focus, selection, scroll and the reference picker. Closing the page
while writes or invalid drafts are pending triggers the browser's unsaved-changes warning.

## References in text

Type `@` followed by an object name, or click **@ Reference** beside any authored text field. Search by name, kind, or
stable ID, then choose with a click or the arrow keys and Enter. Escape closes the picker. References work in names and
titles (including the project title), level briefs, concept descriptions, semantic property text, portal labels, and
generation prompts. Numeric dimensions and structural instance paths retain their original formats.

The persisted syntax is `[[object-id]]`. The UI previews the current object name and makes references clickable in
headings, prose, and field previews. Renames update displayed names without rewriting stored references. Name-reference
cycles are permitted and rendered with bounded expansion. Broken IDs are highlighted while drafting and rejected when
applying or saving a project. Generation prompts resolve references to names before submission.

**Find usages** includes text references, labeled with the owning object and source field; opening a result goes to the
corresponding editor. Existing JSON files remain compatible; no migration is needed. Object references target reusable
definitions, levels, and concepts; portal instance paths remain the mechanism for targeting a specific placed instance.

## Model and backtracing

`model/src/commonMain/kotlin/Model.kt` is shared by browser and server:

- A `Tile` owns palette indices, with `-1` for transparency.
- A `Block` with `kind="block"` contains tile placements. A block with `kind="meta"` contains block/meta-block
  placements. Empty regions are omitted, not materialized as transparent child entities.
- Every definition has a stable ID. Each `Placement` has a separate stable ID in its parent's coordinate space and
  references its asset by ID. Editing an asset updates all its occurrences.
- A `Level` references a top-level meta block and optionally a parent level. Its notes contain all eight requested
  design fields, alongside its title.
- A `Portal` targets a level ID plus a sequence of placement IDs. An empty path targets that level's root meta block.
  This disambiguates repeated instances of one definition. Portal cycles are allowed; composition and level ancestry
  cycles are rejected.
- `Project.usages(id)` derives usages from the current graph, so there is no persisted reverse index to become stale.
  Traversal retains separate paths for repeated instances and scans every level, including sublevels. It also finds
  incoming portal references.
- Validation rejects duplicate entity IDs, duplicate placement IDs within a parent, missing references, incompatible
  child types, invalid dimensions/palette data, out-of-bounds placements, and invalid portal destinations. UI mutations
  and project saves both validate.

The schema is versioned (`version: 1`), JSON is pretty-printed, and project writes use a temporary file plus atomic
replacement where supported. The single-file aggregate keeps graph changes coherent and easy to version in Git. This
first version is intended for one editor at a time; concurrent clients use last-save-wins behavior.

## Filesystem API

All paths are relative to `DATA_DIR`. Absolute paths, `..` escapes, and symlinks are rejected. Request bodies are
limited to 4 MiB. No CORS is enabled; origin and host checks restrict browser writes to the local app.

| Method | Endpoint                            | Result                                                                                 |
|--------|-------------------------------------|----------------------------------------------------------------------------------------|
| GET    | `/api/health`                       | Status and generation availability (never credentials)                                 |
| GET    | `/api/files?path=project.json`      | UTF-8 file contents                                                                    |
| PUT    | `/api/files?path=notes/example.txt` | Atomically write UTF-8 request body; create parent directories                         |
| GET    | `/api/directory?path=`              | List immediate entries with name and directory flag                                    |
| POST   | `/api/generate`                     | `{ "description": "a mossy mushroom", "size": 16 }` → `{ "name": "…", "pixels": […] }` |

`PUT /api/files?path=project.json` additionally decodes and validates the project before replacing it. Other files are
arbitrary UTF-8 documents. The server is a local authoring service, not an authenticated public host.

## Build and tests

```sh
./gradlew :model:jvmTest :app:test :web:wasmJsBrowserDevelopmentExecutableDistribution
```

Tests cover JSON round-tripping, transitive instance usage across multiple levels, repeated nested definitions, incoming
portals, cycle/broken-reference rejection, filesystem read/write/list, preservation after invalid writes, traversal
rejection, and symlink rejection.

Modules: `model` (shared Kotlin data and graph logic), `web` (Kotlin/Wasm UI with a small browser IO bridge), `app` (JVM
HTTP server). The original `utils` starter module remains available but is not used by the designer.

Implementation
references: [Kotlin/Wasm JavaScript interoperability](https://kotlinlang.org/docs/wasm-js-interop.html), [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses).
