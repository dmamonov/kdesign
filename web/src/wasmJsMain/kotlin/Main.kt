package kdesign.web

import kdesign.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@JsFun("(html) => window.designer.mount(html)") external fun mount(html: String)
@JsFun("(id) => window.designer.value(id)") external fun value(id: String): String
@JsFun("(id, color) => window.designer.pixel(id, color)") external fun pixel(id: String, color: String)
@JsFun("(method, url, body, done) => window.designer.request(method, url, body, done)") external fun request(method: String, url: String, body: String, done: (Boolean, String) -> Unit)
@JsFun("(action) => window.designer.bind(action)") external fun bind(action: (String, String, String) -> Unit)
@JsFun("(dirty) => { window.designer.dirty = dirty; }") external fun setDirty(dirty: Boolean)
@JsFun("(message) => window.designer.error(message)") external fun showError(message: String)
@JsFun("() => crypto.randomUUID()") external fun uuid(): String

var project = Project()
var section = "Levels"
var selected = ""
var tab = "Canvas"
var snap = 16
var brush = 22
var assetBrush = ""
var eraser = false
var message = ""
var dirty = false
var saving = false
var generating = false
var generationAvailable = false
var loaded = false
var revision = 0
var modal = ""
var usageId = ""
var highlightPath = emptyList<String>()
val tableErrors = mutableSetOf<String>()
val history = mutableListOf<Project>()
fun esc(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
fun attr(action: String, id: String = "", v: String = "") = "data-action='${esc(action)}' data-id='${esc(id)}' data-value='${esc(v)}'"
fun button(label: String, action: String, id: String = "", v: String = "", cls: String = "") = "<button class='$cls' ${attr(action,id,v)}>$label</button>"
fun field(id: String, label: String, v: String = "", multiline: Boolean = false): String {
    val references = id !in setOf("width", "height", "new-width", "new-height", "target-path")
    val marker = if(references) "data-references='true'" else ""
    val input = if(multiline) "<textarea id='$id' $marker rows='3'>${esc(v)}</textarea>" else "<input id='$id' $marker value='${esc(v)}'>"
    return "<div class='field-wrap'><label class='field' for='$id'>$label$input</label>" + if(references) "<div class='reference-tools'>${button("@ Reference","reference-picker",id,cls="tiny")}<small>Type @ to link an object</small></div><div class='reference-preview' id='$id-preview'>${rich(v)}</div></div>" else "</div>"
}
fun checkpoint() { history += project; if(history.size>50) history.removeAt(0) }
fun changed() { dirty=true; revision++; setDirty(true) }
fun change(transform: (Project) -> Project) {
    val next=transform(project); next.validate(); checkpoint(); project=next; changed()
}
fun currentLevel() = project.levels.find { it.id==selected }
fun currentBlock() = project.blocks.find { it.id==(currentLevel()?.rootId ?: selected) }
fun currentTile() = project.tiles.find { it.id==selected }
fun svg(id: String, width: Int = project.size(id).first, height: Int = project.size(id).second, grid: Boolean = false): String {
    fun draw(asset: String, x: Int, y: Int, seen: Set<String>): String {
        if(asset in seen) return ""
        project.tiles.find { it.id==asset }?.let { t -> return t.pixels.mapIndexedNotNull { i,c -> if(c<0) null else "<rect x='${x+i%t.size}' y='${y+i/t.size}' width='1' height='1' fill='${palette[c]}'/>" }.joinToString("") }
        return project.blocks.find { it.id==asset }?.children?.joinToString("") { draw(it.assetId,x+it.x,y+it.y,seen+asset) } ?: ""
    }
    return "<svg viewBox='0 0 $width $height' shape-rendering='crispEdges' aria-label='${esc(project.name(id))}' role='img'>" +
        (if(grid) "<defs><pattern id='grid' width='$snap' height='$snap' patternUnits='userSpaceOnUse'><path d='M $snap 0 L 0 0 0 $snap' fill='none' stroke='#ffffff0a' stroke-width='.4'/></pattern></defs><rect width='100%' height='100%' fill='url(#grid)'/>" else "") + draw(id,0,0,emptySet()) + "</svg>"
}
fun render() {
    if(!loaded) { mount("<div class='loading'>K<span>design</span><p>${text(message.ifBlank { "Opening your adventure workshop…" })}</p>${button("Retry connection","reload")}</div>"); return }
    publishReferenceObjects()
    val level=currentLevel(); val block=currentBlock(); val tile=currentTile()
    val heading=when { section=="Concepts" -> "Design vocabulary"; tab=="Table" && section=="Levels" -> "Level overview"; else -> project.name(selected) }
    mount("""
        <aside class='sidebar'><a class='brand' href='/'><span class='brand-icon'>K</span> kdesign<span class='beta'>ALPHA</span></a>
        <div class='workspace'><span class='workspace-icon'>✦</span><div>${rich(project.title)}<small>Adventure project</small>${button("Edit title","new",v="Project",cls="tiny")}</div></div>
        <div class='nav-label'>WORKSPACE</div><nav>${listOf("Levels" to "▤", "Tiles" to "▦", "Blocks" to "▧", "Meta blocks" to "▱", "Concepts" to "◇").joinToString("") { (name,icon) -> button("<span>$icon</span>$name<span class='count'>${when(name) { "Levels"->project.levels.size; "Tiles"->project.tiles.size; "Blocks"->project.blocks.count {it.kind=="block"}; "Meta blocks"->project.blocks.count {it.kind=="meta"}; else->project.concepts.size }}</span>","section",name,cls=if(section==name) "active" else "") } }</nav>
        <div class='nav-label'>${if(section=="Concepts") "CATEGORIES" else "EXPLORER"} ${button("+","new",cls="tiny",v=section)}</div>
        <div class='explorer'>${explorer()}</div><div class='sidebar-bottom'><span class='green-dot'></span> Local workspace<small>data/project.json</small></div></aside>
        <main data-view='${text(section)}:${esc(selected)}:${text(tab)}'><header><div class='breadcrumbs'>${rich(project.title)} <span>/</span> ${text(section)}</div><div class='header-actions'><span class='save-state'>${if(saving) "Saving…" else if(dirty) "● Unsaved changes" else "✓ Saved locally"}</span>${button("↶ Undo","undo",cls="quiet")}${button(if(saving) "Saving…" else "Save project","save",cls="primary")}</div></header>
        <div class='page-heading'><div><div class='eyebrow'>${when(section) { "Levels"->"WORLD DESIGN"; "Tiles"->"PIXEL WORKSHOP"; "Concepts"->"SYSTEMS & INTENT"; else->"ASSET WORKSHOP" }}</div><h1>${rich(heading)}</h1><p>${when(section) { "Levels"->"Shape the journey. Give every place a purpose."; "Tiles"->"Small pixels. A world of possibility."; "Concepts"->"Connect the rules, rewards, and reasons to explore."; else->"Build once. Bring it to life everywhere." }}</p></div><div class='heading-actions'>${if(section!="Concepts") button("⌕ Find usages","usages",selected) else ""}${button("+ New ${when(section) {"Levels"->"level"; "Tiles"->"tile"; "Concepts"->"concept"; else->"block"}}","new",v=section)}</div></div>
        ${if(message.isNotBlank()) "<div class='notice' role='status'>${text(message)}${button("×","dismiss",cls="tiny")}</div>" else ""}
        <div class='tabbar'>${(when(section) { "Levels"->listOf("Canvas","Design brief","Table"); "Concepts"->listOf("Library"); else->listOf("Editor","Properties") }).joinToString("") { button(it,"tab",it,cls=if(tab==it) "active" else "") }}<span>${if(tile!=null) "${tile.size} × ${tile.size} PX • NES PALETTE" else if(block!=null) "${block.width} × ${block.height} PX • ${block.children.size} PLACEMENTS" else "SHARED DESIGN LANGUAGE"}</span></div>
        <div class='page-body'>${when { section=="Concepts"->conceptsView(); tab=="Table"->tableView(); tab=="Design brief" && level!=null->briefView(level); tab=="Properties"->propertiesView(); tile!=null->tileView(tile); block!=null->canvasView(block,level); else->"<div class='empty'>Create an asset to begin.</div>" }}</div>
        <footer><span><span class='green-dot'></span> Kotlin / WebAssembly</span><span>Definitions → instances → worlds</span><span>⌘ / Ctrl S to save</span></footer></main>
        ${if(usageId.isNotBlank()) usagesView() else ""}${if(modal.isNotBlank()) modalView() else ""}
    """.trimIndent())
}
fun explorer(): String = when(section) {
    "Levels" -> project.levels.joinToString("") { l -> button("${if(l.parentId!=null) "<span class='branch'>↳</span>" else "<span>▤</span>"}${text(l.title)}", "select", l.id, cls=if(selected==l.id) "selected" else "") }
    "Tiles" -> project.tiles.joinToString("") { button("<span class='mini-art'>${svg(it.id)}</span>${text(it.name)}","select",it.id,cls=if(selected==it.id) "selected" else "") }
    "Concepts" -> categories.keys.joinToString("") { "<div class='category-label'>◇ ${text(it)}<span>${project.concepts.count { c->c.category==it }}</span></div>" }
    else -> project.blocks.filter { it.kind==if(section=="Blocks") "block" else "meta" }.joinToString("") { button("<span>▧</span>${text(it.name)}","select",it.id,cls=if(selected==it.id) "selected" else "") }
}
fun tileView(t: Tile): String = """
<div class='editor-layout'><section class='panel'><div class='panel-bar'><span>Tile editor <small>${t.size} × ${t.size}</small></span><div>${button("✎ Pencil","pencil",cls=if(!eraser) "tool active" else "tool")}${button("◇ Eraser","eraser",cls=if(eraser) "tool active" else "tool")}</div></div><div class='pixel-stage'><div class='pixel-grid' style='grid-template-columns:repeat(${t.size},1fr)'>${t.pixels.mapIndexed { i,c -> "<button id='px-$i' aria-label='Pixel ${i%t.size+1}, ${i/t.size+1}' ${attr("paint",i.toString())} style='background-color:${if(c<0) "transparent" else palette[c]}'></button>" }.joinToString("")}</div></div><div class='panel-caption'>Click or drag to draw · transparency is preserved · shared tiles update every usage</div></section>
<aside class='inspector'><section class='panel padded'><div class='eyebrow'>COLOR PALETTE</div><h3>Nintendo Entertainment System</h3><div class='palette'>${palette.mapIndexed { i,c -> "<button title='Color $i · $c' aria-label='Color $i' class='${if(brush==i&&!eraser) "chosen" else ""}' ${attr("color",i.toString())} style='background:$c'></button>" }.joinToString("")}</div>${button("▧ Transparent","eraser",cls="wide")}<p class='muted'>64 palette entries + transparency.<br>Selected: ${if(eraser) "transparent" else palette[brush]}</p></section>${generationPanel()}<section class='panel padded'><div class='eyebrow'>LIVE PREVIEW</div><div class='preview-tile'>${svg(t.id)}</div></section></aside></div>
"""
fun generationPanel() = """<section class='panel padded'><div class='eyebrow'>✦ GENERATE WITH OPENAI</div><h3>From an idea to pixels</h3>${field("prompt","Describe your tile", "", true)}${button(if(generating) "Generating…" else "✦ Generate tile","generate",cls="primary wide")}<p class='muted'>${if(generationAvailable) "Creates a new editable tile. For blocks, also places it in the first available cell." else "Set OPENAI_API_KEY on the server to enable generation."}</p></section>"""
fun canvasView(b: Block, l: Level?): String {
    val assets=if(b.kind=="block") project.tiles.map { it.id } else project.blocks.filter { it.id!=b.id && it.width<=b.width && it.height<=b.height }.map { it.id }
    if(assetBrush !in assets) assetBrush=assets.firstOrNull() ?: ""
    return """<div class='editor-layout'><div><section class='panel'><div class='panel-bar'><span>${if(l!=null) "Level composition" else "Block composition"}<small><select class='snap-select' aria-label='Grid size' data-change='snap'><option value='16' ${if(snap==16) "selected" else ""}>16 px grid</option><option value='8' ${if(snap==8) "selected" else ""}>8 px grid</option></select></small></span><div>${button("＋ Place","pencil",cls=if(!eraser) "tool active" else "tool")}${button("− Remove","eraser",cls=if(eraser) "tool active" else "tool")}</div></div>
    <div class='scene-scroll'><div class='scene ${if(l!=null) "forest" else ""}' style='width:${maxOf(320,b.width*2)}px;height:${maxOf(320,b.width*2)*b.height/b.width}px'>${svg(b.id,b.width,b.height,true)}<div class='placement-grid' style='grid-template-columns:repeat(${(b.width+snap-1)/snap},${snap*100.0/b.width}%);grid-template-rows:repeat(${(b.height+snap-1)/snap},${snap*100.0/b.height}%)'>${(0 until (b.height+snap-1)/snap).joinToString("") { y -> (0 until (b.width+snap-1)/snap).joinToString("") { x -> "<button title='${if(eraser) "Remove" else "Place ${text(project.name(assetBrush))}"} at ${x*snap},${y*snap}' aria-label='Cell $x,$y' ${attr("place","${x*snap},${y*snap}")}></button>" } }}</div></div></div><div class='panel-caption'><span>${if(eraser) "Remove mode" else "Placing: ${text(project.name(assetBrush))}"}</span><span>Click a grid cell · scroll to explore →</span></div></section>
    <section class='panel asset-tray'><div class='panel-bar'>${if(b.kind=="block") "TILE PALETTE" else "BLOCK PALETTE"}<small>Shared definitions</small></div><div class='asset-list'>${assets.joinToString("") { id -> button("<span class='asset-thumb'>${svg(id)}</span><span>${text(project.name(id))}</span>","asset",id,cls="asset-card ${if(assetBrush==id) "selected" else ""}") }}</div></section>
    <section class='panel padded'><div class='section-title'><h3>Placed instances</h3><span class='muted'>${b.children.size} placements</span></div><div class='instances'>${b.children.joinToString("") { c -> "<div class='instance ${if(highlightPath.firstOrNull()==c.id) "highlight" else ""}'><span><b>${text(project.name(c.assetId))}</b><small>${esc(c.id)} · (${c.x}, ${c.y})</small></span><div>${button("Open","open",c.assetId,cls="tiny")}${button("×","remove",c.id,cls="tiny")}</div></div>" }}</div></section></div>
    <aside class='inspector'><section class='panel padded'><div class='eyebrow'>${if(l!=null) "LEVEL AT A GLANCE" else "REUSABLE DEFINITION"}</div><h3>${rich(l?.title ?: b.name)}</h3><p class='muted'>${rich(l?.notes?.get("description") ?: "Compose smaller assets into a reusable part of your world.")}</p><div class='stat-line'><span>Space</span><b>${b.width} × ${b.height}</b></div><div class='stat-line'><span>Nested instances</span><b>${b.children.size}</b></div><div class='stat-line'><span>Sublevels</span><b>${project.levels.count { it.parentId==l?.id && l!=null }}</b></div>${if(l!=null) button("Edit design brief →","tab","Design brief",cls="wide") else button("Edit properties →","tab","Properties",cls="wide")}</section>
    ${if(l!=null) "<section class='panel padded'><div class='eyebrow'>PLAYER GOALS</div><p>${rich(l.notes["playerGoals"] ?: "No goals yet")}</p><div class='eyebrow'>EMOTIONAL BAITS</div><p class='muted'>${rich(l.notes["emotionalBaits"] ?: "No emotional baits yet")}</p></section>" else if(b.kind=="block") generationPanel() else ""}
    <section class='panel padded'><div class='eyebrow'>CONNECTIONS</div><h3>Doors to other places</h3>${b.portals.joinToString("") { p -> button("↗ ${text(p.label)}<small>${text(project.name(p.targetLevelId))}</small>","portal-go",p.targetLevelId,p.targetPath.joinToString("/"),"portal-link") }.ifBlank { "<p class='muted'>No outgoing portals on this definition.</p>" }}${if(b.kind=="meta") button("+ Add portal","new",v="Portal",cls="wide") else ""}</section></aside></div>"""
}
fun briefView(l: Level) = "<section class='panel padded'><div class='section-title'><div><h3>Level design brief</h3><p class='muted'>The intent behind the space.</p></div>${button("Apply brief","brief-save",cls="primary")}</div><div class='form-grid'>${field("title","Title",l.title)}<label class='field'>Parent level<select id='parent'><option value=''>Independent space</option>${project.levels.filter {it.id!=l.id}.joinToString(""){ "<option value='${esc(it.id)}' ${if(it.id==l.parentId) "selected" else ""}>${text(it.title)}</option>" }}</select></label>${levelFields.mapIndexed { i,k -> field(k,fieldLabels[i],l.notes[k] ?: "",true) }.joinToString("")}</div></section>"
fun tableView() = "<section class='panel table-scroll'><div class='panel-bar'>Level overview <small>Edit any cell · changes apply when you leave the field · Save project writes to disk</small></div><table class='editable-levels'><thead><tr><th>Level / space</th>${fieldLabels.joinToString(""){"<th>$it</th>"}}</tr></thead><tbody>${project.levels.joinToString(""){ l -> "<tr><td>${tableField(l,"title","Title",l.title)}${button("Open level →","select",l.id,cls="text-link")}<small>${l.parentId?.let { "↳ ${text(project.name(it))}" } ?: "Independent space"}</small></td>${levelFields.mapIndexed { i,k -> "<td>${tableField(l,k,fieldLabels[i],l.notes[k] ?: "")}</td>" }.joinToString("")}</tr>" }}</tbody></table></section>"
fun tableField(level: Level, key: String, label: String, content: String): String =
    field("table-${level.id}-$key", "${project.displayText(level.title)} · $label", content, multiline=true)
        .replace("data-references='true'", "data-references='true' data-table-owner='${esc(level.id)}' data-table-field='$key'")

fun propertiesView(): String {
    val b=currentBlock(); val t=currentTile()
    return "<section class='panel padded'><div class='section-title'><h3>Definition properties</h3>${button("Apply properties","properties-save",cls="primary")}</div>${field("name","Name", t?.name ?: b?.name ?: "")}<p class='muted'>Stable ID: ${esc(selected)}</p>" + if(b!=null) "<div class='form-grid'>${field("width","Width (pixels)",b.width.toString())}${field("height","Height (pixels)",b.height.toString())}${field("properties","Semantic properties · one key=value per line",b.properties.entries.joinToString("\n"){"${it.key}=${it.value}"},true)}<label class='field'>Linked concepts<select id='concept'><option value=''>No concept</option>${project.concepts.joinToString(""){"<option value='${esc(it.id)}' ${if(it.id in b.conceptIds) "selected" else ""}>${text(it.category)} / ${text(it.name)}</option>"}}</select><small>This first editor links one concept per definition.</small></label></div><h3>Portals</h3>${b.portals.mapIndexed { i,p -> "<div class='instance'><span>${text(p.label)} → ${text(project.name(p.targetLevelId))} / ${text(p.targetPath.joinToString("/"))}</span>${button("Remove","portal-remove",i.toString(),cls="tiny")}</div>" }.joinToString("")}</section>" else "<p class='muted'>${t?.size} × ${t?.size} pixels · NES palette · transparent background</p></section>"
}
fun conceptsView() = "<div class='concept-grid'>${categories.map { (category,types) -> "<section class='panel padded'><div class='eyebrow'>◇ ${text(category.uppercase())}</div><p class='muted'>${types.joinToString(" · ")}</p>${project.concepts.filter {it.category==category}.joinToString(""){ c -> "<div class='concept-card'><span class='tag'>${text(c.type)}</span><h3>${rich(c.name)}</h3><p>${rich(c.description)}</p><small>${project.blocks.count {c.id in it.conceptIds}} linked definitions</small><div class='concept-actions'>${button("Edit","concept-edit",c.id,cls="tiny")}${button("Find usages","usages",c.id,cls="tiny")}</div></div>" }.ifBlank {"<div class='empty small'>Make room for an idea.</div>"}}${button("+ Add ${category.lowercase()}","new",category,"Concepts","text-link")}</section>" }.joinToString("")}</div>"
fun usagesView(): String {
    val usages=project.usages(usageId)
    return "<div class='overlay'><section class='dialog usage-dialog'><div class='section-title'><div><div class='eyebrow'>REFERENCE EXPLORER</div><h2>${text(project.name(usageId))}</h2></div>${button("×","close-usages",cls="tiny")}</div><p class='muted'>${usages.count {it.levelId!=null}} level occurrences · ${usages.count {it.levelId==null && it.field==null}} definition references · ${usages.count {it.field!=null}} text references. Every nested instance keeps its own path.</p><div class='usage-list'>${usages.joinToString(""){ u -> button("<span class='tag'>${if(u.field!=null) "TEXT REFERENCE" else if(u.levelId!=null) "LEVEL INSTANCE" else "DEFINITION"}</span><b>${text(u.label)}</b><small>Open usage →</small>",if(u.field!=null) "text-usage-open" else "usage-open",u.levelId ?: u.ownerId,u.field ?: u.path.joinToString("/"),"usage-row") }.ifBlank {"<div class='empty'>This definition has no usages yet.</div>"}}</div></section></div>"
}
fun modalView(): String {
    val concept=project.concepts.find {it.id==selected}
    val title=if(modal=="Portal") "Connect two places" else if(modal=="Edit concept") "Edit concept" else "Create ${modal.lowercase().removeSuffix("s")}"
    return "<div class='overlay'><section class='dialog'><div class='section-title'><h2>$title</h2>${button("×","close-modal",cls="tiny")}</div>${if(message.isNotBlank()) "<p class='modal-error' role='alert'>${text(message)}</p>" else ""}" + when(modal) {
        "Project" -> field("new-name","Project title",project.title)
        "Portal" -> field("new-name","Portal label","Enter passage") + "<label class='field'>Destination level<select id='target-level'>${project.levels.joinToString(""){"<option value='${esc(it.id)}'>${text(it.title)}</option>"}}</select></label>" + field("target-path","Instance path (optional, slash separated)") + "<p class='muted'>Leave empty for the level root. Otherwise enter placement IDs leading to a meta block, e.g. cave-door.</p>"
        "Concepts", "Edit concept" -> field("new-name","Name",concept?.takeIf {modal=="Edit concept"}?.name ?: "") + "<label class='field'>Category<select id='category' data-change='category'>${categories.keys.joinToString(""){ "<option ${if(it==(if(modal=="Edit concept") concept?.category else conceptCategory)) "selected" else ""}>${text(it)}</option>" }}</select></label><label class='field'>Type<select id='concept-type'>${categories[conceptCategory]!!.joinToString(""){"<option ${if(it==concept?.type) "selected" else ""}>${text(it)}</option>"}}</select></label>"+field("concept-description","Description",concept?.takeIf {modal=="Edit concept"}?.description ?: "",true)
        else -> field("new-name","Name") + when(modal) { "Tiles"->"<label class='field'>Tile size<select id='new-size'><option value='16'>16 × 16 pixels</option><option value='8'>8 × 8 pixels</option></select></label>"; else -> "<div class='form-grid'>${field("new-width","Width (pixels)",if(modal=="Levels") "512" else "32")}${field("new-height","Height (pixels)",if(modal=="Levels") "192" else "32")}</div>" } + if(modal=="Levels") "<label class='field'>Parent level<select id='new-parent'><option value=''>Independent space</option>${project.levels.joinToString(""){"<option value='${esc(it.id)}'>${text(it.title)}</option>"}}</select></label>" else ""
    } + "<div class='dialog-actions'>${button("Cancel","close-modal")}${button(if(modal=="Edit concept") "Apply changes" else "Create","create",cls="primary")}</div></section></div>"
}
var conceptCategory = "Gates"
fun select(id: String) {
    selected=id; highlightPath=emptyList()
    section=when { project.tiles.any {it.id==id}->"Tiles"; project.levels.any {it.id==id}->"Levels"; project.blocks.any {it.id==id&&it.kind=="block"}->"Blocks"; else->"Meta blocks" }
    tab=if(section=="Levels") "Canvas" else "Editor"
}
fun save() {
    if(saving) return
    require(tableErrors.isEmpty()) { "Correct the highlighted table cell before saving" }
    project.validate(); saving=true; val savedRevision=revision; render()
    request("PUT","/api/files?path=project.json",json.encodeToString(project)) { ok,text ->
        saving=false
        if(ok) { if(revision==savedRevision) {dirty=false;setDirty(false)}; message="Project saved to data/project.json" } else message="Save failed: $text"
        render()
    }
}
fun load() {
    request("GET","/api/files?path=project.json","") { ok,text ->
        try { check(ok) {text}; project=json.decodeFromString<Project>(text); project.validate(); loaded=true; selected=project.levels.firstOrNull()?.id ?: project.tiles.firstOrNull()?.id ?: ""; if(selected.isNotEmpty()) select(selected); message="" }
        catch(e: Exception) {message="Could not load project: ${e.message}"}
        render()
    }
    request("GET","/api/health","") { ok,text -> if(ok) {generationAvailable=json.parseToJsonElement(text).jsonObject["generation"]?.jsonPrimitive?.boolean==true; render()} }
}
fun create() {
    val name=value("new-name").trim(); require(name.isNotBlank()) { "Enter a name" }
    val id=uuid()
    when(modal) {
        "Project" -> change {it.copy(title=name)}
        "Tiles" -> { val size=value("new-size").toInt(); change {it.copy(tiles=it.tiles+Tile(id,name,size))};select(id) }
        "Blocks", "Meta blocks", "Levels" -> {
            val b=Block(id,name,if(modal=="Blocks") "block" else "meta",value("new-width").toInt(),value("new-height").toInt())
            val l=if(modal=="Levels") Level(uuid(),name,id,value("new-parent").ifBlank {null}) else null
            change {it.copy(blocks=it.blocks+b, levels=it.levels+listOfNotNull(l))}; select(l?.id ?: id)
        }
        "Portal" -> { val b=currentBlock()!!; val p=Portal(name,value("target-level"),value("target-path").split('/').filter {it.isNotBlank()}); change {it.copy(blocks=it.blocks.map { if(it.id==b.id) it.copy(portals=it.portals+p) else it })} }
        "Concepts", "Edit concept" -> { val c=Concept(if(modal=="Edit concept") selected else id,name,value("category"),value("concept-type"),value("concept-description")); change {it.copy(concepts=it.concepts.filterNot {it.id==c.id}+c)};section="Concepts";tab="Library" }
    }
    modal=""
}
fun generate() {
    if(generating) return
    require(generationAvailable) { "Generation needs OPENAI_API_KEY. Set it in the server environment and restart." }
    val prompt=project.displayText(value("prompt")); require(prompt.isNotBlank()) {"Describe the tile you want to generate"}
    val targetBlock=currentBlock()?.takeIf {it.kind=="block"}; val size=currentTile()?.size ?: 16
    generating=true; message="Generating editable pixels…"; render()
    request("POST","/api/generate",buildJsonObject {put("description",prompt);put("size",size)}.toString()) {ok,text ->
        generating=false
        try {
            check(ok) {text}; val obj=json.parseToJsonElement(text).jsonObject
            val t=Tile(uuid(),obj["name"]!!.jsonPrimitive.content,size,obj["pixels"]!!.jsonArray.map {it.jsonPrimitive.int})
            var placed=false
            change { p ->
                val blocks=p.blocks.map { b -> if(b.id!=targetBlock?.id) b else {
                    val spot=(0..b.height-size step size).flatMap {y->(0..b.width-size step size).map {x->x to y}}.firstOrNull {(x,y)-> b.children.none {c-> val (w,h)=p.size(c.assetId); x<c.x+w && x+size>c.x && y<c.y+h && y+size>c.y}}
                    if(spot==null) b else {placed=true;b.copy(children=b.children+Placement(uuid(),t.id,spot.first,spot.second))}
                } }
                p.copy(tiles=p.tiles+t,blocks=blocks)
            }
            if(targetBlock==null) select(t.id)
            message=if(targetBlock!=null&&!placed) "Tile created in Tiles. The block has no free cell." else "Generated tile added. Review and save when ready."
        } catch(e: Exception) {message="Generation failed: ${e.message}"}
        render()
    }
}
fun action(kind: String, id: String, v: String) {
    try {
        when(kind) {
            "table-edit" -> {
                val input=json.parseToJsonElement(v).jsonObject
                val owner=input["owner"]!!.jsonPrimitive.content
                val key=input["field"]!!.jsonPrimitive.content
                val content=input["content"]!!.jsonPrimitive.content
                try {
                    change { p -> p.copy(levels=p.levels.map { l -> if(l.id!=owner) l else if(key=="title") l.copy(title=content.trim().also {require(it.isNotBlank()) { "Level title cannot be empty" }}) else l.copy(notes=l.notes+(key to content)) }) }
                    tableErrors.remove(id);tableCellState(id, "");publishReferenceObjects();referencePreview(id,rich(content))
                } catch(e: Exception) { tableErrors.add(id);tableCellState(id,e.message ?: "Invalid value") }
                return
            }
            "reference-preview" -> { referencePreview(id,rich(v));return }
            "reference-open" -> openReference(id)
            "text-usage-open" -> openTextUsage(id,v)
            "reload" -> {load();return}
            "save" -> {save();return}
            "dismiss" -> message=""
            "section" -> { tableErrors.clear();section=id;tab=if(id=="Levels") "Canvas" else if(id=="Concepts") "Library" else "Editor";selected=when(id) {"Levels"->project.levels.firstOrNull()?.id;"Tiles"->project.tiles.firstOrNull()?.id;else->project.blocks.firstOrNull {it.kind==if(id=="Blocks") "block" else "meta"}?.id} ?: "" }
            "select", "open" -> select(id)
            "tab" -> {tableErrors.clear();tab=id}
            "snap" -> snap=v.toInt()
            "pencil" -> eraser=false
            "eraser" -> eraser=true
            "color" -> {brush=id.toInt();eraser=false}
            "asset" -> {assetBrush=id;eraser=false}
            "stroke" -> {checkpoint();return}
            "paint" -> { val t=currentTile() ?: return; val index=id.toInt(); val c=if(eraser) -1 else brush; project=project.copy(tiles=project.tiles.map {if(it.id==t.id) it.copy(pixels=it.pixels.toMutableList().also {it[index]=c}) else it});changed();pixel("px-$index",if(c<0) "transparent" else palette[c]);return }
            "stroke-end" -> Unit
            "undo" -> if(history.isNotEmpty()) {project=history.removeAt(history.lastIndex);changed();if(selected !in project.tiles.map{it.id}+project.blocks.map{it.id}+project.levels.map{it.id}) select(project.levels.firstOrNull()?.id ?: project.tiles.firstOrNull()?.id ?: "")}
            "place" -> { val b=currentBlock()!!; val (x,y)=id.split(',').map {it.toInt()}; change {p->p.copy(blocks=p.blocks.map { if(it.id!=b.id) it else if(eraser) it.copy(children=it.children.filterNot {c -> val (w,h)=p.size(c.assetId); x>=c.x&&x<c.x+w&&y>=c.y&&y<c.y+h }) else {require(assetBrush.isNotBlank()){ "Create a child asset first" };it.copy(children=it.children+Placement(uuid(),assetBrush,x,y))} })} }
            "remove" -> {val b=currentBlock()!!;change {p->p.copy(blocks=p.blocks.map {if(it.id==b.id) it.copy(children=it.children.filterNot {it.id==id}) else it})}}
            "brief-save" -> {val l=currentLevel()!!;change {p->p.copy(levels=p.levels.map {if(it.id==l.id) it.copy(title=value("title").trim().also {require(it.isNotBlank()){ "Enter a title" }}, parentId=value("parent").ifBlank {null}, notes=levelFields.associateWith {value(it)}) else it})};message="Design brief applied. Save project to write it to disk."}
            "properties-save" -> { val name=value("name").trim();require(name.isNotBlank()){ "Enter a name" };val b=currentBlock();change {p->p.copy(tiles=p.tiles.map {if(it.id==selected) it.copy(name=name) else it}, blocks=p.blocks.map {if(it.id!=b?.id) it else it.copy(name=name,width=value("width").toInt(),height=value("height").toInt(),properties=value("properties").lines().filter {it.isNotBlank()}.associate { val pair=it.split('=',limit=2);require(pair.size==2&&pair[0].isNotBlank()){ "Properties use key=value" };pair[0].trim() to pair[1].trim()},conceptIds=listOf(value("concept")).filter {it.isNotBlank()})})} }
            "new" -> {modal=v;message="";conceptCategory=id.ifBlank {"Gates"}}
            "create" -> create()
            "close-modal" -> modal=""
            "category" -> {conceptCategory=v; if(modal=="Edit concept") { message="Category changes are applied when you submit the form." }; updateCategoryOptions(); return}
            "concept-edit" -> {selected=id;conceptCategory=project.concepts.first {it.id==id}.category;modal="Edit concept";message=""}
            "usages" -> usageId=id
            "close-usages" -> usageId=""
            "usage-open", "portal-go" -> {select(id);highlightPath=v.split('/').filter {it.isNotBlank()};usageId="";message=if(highlightPath.isEmpty()) "Opened destination" else "Instance path: ${highlightPath.joinToString(" → ")}"}
            "portal-remove" -> {val b=currentBlock()!!;change {p->p.copy(blocks=p.blocks.map {if(it.id==b.id) it.copy(portals=it.portals.filterIndexed { i,_ -> i!=id.toInt()}) else it})}}
            "generate" -> {generate();return}
        }
    } catch(e: Exception) {message=e.message ?: "Could not apply change";showError(message);return}
    render()
}
@JsFun("(html) => { document.getElementById('concept-type').innerHTML = html; }") external fun setOptions(html: String)
fun updateCategoryOptions() {setOptions(categories[conceptCategory]!!.joinToString(""){"<option>${esc(it)}</option>"})}
fun main() {bind(::action);load()}
