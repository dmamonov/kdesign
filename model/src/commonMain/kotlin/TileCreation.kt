package kdesign

/** A tile is directly placeable as a one-cell block. */
fun Project.addTile(tile: Tile): Project = copy(tiles=tiles+tile).let {if(version>=3) it.withBlockTypes(setOf(tile.id)) else it}.also {it.validate()}

/** Never resurrect a deleted tile or overwrite drawing done while generation was running. */
fun Project.applyGeneratedPixels(original: Tile, pixels: List<Int>): Project {
    val current=tiles.find {it.id==original.id} ?: error("The tile was removed while generating")
    require(current.size==original.size && current.pixels==original.pixels) {
        "The tile was edited while generating. Your edits were kept; retry generation when ready."
    }
    return copy(tiles=tiles.map {if(it.id==original.id) it.copy(pixels=pixels) else it})
        .also {it.validate()}
}
