package kdesign

/** Parentless definitions are displayed under Empty without changing stored inheritance. */
fun Project.typeHierarchyChildren(): Map<String, List<BlockType>> {
    val anonymous = blocks.filter { it.anonymous }.map { it.typeId }.toSet() - EMPTY_TYPE_ID
    fun visibleParents(id: String): List<String> {
        if(id !in anonymous) return listOf(id)
        val parents = (types.find { it.id == id } as? BlockType.CompositeType)?.parentTypes.orEmpty()
        return parents.ifEmpty { listOf(EMPTY_TYPE_ID) }.flatMap(::visibleParents)
    }
    val children = linkedMapOf<String, MutableList<BlockType>>()
    types.filter { it.id != EMPTY_TYPE_ID && it.id !in anonymous }.forEach { type ->
        val parents = (type as? BlockType.CompositeType)?.parentTypes.orEmpty()
            .ifEmpty { listOf(EMPTY_TYPE_ID) }
        parents.flatMap(::visibleParents).distinct().forEach { parent ->
            children.getOrPut(parent) { mutableListOf() }.add(type)
        }
    }
    return children
}
