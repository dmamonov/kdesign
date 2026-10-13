package kdesign

/** Count reusable placement references, without counting expanded level occurrences twice. */
fun Project.placementUsageCount(id: String): Int =
    blocks.sumOf { parent -> parent.children.count { it.assetId == id } }

/** Expand all direct placements in one edit, retaining the reusable source definition. */
fun Project.inlineMetaBlock(id: String, newId: () -> String): Project {
    val source = blocks.first { it.id == id }
    require(source.kind == "meta") { "Only meta blocks can be inlined" }
    require(placementUsageCount(id) > 0) { "This meta block has no placements to inline" }
    require(typeFields(source.typeId).isEmpty() && source.overrides.isEmpty() &&
        blocks.flatMap {it.children}.none {it.assetId==id && it.overrides.isNotEmpty()}) {
        "This meta block has typed properties. Remove them or move them to its children before inlining."
    }
    val replacements = mutableMapOf<Pair<String, String>, Map<String, String>>()
    val expanded = blocks.map { parent ->
        val used = parent.children.map { it.id }.toMutableSet()
        val children = parent.children.flatMap { placement ->
            if (placement.assetId != id) listOf(placement) else {
                val names = source.children.associate { child ->
                    var fresh = newId()
                    while (!used.add(fresh)) fresh = newId()
                    child.id to fresh
                }
                replacements[parent.id to placement.id] = names
                source.children.map { child ->
                    child.copy(id = names.getValue(child.id),
                        x = placement.x + child.x, y = placement.y + child.y)
                }
            }
        }
        val copies = parent.children.count { it.assetId == id }
        parent.copy(children = children,
            portals = parent.portals + List(copies) { source.portals }.flatten())
    }
    fun remap(portal: Portal): Portal {
        var owner = levels.first { it.id == portal.targetLevelId }.rootId
        val path = portal.targetPath
        val result = mutableListOf<String>()
        var i = 0
        while (i < path.size) {
            val step = path[i]
            val child = blocks.first { it.id == owner }.children.first { it.id == step }
            val replacement = replacements[owner to step]
            if (replacement == null) {
                result += step
                owner = child.assetId
            } else if (i + 1 < path.size) {
                val nested = path[++i]
                result += replacement.getValue(nested)
                owner = source.children.first { it.id == nested }.assetId
            } else {
                error("A connection targets this meta-block instance. Retarget it before inlining.")
            }
            i++
        }
        return portal.copy(targetPath = result)
    }
    return copy(blocks = expanded.map { b -> b.copy(portals = b.portals.map(::remap)) })
        .also { it.validate() }
}
