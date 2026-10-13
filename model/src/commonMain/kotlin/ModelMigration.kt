package kdesign

import kotlinx.serialization.encodeToString

/** Collapse redundant tile wrappers; retain authored compositions and semantics as meta blocks. */
fun Project.simplifyBlocks(): Project {
    validate()
    if (version == 2) return this
    val aliases = blocks.filter { b ->
        val child = b.children.singleOrNull()
        val tile = tiles.find { it.id == child?.assetId }
        b.kind == "block" && tile != null && child != null &&
            child.x == 0 && child.y == 0 && b.width == tile.size && b.height == tile.size &&
            b.name == tile.name && b.properties.isEmpty() && b.conceptIds.isEmpty() &&
            b.portals.isEmpty()
    }.associate { it.id to it.children.single().assetId }
    val assigned = mutableSetOf<String>()
    val migrated = copy(
        version = 2,
        blocks = blocks.filterNot { it.id in aliases }.map { b ->
            b.copy(kind = "meta", children = b.children.map {
                it.copy(assetId = aliases[it.assetId] ?: it.assetId)
            })
        },
        hotkeys = hotkeys.mapValues { aliases[it.value] ?: it.value }
            .filter { assigned.add(it.value) }
    )
    // References can occur in every textual field, including property keys and titles.
    var encoded = json.encodeToString(migrated)
    aliases.forEach { (old, tile) ->
        val from = json.encodeToString("[[$old]]").removeSurrounding("\"")
        val to = json.encodeToString("[[$tile]]").removeSurrounding("\"")
        encoded = encoded.replace(from, to)
    }
    return json.decodeFromString<Project>(encoded).also { it.validate() }
}
