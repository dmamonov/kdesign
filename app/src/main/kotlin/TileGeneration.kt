package org.example.app

fun isTerrainTile(description: String, mode: String): Boolean {
    require(mode in setOf("auto", "sprite", "terrain")) {"Invalid tile style"}
    if(mode!="auto") return mode=="terrain"
    val text=description.lowercase()
    if(Regex("\\b(character|enemy|monster|creature|bottle|bucket|chest|sword)\\b").containsMatchIn(text)) return false
    return Regex("\\b(water|lava|terrain|ground|grass|dirt|soil|sand|wall|floor|surface|texture|platform)\\b").containsMatchIn(text)
}

fun tileGenerationPrompt(description: String, size: Int, terrain: Boolean): String = """
    Create one pixel-art ${if(terrain) "terrain texture tile" else "object sprite"} for a side-scrolling NES-style game.
    Subject: $description
    Design on a coarse ${size}x$size grid enlarged to this canvas.
    Use large flat color clusters and 4 to 6 coherent colors.
    No gradients, tiny details, text, labels, grids, checkerboard, sprite sheets, or multiple versions.
    ${if(terrain) """
    The entire square is the material itself, viewed straight on, completely opaque and edge to edge.
    No enclosing outline, frame, border, padding, isolated square object, container, bevel, or perspective.
    Material must reach all four image boundaries. No transparent pixels or holes anywhere.
    Left and right edges should join naturally when repeated horizontally.
    For water or lava with waves on top, put the surface highlights in the top rows.
    Fill all rows underneath with the liquid, continuing through the side and bottom boundaries.
    """ else """
    Show exactly one complete centered object, front or slight side view, with a clear silhouette.
    Use a readable dark outline, transparent surroundings, and no cast shadow or scenery.
    Enlarge the object to use the canvas without clipping its silhouette.
    """}
""".trimIndent()
