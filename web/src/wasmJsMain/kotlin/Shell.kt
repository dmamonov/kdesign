package kdesign.web

import kdesign.*

val sections = listOf("Levels", "Tiles", "Meta blocks", "Types")
val sectionMemory = mutableMapOf<String, Pair<String, String>>()
val expandedNodes = mutableSetOf<String>()
var treeQuery = ""
var lastActiveLevel = ""
@JsFun("() => { try { return localStorage.getItem('kdesign.lastActiveLevel') || ''; } catch (_) { return ''; } }")
external fun readLastActiveLevel(): String
@JsFun("(id) => { try { localStorage.setItem('kdesign.lastActiveLevel', id); } catch (_) {} }")
external fun storeLastActiveLevel(id: String)
fun rememberActiveLevel() {
    if(section=="Levels" && project.levels.any {it.id==selected} && lastActiveLevel!=selected) {
        lastActiveLevel=selected
        storeLastActiveLevel(selected)
    }
}
data class Location(val section: String, val selected: String, val tab: String,
                    val usage: String, val path: List<String>, val query: String)
val locations = mutableListOf<Location>()
fun rememberLocation() {
    locations += Location(section, selected, tab, usageId, highlightPath, treeQuery)
    if (locations.size > 100) locations.removeAt(0)
}
fun switchSection(name: String) {
    rememberActiveLevel()
    sectionMemory[section] = selected to tab
    section = name
    val remembered = sectionMemory[name]
    selected = remembered?.first ?: ""
    tab = remembered?.second ?: if(name == "Levels") "World map" else "Library"
    if(name=="Levels") {
        val previous=lastActiveLevel.ifBlank {readLastActiveLevel()}
        selected=project.levels.find {it.id==previous}?.id ?: project.levels.firstOrNull()?.id ?: ""
        tab=if(selected.isNotEmpty()) "Canvas" else "World map"
        highlightPath=emptyList()
    }
    treeQuery = ""
}
fun sectionItems(): List<String> = when(section) {
    "Types" -> project.types.map {it.id}
    "Levels" -> project.levels.map { it.id }
    "Tiles" -> project.tiles.map { it.id }
    "Meta blocks" -> project.blocks.filter { it.kind == "meta" && !it.anonymous }.map { it.id }
    "Objects" -> project.tiles.map { it.id } + project.blocks.map { it.id }
    else -> project.concepts.filter { it.category == section }.map { it.id }
}
fun createButton(compact: Boolean = false): String {
    val type = when { section in categories -> "Concepts"; section == "Objects" -> "Meta blocks"; else -> section }
    val label = when(type) { "Types" -> "type"; "Levels" -> "level"; "Tiles" -> "tile"; "Concepts" -> "concept"; "Meta blocks" -> "meta block"; else -> "block" }
    return button("+ New $label", "new", if(section in categories) section else "", type, if(compact) "primary tiny tree-new" else "primary")
}
fun objectIcon(id: String): String {
    val symbol=when {
        project.tiles.any {it.id==id} -> "▦"
        project.levels.any {it.id==id} -> "⚑"
        project.blocks.any {it.id==id && it.kind=="meta"} -> "▧"
        project.blocks.any {it.id==id} -> "▣"
        else -> when(project.concepts.find {it.id==id}?.category) {
            "Gates" -> "⊣"; "Mechanics" -> "⚙"; "Switches" -> "⏻"
            "Quests" -> "◆"; "Puzzles" -> "◇"; "Inventory objects" -> "⌑"
            else -> "⌖"
        }
    }
    return "<span class='object-icon' role='img' aria-label='${text(project.objectType(id))}' title='${text(project.objectType(id))}'>$symbol</span>"
}
fun usageCaption(id: String): String = if(project.blocks.any {it.id==id})
    " · ${project.placementUsageCount(id)} usages" else ""

