package kdesign

import kotlin.test.*

class TileCreationTest {
    @Test fun tileIsDirectlyPlaceableWithoutWrapper() {
        val tile=Tile("tile-new","Treasure chest",8)
        val p=sampleProject().addTile(tile)
        assertFalse(p.blocks.any {it.id=="block-new"})
        assertTrue(tile.id in p.compositionAssets("meta-clearing"))
        val generated=p.applyGeneratedPixels(tile,List(64) {22})
        assertEquals(List(64) {22},generated.tiles.first {it.id==tile.id}.pixels)
        assertEquals(p.blocks,generated.blocks)
        assertEquals(8 to 8,generated.size(tile.id))
    }
    @Test fun lateResultsCannotOverwriteEditsOrRestoreDeletedTiles() {
        val tile=Tile("tile-new","Chest",8)
        val p=sampleProject().addTile(tile)
        val edited=p.applyGeneratedPixels(tile,List(64) {1})
        assertFails {edited.applyGeneratedPixels(tile,List(64) {2})}
        assertFails {p.deleteObject(tile.id).applyGeneratedPixels(tile,List(64) {2})}
        assertFails {p.applyGeneratedPixels(tile,listOf(1))}
    }
}
