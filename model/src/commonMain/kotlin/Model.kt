package kdesign

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json { prettyPrint = true; encodeDefaults = true }
val palette = listOf(
    "#666666", "#002A88", "#1412A7", "#3B00A4", "#5C007E", "#6E0040", "#6C0600", "#561D00", "#333500", "#0B4800", "#005200", "#004F08", "#00404D", "#000000", "#000000", "#000000",
    "#ADADAD", "#155FD9", "#4240FF", "#7527FE", "#A01ACC", "#B71E7B", "#B53120", "#994E00", "#6B6D00", "#388700", "#0C9300", "#008F32", "#007C8D", "#000000", "#000000", "#000000",
    "#FFFEFF", "#64B0FF", "#9290FF", "#C676FF", "#F36AFF", "#FE6ECC", "#FE8170", "#EA9E22", "#BCBE00", "#88D800", "#5CE430", "#45E082", "#48CDDE", "#4F4F4F", "#000000", "#000000",
    "#FFFEFF", "#C0DFFF", "#D3D2FF", "#E8C8FF", "#FBC2FF", "#FEC4EA", "#FECCC5", "#F7D8A5", "#E4E594", "#CFEF96", "#BDF4AB", "#B3F3CC", "#B5EBF2", "#B8B8B8", "#000000", "#000000"
)
val categories = linkedMapOf(
    "Gates" to listOf("Lock and key", "Inventory object", "Ability", "Puzzle", "Difficulty"),
    "Mechanics" to listOf("Movement", "Combat", "Interaction"),
    "Switches" to listOf("Puzzle", "Tool based", "Environment based", "Key switches"),
    "Quests" to listOf("Kill", "Fetch", "Delivery", "Collect", "Escort"),
    "Puzzles" to listOf("Logic", "Spatial", "Sequence"),
    "Inventory objects" to listOf("Key", "Tool", "Collectible"),
    "Map objects" to listOf("Landmark", "Entrance", "Checkpoint")
)
val levelFields = listOf("description", "playerMechanics", "playerGoals", "designGoals", "emotionalBaits", "characters", "enemies", "objects")
val fieldLabels = listOf("Description", "Player mechanics", "Player goals", "Design goals", "Emotional baits", "Characters encountered", "Enemies encountered", "Objects encountered")