fun treeView(): String {
    if(section=="Types") return typeHierarchyView()
    val query = treeQuery.trim().lowercase()
    fun matches(id: String) = query.isEmpty() || project.displayText(project.name(id)).lowercase().contains(query) || id.lowercase().contains(query)
    fun node(id: String, depth: Int, children: String, expandable: Boolean): String {
        val open = id in expandedNodes || query.isNotEmpty()
        return "<div class='tree-row ${if(selected==id) "selected" else ""}' style='--depth:$depth'>" +
            (if(expandable) "<button class='tree-toggle' aria-label='${if(open) "Collapse" else "Expand"} ${text(project.name(id))}' aria-expanded='$open' ${attr("tree-toggle",id)}>${if(open) "▾" else "▸"}</button>" else "<span class='tree-spacer'></span>") +
            button("${objectIcon(id)}<span>${text(project.name(id))}${usageCaption(id)}</span>","select",id,cls="tree-label") + "</div>" + if(open) children else ""
    }
    fun levelNodes(parent: String?, depth: Int): String = project.levels.filter { it.parentId==parent }.joinToString("") { l ->
        val children = levelNodes(l.id, depth+1)
        if(!matches(l.id) && children.isEmpty()) "" else node(l.id, depth, children, project.levels.any {it.parentId==l.id})
    }
    val entries = if(section=="Levels") levelNodes(null,0) else sectionItems().filter(::matches).joinToString("") { id ->
        val b = project.blocks.find {it.id==id}
        val children = b?.children?.joinToString("") { c ->
            "<div class='tree-instance' style='--depth:1'>" + button("${objectIcon(c.assetId)}<span>${text(project.name(c.assetId))}</span><small>${c.x},${c.y}</small>","instance-open",id,c.id) + "</div>"
        } ?: ""
        node(id,0,children,!b?.children.isNullOrEmpty())
    }
    return entries.ifBlank {"<p class='empty small'>${if(query.isEmpty()) "No items yet. Create one to begin." else "No matching items."}</p>"}
}
fun libraryView(): String {
    val items = sectionItems()
    if(section=="Types") return "<section class='panel padded type-hierarchy'>${typeHierarchyView(true)}</section>"
    if(items.isEmpty()) return "<section class='panel empty'><h2>No ${text(section.lowercase())} yet</h2><p>Create an item to start designing.</p>${createButton()}</section>"
    if(section=="Tiles") return "<div class='tile-gallery'>" + items.joinToString("") {id ->
        "<article class='panel tile-card'>" +
            "<button class='tile-card-open' ${attr("select",id)} title='${text(project.name(id))}' aria-label='Open ${text(project.name(id))}'><span class='tile-card-art'>${svg(id)}</span><span class='tile-card-name'>${text(project.name(id))}</span></button>" +
            button("Find usages","usages",id,cls="tile-card-usages quiet") + "</article>"
    } + "</div>"
    return "<div class='library-grid'>" + items.joinToString("") { id ->
        val concept = project.concepts.find {it.id==id}
        "<article class='panel library-card'>" + (if(concept==null) "<div class='library-art'>${svg(id)}</div>" else "<span class='tag'>${text(concept.type)}</span>") +
            "<h3>${rich(project.name(id))}${usageCaption(id)}</h3><p class='muted'>${if(concept!=null) rich(concept.description) else text(project.objectType(id))}</p><div class='concept-actions'>${button("Open", "select",id)}${button("Find usages","usages",id,cls="quiet")}</div></article>"
    } + "</div>"
}
fun conceptWorkspace(): String {
    val c = project.concepts.first {it.id==selected}
    return "<section class='panel padded'><div class='eyebrow'>${text(c.category)} · ${text(c.type)}</div>${field("new-name","Name",c.name)}${field("concept-description","Description",c.description,true)}<p class='muted'>Changes save automatically. References connect this concept to other objects.</p>${button("Change category or type","concept-edit",c.id)}</section>" +
        "<section class='panel padded'><h3>Linked definitions</h3>" + project.blocks.filter {c.id in it.conceptIds}.joinToString("") {button(text(it.name),"open",it.id)}.ifBlank {"<p class='muted'>No linked definitions yet. Attach this concept in a block's properties.</p>"} + "</section>"
}
fun renderShell() {
    if(selected.isNotEmpty() && selected !in project.objectIds()) selected=""
    rememberActiveLevel()
    val level=currentLevel(); val block=currentBlock(); val tile=currentTile()
    val concept=project.concepts.any {it.id==selected}
    val type=project.types.any {it.id==selected}
    val root=selected.isEmpty()
    val tabs=when {root && section=="Levels"->listOf("World map","Table");root->emptyList(); concept || type->listOf("Design"); level!=null->listOf("Canvas","Overview","Design brief","Properties");else->listOf("Editor","Properties")}
    mount("""
      <div class='app-shell'>
      <header class='app-top'><div class='project-brand'><span class='brand-icon'>K</span><div><strong>${rich(project.title)}</strong><small>Adventure designer</small></div>${button("Edit title","new",v="Project",cls="tiny quiet")}</div><div class='header-actions'><span class='save-state' role='status'>${text(statusText())}</span>${button("↶ Undo","undo",cls="quiet")}${if(saveConflict) button("Download draft","export-draft",cls="quiet") else button("Retry / save now","save",cls="quiet")}</div></header>
      <nav class='category-nav' aria-label='Object categories'>${sections.joinToString("") {"<button ${attr("section",it)} aria-current='${if(section==it) "page" else "false"}' class='${if(section==it) "active" else ""}'>${esc(it)}</button>"}}</nav>
      <aside class='navigation-panel' aria-label='Category navigation'><div class='tree-heading'>${button(text(section),"category-root",cls="tree-caption")}${createButton(true)}</div><div class='tree-search'><input id='tree-search' aria-label='Search ${text(section.lowercase())}' value='${esc(treeQuery)}' placeholder='Search'></div><div class='explorer'>${treeView()}</div></aside>
      <main data-view='${esc(section)}:${esc(selected)}:${esc(tab)}'>
      ${if(locations.isNotEmpty() || !root) "<div class='workspace-location'>${if(locations.isNotEmpty()) button("← Back","navigate-back",cls="tiny quiet") else ""}${if(!root) button("Find usages","usages",selected,cls="quiet")+button("Delete","delete-request",selected,cls="quiet danger")+(if(block!=null && level==null && project.placementUsageCount(block.id)>0) button("Inline usages","inline-meta",block.id,cls="quiet") else "") else ""}</div>" else ""}
      ${if(message.isNotBlank()) "<div class='notice' role='status'>${text(message)}${button("Dismiss","dismiss",cls="tiny")}</div>" else ""}
      ${if(tabs.isNotEmpty()) "<div class='tabbar'>${tabs.joinToString("") {button(when(it){"Canvas"->"Map";"Design brief"->"Design";"Editor"->if(block!=null) "Editor (${project.placementUsageCount(block.id)} usages)" else it;else->it},"tab",it,cls=if(tab==it) "active" else "")}}<span>${if(tile!=null) "${tile.size} × ${tile.size} · NES PALETTE" else if(block!=null) "${block.width} × ${block.height} · ${block.children.size} PLACEMENTS" else if(root) "" else "DESIGN CONCEPT"}</span></div>" else ""}
      ${hotkeyConflict?.let { (asset,key) -> "<div class='notice' role='alert'><span>Key $key is assigned to ${text(project.name(project.hotkeys[key] ?: ""))}. Reassign it to ${text(project.name(asset))}?</span><div>${button("Reassign key","hotkey-confirm")}${button("Cancel","hotkey-cancel")}</div></div>" } ?: ""}
      <div class='page-body'>${when {root && section=="Levels"->if(tab=="Table") tableView() else worldMapView();root->libraryView();type->typeWorkspace();concept->conceptWorkspace();tab=="Overview" && level!=null->worldMapView(level);tab=="Design brief" && level!=null->briefView(level);tab=="Properties" && level!=null->levelPropertiesView(level);tab=="Properties"->propertiesView()+typedProperties(selected);tile!=null->tileView(tile);block!=null->canvasView(block,level);else->libraryView()}}</div></main></div>
      ${if(inliningId.isNotBlank()) inlineConfirmationView() else ""}${if(deletingId.isNotBlank()) deletionView() else ""}${if(usageId.isNotBlank()) usagesView() else ""}${if(modal.isNotBlank()) modalView() else ""}
    """.trimIndent())
}

