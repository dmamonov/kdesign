# UI structure

## Purpose

The UI combines high-level game design with basic graphics and map editing.
It follows the product requirements in [DESIGN.md](DESIGN.md).
This document describes the intended interface, independently of the current implementation.

The main layout has three persistent areas:

- A horizontal category menu at the top.
- A navigation tree on the left.
- A workspace for the selected item in the remaining area.

Contextual tools and properties belong to the workspace.
They can appear beside the editor without replacing the category menu or navigation tree.

```text
+--------------------------------------------------------------------------------------+
| Project                 Category menu items                         Autosave status  |
+---------------------+----------------------------------------------------------------+
| Navigation tree     | Back / contextual actions                              |
|                     | Workspace views and contextual actions                         |
| Search              +-------------------------------------------+--------------------+
| Category root       |                                           |                    |
|   Item              | Table, design form, or graphics canvas    | Properties,        |
|     Child           |                                           | assets, references |
|   Item              |                                           |                    |
+---------------------+-------------------------------------------+--------------------+
```

## Horizontal menu at the top

Designers work with categories of objects.
A flat menu at the top switches between these categories.
The active category remains visually distinct.

The menu provides access to:

- Levels.
- Tiles.
- Blocks.
- Meta blocks.
- Objects.
- Gates.
- Mechanics.
- Switches.
- Quests.
- Puzzles.
- Inventory objects.
- Map objects.

```text
[LEVELS] [TILES] [BLOCKS] [META BLOCKS] [OBJECTS] [GATES] [MECHANICS] [...]
```

When the categories do not fit, provide horizontal scrolling or an overflow menu.
Overflow must not introduce another category hierarchy.
Switching categories updates the left tree and restores the last selected item in that category.

Objects retains the object-oriented workflow illustrated below.
Its relationship to blocks, inventory objects, and map objects needs a model decision.
The UI should not create duplicate definitions merely to expose them in different categories.

## Navigation tree on the left

The left panel shows the structure of the selected category.
The structure can be flat or hierarchical.
A virtual root represents the category as a whole when useful.

Selecting a node opens its content in the workspace.
Expanding a node reveals its children without changing the workspace selection.
The selected node and its ancestors should remain easy to identify.

Provide search within the category.
Search results should retain enough parent context to distinguish identically named items.
Creation actions should make clear whether a new item is a top-level definition or a child.

### Level tree

Selecting the Levels root opens the level overview table for conceptual design.
Selecting a level opens that level's workspace.
Sublevels and other structural nodes appear beneath their parent.

```text
Levels                         -> Level overview table
  Level 1                      -> Level workspace
    Rooms                      -> Room collection
      Room 1
      Room 2
    Zone 1
    Zone 2
  Level 2
    Room 1
    Room 2
```

Rooms can be grouped under a collection node, as shown for Level 1.
The example also allows direct child nodes, as shown for Level 2.
Grouping nodes organize navigation and need not be independent game objects.

The relationship between rooms, zones, meta blocks, and sublevels remains to be defined.
The tree must distinguish organizational grouping from containment in a game space.
It must not imply that a sublevel shares its parent's coordinate space.

### Object tree

An object can expose its internal structure through the same navigation pattern.

```text
Objects
  Object 1
    Animation 1
      Sprite 1
      Sprite 2
  Object 2
    Animation 1
      Sprite 1
      Sprite 2
```

Selecting an object opens its overall design information.
Selecting an animation or sprite opens the editor appropriate to that item.
Animation and sprite editing details are not yet defined in DESIGN.md.
This tree reserves a navigation structure for them without specifying playback or frame tools.

### Reusable definitions and placed instances

A definition and a placed instance must be distinguishable in navigation.
A reusable block may appear in many places without becoming multiple definitions.
An instance entry should show its containing level and composition path when needed.

Opening a definition from an instance should preserve a way back to that instance.
Following connections must not recursively expand linked objects as containment children.

## Workspace navigation

Use the left navigation to identify the current category and selected item.
Do not repeat titles, category labels, or introductory descriptions above the central workspace.
Keep Back and contextual actions such as Find usages in a compact toolbar.
Titles remain editable and support object references in the relevant design fields.

Use local views for different aspects of the same item.
For example, a level can offer Map, Design, and Usages views.
Changing a local view keeps the same selected item in the navigation tree.

Navigation between references should support returning to the previous location.
Preserve useful context such as the selected placement, table cell, or canvas position.

## Level overview table

The Levels root opens an editable table with one row per level.
Include sublevels and show their parent context.

The columns are:

- Title.
- Description.
- Player mechanics.
- Player goals.
- Design goals.
- Emotional baits.
- Characters encountered.
- Enemies encountered.
- Objects encountered.

Every design cell can be edited directly, including titles.
All text cells support object references.
Edits autosave without requiring a row-level Save action.

Long text should wrap or expand into a larger cell editor.
Keep the level identity visible while scrolling through the design columns.
Provide an explicit action to open a level so editing its title does not unexpectedly navigate away.

## Level workspace

### Map view

The map view shows the level's top-level meta block in its own space.
Provide a canvas, an asset palette, and contextual editing tools.
The current block brush, active tool, and selected area should always be visible.

