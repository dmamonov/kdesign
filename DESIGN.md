# 2D action-adventure game designer

## Purpose

Build a high-level designer for side-scrolling 2D action-adventure games.
Support both conceptual game design and basic graphics editing.
Connect reusable graphics, level layouts, and design concepts in one model.

This document describes the requested design, based on the requirements in this conversation.
It does not describe or depend on the current implementation.
Details not established by those requirements remain open design decisions.

## Application and storage

Write the application in Kotlin and run it in the browser through WebAssembly.

Store project data in the filesystem under a `data` folder.
Use a simple server to expose basic filesystem operations:

- Read a file.
- Write a file.
- List a directory.

The file layout and serialization format remain to be defined.

All changes should autosave.
This includes graphics edits, compositions, text fields, level table edits, and hotkey assignments.
Saving should not require a separate manual action after each edit.

## Graphics hierarchy

The graphics model has three main layers:

- **Tile** - Smallest graphic primitive and directly placeable 1×1 block
- **Meta block** - Sparse combination of tiles or other meta blocks
- **Level** - Independent space containing a top-level meta block

### Tiles

A tile is the smallest graphic primitive.
Support 8×8 and 16×16 tile sizes as defaults.
Use a Nintendo Entertainment System color palette by default.
Allow transparent pixels.

Provide a basic editor for drawing tiles.
Also allow a tile to be generated from a text description through the OpenAI API.
Each tile is automatically a 1×1 block, using its native 8×8 or 16×16 pixel footprint.
It has one identity for drawing, placement, hotkeys, and usage navigation.
There is no separate basic block definition or Blocks section.

### Meta blocks

A meta block combines tiles and other meta blocks into a hierarchical, sparse composition.
Some positions may be omitted.
Meta blocks carry semantic properties, linked concepts, and connections.
Tile edits update all direct and nested usages.
Deleting a tile removes its placements while retaining containing meta blocks.

Meta blocks may hold identity references to other meta blocks.
For example, a door into a cave can point to a cave entrance in a sublevel.
Such a reference expresses a connection between objects, separate from visual containment.

The model must distinguish a reusable object's identity from its individual uses.
How references select a particular use when an object appears multiple times remains to be defined.

### Levels and sublevels

Each level contains a top-level meta block.
Levels occupy separate spaces.
A level can have sublevels.

The design must represent both the level hierarchy and connections between spaces.
A cave can be a sublevel of an outdoor level while retaining its own space.
A door reference can connect the outdoor level to the cave entrance.

## Identity and backtracking

Backtracking is a core model requirement.
It must be considered whenever a new kind of object or reference is introduced.

Given a definition, the designer must be able to navigate to all of its usages across all levels.
For example, selecting a mushroom block should reveal every place that uses it.
This must include uses nested inside other blocks or meta blocks and uses within sublevels.
Repeated uses must remain distinguishable.

The model should support answering these questions:

- Where is this object used directly?
- In which levels does it appear through nested compositions?
- Which other objects refer to it?
- How can the designer navigate from the definition to a particular usage?

The identity scheme and the mechanism for finding usages remain design decisions.
Any chosen approach must preserve reliable navigation as the project is edited.

## References in text

Allow references to other objects in every authored text field, including titles.
This applies to descriptions, design notes, semantic metadata, and other free-text content.

References should connect text to object identity.
They should support navigation to the referenced object and backtracing to the referring text.
A reference in a title must be treated as a reference, just as one in a description would be.

The reference syntax, insertion UI, and display behavior remain to be defined.
Renaming an object should preserve the connection to its references.

## Conceptual categories

The designer must support the following conceptual categories.
They describe game design independently of the graphics hierarchy.
The exact relationship between concepts and graphic objects remains to be defined.

### Gates

- Lock and key.
- Inventory object.
- Ability.
- Puzzle.
- Difficulty.

### Mechanics

Support mechanics as a conceptual category.
No specific subcategories have been requested yet.

### Switches

- Puzzle.
- Tool based.
- Environment based.
- Key switches.

### Quests

- Kill.
- Fetch.
- Delivery.
- Collect.
- Escort.

### Puzzles

Support puzzles as a conceptual category.
No specific subcategories have been requested yet.

### Inventory objects

Support inventory objects as a conceptual category.
No specific subcategories have been requested yet.

### Map objects

Support map objects as a conceptual category.
No specific subcategories have been requested yet.

## Level design information

Each level defines the following information:

| Field                  | Content                                        |
|------------------------|------------------------------------------------|
| Title                  | The level's title                              |
| Description            | A description of the level                     |
| Player mechanics       | Mechanics available to or used by the player   |
| Player goals           | What the player aims to accomplish             |
| Design goals           | What the designer intends the level to achieve |
| Emotional baits        | Intended emotional draws for the player        |
| Characters encountered | Characters encountered in the level            |
| Enemies encountered    | Enemies encountered in the level               |
| Objects encountered    | Objects encountered in the level               |

Display this information in a level overview table.
Use one row per level and columns for the level's design fields.
Allow direct editing in the table.
Table edits must autosave and support object references, including in the title column.

## Efficient map editing

The map editor should make repeated placement and larger layout changes efficient.
Support individual block selection as well as operations over an area.

### Assignable block hotkeys

Allow the designer to assign hotkeys to blocks.
An assigned hotkey should select its block for placement without returning to the block library.
Hotkey assignment changes must autosave.

The available keys, assignment interface, and conflict behavior remain to be defined.

### Area selection and filling

Allow the designer to select an area of the map.
Allow the selected area to be filled with a chosen block.
The resulting map changes must autosave.

Selection shape, grid alignment, and treatment of existing content remain to be defined.
The fill behavior must also define how to handle blocks that do not fit at an area's edge.

### Around-cursor block picker

Provide a block picker around the cursor.
It should allow the designer to choose a block near the point where they are working.
This reduces travel between the map and a separate block library.

The opening gesture, arrangement, and handling of large block collections remain to be defined.

## OpenAI integration

Use the OpenAI API to generate tiles from descriptions.
Read the API key from the `OPENAI_API_KEY` environment variable.
The server should perform API requests so the key does not need to be delivered to the browser.

Provide generation access from the tile workflow and from the block workflow.
Generated graphics must fit the tile model, including its size, palette, and transparency support.
Adding generated graphics to the project must participate in autosave.

The model choice, request format, and conversion into tile pixels remain to be defined.

## Initial scope

The starting design brings together:

- A Kotlin application running in the browser through WebAssembly.
- Filesystem storage under `data`, accessed through a simple server API.
- Tile drawing and description-based generation.
- Directly placeable tiles, hierarchical meta blocks, levels, and sublevels.
- Identity references and navigation to usages across levels.
- References to objects in all authored text fields.
- The requested conceptual categories and level design information.
- An editable level overview table.
- Automatic saving of all changes.
- Block hotkeys, area selection and filling, and an around-cursor block picker.

Further implementation choices should support these requirements without weakening backtracing.
