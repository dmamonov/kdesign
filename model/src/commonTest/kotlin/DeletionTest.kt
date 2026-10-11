package kdesign

import kotlin.test.*

class DeletionTest {
    @Test fun deletesUsedTileAndHotkeyWithoutDeletingBlocks() {
        val p=sampleProject();val id=p.tiles.first().id
        val changed=p.copy(hotkeys=mapOf("1" to id)).deleteObject(id)
        assertFalse(changed.tiles.any {it.id==id})
        assertEquals(p.blocks.size,changed.blocks.size)
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
}
