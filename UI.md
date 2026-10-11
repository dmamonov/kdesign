# UI Structure

## Horizontal meny in the top

Designers works with categories of objects, 
user can switch between this categories using flat menu on the top.

```text
[LEVELS] [TILES] [BLOCKS] [Objects] [MECHANICS] [...]
```

## Navigation Tree in the left

Left panel should contain structure of the selected element type.
It can be flat or hierarchical. Potentially with a virtual root.

For example a levels structure:

```Text
[Levels] -- clicking here we go to table concept design.
 - Level 1
   [Rooms]
    - Room 1
    - Room 2
   - Zone 1
   - Zone 2
 - Level 2
   - Room 1
   - Room 2    
```

And object structure:

```text
Object1
 - Animation 1
    - Sprite 1
    - Sprint 2
Object2
 - Animation 1
    - Sprite 1
    - Sprint 2
```


