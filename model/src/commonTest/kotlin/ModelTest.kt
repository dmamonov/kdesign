package kdesign

import kotlin.test.*
import kotlinx.serialization.encodeToString

class ModelTest {
    @Test fun sampleRoundTrips() {
        val p=sampleProject();p.validate()
        assertEquals(p,json.decodeFromString<Project>(json.encodeToString(p)))
    }
    @Test fun tracesEveryInstanceAcrossSpaces() {
        val p=sampleProject()
        val uses=p.usages("tile-mushroom").filter {it.levelId!=null}
        assertEquals(6,uses.size)
        assertEquals(2,uses.map {it.levelId}.distinct().size)
        assertTrue(uses.any {it.path==listOf("mushroom-0","tile")})
    }
    @Test fun repeatedNestedDefinitionsKeepDistinctPaths() {
        val p=sampleProject()
        val b=Block("cluster","Cluster","meta",32,16,listOf(Placement("a","block-mushroom",0,0),Placement("b","block-mushroom",16,0)))
        val root=Block("root","Root","meta",64,16,listOf(Placement("left",b.id,0,0),Placement("right",b.id,32,0)))
        val q=p.copy(blocks=p.blocks+listOf(b,root),levels=p.levels+Level("nested","Nested",root.id))
        q.validate()
        assertEquals(4,q.usages("block-mushroom").count {it.levelId=="nested"})
    }
    @Test fun rejectsCyclesButAllowsPortalCycles() {
        val p=sampleProject();p.validate()
        val b=Block("cycle","Cycle","meta",16,16,listOf(Placement("self","cycle",0,0)))
        assertFailsWith<IllegalArgumentException> {p.copy(blocks=p.blocks+b).validate()}
    }
    @Test fun rejectsBrokenReferencesAndPixels() {
        val p=sampleProject()
        assertFails {p.copy(tiles=p.tiles.drop(1)).validate()}
        assertFails {p.copy(tiles=p.tiles.map {it.copy(pixels=listOf(90))}).validate()}
        assertFails {p.copy(blocks=p.blocks.map {if(it.id=="meta-entrance") it.copy(portals=listOf(Portal("Broken","level-cave",listOf("missing")))) else it}).validate()}
        assertFails {p.copy(levels=p.levels.map {it.copy(parentId=it.id)}).validate()}
    }
    @Test fun conceptsTraceThroughAttachedBlocks() {
        assertEquals(6, sampleProject().usages("concept-quest").count {it.levelId != null})
    }
    @Test fun findsIncomingPortalReferences() {
        assertTrue(sampleProject().usages("meta-entrance").any {it.label.startsWith("Portal reference")})
    }
}
