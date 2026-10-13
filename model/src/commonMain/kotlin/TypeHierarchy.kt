package kdesign

/** Parentless definitions are displayed under Empty without changing stored inheritance. */
fun Project.typeHierarchyChildren(): Map<String, List<BlockType>> {
    val children = linkedMapOf<String, MutableList<BlockType>>()
    types.filter { it.id != EMPTY_TYPE_ID }.forEach { type ->
        val parents = (type as? BlockType.CompositeType)?.parentTypes.orEmpty()
            .ifEmpty { listOf(EMPTY_TYPE_ID) }
        parents.distinct().forEach { parent -> children.getOrPut(parent) { mutableListOf() }.add(type) }
    }
    return children
}
