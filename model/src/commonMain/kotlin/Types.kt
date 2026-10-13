package kdesign

import kotlinx.serialization.Serializable

const val EMPTY_TYPE_ID = "type:empty"

@Serializable sealed interface PrimitiveType {
    @Serializable data object PrimitiveString : PrimitiveType
    @Serializable data object PrimitiveInteger : PrimitiveType
    @Serializable data class PrimitiveEnum(val options: List<String>) : PrimitiveType
    @Serializable data object ObjectReference : PrimitiveType
}

@Serializable sealed interface PrimitiveValue {
    val primitiveType: PrimitiveType
    @Serializable data class StringValue(val value: String) : PrimitiveValue {
        override val primitiveType: PrimitiveType get() = PrimitiveType.PrimitiveString
    }
    @Serializable data class IntegerValue(val value: Int) : PrimitiveValue {
        override val primitiveType: PrimitiveType get() = PrimitiveType.PrimitiveInteger
    }
    @Serializable data class EnumValue(
        val value: String,
        override val primitiveType: PrimitiveType.PrimitiveEnum,
    ) : PrimitiveValue
    @Serializable data class ReferenceValue(val objectId: String? = null) : PrimitiveValue {
        override val primitiveType: PrimitiveType get() = PrimitiveType.ObjectReference
    }
}

@Serializable sealed interface BlockType {
    val id: String
    val name: String
    @Serializable data class SimpleType(
        override val id: String,
        override val name: String,
        val defaultValue: PrimitiveValue,
    ) : BlockType
    @Serializable data class CompositeType(
        override val id: String,
        override val name: String,
        val parentTypes: List<String> = emptyList(),
    ) : BlockType
    companion object { val Empty = CompositeType(EMPTY_TYPE_ID, "Empty") }
}

fun Project.typeFields(typeId: String): List<BlockType.SimpleType> {
    val fields = linkedMapOf<String, BlockType.SimpleType>()
    fun visit(id: String, path: Set<String>) {
        require(id !in path) { "Cyclic type inheritance" }
        when(val type = types.find { it.id == id } ?: error("Missing type: $id")) {
            is BlockType.SimpleType -> fields[id] = type
            is BlockType.CompositeType -> type.parentTypes.forEach { visit(it, path + id) }
        }
    }
    visit(typeId, emptySet())
    require(fields.values.map { it.name }.distinct().size == fields.size) {
        "Different properties have the same name in this type"
    }
    return fields.values.toList()
}
fun Project.assetType(id: String): String = tiles.find { it.id == id }?.typeId
    ?: blocks.first { it.id == id }.typeId
fun Project.assetOverrides(id: String): Map<String, PrimitiveValue> =
    tiles.find { it.id == id }?.overrides ?: blocks.first { it.id == id }.overrides
fun Project.effectiveValues(id: String, placement: Placement? = null): Map<String, PrimitiveValue> {
    require(placement == null || placement.assetId == id)
    return typeFields(assetType(id)).associate { it.id to it.defaultValue } +
        assetOverrides(id) + (placement?.overrides ?: emptyMap())
}
fun PrimitiveValue.textValue(): String = when(this) {
    is PrimitiveValue.StringValue -> value
    is PrimitiveValue.IntegerValue -> value.toString()
    is PrimitiveValue.EnumValue -> value
    is PrimitiveValue.ReferenceValue -> objectId ?: ""
}
fun PrimitiveType.parseValue(text: String): PrimitiveValue = when(this) {
    PrimitiveType.PrimitiveString -> PrimitiveValue.StringValue(text)
    PrimitiveType.PrimitiveInteger -> PrimitiveValue.IntegerValue(text.toIntOrNull()
        ?: error("Enter a whole number within the Int range"))
    is PrimitiveType.PrimitiveEnum -> PrimitiveValue.EnumValue(text, this).also {
        require(text in options) { "Choose an enum option" }
    }
    PrimitiveType.ObjectReference -> PrimitiveValue.ReferenceValue(text.ifBlank { null })
}
fun Project.validateTypes() {
    require(types.map { it.id }.distinct().size == types.size) { "Duplicate type IDs" }
    require(types.find { it.id == EMPTY_TYPE_ID } == BlockType.Empty) { "Empty type is immutable" }
    fun check(value: PrimitiveValue) {
        if(value is PrimitiveValue.EnumValue) {
            val options = value.primitiveType.options
            require(options.isNotEmpty() && options.all { it.isNotBlank() } &&
                options.distinct().size == options.size && value.value in options) { "Invalid enum value" }
        }
        if(value is PrimitiveValue.ReferenceValue) require(value.objectId == null ||
            value.objectId in objectIds()) { "Missing referenced object" }
    }
    types.forEach {
        require(it.id.isNotBlank() && it.name.isNotBlank()) { "Types need an ID and name" }
        typeFields(it.id)
        if(it is BlockType.SimpleType) check(it.defaultValue)
        if(it is BlockType.CompositeType) require(it.parentTypes.distinct().size == it.parentTypes.size)
    }
    fun overrides(type: String, values: Map<String, PrimitiveValue>) {
        val fields = typeFields(type).associateBy { it.id }
        values.forEach { (id, value) ->
            require(fields[id]?.defaultValue?.primitiveType == value.primitiveType) {
                "Override must match an inherited property's type"
            }
            check(value)
        }
    }
    tiles.forEach { overrides(it.typeId, it.overrides) }
    blocks.forEach { b ->
        overrides(b.typeId, b.overrides)
        b.children.forEach { overrides(assetType(it.assetId), it.overrides) }
    }
}

