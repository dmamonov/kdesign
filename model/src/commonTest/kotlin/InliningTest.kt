package kdesign

import kotlin.test.*

class InliningTest {
    private fun project(): Project = Project(version=2, tiles=listOf(Tile("t","Tile")), blocks=listOf(
        Block("inner","Inner","meta",16,16,listOf(Placement("tile","t",0,0))),
        Block("source","Source","meta",32,16,listOf(Placement("nested","inner",16,0))),
        Block("root","Root","meta",128,128,listOf(
            Placement("a","source",16,32),Placement("b","source",64,32)))
    ), levels=listOf(Level("level","Level","root")))

    @Test fun expandsEveryUsageWithOffsetsAndFreshIdsAndKeepsDefinition() {
        val p=project()
        var serial=0
        val result=p.inlineMetaBlock("source") {"fresh-${serial++}"}
        assertEquals(2,p.placementUsageCount("source"))
        assertEquals(0,result.placementUsageCount("source"))
        assertEquals(p.blocks.first(),result.blocks.first())
        assertEquals(p.blocks[1],result.blocks[1])
        assertEquals(listOf(32 to 32,80 to 32),result.blocks.last().children.map {it.x to it.y})
        assertTrue(result.blocks.last().children.all {it.assetId=="inner"})
        assertEquals(2,result.usages("t").count {it.levelId=="level"})
    }

    @Test fun rewritesConnectionsToNestedInstances() {
        val base=project()
        val p=base.copy(blocks=base.blocks.map {if(it.id=="root") it.copy(
            portals=listOf(Portal("Go","level",listOf("b","nested")))) else it})
        var serial=0
        val result=p.inlineMetaBlock("source") {"new-${serial++}"}
        assertEquals(listOf("new-1"),result.blocks.last().portals.single().targetPath)
        result.validate()
    }

    @Test fun refusesToLoseConnectionTargetIdentity() {
        val base=project()
        val p=base.copy(blocks=base.blocks.map {if(it.id=="root") it.copy(
            portals=listOf(Portal("Go","level",listOf("b")))) else it})
        var serial=0
        assertFailsWith<IllegalStateException> {p.inlineMetaBlock("source") {"new-${serial++}"}}
        assertEquals(2,p.placementUsageCount("source"))
    }
}
