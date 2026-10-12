package kdesign

import kotlin.test.*

class DeletionTest {
    @Test fun deletesUsedTileWithBlocksPlacementsAndHotkeys() {
        val p=sampleProject();val id=p.tiles.first().id
        val changed=p.copy(hotkeys=mapOf("1" to id)).deleteObject(id)
        assertFalse(changed.tiles.any {it.id==id})
        assertFalse(changed.blocks.any {it.id=="block-mushroom"})
        assertTrue(changed.blocks.none {b->b.children.any {it.assetId=="block-mushroom"}})
        assertTrue(changed.blocks.none {b->b.children.any {it.assetId==id}})
        assertTrue(changed.hotkeys.isEmpty())
    }
    @Test fun deletesMetaInstanceAndIncomingPortalPath() {
        val p=sampleProject();val changed=p.deleteObject("meta-entrance")
        assertFalse(changed.blocks.any {it.id=="meta-entrance"})
        assertTrue(changed.blocks.flatMap {it.portals}.none {"cave-door" in it.targetPath})
        changed.validate()
    }
    @Test fun deletesLevelAndReparentsSublevelsKeepingAssets() {
        val p=sampleProject();val changed=p.deleteObject("level-woods")
        assertNull(changed.levels.first {it.id=="level-cave"}.parentId)
        assertEquals(p.blocks.size,changed.blocks.size)
        assertTrue(changed.blocks.flatMap {it.portals}.none {it.targetLevelId=="level-woods"})
    }
    @Test fun refusesRootDeletionAndUnknownIds() {
        val p=sampleProject()
        assertFailsWith<IllegalArgumentException> {p.deleteObject(p.levels.first().rootId)}
        assertFailsWith<IllegalArgumentException> {p.deleteObject("missing")}
    }
    @Test fun deletesConceptAndDetachesTextAndSemanticLinks() {
        val p=sampleProject();val c=p.concepts.first()
        val changed=p.copy(title="About [[${c.id}]]").deleteObject(c.id)
        assertFalse(changed.concepts.any {it.id==c.id})
        assertEquals("About ${p.displayText(c.name)}",changed.title)
        assertTrue(changed.blocks.none {c.id in it.conceptIds})
    }
    @Test fun deletesBasicBlockPreservingItsTiles() {
        val p=sampleProject();val changed=p.deleteObject("block-mushroom")
        assertEquals(p.tiles,changed.tiles)
        assertTrue(changed.blocks.none {b->b.children.any {it.assetId=="block-mushroom"}})
    }
    @Test fun cleanupRemovesEmptyBasicBlocksAndTheirReferencesOnly() {
        val p=sampleProject()
        val empty=Block("empty","Empty")
        val meta=Block("container","Container","meta")
        val source=p.copy(title="Use [[empty]]",blocks=p.blocks.map {
            if(it.id=="meta-clearing") it.copy(children=it.children+Placement("empty-use","empty",0,0)) else it
        }+listOf(empty,meta),hotkeys=mapOf("e" to "empty"))
        val result=source.cleanupEmptyBlocks()
        assertFalse(result.blocks.any {it.id=="empty"})
        assertTrue(result.blocks.any {it.id=="container"})
        assertTrue(result.blocks.none {b->b.children.any {it.assetId=="empty"}})
        assertEquals("Use Empty",result.title)
        assertTrue(result.hotkeys.isEmpty())
        assertEquals(result,result.cleanupEmptyBlocks())
    }
    @Test fun cascadingTileDeletionDetachesBlockTextReferences() {
        val p=sampleProject().copy(title="Use [[block-mushroom]]",hotkeys=mapOf("m" to "block-mushroom"))
        val result=p.deleteObject("tile-mushroom")
        assertEquals("Use Ember mushroom",result.title)
        assertTrue(result.hotkeys.isEmpty())
        assertEquals(p.levels,result.levels)
        result.validate()
    }
}
