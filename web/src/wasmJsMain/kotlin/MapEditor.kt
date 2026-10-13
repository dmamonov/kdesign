package kdesign.web

import kdesign.*
import kotlin.math.*

var mapTool="select"
var mapClipboard: MapClipboard?=null
var mapSelection: MapArea?=null
var selectionOwner=""
var replaceFill=true
var hotkeyConflict: Pair<String,String>? = null
var radialPage=0
var radialX=0
var radialY=0
@JsFun("(html, x, y) => window.designer.showRadial(html, x, y)") external fun showRadial(html: String,x: Int,y: Int)
@JsFun("() => document.getElementById('block-picker')?.remove()") external fun closeRadial()
fun hotkey(id: String) = project.hotkeys.entries.find {it.value==id}?.key ?: ""
fun mapAssetCard(id: String): String = "<div class='map-asset'>" + button("<span class='asset-thumb'>${svg(id)}</span><span>${text(project.name(id))}</span><kbd>${hotkey(id).ifBlank {"—"}}</kbd>","asset",id,cls="asset-card ${if(assetBrush==id) "selected" else ""}") +
    "<select aria-label='Hotkey for ${text(project.name(id))}' data-change='bind-hotkey' data-id='${esc(id)}'><option value=''>No hotkey</option>${("1234567890abcdefghijklmnopqrstuvwxyz").map { key -> "<option value='$key' ${if(hotkey(id)==key.toString()) "selected" else ""}>$key</option>" }.joinToString("")}</select></div>"
fun mapToolbar() = "<div class='map-tools'>"+button("Copy","map-copy",cls="tool")+button("Paste","map-paste",cls="tool")+button("Enter · Blocks","radial-open",cls="tool")+"</div>"

