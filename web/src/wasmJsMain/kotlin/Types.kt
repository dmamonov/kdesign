package kdesign.web

import kdesign.*

fun typeSelect(id: String, label: String, selectedId: String, options: List<Pair<String,String>>) =
    "<label class='field'>$label<select id='${esc(id)}'>" + options.joinToString("") { (key,name) ->
        "<option value='${esc(key)}' ${if(key==selectedId) "selected" else ""}>${text(name)}</option>"
    } + "</select></label>"

fun tileParentPanel(tile: Tile): String {
    val type=project.types.first {it.id==tile.typeId}
    if(type !is BlockType.CompositeType || type.id==EMPTY_TYPE_ID) {
        return "<section class='panel padded'>" + typeSelect("asset-type","Type",tile.typeId,
            project.types.filter { candidate -> project.blocks.none {
                it.anonymous && it.typeId==candidate.id
            } }.map {it.id to it.name}) + "</section>"
    }
    fun descendsFrom(id: String): Boolean = id==type.id ||
        (project.types.find {it.id==id} as? BlockType.CompositeType)?.parentTypes
            ?.any {descendsFrom(it)} == true
    val candidates=project.types.filter { candidate ->
        !descendsFrom(candidate.id) && project.blocks.none {it.anonymous && it.typeId==candidate.id}
    }
    val parents=type.parentTypes
    return "<section class='panel padded'>" +
        (parents.indices.toList()+parents.size).joinToString("") { index ->
            val current=parents.getOrNull(index).orEmpty()
            typeSelect("tile-parent:$index",if(index==0) "Parent type" else if(current.isEmpty())
                "Add parent type" else "Parent type ${index+1}",current,
                listOf("" to if(current.isEmpty()) "Choose a type" else "Remove parent") +
                    candidates.filter {it.id==current || it.id !in parents}.map {it.id to it.name})
        } + "</section>"
}

fun primitiveInput(id: String, label: String, value: PrimitiveValue): String = when(value) {
    is PrimitiveValue.EnumValue -> typeSelect(id,label,value.value,
        value.primitiveType.options.map {it to it})
    is PrimitiveValue.ReferenceValue -> typeSelect(id,label,value.objectId ?: "",
        listOf("" to "None") + project.objectIds().map {it to project.name(it)})
    else -> field(id,label,value.textValue())
}

fun typeWorkspace(): String {
    val type = project.types.first {it.id==selected}
    if(type.id==EMPTY_TYPE_ID) return "<section class='panel padded'>Empty is the shared type with no properties.</section>"
    return "<section class='panel padded'>${field("type-name","Name",type.name)}" + when(type) {
        is BlockType.SimpleType -> {
            val kind = when(type.defaultValue) {
                is PrimitiveValue.StringValue -> "String"
                is PrimitiveValue.IntegerValue -> "Int"
                is PrimitiveValue.EnumValue -> "Enum"
                is PrimitiveValue.ReferenceValue -> "Object reference"
            }
            typeSelect("type-primitive","Value type",kind,
                listOf("String","Int","Enum","Object reference").map {it to it}) +
                (if(type.defaultValue is PrimitiveValue.EnumValue)
                    field("type-options","Options · one per line",(type.defaultValue as PrimitiveValue.EnumValue).primitiveType.options.joinToString("\n"),true) else "") +
                primitiveInput("type-default","Default",type.defaultValue)
        }
        is BlockType.CompositeType -> "<h3>Parents and properties</h3>" +
            type.parentTypes.joinToString("") { id ->
                "<div>${button(text(project.name(id)),"select",id)}${button("Remove","type-parent-remove",id,cls="quiet")}</div>"
            } + typeSelect("type-parent","Add parent or property","",
                listOf("" to "Choose a type") + project.types.filter {
                    it.id!=type.id && it.id !in type.parentTypes
                }.map {it.id to it.name}) + "<h3>Resolved defaults</h3>" +
            project.typeFields(type.id).joinToString("") {
                "<p>${text(it.name)}: ${text(it.defaultValue.textValue())}</p>"
            }
    } + "</section>"
}

fun typedProperties(assetId: String, placement: Placement? = null): String {
    val typeId = project.assetType(assetId)
    val values = project.effectiveValues(assetId,placement)
    val overrides = placement?.overrides ?: project.assetOverrides(assetId)
    val prefix = if(placement==null) "override:" else "instance-override:${placement.id}:"
    return "<section class='panel padded'><h3>${if(placement==null) "Type and values" else "Placement values"}</h3>" +
        (if(placement==null) typeSelect("asset-type","Type",typeId,
            project.types.map {it.id to it.name}) else "<p>${text(project.name(assetId))} · ${text(project.name(typeId))}</p>") +
        button("Edit type","select",typeId,cls="quiet") +
        project.typeFields(typeId).joinToString("") { property ->
            primitiveInput(prefix+property.id,text(property.name),values.getValue(property.id)) +
                if(property.id in overrides) button("Reset to inherited","type-reset",property.id,
                    placement?.id ?: "",cls="quiet tiny") else "<small>Inherited</small>"
        } + "</section>"
}

