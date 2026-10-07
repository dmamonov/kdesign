package kdesign

/** Stored as stable IDs in ordinary strings, including titles: [[block-mushroom]]. */
val referencePattern = Regex("\\[\\[([^\\[\\]\\r\\n]+)\\]\\]")
data class TextReference(val ownerId: String, val field: String, val targetId: String, val offset: Int)
fun Project.objectIds(): List<String> = tiles.map { it.id } + blocks.map { it.id } + levels.map { it.id } + concepts.map { it.id }
fun Project.objectType(id: String): String = when {
    tiles.any { it.id == id } -> "Tile"
    blocks.any { it.id == id && it.kind == "block" } -> "Block"
    blocks.any { it.id == id } -> "Meta block"
    levels.any { it.id == id } -> "Level"
    concepts.any { it.id == id } -> "Concept"
    else -> "Missing object"
}
/** Name references may form cycles. Bound expansion and show the stable ID at a cycle. */
fun Project.displayText(text: String, seen: Set<String> = emptySet()): String = referencePattern.replace(text) { match ->
    val id = match.groupValues[1]
    when {
        id !in objectIds() -> "Missing: $id"
        id in seen || seen.size >= 12 -> id
        else -> displayText(name(id), seen + id)
    }
}
fun Project.textReferences(): List<TextReference> = buildList {
    fun scan(owner: String, field: String, text: String) {
        referencePattern.findAll(text).forEach { add(TextReference(owner, field, it.groupValues[1], it.range.first)) }
    }
    scan("", "Project title", title)
    tiles.forEach { scan(it.id, "Name", it.name) }
    blocks.forEach { b ->
        scan(b.id, "Name", b.name)
        b.properties.forEach { (key, value) -> scan(b.id, "Property key: $key", key); scan(b.id, "Property: $key", value) }
        b.portals.forEachIndexed { i, p -> scan(b.id, "Portal ${i + 1} label", p.label) }
    }
    levels.forEach { l ->
        scan(l.id, "Title", l.title)
        l.notes.forEach { (key, value) -> scan(l.id, fieldLabels.getOrNull(levelFields.indexOf(key)) ?: key, value) }
    }
    concepts.forEach { scan(it.id, "Name", it.name); scan(it.id, "Description", it.description) }
}
