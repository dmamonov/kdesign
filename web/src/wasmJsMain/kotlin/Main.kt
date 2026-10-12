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
var generationPrompt = ""
var generationSize = "16"
var generationMode = "auto"
var generating = false
var generationAvailable = false
var loaded = false
var modal = ""
var usageId = ""
var highlightPath = emptyList<String>()
val tableErrors = mutableSetOf<String>()
val history = mutableListOf<Project>()
var tileStrokeChanged = false
fun esc(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
fun attr(action: String, id: String = "", v: String = "") = "data-action='${esc(action)}' data-id='${esc(id)}' data-value='${esc(v)}'"
fun button(label: String, action: String, id: String = "", v: String = "", cls: String = "") = "<button class='$cls' ${attr(action,id,v)}>$label</button>"
fun field(id: String, label: String, v: String = "", multiline: Boolean = false): String {
    val references = id !in setOf("width", "height", "new-width", "new-height", "target-path")
    val marker = if(references) "data-references='true'" else ""
    val input = if(multiline) "<textarea id='$id' $marker rows='3'>${esc(v)}</textarea>" else "<input id='$id' $marker value='${esc(v)}'>"
    return "<div class='field-wrap'><label class='field' for='$id'>$label$input</label></div>"
}
fun checkpoint() { history += project; if(history.size>50) history.removeAt(0) }
fun changed() { autosave.edited();queueSave() }
fun change(transform: (Project) -> Project) {
    val next=transform(project); if(next==project) return; next.validate(); checkpoint(); project=next; changed()
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
    return "<svg viewBox='0 0 $width $height' shape-rendering='crispEdges' aria-label='${text(project.name(id))}' role='img'>" +
        (if(grid) "<defs><pattern id='grid' width='$snap' height='$snap' patternUnits='userSpaceOnUse'><path d='M $snap 0 L 0 0 0 $snap' fill='none' stroke='#ffffff0a' stroke-width='.4'/></pattern></defs><rect width='100%' height='100%' fill='url(#grid)'/>" else "") + draw(id,0,0,emptySet()) + "</svg>"
}
fun render() {
    if(!loaded) { mount("<div class='loading'>K<span>design</span><p>${text(message.ifBlank { "Opening your adventure workshop…" })}</p>${button("Retry connection","reload")}</div>"); return }
    publishReferenceObjects()
    renderShell()
    syncRoute()
}
fun tileView(t: Tile): String = """
<div class='tile-workspace'>
<section class='panel padded tile-color-palette'><div class='eyebrow'>COLOR PALETTE</div><h3>Nintendo Entertainment System</h3><div class='palette-scroll'><div class='palette'>${palette.mapIndexed { i,c -> "<button title='Color $i · $c' aria-label='Color $i' class='${if(brush==i&&!eraser) "chosen" else ""}' ${attr("color",i.toString())} style='background:$c'></button>" }.joinToString("")}</div></div>${button("▧ Transparent","eraser",cls="tiny")}<p class='muted'>64 palette entries + transparency. · Selected: ${if(eraser) "transparent" else palette[brush]}</p></section>
<div class='editor-layout'><section class='panel'><div class='panel-bar'><span>Tile editor <small>${t.size} × ${t.size}</small></span><div>${button("↶ Undo","undo",cls="tool")}${button("Fill holes","tile-fill-holes",cls="tool")}${button("✎ Pencil","pencil",cls=if(!eraser) "tool active" else "tool")}${button("◇ Eraser","eraser",cls=if(eraser) "tool active" else "tool")}</div></div><div class='pixel-stage'><div class='pixel-grid' style='grid-template-columns:repeat(${t.size},1fr)'>${t.pixels.mapIndexed { i,c -> "<button id='px-$i' aria-label='Pixel ${i%t.size+1}, ${i/t.size+1}' ${attr("paint",i.toString())} style='background-color:${if(c<0) "transparent" else palette[c]}'></button>" }.joinToString("")}</div></div><div class='panel-caption'>Click or drag to draw · transparency is preserved · shared tiles update every usage</div></section>
<aside class='inspector'>${generationPanel()}<section class='panel padded tile-preview'><div class='eyebrow'>LIVE PREVIEW</div><div class='preview-tile'>${svg(t.id)}</div></section></aside></div>
</div>
"""
fun generationPanel() = """<section class='panel padded'><div class='eyebrow'>✦ GENERATE WITH OPENAI</div><h3>From an idea to pixels</h3>${field("prompt","Describe your tile", generationPrompt, true)}<div class='generation-options'><label class='field'>Tile size<select id='generation-size'><option value='16' ${if(generationSize=="16") "selected" else ""}>16 × 16 pixels</option><option value='8' ${if(generationSize=="8") "selected" else ""}>8 × 8 pixels</option></select></label><label class='field'>Tile style<select id='generation-mode'>${listOf("auto" to "Auto", "terrain" to "Terrain · fills tile", "sprite" to "Sprite · transparent").joinToString("") {(mode,label)->"<option value='$mode' ${if(generationMode==mode) "selected" else ""}>$label</option>"}}</select></label></div>${button(if(generating) "Generating…" else "✦ Generate tile","generate",cls="primary wide")}<p class='muted'>${if(generationAvailable) "Generates editable NES pixels. May take a few minutes." else "Set OPENAI_API_KEY on the server to enable generation."}</p></section>"""
fun canvasView(b: Block, l: Level?): String {
    val assets=project.compositionAssets(b.id)
    if(selectionOwner!=b.id) {mapSelection=null;selectionOwner=b.id}
    if(assetBrush !in assets) assetBrush=assets.firstOrNull() ?: ""
    return """<div class='canvas-layout'><div class='canvas-main'><section class='panel'><div class='panel-bar'><span>${if(l!=null) "Level composition" else "Block composition"}<small><select class='snap-select' aria-label='Grid size' data-change='snap'><option value='16' ${if(snap==16) "selected" else ""}>16 px grid</option><option value='8' ${if(snap==8) "selected" else ""}>8 px grid</option></select></small></span>${mapToolbar()}</div>
    <div class='scene-scroll'><div class='scene ${if(l!=null) "forest" else ""}' tabindex='0' aria-label='Map canvas' data-map-id='${esc(b.id)}' data-map-tool='$mapTool' data-width='${b.width}' data-height='${b.height}' data-snap='$snap' style='width:${maxOf(320,b.width*2)}px;height:${maxOf(320,b.width*2)*b.height/b.width}px'>${svg(b.id,b.width,b.height,true)}${if(l!=null) screenGrid(b) else ""}${selectionOverlay(b)}<div class='placement-grid' style='grid-template-columns:repeat(${(b.width+snap-1)/snap},${snap*100.0/b.width}%);grid-template-rows:repeat(${(b.height+snap-1)/snap},${snap*100.0/b.height}%)'>${(0 until (b.height+snap-1)/snap).joinToString("") { y -> (0 until (b.width+snap-1)/snap).joinToString("") { x -> "<button title='${if(mapTool=="inspect") "Inspect" else if(mapTool=="erase") "Remove" else if(mapTool=="select") "Select" else "Place ${text(project.name(assetBrush))}"} at ${x*snap},${y*snap}' aria-label='Cell $x,$y' ${attr("place","${x*snap},${y*snap}")}></button>" } }}</div></div></div><div class='panel-caption'><span>${if(mapTool=="inspect") "Inspect a placed instance" else if(mapTool=="erase") "Remove mode" else if(mapTool=="select") "Select area" else "Placing: ${text(project.name(assetBrush))}"}</span><span>Double-click: place · Drag: select · Ctrl/⌘ C/X/V: copy/cut/paste · Space: fill · Enter: picker · Shift + letter/digit: bind brush</span></div>${selectionBar()}</section>
    </div><aside class='panel asset-tray'><div class='panel-bar'>${if(b.kind=="block") "TILE PALETTE" else "BLOCK PALETTE"}<small>Shared definitions</small></div><div class='asset-list'>${assets.joinToString("") { mapAssetCard(it) }}</div></aside>
    </div>"""
}
fun briefView(l: Level) = "<section class='panel padded'><div class='section-title'><div><h3>Level design brief</h3><p class='muted'>The intent behind the space.</p></div><span class='muted'>Changes save automatically</span></div><div class='form-grid'>${field("title","Title",l.title)}<label class='field'>Parent level<select id='parent'><option value=''>Independent space</option>${project.levels.filter {it.id!=l.id}.joinToString(""){ "<option value='${esc(it.id)}' ${if(it.id==l.parentId) "selected" else ""}>${text(it.title)}</option>" }}</select></label>${levelFields.mapIndexed { i,k -> field(k,fieldLabels[i],l.notes[k] ?: "",true) }.joinToString("")}</div></section>"
fun tableView() = "<section class='panel table-scroll'><div class='panel-bar'>Level overview <small>Edit any cell · changes save automatically as you type</small></div><table class='editable-levels'><thead><tr><th>Level / space</th>${fieldLabels.joinToString(""){"<th>$it</th>"}}</tr></thead><tbody>${project.levels.joinToString(""){ l -> "<tr><td>${tableField(l,"title","Title",l.title)}${button("Open level →","select",l.id,cls="text-link")}<small>${l.parentId?.let { "↳ ${text(project.name(it))}" } ?: "Independent space"}</small></td>${levelFields.mapIndexed { i,k -> "<td>${tableField(l,k,fieldLabels[i],l.notes[k] ?: "")}</td>" }.joinToString("")}</tr>" }}</tbody></table></section>"
fun tableField(level: Level, key: String, label: String, content: String): String =
    field("table-${level.id}-$key", "${project.displayText(level.title)} · $label", content, multiline=true)
        .replace("data-references='true'", "data-references='true' data-table-owner='${esc(level.id)}' data-table-field='$key'")

fun propertiesView(): String {
    val b=currentBlock(); val t=currentTile()
    return "<section class='panel padded'><div class='section-title'><h3>Definition properties</h3><span class='muted'>Changes save automatically</span></div>${field("name","Name", t?.name ?: b?.name ?: "")}<p class='muted'>Stable ID: ${esc(selected)}</p>" + if(b!=null) "<div class='form-grid'>${field("width","Width (pixels)",b.width.toString())}${field("height","Height (pixels)",b.height.toString())}${field("properties","Semantic properties · one key=value per line",b.properties.entries.joinToString("\n"){"${it.key}=${it.value}"},true)}<label class='field'>Linked concepts<select id='concept'><option value=''>No concept</option>${project.concepts.joinToString(""){"<option value='${esc(it.id)}' ${if(it.id in b.conceptIds) "selected" else ""}>${text(it.category)} / ${text(it.name)}</option>"}}</select><small>This first editor links one concept per definition.</small></label></div><h3>Portals</h3>${b.portals.mapIndexed { i,p -> "<div class='instance'><span>${text(p.label)} → ${text(project.name(p.targetLevelId))} / ${text(p.targetPath.joinToString("/"))}</span>${button("Remove","portal-remove",i.toString(),cls="tiny")}</div>" }.joinToString("")}</section>" else "<p class='muted'>${t?.size} × ${t?.size} pixels · NES palette · transparent background</p></section>"
}
fun usagesView(): String {
    val usages=project.usages(usageId)
    return "<div class='overlay'><section class='dialog usage-dialog' role='dialog' aria-modal='true' aria-label='Find usages'><div class='section-title'><div><div class='eyebrow'>REFERENCE EXPLORER</div><h2>${text(project.name(usageId))}</h2></div>${button("×","close-usages",cls="tiny")}</div><p class='muted'>${usages.count {it.levelId!=null}} level occurrences · ${usages.count {it.levelId==null && it.field==null}} definition references · ${usages.count {it.field!=null}} text references. Every nested instance keeps its own path.</p><div class='usage-list'>${usages.joinToString(""){ u -> button("<span class='tag'>${if(u.field!=null) "TEXT REFERENCE" else if(u.levelId!=null) "LEVEL INSTANCE" else "DEFINITION"}</span><b>${text(u.label)}</b><small>Open usage →</small>",if(u.field!=null) "text-usage-open" else "usage-open",u.levelId ?: u.ownerId,u.field ?: u.path.joinToString("/"),"usage-row") }.ifBlank {"<div class='empty'>This definition has no usages yet.</div>"}}</div></section></div>"
}
fun modalView(): String {
    val concept=project.concepts.find {it.id==selected}
    val title=if(modal=="Project") "Edit project title" else if(modal=="Portal") "Connect two places" else if(modal=="Edit concept") "Edit concept" else "Create ${modal.lowercase().removeSuffix("s")}"
    return "<div class='overlay'><section class='dialog' role='dialog' aria-modal='true' aria-label='$title'><div class='section-title'><h2>$title</h2>${button("×","close-modal",cls="tiny")}</div>${if(message.isNotBlank()) "<p class='modal-error' role='alert'>${text(message)}</p>" else ""}" + when(modal) {
        "Project" -> field("new-name","Project title",project.title)
        "Portal" -> field("new-name","Portal label","Enter passage") + "<label class='field'>Destination space and instance<select id='portal-destination'>${portalDestinationOptions()}</select></label><p class='muted'>Choose a level root or a named meta-block occurrence. Paths distinguish repeated instances.</p>"
        "Concepts", "Edit concept" -> field("new-name","Name",concept?.takeIf {modal=="Edit concept"}?.name ?: "") + "<label class='field'>Category<select id='category' data-change='category'>${categories.keys.joinToString(""){ "<option ${if(it==(if(modal=="Edit concept") concept?.category else conceptCategory)) "selected" else ""}>${text(it)}</option>" }}</select></label><label class='field'>Type<select id='concept-type'>${categories[conceptCategory]!!.joinToString(""){"<option ${if(it==concept?.type) "selected" else ""}>${text(it)}</option>"}}</select></label>"+field("concept-description","Description",concept?.takeIf {modal=="Edit concept"}?.description ?: "",true)
        else -> field("new-name","Name") + when(modal) { "Tiles"->"<label class='field'>Tile size<select id='new-size'><option value='16'>16 × 16 pixels</option><option value='8'>8 × 8 pixels</option></select></label>"; else -> "<div class='form-grid'>${field("new-width","Width (pixels)",if(modal=="Levels") screenWidth.toString() else "16")}${field("new-height","Height (pixels)",if(modal=="Levels") screenHeight.toString() else "16")}</div>" } + if(modal=="Levels") "<label class='field'>Parent level<select id='new-parent'><option value=''>Independent space</option>${project.levels.joinToString(""){"<option value='${esc(it.id)}'>${text(it.title)}</option>"}}</select></label>" else ""
    } + "<div class='dialog-actions'>${if(modal in listOf("Edit concept","Project")) button("Done","close-modal",cls="primary") else button("Cancel","close-modal")+button("Create","create",cls="primary")}</div></section></div>"
}
var conceptCategory = "Gates"
fun select(id: String) {
    sectionMemory[section] = selected to tab
    val previousSection=section
    selected=id; highlightPath=emptyList()
    var ancestor=project.levels.find {it.id==id}?.parentId
    while(ancestor!=null) {expandedNodes.add(ancestor);ancestor=project.levels.find {it.id==ancestor}?.parentId}
    section=when { project.concepts.any {it.id==id}->project.concepts.first {it.id==id}.category; section=="Objects" && project.blocks.any {it.id==id}->"Objects"; project.tiles.any {it.id==id}->"Tiles"; project.levels.any {it.id==id}->"Levels"; project.blocks.any {it.id==id&&it.kind=="block"}->"Blocks"; else->"Meta blocks" }
    if(section!=previousSection) treeQuery=""
    tab=if(section=="Levels") "Canvas" else if(section in categories) "Design" else "Editor"
}

fun load() {
    request("GET","/api/files?path=project.json","") { ok,text ->
        try { check(ok) {text}; project=json.decodeFromString<Project>(text); project.validate(); loaded=true; selected="";tab="World map"; message="";restoreRoute() }
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
        "Tiles" -> {
            require(!generating) {"Wait for the current generation to finish before creating another tile"}
            val size=value("new-size").toInt()
            val tile=Tile(id,name,size)
            change {it.addTileWithBlock(tile,uuid(),uuid())};select(id);modal=""
            generationPrompt=name;generationSize=size.toString()
            if(generationAvailable) generate(tile,name,"auto") else message="Tile and block created. Automatic generation needs OPENAI_API_KEY on the server."
        }
        "Blocks", "Meta blocks", "Levels" -> {
            val b=Block(id,name,if(modal=="Blocks") "block" else "meta",value("new-width").toInt(),value("new-height").toInt())
            val l=if(modal=="Levels") Level(uuid(),name,id,value("new-parent").ifBlank {null}) else null
            change {it.copy(blocks=it.blocks+b, levels=it.levels+listOfNotNull(l))}; select(l?.id ?: id)
        }
        "Portal" -> { val b=currentBlock()!!; val destination=json.parseToJsonElement(value("portal-destination")).jsonArray.map {it.jsonPrimitive.content}; val p=Portal(name,destination.first(),destination.drop(1)); change {it.copy(blocks=it.blocks.map { if(it.id==b.id) it.copy(portals=it.portals+p) else it })} }
        "Concepts", "Edit concept" -> { val c=Concept(if(modal=="Edit concept") selected else id,name,value("category"),value("concept-type"),value("concept-description")); change {it.copy(concepts=it.concepts.filterNot {it.id==c.id}+c)};select(c.id) }
    }
    modal=""
}
fun generate(existing: Tile? = currentTile(), description: String? = null, mode: String = generationMode) {
    if(generating) return
    require(generationAvailable) { "Generation needs OPENAI_API_KEY. Set it in the server environment and restart." }
    val prompt=project.displayText(description ?: value("prompt")); require(prompt.isNotBlank()) {"Describe the tile you want to generate"}
    val targetBlock=currentBlock()?.takeIf {it.kind=="block"}; val size=existing?.size ?: value("generation-size").toIntOrNull() ?: 16
    generating=true; message="Generating editable pixels…"; render()
    request("POST","/api/generate",buildJsonObject {put("description",prompt);put("size",size);put("mode",mode)}.toString()) {ok,text ->
        generating=false
        try {
            check(ok) {runCatching {json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content}.getOrNull() ?: "Generation request failed. Check the server connection."}; val obj=json.parseToJsonElement(text).jsonObject
            val pixels=obj["pixels"]!!.jsonArray.map {it.jsonPrimitive.int}
            if(existing!=null) {
                change {it.applyGeneratedPixels(existing,pixels)}
                message="Tile generated. Its block and all usages have updated automatically."
                render();return@request
            }
            val t=Tile(uuid(),obj["name"]!!.jsonPrimitive.content,size,pixels)
            var placed=false
            change { p ->
                val blocks=p.blocks.map { b -> if(b.id!=targetBlock?.id) b else {
                    val spot=(0..b.height-size step size).flatMap {y->(0..b.width-size step size).map {x->x to y}}.firstOrNull {(x,y)-> b.children.none {c-> val (w,h)=p.size(c.assetId); x<c.x+w && x+size>c.x && y<c.y+h && y+size>c.y}}
                    if(spot==null) b else {placed=true;b.copy(children=b.children+Placement(uuid(),t.id,spot.first,spot.second))}
                } }
                p.copy(blocks=blocks).addTileWithBlock(t,uuid(),uuid())
            }
            if(targetBlock==null) select(t.id)
            message=if(targetBlock!=null&&!placed) "Tile created in Tiles. The block has no free cell." else "Generated tile added. Saving automatically."
        } catch(e: Exception) {message="Generation failed: ${e.message}"}
        render()
    }
}
fun action(kind: String, id: String, v: String) {
    try {
        if(tableErrors.isNotEmpty() && kind !in setOf("form-edit", "table-edit", "save", "undo", "tree-search", "category")) {
            if(kind=="url-navigation") {replaceRoute=true;syncRoute()}
            showError("Correct the invalid input before leaving this editor, or use Undo to discard it.")
            return
        }
        if(kind in setOf("section", "category-root", "select", "open", "reference-open", "text-usage-open", "usage-open", "portal-go", "instance-open")) rememberLocation()
        if(kind=="url-navigation") {if(loaded) {restoreRoute();render()};return}
        if(handleMapAction(kind,id,v)) return
        when(kind) {
            "delete-request" -> {deletingId=id}
            "delete-cancel" -> deletingId=""
            "delete-confirm" -> {
                require(id==deletingId) {"Select the object to delete first"}
                change {it.deleteObject(id)}
                deletingId="";usageId="";highlightPath=emptyList();hotkeyConflict=null
                selected="";tab=if(section=="Levels") "Table" else "Library"
                message="Object deleted. Undo restores it and its references."
            }
            "form-edit" -> {editForm(id,v);return}
            "table-edit" -> {
                val input=json.parseToJsonElement(v).jsonObject
                val owner=input["owner"]!!.jsonPrimitive.content
                val key=input["field"]!!.jsonPrimitive.content
                val content=input["content"]!!.jsonPrimitive.content
                try {
                    change { p -> p.copy(levels=p.levels.map { l -> if(l.id!=owner) l else if(key=="title") l.copy(title=content.trim().also {require(it.isNotBlank()) { "Level title cannot be empty" }}) else l.copy(notes=l.notes+(key to content)) }) }
                    tableErrors.remove(id);message="";tableCellState(id, "");publishReferenceObjects()
                } catch(e: Exception) { tableErrors.add(id);tableCellState(id,e.message ?: "Invalid value") }
                updateSaveStatus()
                return
            }
            "reference-open" -> openReference(id)
            "text-usage-open" -> openTextUsage(id,v)
            "reload" -> {load();return}
            "save" -> {save();return}
            "dismiss" -> message=""
            "section" -> switchSection(id)
            "category-root" -> {selected="";tab=if(section=="Levels") "Table" else "Library"}
            "tree-toggle" -> {if(!expandedNodes.add(id)) expandedNodes.remove(id)}
            "tree-search" -> {treeQuery=v;refreshTree(treeView());return}
            "instance-open" -> {select(id);highlightPath=listOf(v);message="Selected instance: $v"}
            "navigate-back" -> if(locations.isNotEmpty()) {val location=locations.removeAt(locations.lastIndex);section=location.section;selected=location.selected;tab=location.tab;usageId=location.usage;highlightPath=location.path;treeQuery=location.query}
            "select", "open" -> select(id)
            "tab" -> {tableErrors.clear();tab=id}
            "snap" -> snap=v.toInt()
            "tile-fill-holes" -> {
                val t=currentTile() ?: return
                val pixels=t.pixels.toMutableList()
                repairEnclosedTransparency(pixels,t.size)
                change {p->p.copy(tiles=p.tiles.map {if(it.id==t.id) it.copy(pixels=pixels) else it})}
                message=if(pixels==t.pixels) "No enclosed transparent holes found" else "Enclosed holes filled. Undo restores the previous pixels."
            }
            "pencil" -> eraser=false
            "eraser" -> eraser=true
            "color" -> {brush=id.toInt();eraser=false}
            "asset" -> {assetBrush=id;eraser=false;mapTool="select";closeRadial()}
            "stroke" -> {tileStrokeChanged=false;return}
            "paint" -> { val t=currentTile() ?: return; val index=id.toInt(); val c=if(eraser) -1 else brush; if(t.pixels[index]==c) return; if(!tileStrokeChanged) {checkpoint();tileStrokeChanged=true}; project=project.copy(tiles=project.tiles.map {if(it.id==t.id) it.copy(pixels=it.pixels.toMutableList().also {it[index]=c}) else it});changed();pixel("px-$index",if(c<0) "transparent" else palette[c]);return }
            "stroke-end" -> {tileStrokeChanged=false}
            "undo" -> if(tableErrors.isNotEmpty()) {tableErrors.clear()} else if(history.isNotEmpty()) {project=history.removeAt(history.lastIndex);message="Change undone";changed();if(selected.isNotEmpty() && selected !in project.objectIds()) {selected="";tab=if(section=="Levels") "Table" else "Library"}}
            "place" -> {handleMapAction("map-select","$id,$id","");return}
            "remove" -> {val b=currentBlock()!!;change {p->p.copy(blocks=p.blocks.map {if(it.id==b.id) it.copy(children=it.children.filterNot {it.id==id}) else it})}}
            "new" -> {modal=v;message="";conceptCategory=id.ifBlank {"Gates"}}
            "create" -> create()
            "close-modal" -> {if(modal=="Edit concept") select(selected);modal=""}
            "category" -> {conceptCategory=v; updateCategoryOptions(); if(modal=="Edit concept") editForm("category",v); return}
            "concept-edit" -> {selected=id;conceptCategory=project.concepts.first {it.id==id}.category;modal="Edit concept";message=""}
            "usages" -> usageId=id
            "close-usages" -> usageId=""
            "usage-open", "portal-go" -> {select(id);highlightPath=v.split('/').filter {it.isNotBlank()};usageId="";message=if(highlightPath.isEmpty()) "Opened destination" else "Instance path: ${highlightPath.joinToString(" → ")}"}
            "portal-remove" -> {val b=currentBlock()!!;change {p->p.copy(blocks=p.blocks.map {if(it.id==b.id) it.copy(portals=it.portals.filterIndexed { i,_ -> i!=id.toInt()}) else it})}}
            "generate" -> {generate();return}
        }
    } catch(e: Exception) {message=e.message ?: "Could not apply change";showError(message);return}
    render();updateSaveStatus()
    if(kind=="asset") focusMap()
    if(pendingFieldFocus.isNotBlank()) {focusField(pendingFieldFocus);pendingFieldFocus=""}
}
@JsFun("(html) => { document.getElementById('concept-type').innerHTML = html; }") external fun setOptions(html: String)
fun updateCategoryOptions() {setOptions(categories[conceptCategory]!!.joinToString(""){"<option>${esc(it)}</option>"})}
fun main() {bind(::action);load()}

@JsFun("() => document.querySelector('.scene')?.focus({preventScroll:true})")
external fun focusMap()