fun Project.editOverrides(asset: String, field: String, value: PrimitiveValue?, placementId: String = ""): Project {
    fun update(values: Map<String,PrimitiveValue>) = if(value==null) values-field else values+(field to value)
    return if(placementId.isNotEmpty()) copy(blocks=blocks.map { b ->
        if(b.id!=currentBlock()?.id) b else b.copy(children=b.children.map {
            if(it.id==placementId) it.copy(overrides=update(it.overrides)) else it
        })
    }) else copy(tiles=tiles.map {if(it.id==asset) it.copy(overrides=update(it.overrides)) else it},
        blocks=blocks.map {if(it.id==asset) it.copy(overrides=update(it.overrides)) else it})
}

fun editTypedField(id: String, content: String): Boolean {
    if(modal.isNotEmpty()) return false
    if(!id.startsWith("tile-parent:") && !id.startsWith("type-") && id!="asset-type" && !id.startsWith("override:") &&
        !id.startsWith("instance-override:")) return false
    try {
        change { p -> when {
            id.startsWith("tile-parent:") -> {
                val tile=p.tiles.first {it.id==selected}
                val index=id.substringAfter(':').toInt()
                p.copy(types=p.types.map { type ->
                    if(type.id!=tile.typeId) type else {
                        require(type is BlockType.CompositeType && type.id!=EMPTY_TYPE_ID)
                        val parents=type.parentTypes.toMutableList()
                        if(index<parents.size) {
                            if(content.isEmpty()) parents.removeAt(index) else parents[index]=content
                        } else if(content.isNotEmpty()) parents.add(content)
                        type.copy(parentTypes=parents)
                    }
                })
            }
            id=="asset-type" -> p.copy(
                tiles=p.tiles.map {if(it.id==selected) it.copy(typeId=content) else it},
                blocks=p.blocks.map {if(it.id==selected) it.copy(typeId=content) else it})
            id.startsWith("override:") || id.startsWith("instance-override:") -> {
                val instance = if(id.startsWith("instance-override:")) currentBlock()!!.children.first { id.startsWith("instance-override:${it.id}:") }.id else ""
                val field = if(instance.isEmpty()) id.removePrefix("override:") else id.removePrefix("instance-override:$instance:")
                val property = p.types.filterIsInstance<BlockType.SimpleType>().first {it.id==field}
                p.editOverrides(selected,field,property.defaultValue.primitiveType.parseValue(content),instance)
            }
            else -> p.copy(types=p.types.map { type -> if(type.id!=selected) type else {
                require(type.id!=EMPTY_TYPE_ID) {"Empty is immutable"}
                when(type) {
                    is BlockType.SimpleType -> when(id) {
                        "type-name" -> type.copy(name=content)
                        "type-default" -> type.copy(defaultValue=type.defaultValue.primitiveType.parseValue(content))
                        "type-options" -> {
                            val options=content.lines().filter {it.isNotBlank()}
                            val old=type.defaultValue as PrimitiveValue.EnumValue
                            type.copy(defaultValue=PrimitiveValue.EnumValue(
                                old.value.takeIf {it in options} ?: options.firstOrNull().orEmpty(),
                                PrimitiveType.PrimitiveEnum(options)))
                        }
                        else -> type.copy(defaultValue=when(content) {
                            "Int" -> PrimitiveValue.IntegerValue(0)
                            "Enum" -> PrimitiveValue.EnumValue("Option",PrimitiveType.PrimitiveEnum(listOf("Option")))
                            "Object reference" -> PrimitiveValue.ReferenceValue()
                            else -> PrimitiveValue.StringValue("")
                        })
                    }
                    is BlockType.CompositeType -> if(id=="type-name") type.copy(name=content)
                        else type.copy(parentTypes=type.parentTypes+content)
                }
            } })
        } }
        tableErrors.remove(id);tableCellState(id,"");publishReferenceObjects()
        if(id.startsWith("tile-parent:") || id in listOf("asset-type","type-primitive","type-parent")) render()
    } catch(e: Exception) {tableErrors.add(id);tableCellState(id,e.message ?: "Invalid type value")}
    updateSaveStatus()
    return true
}

fun instanceTypePanel(parent: Block): String {
    val instances = typeSelect("instance-choice","Placement","",
        listOf("" to "Choose a placement to edit its values") + parent.children.map {
            it.id to "${project.name(it.assetId)} · ${it.x},${it.y} · ${it.id}"
        }).replace("id='instance-choice'", "id='instance-choice' data-change='instance-type-select'")
    val placement = parent.children.find {it.id==highlightPath.firstOrNull()}
    return "<section class='panel padded'>$instances</section>" +
        (placement?.let {typedProperties(it.assetId,it)} ?: "")
}
