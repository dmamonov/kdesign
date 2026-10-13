package kdesign.web

import kdesign.*

val collapsedTypes = mutableSetOf<String>()

fun typeHierarchyView(overview: Boolean = false): String {
    val children = project.typeHierarchyChildren()
    val query = treeQuery.trim().lowercase()
    val matches = mutableMapOf<String, Boolean>()
    fun visible(type: BlockType): Boolean = matches.getOrPut(type.id) {
        query.isEmpty() || project.displayText(type.name).lowercase().contains(query) ||
            type.id.lowercase().contains(query) || children[type.id].orEmpty().any { visible(it) }
    }
    fun node(type: BlockType, depth: Int): String {
        if(!visible(type)) return ""
        val descendants = children[type.id].orEmpty().filter { visible(it) }
        val open = type.id !in collapsedTypes || query.isNotEmpty()
        val kind = if(type is BlockType.SimpleType) "Simple property" else "Composite type"
        return "<div class='tree-row ${if(selected==type.id) "selected" else ""}' style='--depth:$depth'>" +
            (if(descendants.isNotEmpty())
                "<button class='tree-toggle' aria-label='${if(open) "Collapse" else "Expand"} ${text(type.name)}' aria-expanded='$open' ${attr("type-tree-toggle",type.id)}>${if(open) "▾" else "▸"}</button>"
            else "<span class='tree-spacer'></span>") +
            button("<span class='object-icon' role='img' aria-label='$kind'>${if(type is BlockType.SimpleType) "◇" else "▧"}</span><span>${text(type.name)}</span>","select",type.id,cls="tree-label") +
            (if(overview) "<small class='type-kind'>$kind</small>${button("Find usages","usages",type.id,cls="quiet tiny")}" else "") +
            "</div>" + if(open) descendants.joinToString("") { node(it,depth+1) } else ""
    }
    return node(BlockType.Empty,0).ifBlank {"<p class='empty small'>No matching types.</p>"}
}

@JsFun("(html) => { const panel = document.querySelector('.type-hierarchy'); if(panel) panel.innerHTML = html; }")
external fun refreshTypeOverview(html: String)