fun selectionBar(): String {
    val area=mapSelection
    return "<div class='selection-bar'><span id='selection-label'>${area?.let {"${it.width} × ${it.height} px selected"} ?: "Click a cell or drag an area"}</span><label><select aria-label='Fill behavior' data-change='fill-behavior'><option value='empty' ${if(!replaceFill) "selected" else ""}>Fill empty space</option><option value='replace' ${if(replaceFill) "selected" else ""}>Replace contained blocks</option></select></label>${button("Space · Fill","fill-area",cls="tiny")}${button("Delete · Clear","clear-area",cls="tiny")}${button("Esc · Deselect","deselect",cls="tiny")}</div><p class='fill-help'>Fill: ${text(project.name(assetBrush))}. ${if(replaceFill) "Replaces fully contained blocks of other types." else "Keeps existing placements and fills empty space."} Whole blocks only; partial boundary overlaps stay intact.</p>"
}
fun selectionOverlay(b: Block): String = mapSelection?.let {"<div class='area-selection' style='left:${it.x*100.0/b.width}%;top:${it.y*100.0/b.height}%;width:${it.width*100.0/b.width}%;height:${it.height*100.0/b.height}%'></div>"} ?: ""
fun openRadial(position: String="") {
    val coords=position.split(',').mapNotNull {it.toIntOrNull()}
    if(coords.size==2) {radialX=coords[0];radialY=coords[1];radialPage=0}
    val b=currentBlock() ?: return
    val assets=activePaletteGroup(b)?.assets.orEmpty()
    val pages=maxOf(1,(assets.size+7)/8);radialPage=radialPage.coerceIn(0,pages-1)
    val page=assets.drop(radialPage*8).take(8)
    val choices=page.mapIndexed { i,id ->
        val angle=-PI/2 + i*2*PI/page.size
        "<button class='radial-choice ${if(assetBrush==id) "selected" else ""}' style='left:${180+sin(angle+PI/2)*123}px;top:${180-cos(angle+PI/2)*123}px' ${attr("asset",id)} aria-label='Pick ${text(project.name(id))}'><span class='radial-art'>${svg(id)}</span><span>${text(project.name(id))}</span><kbd>${hotkey(id)}</kbd></button>"
    }.joinToString("")
    showRadial("<div class='radial-center'><b>${text(activePaletteGroup(b)?.name ?: "Blocks")}</b><div>${button("‹ Group","palette-radial-group","-1",cls="tiny")}${button("Group ›","palette-radial-group","1",cls="tiny")}</div><small>${radialPage+1} / $pages</small><div>${button("‹","radial-page","-1",cls="tiny")}${button("×","radial-close",cls="tiny")}${button("›","radial-page","1",cls="tiny")}</div></div>$choices",radialX,radialY)
}
fun assignHotkey(asset: String, key: String, confirmed: Boolean = false) {
    val previous = project.hotkeys[key]
    if(!confirmed && previous != null && previous != asset) {
        hotkeyConflict = asset to key
        return
    }
    hotkeyConflict = null
    change { p -> p.copy(hotkeys=p.hotkeys.filter {it.value!=asset && it.key!=key} + if(key.isBlank()) emptyMap() else mapOf(key to asset)) }
}
fun handleMapAction(kind: String,id: String,v: String): Boolean {
    when(kind) {
        "map-context" -> {
            val b=currentBlock() ?: return true
            val (x,y)=id.split(',').map {it.toInt()}
            val hit=b.children.lastOrNull { c -> val (w,h)=project.size(c.assetId)
                x>=c.x && y>=c.y && x<c.x+w && y<c.y+h }
            val coords=v.split(',').map {it.toInt()}
            if(hit==null) openRadial(v) else showRadial(
                "<div class='radial-center'><b>${text(project.name(hit.assetId))}</b>${button("Configure","map-configure",hit.id)}${button("Choose block","radial-open",v)}${button("Cancel","radial-close")}</div>",coords[0],coords[1])
            return true
        }
        "map-configure" -> {
            val parent=currentBlock() ?: return true
            rememberLocation()
            change {it.configurePlacement(parent.id,id,::uuid)}
            val wrapper=project.blocks.first {it.id==parent.id}.children.first {it.id==id}.assetId
            closeRadial();select(wrapper);tab="Properties"
        }
        "palette-group" -> {
            paletteGroup=id;radialPage=0
            assetBrush=activePaletteGroup(currentBlock()!!)?.assets?.firstOrNull() ?: ""
        }
        "palette-step-group", "palette-radial-group" -> {
            val b=currentBlock() ?: return true
            val groups=project.paletteGroups(b.id)
            if(groups.isEmpty()) return true
            val index=groups.indexOfFirst {it.id==activePaletteGroup(b)?.id}
            val group=groups[(index+id.toInt()+groups.size)%groups.size]
            paletteGroup=group.id;assetBrush=group.assets.first();radialPage=0
            if(kind=="palette-radial-group") {openRadial();return true}
        }
        "palette-step-asset" -> {
            val assets=activePaletteGroup(currentBlock()!!)?.assets.orEmpty()
            if(assets.isEmpty()) return true
            assetBrush=assets[(assets.indexOf(assetBrush).coerceAtLeast(0)+id.toInt()+assets.size)%assets.size]
        }

        "hotkey-confirm" -> {hotkeyConflict?.let {assignHotkey(it.first,it.second,true)}}
        "hotkey-cancel" -> hotkeyConflict=null
        "map-tool" -> mapTool=id
        "bind-hotkey" -> assignHotkey(id,v)
        "map-key" -> {
            if(v=="assign") {require(assetBrush.isNotBlank()) {"Choose an asset first"};assignHotkey(assetBrush,id)}
            else { val target=project.hotkeys[id] ?: return true;require(target in project.compositionAssets(currentBlock()!!.id)) {"Hotkey $id is not available for this canvas"};message="";assetBrush=target;paletteGroup="";val area=mapSelection ?: error("Select a cell or area first");change {it.fillArea(currentBlock()!!.id,target,area,snap,true,::uuid)} }
        }
        "map-apply" -> {
            val b=currentBlock() ?: return true
            require(assetBrush.isNotBlank()) {"Choose a block from the palette first"}
            val (x,y)=id.split(',').map {it.toInt()}
            val (width,height)=project.size(assetBrush)
            val area=MapArea(x,y,width,height)
            change {it.fillArea(b.id,assetBrush,area,snap,true,::uuid)}
            mapSelection=area;selectionOwner=b.id;message=""
        }
        "map-select" -> {
            val b=currentBlock()!!;val coords=id.split(',').map {it.toInt()}
            val x=min(coords[0],coords[2]);val y=min(coords[1],coords[3])
            mapSelection=MapArea(x,y,min(b.width,max(coords[0],coords[2])+snap)-x,min(b.height,max(coords[1],coords[3])+snap)-y)
            selectionOwner=b.id
        }
        "map-copy", "map-cut" -> {
            val b=currentBlock()!!;val area=mapSelection ?: error("Select a cell or area first")
            val copied=project.copyArea(b.id,area)
            require(copied.placements.isNotEmpty()) {"Select at least one whole item to copy"}
            if(kind=="map-cut") change {it.clearArea(b.id,area)}
            mapClipboard=copied;message="Copied ${copied.placements.size} items"
        }
        "map-paste" -> {
            val b=currentBlock()!!;val area=mapSelection ?: error("Select a destination cell first")
            val copied=mapClipboard ?: error("Copy an area first")
            change {it.pasteArea(b.id,copied,area.x,area.y,::uuid)}
            mapSelection=MapArea(area.x,area.y,copied.width,copied.height)
            message="Pasted ${copied.placements.size} items"
        }
        "fill-behavior" -> replaceFill=v=="replace"
        "fill-area" -> {message="";val b=currentBlock()!!;val area=mapSelection ?: error("Drag to select an area first");change {it.fillArea(b.id,assetBrush,area,snap,replaceFill,::uuid)}}
        "clear-area" -> {message="";val b=currentBlock()!!;val area=mapSelection ?: error("Drag to select an area first");change {it.clearArea(b.id,area)}}
        "deselect" -> {mapSelection=null;closeRadial()}
        "radial-open" -> {openRadial(id);return true}
        "radial-page" -> {radialPage+=id.toInt();openRadial();return true}
        "radial-close" -> {closeRadial();return true}
        else -> return false
    }
    render();updateSaveStatus();focusMap();return true
}

var paletteGroup = ""
fun activePaletteGroup(b: Block): AssetGroup? {
    val groups=project.paletteGroups(b.id)
    val group=groups.find {it.id==paletteGroup} ?: groups.find {assetBrush in it.assets} ?: groups.firstOrNull()
    paletteGroup=group?.id ?: ""
    return group
}
fun groupedPalette(b: Block): String {
    val group=activePaletteGroup(b)
    return "<div class='palette-groups' aria-label='Block groups'>" + project.paletteGroups(b.id).joinToString("") {
        button("${text(it.name)} (${it.assets.size})","palette-group",it.id,cls=if(it.id==group?.id) "tiny active" else "tiny")
    } + "</div><div class='palette-stepper'>${button("‹ Group","palette-step-group","-1",cls="tiny")}${button("Group ›","palette-step-group","1",cls="tiny")}${button("↑ Tile","palette-step-asset","-1",cls="tiny")}${button("Tile ↓","palette-step-asset","1",cls="tiny")}</div>" +
        "<small class='palette-shortcuts'>Alt + ←/→: group · Alt + ↑/↓: tile</small>" +
        "<div class='asset-list'>${group?.assets?.joinToString("") {mapAssetCard(it)} ?: "No available blocks"}</div>"
}
