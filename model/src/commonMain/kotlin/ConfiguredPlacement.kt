package kdesign

/** Ordinary placements stay direct. Configuration creates a private wrapper only on demand. */
fun Project.configurePlacement(parentId: String, placementId: String, newId: () -> String): Project {
    val parent = blocks.first { it.id == parentId }
    val placement = parent.children.first { it.id == placementId }
    val existing = blocks.find { it.id == placement.assetId && it.anonymous }
    if(existing != null && placementUsageCount(existing.id) == 1) return this
    val wrapperId = newId()
    val typeId = newId()
    val childId = newId()
    val (width,height) = size(placement.assetId)
    val instanceType = BlockType.CompositeType(typeId,"Instance of ${name(placement.assetId)}",
        listOf(assetType(placement.assetId)))
    val wrapper = Block(wrapperId,"Instance of ${name(placement.assetId)}","meta",width,height,
        children=listOf(Placement(childId,placement.assetId,0,0)), anonymous=true,
        typeId=typeId, overrides=assetOverrides(placement.assetId)+placement.overrides)
    fun remap(portal: Portal): Portal {
        var owner = levels.first { it.id == portal.targetLevelId }.rootId
        val path = mutableListOf<String>()
        portal.targetPath.forEachIndexed { index, step ->
            path += step
            if(owner == parentId && step == placementId && index < portal.targetPath.lastIndex) path += childId
            owner = blocks.first { it.id == owner }.children.first { it.id == step }.assetId
        }
        return portal.copy(targetPath=path)
    }
    return copy(types=types+instanceType, blocks=blocks.map { b ->
        b.copy(children=if(b.id==parentId) b.children.map {
            if(it.id==placementId) it.copy(assetId=wrapperId,overrides=emptyMap()) else it
        } else b.children, portals=b.portals.map(::remap))
    } + wrapper).also { it.validate() }
}

data class AssetGroup(val id: String, val name: String, val assets: List<String>)

/** Flatten type inheritance to two palette levels: immediate parent groups and actual tiles. */
fun Project.paletteGroups(parentId: String): List<AssetGroup> {
    val allowed = compositionAssets(parentId).toSet()
    val groups = linkedMapOf<String,MutableList<String>>()
    tiles.filter {it.id in allowed}.forEach { tile ->
        val type = types.first {it.id==tile.typeId}
        val parents = (type as? BlockType.CompositeType)?.parentTypes.orEmpty()
            .ifEmpty {listOf(EMPTY_TYPE_ID)}
        parents.forEach { groups.getOrPut(it) {mutableListOf()}.add(tile.id) }
    }
    return groups.map { (id,assets) -> AssetGroup(id,name(id),assets) } +
        listOfNotNull(blocks.filter {it.id in allowed && !it.anonymous}.map {it.id}
            .takeIf {it.isNotEmpty()}?.let {AssetGroup("palette:meta","Meta blocks",it)})
}
