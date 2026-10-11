package kdesign

import kotlin.test.*

class TileCreationTest {
    @Test fun tileAndBlockAreCreatedTogetherAndShareGeneratedPixels() {
        val tile=Tile("tile-new","Treasure chest",8)
        val p=sampleProject().addTileWithBlock(tile,"block-new","placed-tile")
        val b=p.blocks.first {it.id=="block-new"}
        assertEquals(tile.name,b.name)
        assertEquals(8,b.width);assertEquals(8,b.height)
        assertEquals(listOf(Placement("placed-tile",tile.id,0,0)),b.children)
        val generated=p.applyGeneratedPixels(tile,List(64) {22})
        assertEquals(List(64) {22},generated.tiles.first {it.id==tile.id}.pixels)
        assertEquals(p.blocks,generated.blocks)
        assertTrue(generated.usages(tile.id).isNotEmpty())
    }
    @Test fun lateResultsCannotOverwriteEditsOrRestoreDeletedTiles() {
        val tile=Tile("tile-new","Chest",8)
        val p=sampleProject().addTileWithBlock(tile,"block-new","placed-tile")
        val edited=p.applyGeneratedPixels(tile,List(64) {1})
        assertFails {edited.applyGeneratedPixels(tile,List(64) {2})}
        assertFails {p.deleteObject(tile.id).applyGeneratedPixels(tile,List(64) {2})}
        assertFails {p.applyGeneratedPixels(tile,listOf(1))}
    }
}
