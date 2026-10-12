package kdesign

/** Delete a definition and detach its incoming links as one undoable project change. */
fun Project.deleteObject(id: String): Project {
    require(id in objectIds()) { "Object no longer exists" }
    require(levels.none {it.rootId==id}) {
        "This meta block is a level root. Delete those levels or change their root before deleting it."
    }
    val removed=setOf(id) + if(tiles.any {it.id==id}) blocks.filter {
        it.kind=="block" && it.children.any {child->child.assetId==id}
    }.map {it.id} else emptyList()
    fun detach(text: String)=referencePattern.replace(text) {
        if(it.groupValues[1] in removed) displayText(name(it.groupValues[1])) else it.value
    }
    val deletedLevel=levels.find {it.id==id}
    val candidate=copy(
        title=detach(title),
        tiles=tiles.filterNot {it.id==id}.map {it.copy(name=detach(it.name))},
        blocks=blocks.filterNot {it.id in removed}.map { b ->
            val properties=b.properties.entries.map {detach(it.key) to detach(it.value)}
            require(properties.map {it.first}.distinct().size==properties.size) {"Deleting this reference would create duplicate property names in ${b.name}"}
            b.copy(name=detach(b.name),children=b.children.filterNot {it.assetId in removed},
                conceptIds=b.conceptIds.filterNot {it==id},properties=properties.toMap(),
                portals=b.portals.map {it.copy(label=detach(it.label))})
        },
        levels=levels.filterNot {it.id==id}.map {l -> l.copy(title=detach(l.title),
            parentId=if(l.parentId==id) deletedLevel?.parentId else l.parentId,
            notes=l.notes.mapValues {detach(it.value)})},
        concepts=concepts.filterNot {it.id==id}.map {it.copy(name=detach(it.name),description=detach(it.description))},
        hotkeys=hotkeys.filterValues {it !in removed}
    )
    fun destinationExists(portal: Portal): Boolean {
        var target=candidate.levels.find {it.id==portal.targetLevelId}?.rootId ?: return false
        for(step in portal.targetPath) {
            target=candidate.blocks.find {it.id==target}?.children?.find {it.id==step}?.assetId ?: return false
        }
        return candidate.blocks.any {it.id==target && it.kind=="meta"}
    }
    return candidate.copy(blocks=candidate.blocks.map {b ->
        b.copy(portals=b.portals.filter(::destinationExists))
    }).also {it.validate()}
}

/** Remove unused empty graphic definitions, retaining meta blocks as design containers. */
fun Project.cleanupEmptyBlocks(): Project {
    val ids=blocks.filter {it.kind=="block" && it.children.isEmpty()}.map {it.id}
    return ids.fold(this) {project,id->project.deleteObject(id)}
}
