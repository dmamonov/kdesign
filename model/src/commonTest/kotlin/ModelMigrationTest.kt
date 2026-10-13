package kdesign

import kotlin.test.*

class ModelMigrationTest {
    @Test fun collapsesWrappersAndPreservesPlacementsReferencesAndHotkeys() {
        val tile = Tile("tile", "Stone")
        val wrapper = Block("wrapper", tile.name, children=listOf(Placement("pixel", tile.id, 0, 0)))
        val root = Block("root", "Room", "meta", 256, 240,
            listOf(Placement("placed", wrapper.id, 32, 48)))
        val source = Project(title="Use [[wrapper]]", tiles=listOf(tile), blocks=listOf(wrapper, root),
            levels=listOf(Level("level", "[[wrapper]]", root.id)), hotkeys=mapOf("s" to wrapper.id))
        val migrated = source.simplifyBlocks()
        assertEquals(2, migrated.version)
        assertEquals(listOf(root.id), migrated.blocks.map {it.id})
        assertEquals(Placement("placed", tile.id, 32, 48), migrated.blocks.single().children.single())
        assertEquals("Use [[tile]]", migrated.title)
        assertEquals("[[tile]]", migrated.levels.single().title)
        assertEquals(tile.id, migrated.hotkeys["s"])
        assertTrue(migrated.usages(tile.id).any {it.levelId=="level"})
        assertEquals(migrated, migrated.simplifyBlocks())
        val filled = migrated.fillArea(root.id, tile.id, MapArea(0,0,16,16),16,false) {"new"}
        assertEquals(2, filled.blocks.single().children.size)
        assertTrue(filled.deleteObject(tile.id).blocks.single().children.isEmpty())
    }

    @Test fun authoredBlocksBecomeMetaBlocksWithoutLosingSemantics() {
        val source = sampleProject()
        val migrated = source.simplifyBlocks()
        assertTrue(migrated.blocks.all {it.kind=="meta"})
        val before = source.blocks.first {it.id=="block-mushroom"}
        assertEquals(before.copy(kind="meta"), migrated.blocks.first {it.id==before.id})
        assertEquals(source.levels, migrated.levels)
        assertEquals(source.blocks.flatMap {it.portals}, migrated.blocks.flatMap {it.portals})
        migrated.validate()
    }

    @Test fun duplicateWrapperHotkeysRemainValidAndVersionTwoRejectsLegacyBlocks() {
        val tile = Tile("tile", "Stone")
        val wrapper = Block("wrapper", tile.name, children=listOf(Placement("p",tile.id,0,0)))
        val source = Project(tiles=listOf(tile), blocks=listOf(wrapper),
            hotkeys=mapOf("a" to tile.id, "b" to wrapper.id))
        assertEquals(mapOf("a" to tile.id), source.simplifyBlocks().hotkeys)
        assertFails {source.copy(version=2).validate()}
    }
}