Selecting a placed object exposes its identity, containing composition, and relevant properties.
Provide actions to open its definition and inspect its usages.
Make it clear when editing a shared definition will affect other occurrences.

Connections to other spaces should offer an action to open their destination.
Show enough destination context to distinguish repeated entrances or other repeated objects.

### Design view

The Design view presents the same fields as the level overview table.
Use larger text editors where the designer needs room to write.
Editing either view updates the same level information.

Display sublevel relationships separately from free-text design notes.
Allow navigation to the parent level and its sublevels.

## Tile editor

Show a magnified pixel grid with a preview of the tile.
Support the requested 8×8 and 16×16 tile sizes.
Provide the Nintendo Entertainment System color palette and a transparent drawing option.
The active color or transparency choice should be visible.

Keep the tile name, drawing tools, and generation action close to the canvas.
Tile names support references.
Drawing changes autosave.

## Block and meta-block editors

A block editor combines tiles into a sparse composition.
Its asset palette contains tiles.
Empty positions remain visually distinguishable from occupied positions.

A meta-block editor combines blocks and other meta blocks.
Its asset palette presents the corresponding reusable definitions.
Nested compositions should offer navigation into their children and back to the parent context.

Provide a properties view for semantic metadata.
Separate an object's name and descriptive information from placement-specific information.
Text in metadata supports object references.

Meta-block connections need a destination picker and a way to inspect the current target.
Choosing a destination should identify the relevant space and object.
The exact interface for selecting repeated instances depends on the identity model.

## Efficient map tools

### Block hotkeys

Allow a hotkey to be assigned from a block's palette entry or a nearby assignment control.
Show assigned keys alongside block names or thumbnails.
Pressing a key selects that block for placement and updates the active-brush indicator.

If a key is already assigned, show the conflict and make reassignment explicit.
Hotkey changes autosave.
Map hotkeys must not interfere with typing in text fields.
The supported keys and assignment scope remain to be defined.

### Area selection and filling

Provide an area-selection tool with a visible selection boundary.
Show the chosen fill block before applying a fill.
Allow the selection to be adjusted or dismissed without changing the map.

Expose Fill near the selection tools.
Preview or clearly describe how filling will affect existing content and boundary placements.
Do not leave overwrite behavior implicit.
The precise selection geometry and fill rules remain open in DESIGN.md.

Applying a fill updates the map and autosaves the change.

### Around-cursor block picker

Provide a picker around the cursor while working on the map.
Show block thumbnails, names, and assigned hotkeys.
Choosing an entry selects the placement brush and returns focus to the canvas.
Opening or dismissing the picker must not place a block by itself.

Keep the picker usable near viewport edges.
Provide search or paging when the available blocks cannot fit around the cursor.
Also expose block selection in the normal asset palette for keyboard accessibility.
The exact opening gesture and arrangement remain to be defined.

## Concept editors

Each conceptual category uses the shared tree and workspace layout.
A category root can show a collection overview.
Selecting a concept opens its name, description, type where applicable, and related objects.

Use the requested types for gates, switches, and quests from DESIGN.md.
Do not invent required subcategories for mechanics, puzzles, inventory objects, or map objects.
Concept text supports references and autosaves.

Provide navigation to referenced objects and incoming usages.
The final attachment controls depend on how concepts relate to graphic definitions in the model.

## References in text

Every authored text editor must allow the designer to insert an object reference.
This includes titles, table cells, metadata, and generation descriptions.
Typing `@` opens a searchable object picker that distinguishes targets by kind and context.
Do not show a reference button or repeated usage guidance beneath each text field.
Always show the editable field.
Do not duplicate its value in a rendered preview beside or beneath it.

References should be recognizable and navigable when text is displayed.
Editing text and following a link must be distinct interactions.
Renaming a target should preserve its reference identity.

Show an unresolved reference beside the affected field with a way to correct it.
The stored reference syntax remains a model decision.
The UI should not require the designer to memorize object IDs.

## Usages and backtracking

Expose Find usages from every reusable object's workspace.
Include direct placements, nested occurrences across levels, and incoming object or text references.
Repeated occurrences must have separate, distinguishable results.

For each result, show the relevant context:

- Owning level or object.
- Containment path for a placed occurrence.
- Field name for a text reference.
- Relationship type, such as placement or connection.

Opening a result navigates to the relevant canvas, object, or text field.
Highlight the occurrence or referring field when possible.
Preserve a route back to the usages list and the original definition.

## Description-based tile generation

Expose Generate tile in the tile workflow and the block workflow.
Provide a description field and a choice of supported tile size.
The description field supports object references.

Generation starts from an explicit Generate action.
Show progress, failure, and a resulting tile preview.
Make it clear how the generated tile becomes part of the project or the current block.
Once added, it follows the same editing and autosave behavior as other tiles.

If generation is unavailable, explain that the server needs `OPENAI_API_KEY` configured.
Do not request or display the secret key in the browser UI.

## Autosave and feedback

Show autosave status in a consistent location in the application shell.
Distinguish pending changes, saving, saved, and failed saves.
Never display Saved while changes remain unsaved.

