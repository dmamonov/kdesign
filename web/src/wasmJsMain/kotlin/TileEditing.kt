package kdesign.web

import kdesign.*

var tileSelecting=false
var tileSelection: MapArea?=null
var tileSelectionOwner=""
fun tileSelectionAttributes(t: Tile): String {
    if(tileSelectionOwner!=t.id) {tileSelection=null;tileSelectionOwner=t.id}
    val area=tileSelection
    return "data-tile-size='${t.size}' data-tile-select='$tileSelecting' data-tile-area='${area?.let {"${it.x},${it.y},${it.width},${it.height}"} ?: ""}'"
}
fun tileSelectionOverlay(t: Tile): String = tileSelection?.let {
    "<div class='tile-selection' style='left:${it.x*100.0/t.size}%;top:${it.y*100.0/t.size}%;width:${it.width*100.0/t.size}%;height:${it.height*100.0/t.size}%'></div>"
} ?: ""
fun handleTileSelection(kind: String,id: String): Boolean {
    when(kind) {
        "tile-select-tool" -> tileSelecting=true
        "tile-deselect" -> tileSelection=null
        "tile-select-area" -> {
            val t=currentTile() ?: return true
            val c=id.split(',').map {it.toInt()}
            val area=MapArea(c[0],c[1],c[2],c[3])
            require(area.x>=0 && area.y>=0 && area.width>0 && area.height>0 && area.x+area.width<=t.size && area.y+area.height<=t.size)
            tileSelection=area;tileSelectionOwner=t.id
        }
        "tile-move-area" -> {
            val t=currentTile() ?: return true
            val area=tileSelection ?: return true
            val c=id.split(',').map {it.toInt()}
            val moved=t.moveArea(area,c[0],c[1])
            change {p->p.copy(tiles=p.tiles.map {if(it.id==t.id) moved else it})}
            tileSelection=area.copy(x=c[0],y=c[1])
        }
        else -> return false
    }
    render();updateSaveStatus();focusTile();return true
}
@JsFun("() => document.querySelector('.pixel-grid')?.focus({preventScroll:true})")
external fun focusTile()