/** Give each untyped graphic a named composite, preserving legacy properties as string defaults. */
fun Project.withBlockTypes(only: Set<String>? = null): Project {
    val definitions = types.toMutableList()
    val used = (objectIds() + types.map { it.id }).toMutableSet()
    fun fresh(stem: String): String {
        var id = stem
        var suffix = 1
        while(!used.add(id)) id = "$stem:${suffix++}"
        return id
    }
    fun assigned(id: String, name: String, current: String, properties: Map<String,String>): String {
        if(current != EMPTY_TYPE_ID || (only != null && id !in only)) return current
        val parents = properties.map { (key, value) ->
            val fieldId = fresh("property:$id")
            definitions += BlockType.SimpleType(fieldId, key, PrimitiveValue.StringValue(value))
            fieldId
        }
        val typeId = fresh("type:$id")
        definitions += BlockType.CompositeType(typeId, name, parents)
        return typeId
    }
    val newTiles = tiles.map { it.copy(typeId=assigned(it.id,it.name,it.typeId,emptyMap())) }
    val newBlocks = blocks.map { it.copy(typeId=assigned(it.id,it.name,it.typeId,it.properties),
        properties=if(only == null || it.id in only) emptyMap() else it.properties) }
    return copy(version=3, types=definitions, tiles=newTiles, blocks=newBlocks)
}

fun Project.mapTypedValues(transform: (PrimitiveValue) -> PrimitiveValue): Project = copy(
    types=types.map { if(it is BlockType.SimpleType) it.copy(defaultValue=transform(it.defaultValue)) else it },
    tiles=tiles.map { it.copy(overrides=it.overrides.mapValues { transform(it.value) }) },
    blocks=blocks.map { b -> b.copy(overrides=b.overrides.mapValues { transform(it.value) },
        children=b.children.map { c -> c.copy(overrides=c.overrides.mapValues { transform(it.value) }) }) }
)
fun Project.typeUsages(id: String): List<Usage> = buildList {
    types.filterIsInstance<BlockType.CompositeType>().filter {id in it.parentTypes}.forEach {
        add(Usage(null,it.id,emptyList(),"Inherited by ${it.name}","Type parents"))
    }
    (tiles.map {it.id to it.typeId} + blocks.map {it.id to it.typeId}).filter {it.second==id}.forEach {
        add(Usage(null,it.first,emptyList(),"Type of ${name(it.first)}","Type"))
    }
    fun scan(owner: String, values: Map<String,PrimitiveValue>, path: List<String> = emptyList()) {
        values.forEach { (field,value) -> if(value is PrimitiveValue.ReferenceValue && value.objectId==id)
            add(Usage(null,owner,path,"${name(owner)} / ${name(field)}",if(path.isEmpty()) "Typed reference" else "Placement:${path.first()}")) }
    }
    types.filterIsInstance<BlockType.SimpleType>().forEach {scan(it.id,mapOf(it.id to it.defaultValue))}
    tiles.forEach {scan(it.id,it.overrides)}
    blocks.forEach {b -> scan(b.id,b.overrides);b.children.forEach {scan(b.id,it.overrides,listOf(it.id))}}
}
