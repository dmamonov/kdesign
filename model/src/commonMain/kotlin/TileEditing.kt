package kdesign

/** Move a pixel rectangle atomically, including transparent pixels, with overlap-safe copying. */
fun Tile.moveArea(area: MapArea, x: Int, y: Int): Tile {
    require(area.width>0 && area.height>0 && area.x>=0 && area.y>=0 &&
        area.x+area.width<=size && area.y+area.height<=size) {"Selection is outside the tile"}
    require(x>=0 && y>=0 && x+area.width<=size && y+area.height<=size) {"Selection does not fit here"}
    if(x==area.x && y==area.y) return this
    val result=pixels.toMutableList()
    for(dy in 0 until area.height) for(dx in 0 until area.width)
        result[(area.y+dy)*size+area.x+dx]=-1
    for(dy in 0 until area.height) for(dx in 0 until area.width)
        result[(y+dy)*size+x+dx]=pixels[(area.y+dy)*size+area.x+dx]
    return copy(pixels=result)
}