Autosave should not interrupt typing, selection, or canvas interaction.
If a write fails, preserve the edits and provide a clear retry path.
If invalid input prevents saving, identify the affected field and explain how to correct it.
Do not silently discard unsaved input when navigating between items.

Creation, generation, and fill actions may have explicit action buttons.
Those buttons perform their named operation, rather than acting as general Save buttons.

## Common interaction states

Empty categories should explain what belongs there and offer an appropriate creation action.
Loading states should distinguish waiting for data from an empty project.
Errors should appear near the affected operation and preserve the designer's context.

Support keyboard navigation through the top menu, tree, table, and object pickers.
Keep focus visible and label icon-only controls.
Do not rely on color alone to communicate selection, references, or save failures.

## Deleting objects

Every selected tile, block, meta block, level, and conceptual object has a Delete action.
Show a confirmation dialog explaining which placements, connections, and references will change.
Keep child definitions when deleting their container.
Replace text references with the referenced object's readable name.
Remove incoming placements, semantic links, and hotkeys.
Remove portal connections whose destinations no longer exist.
Move a deleted level's sublevels to its parent.
Require removing or reassigning a level before deleting its root meta block.
Deletion autosaves as one change.
Undo restores the object and its previous references during the current session.

## Screen size and high-level maps

Use 256 × 240 pixels as the default screen size and the initial size for new levels.
Keep existing level dimensions unchanged.
Divide level previews and editing canvases into screen-sized regions.
The Levels category opens a World map with previews of every independent level space.
Show parent, sublevel, and portal links beside each preview.
Click a preview to open its editable map.
Keep the editable overview table available in the Table tab.
Provide an Overview tab within each level for its complete map at a glance.

## Compact map editor

End the map editor after the tile or block palette.
Do not show the Placed instances list or the supplementary panels that followed it.
Place the tile or block palette to the right of the canvas.
Use a scrollable grid of assets with their hotkey controls.
On narrow phone screens, place the palette below the canvas.

## Selection and map clipboard

Click to select a cell, or drag to select an area at any time.
Assign any letter or digit to an asset using its palette control or Shift plus the key.
Press the assigned key to fill the selection with that asset.
Letter keys are reserved for assets rather than tool modes.
Delete or Backspace clears whole items contained in the selection.
Ctrl/Command+C copies whole selected items; Ctrl/Command+X cuts them.
Ctrl/Command+V pastes at the selected cell or the selection's upper-left corner.
The map clipboard is shared across maps during the current browser session.
Pasted items keep shared definition references and receive new placement identities.
Reject incompatible items and out-of-bounds pastes without applying partial changes.
Keep standard text editing shortcuts unchanged while a text field has focus.

## Tile color palette

Place the NES color palette horizontally above the tile editor.
Arrange its 64 colors in two rows of 32 swatches.
Keep transparency and the current color beside the palette controls.
Allow horizontal scrolling on narrow screens rather than adding more rows.

Keep the tile workspace compact with a viewport-sized drawing grid and reduced panel spacing.
Keep generation controls and a small live preview beside the canvas on desktop screens.
Keep the two-row palette above both columns.

Tile drawing supports Undo in the tile toolbar and with Ctrl/Command+Z.
Treat each pencil or eraser stroke as one undoable edit.
Strokes that do not change any pixels do not consume an undo step.
Keep native text undo when a text field has focus.
Undo autosaves the restored tile and updates its preview and shared usages.

## Navigation URLs

Store the current category, object, and editor tab in the URL fragment.
Include the selected instance path and open usages when present.
Restore navigation after refresh and support browser Back and Forward.
Use stable object IDs and fall back gracefully for deleted objects or invalid tabs.
Do not add history entries when editing or autosaving within the same view.

## Automatic tile creation

Creating a tile also creates a same-sized block containing that shared tile at the origin.
Start image generation automatically using the tile title as its description.
Apply generated pixels to the original tile so its block and usages update together.
Keep the tile and block editable if generation fails, and allow retry from the tile editor.
Do not overwrite pixels edited while generation was pending or recreate a deleted tile.
Generating a new tile from a block editor also creates its matching reusable block.

New blocks and meta blocks default to 16 × 16 pixels.

## Compact navigation panel

Show object types with Unicode icons beside single-line names, with accessible type labels.
Remove repeated type subtitles and the overview/count card.
Use tightly spaced rows and a search input whose placeholder is Search.
Place a small New action beside the category caption instead of the Explorer label.
Click the category caption to return to its overview.
Do not repeat the creation button or autosave status at the bottom of this panel.

Generation offers Auto, Terrain, and Sprite styles.
Auto recognizes common terrain descriptions such as water, ground, walls, and lava.
Terrain fills the complete tile with opaque material and requests no enclosing outline.
Water surface highlights belong at the top, with liquid extending to the sides and bottom.
Sprite mode preserves transparency around standalone objects.

Double-click a map cell to place the currently selected asset at that cell's origin.
Use its full dimensions and the same replacement rules as hotkey fills.
Single-click selects a cell and dragging selects an area without placing anything.
Double-click placement autosaves and is one undoable edit.
