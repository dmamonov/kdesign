package kdesign

/** A new tile and its map-ready block are one atomic edit. */
fun Project.addTileWithBlock(tile: Tile, blockId: String, placementId: String): Project = copy(
    tiles=tiles+tile,
    blocks=blocks+Block(blockId,tile.name,"block",tile.size,tile.size,
        children=listOf(Placement(placementId,tile.id,0,0)))
).also {it.validate()}

/** Never resurrect a deleted tile or overwrite drawing done while generation was running. */
fun Project.applyGeneratedPixels(original: Tile, pixels: List<Int>): Project {
    val current=tiles.find {it.id==original.id} ?: error("The tile was removed while generating")
    require(current.size==original.size && current.pixels==original.pixels) {
        "The tile was edited while generating. Your edits were kept; retry generation when ready."
    }
    return copy(tiles=tiles.map {if(it.id==original.id) it.copy(pixels=pixels) else it})
        .also {it.validate()}
}
