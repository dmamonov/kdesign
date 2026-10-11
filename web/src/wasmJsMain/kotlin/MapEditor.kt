package kdesign.web

import kdesign.*
import kotlin.math.*

var mapTool="place"
var mapSelection: MapArea?=null
var selectionOwner=""
var replaceFill=false
var hotkeyConflict: Pair<String,String>? = null
var radialPage=0
var radialX=0
var radialY=0
@JsFun("(html, x, y) => window.designer.showRadial(html, x, y)") external fun showRadial(html: String,x: Int,y: Int)
@JsFun("() => document.getElementById('block-picker')?.remove()") external fun closeRadial()
fun hotkey(id: String) = project.hotkeys.entries.find {it.value==id}?.key ?: ""
fun mapAssetCard(id: String): String = "<div class='map-asset'>" + button("<span class='asset-thumb'>${svg(id)}</span><span>${text(project.name(id))}</span><kbd>${hotkey(id).ifBlank {"—"}}</kbd>","asset",id,cls="asset-card ${if(assetBrush==id) "selected" else ""}") +
    "<select aria-label='Hotkey for ${text(project.name(id))}' data-change='bind-hotkey' data-id='${esc(id)}'><option value=''>No hotkey</option>${("1234567890").map { key -> "<option value='$key' ${if(hotkey(id)==key.toString()) "selected" else ""}>$key</option>" }.joinToString("")}</select></div>"
fun mapToolbar() = "<div class='map-tools'>"+listOf("inspect" to "I · Inspect", "place" to "B · Place", "select" to "V · Select area", "erase" to "E · Remove").joinToString("") {(mode,label)->button(label,"map-tool",mode,cls="tool ${if(mapTool==mode) "active" else ""}")}+button("Space · Blocks","radial-open",cls="tool")+"</div>"
fun selectionBar(): String {
    val area=mapSelection
    return "<div class='selection-bar'><span id='selection-label'>${area?.let {"${it.width} × ${it.height} px selected"} ?: "V: drag to select an area"}</span><label><select aria-label='Fill behavior' data-change='fill-behavior'><option value='empty' ${if(!replaceFill) "selected" else ""}>Fill empty space</option><option value='replace' ${if(replaceFill) "selected" else ""}>Replace contained blocks</option></select></label>${button("F · Fill area","fill-area",cls="tiny")}${button("Delete · Clear","clear-area",cls="tiny")}${button("Esc · Deselect","deselect",cls="tiny")}</div><p class='fill-help'>Fill: ${text(project.name(assetBrush))}. ${if(replaceFill) "Replaces fully contained blocks of other types." else "Keeps existing placements and fills empty space."} Whole blocks only; partial boundary overlaps stay intact.</p>"
}
fun selectionOverlay(b: Block): String = mapSelection?.let {"<div class='area-selection' style='left:${it.x*100.0/b.width}%;top:${it.y*100.0/b.height}%;width:${it.width*100.0/b.width}%;height:${it.height*100.0/b.height}%'></div>"} ?: ""
fun openRadial(position: String="") {
    val coords=position.split(',').mapNotNull {it.toIntOrNull()}
    if(coords.size==2) {radialX=coords[0];radialY=coords[1];radialPage=0}
    val b=currentBlock() ?: return
    val assets=project.compositionAssets(b.id)
    val pages=maxOf(1,(assets.size+7)/8);radialPage=radialPage.coerceIn(0,pages-1)
    val page=assets.drop(radialPage*8).take(8)
    val choices=page.mapIndexed { i,id ->
        val angle=-PI/2 + i*2*PI/page.size
        "<button class='radial-choice ${if(assetBrush==id) "selected" else ""}' style='left:${180+sin(angle+PI/2)*123}px;top:${180-cos(angle+PI/2)*123}px' ${attr("asset",id)} aria-label='Pick ${text(project.name(id))}'><span class='radial-art'>${svg(id)}</span><span>${text(project.name(id))}</span><kbd>${hotkey(id)}</kbd></button>"
    }.joinToString("")
    showRadial("<div class='radial-center'><b>Choose a block</b><small>${radialPage+1} / $pages</small><div>${button("‹","radial-page","-1",cls="tiny")}${button("×","radial-close",cls="tiny")}${button("›","radial-page","1",cls="tiny")}</div></div>$choices",radialX,radialY)
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
        "hotkey-confirm" -> {hotkeyConflict?.let {assignHotkey(it.first,it.second,true)}}
        "hotkey-cancel" -> hotkeyConflict=null
        "map-tool" -> mapTool=id
        "bind-hotkey" -> assignHotkey(id,v)
        "map-key" -> {
            if(v=="assign") {require(assetBrush.isNotBlank()) {"Choose an asset first"};assignHotkey(assetBrush,id)}
            else { val target=project.hotkeys[id] ?: return true;require(target in project.compositionAssets(currentBlock()!!.id)) {"Hotkey $id is not available for this canvas"};assetBrush=target;mapTool="place" }
        }
        "map-select" -> {
            val b=currentBlock()!!;val coords=id.split(',').map {it.toInt()}
            val x=min(coords[0],coords[2]);val y=min(coords[1],coords[3])
            mapSelection=MapArea(x,y,min(b.width,max(coords[0],coords[2])+snap)-x,min(b.height,max(coords[1],coords[3])+snap)-y)
            selectionOwner=b.id
        }
        "fill-behavior" -> replaceFill=v=="replace"
        "fill-area" -> {val b=currentBlock()!!;val area=mapSelection ?: error("Drag to select an area first");change {it.fillArea(b.id,assetBrush,area,snap,replaceFill,::uuid)}}
        "clear-area" -> {val b=currentBlock()!!;val area=mapSelection ?: error("Drag to select an area first");change {it.clearArea(b.id,area)}}
        "deselect" -> {mapSelection=null;closeRadial()}
        "radial-open" -> {openRadial(id);return true}
        "radial-page" -> {radialPage+=id.toInt();openRadial();return true}
        "radial-close" -> {closeRadial();return true}
        else -> return false
    }
    render();updateSaveStatus();return true
}
