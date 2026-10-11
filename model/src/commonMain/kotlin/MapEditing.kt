package kdesign

/** Half-open rectangle in the parent block's pixel coordinate space. */
data class MapArea(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun contains(px: Int, py: Int, w: Int, h: Int) = px >= x && py >= y && px+w <= x+width && py+h <= y+height
    fun intersects(px: Int, py: Int, w: Int, h: Int) = px < x+width && px+w > x && py < y+height && py+h > y
}
fun Project.compositionAssets(parentId: String): List<String> {
    val parent=blocks.first {it.id==parentId}
    fun reaches(id: String, seen: Set<String> = emptySet()): Boolean = id==parentId || (id !in seen && blocks.find {it.id==id}?.children?.any {reaches(it.assetId,seen+id)}==true)
    return if(parent.kind=="block") tiles.filter {it.size<=parent.width && it.size<=parent.height}.map {it.id}
    else blocks.filter {it.width<=parent.width && it.height<=parent.height && !reaches(it.id)}.map {it.id}
}
/** A fill is one validated edit. Partial boundary overlaps are preserved, never cropped. */
fun Project.fillArea(parentId: String, assetId: String, area: MapArea, snap: Int, replace: Boolean, newId: () -> String): Project {
    require(snap in listOf(8,16))
    require(assetId in compositionAssets(parentId)) {"This asset cannot be placed in this block"}
    val parent=blocks.first {it.id==parentId}
    require(area.width>0 && area.height>0 && area.x>=0 && area.y>=0 && area.x+area.width<=parent.width && area.y+area.height<=parent.height) {"Selection is outside the canvas"}
    val (w,h)=size(assetId)
    require(w<=area.width && h<=area.height) {"Selected asset does not fit inside this area"}
    val remaining=parent.children.filterNot { c -> val (cw,ch)=size(c.assetId);replace && area.contains(c.x,c.y,cw,ch) && c.assetId!=assetId }.toMutableList()
    val stepX=((w+snap-1)/snap)*snap; val stepY=((h+snap-1)/snap)*snap
    for(y in area.y..area.y+area.height-h step stepY) for(x in area.x..area.x+area.width-w step stepX) {
        if(remaining.none {c -> val (cw,ch)=size(c.assetId);MapArea(x,y,w,h).intersects(c.x,c.y,cw,ch)}) remaining+=Placement(newId(),assetId,x,y)
    }
    return copy(blocks=blocks.map {if(it.id==parentId) it.copy(children=remaining) else it}).also {it.validate()}
}
fun Project.clearArea(parentId: String, area: MapArea): Project = copy(blocks=blocks.map { b ->
    if(b.id!=parentId) b else b.copy(children=b.children.filterNot { c -> val (w,h)=size(c.assetId);area.contains(c.x,c.y,w,h) })
}).also {it.validate()}

/** Clipboard placements are relative to the selection origin and keep shared asset references. */
data class MapClipboard(val width: Int, val height: Int, val placements: List<Placement>)
fun Project.copyArea(parentId: String, area: MapArea): MapClipboard {
    val parent=blocks.first {it.id==parentId}
    return MapClipboard(area.width,area.height,parent.children.filter {c ->
        val (w,h)=size(c.assetId);area.contains(c.x,c.y,w,h)
    }.map {it.copy(x=it.x-area.x,y=it.y-area.y)})
}
fun Project.pasteArea(parentId: String, clipboard: MapClipboard, x: Int, y: Int, newId: () -> String): Project {
    require(clipboard.placements.isNotEmpty()) {"The copied area contains no whole items"}
    val parent=blocks.first {it.id==parentId}
    require(x>=0 && y>=0 && x+clipboard.width<=parent.width && y+clipboard.height<=parent.height) {"The copied area does not fit here"}
    val allowed=compositionAssets(parentId)
    require(clipboard.placements.all {it.assetId in allowed}) {"The copied items are not compatible with this map"}
    val pasted=clipboard.placements.map {it.copy(id=newId(),x=x+it.x,y=y+it.y)}
    val area=MapArea(x,y,clipboard.width,clipboard.height)
    return copy(blocks=blocks.map { b -> if(b.id!=parentId) b else b.copy(
        children=b.children.filterNot {c -> val (w,h)=size(c.assetId);area.contains(c.x,c.y,w,h)}+pasted
    )}).also {it.validate()}
}