@Serializable data class Tile(val id: String, val name: String, val size: Int = 16, val pixels: List<Int> = List(size * size) { -1 })
// Positions are pixels in the parent coordinate space. Missing cells are transparent.
@Serializable data class Placement(val id: String, val assetId: String, val x: Int, val y: Int)
// A portal points to an instance inside a level, not merely its reusable definition.
@Serializable data class Portal(val label: String, val targetLevelId: String, val targetPath: List<String> = emptyList())
@Serializable data class Block(val id: String, val name: String, val kind: String = "block", val width: Int = 32, val height: Int = 32, val children: List<Placement> = emptyList(), val properties: Map<String, String> = emptyMap(), val conceptIds: List<String> = emptyList(), val portals: List<Portal> = emptyList())
@Serializable data class Level(val id: String, val title: String, val rootId: String, val parentId: String? = null, val notes: Map<String, String> = emptyMap())
@Serializable data class Concept(val id: String, val name: String, val category: String, val type: String, val description: String = "")
@Serializable data class Project(val version: Int = 1, val title: String = "Moss & Ember", val tiles: List<Tile> = emptyList(), val blocks: List<Block> = emptyList(), val levels: List<Level> = emptyList(), val concepts: List<Concept> = emptyList()) {
    fun name(id: String): String = tiles.find { it.id == id }?.name ?: blocks.find { it.id == id }?.name ?: levels.find { it.id == id }?.title ?: concepts.find { it.id == id }?.name ?: id
    fun size(id: String): Pair<Int, Int> = tiles.find { it.id == id }?.let { it.size to it.size } ?: blocks.find { it.id == id }?.let { it.width to it.height } ?: (16 to 16)
    fun validate() {
        require(version == 1) { "Unsupported project version" }
        val ids = tiles.map { it.id } + blocks.map { it.id } + levels.map { it.id } + concepts.map { it.id }
        require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "Entity IDs must be unique and nonempty" }
        textReferences().forEach { require(it.targetId in ids) { "Missing text reference [[${it.targetId}]] in ${it.field}" } }
        tiles.forEach { require(it.size in listOf(8, 16) && it.pixels.size == it.size * it.size && it.pixels.all { p -> p in -1..63 }) { "Invalid tile ${it.name}" } }
        blocks.forEach { b ->
            require(b.kind in listOf("block", "meta") && b.width in 8..4096 && b.height in 8..4096) { "Invalid block dimensions or kind" }
            require(b.children.map { it.id }.distinct().size == b.children.size && b.children.all { it.id.isNotBlank() }) { "Placement IDs must be unique within a block" }
            b.children.forEach { c ->
                require(if (b.kind == "block") tiles.any { it.id == c.assetId } else blocks.any { it.id == c.assetId }) { "Invalid child type or missing asset in ${b.name}" }
                val (w, h) = size(c.assetId)
                require(c.x >= 0 && c.y >= 0 && c.x + w <= b.width && c.y + h <= b.height) { "Placement outside ${b.name}" }
            }
            require(b.conceptIds.all { id -> concepts.any { it.id == id } }) { "Missing concept" }
            b.portals.forEach { p ->
                require(b.kind == "meta") { "Portals belong to meta blocks" }
                var current = levels.find { it.id == p.targetLevelId }?.rootId ?: error("Missing portal level")
                p.targetPath.forEach { step -> current = blocks.find { it.id == current }?.children?.find { it.id == step }?.assetId ?: error("Missing portal instance") }
                require(blocks.any { it.id == current && it.kind == "meta" }) { "Portal destination must be a meta block" }
            }
        }
        val done = mutableSetOf<String>()
        fun visit(id: String, stack: Set<String>) {
            require(id !in stack) { "Cyclic composition" }
            if (id in done) return
            blocks.find { it.id == id }?.children?.forEach { visit(it.assetId, stack + id) }
            done += id
        }
        blocks.forEach { visit(it.id, emptySet()) }
        levels.forEach { l ->
            require(blocks.any { it.id == l.rootId && it.kind == "meta" }) { "Level root must be a meta block" }
            var parent = l.parentId; val seen = mutableSetOf(l.id)
            while (parent != null) {
                require(seen.add(parent)) { "Cyclic level hierarchy" }
                parent = (levels.find { it.id == parent } ?: error("Missing parent level")).parentId
            }
        }
        concepts.forEach { require(it.type in (categories[it.category] ?: emptyList())) { "Invalid concept category/type" } }
    }
    fun usages(assetId: String): List<Usage> {
        val result = mutableListOf<Usage>()
        blocks.forEach { b -> b.children.filter { it.assetId == assetId }.forEach { result += Usage(null, b.id, listOf(it.id), "Direct placement in ${b.name}") } }
        blocks.filter { assetId in it.conceptIds }.forEach { result += Usage(null, it.id, emptyList(), "Concept attached to ${it.name}") }
        fun walk(level: Level, id: String, path: List<String>, seen: Set<String>) {
            if (id == assetId || blocks.find { it.id == id }?.conceptIds?.contains(assetId) == true) result += Usage(level.id, id, path, (listOf(level.title) + path).joinToString(" / "))
            if (id in seen) return
            blocks.find { it.id == id }?.children?.forEach { walk(level, it.assetId, path + it.id, seen + id) }
        }
        levels.forEach { walk(it, it.rootId, emptyList(), emptySet()) }
        blocks.forEach { b -> b.portals.forEach { p ->
            val l = levels.find { it.id == p.targetLevelId }
            var target = l?.rootId
            p.targetPath.forEach { step -> target = blocks.find { it.id == target }?.children?.find { it.id == step }?.assetId }
            if (target == assetId) result += Usage(null, b.id, emptyList(), "Portal reference: ${b.name} → ${p.label}")
        } }
        textReferences().filter { it.targetId == assetId }.forEach { ref ->
            result += Usage(null, ref.ownerId, emptyList(), "${if(ref.ownerId.isEmpty()) displayText(title) else displayText(name(ref.ownerId))} · ${ref.field}", ref.field)
        }
        return result
    }
}
data class Usage(val levelId: String?, val ownerId: String, val path: List<String>, val label: String, val field: String? = null)