@JsFun("(html) => { document.querySelector('.explorer').innerHTML = html; }")
external fun refreshTree(html: String)

fun portalDestinationOptions(): String = buildString {
    project.levels.forEach { level ->
        fun visit(id: String, path: List<String>, names: List<String>) {
            val block = project.blocks.find {it.id==id && it.kind=="meta"} ?: return
            val encoded = kotlinx.serialization.json.JsonArray((listOf(level.id)+path).map {kotlinx.serialization.json.JsonPrimitive(it)}).toString()
            append("<option value='${esc(encoded)}'>${text((listOf(level.title)+names).joinToString(" / "))}${if(path.isEmpty()) " · level root" else " · " + esc(path.joinToString(" / "))}</option>")
            block.children.forEach {visit(it.assetId,path+it.id,names+project.name(it.assetId))}
        }
        visit(level.rootId,emptyList(),emptyList())
    }
}

fun placementInspector(parent: Block): String {
    val placement=parent.children.find {it.id==highlightPath.firstOrNull()} ?: return ""
    val definition=project.blocks.find {it.id==placement.assetId}
    return "<section class='panel padded selected-instance'><div class='eyebrow'>SELECTED INSTANCE</div><h3>${rich(project.name(placement.assetId))}</h3><p class='muted'>In ${text(parent.name)} · ${placement.x}, ${placement.y}</p><small>${esc(highlightPath.joinToString(" / "))}</small>${definition?.properties?.entries?.joinToString("") {"<p>${rich(it.key)}: ${rich(it.value)}</p>"} ?: ""}${button("Open shared definition","open",placement.assetId,cls="wide")}${button("Find usages","usages",placement.assetId,cls="wide")}</section>"
}
