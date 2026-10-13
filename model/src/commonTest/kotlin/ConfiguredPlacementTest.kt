package kdesign

import kotlin.test.*

class ConfiguredPlacementTest {
    private fun fixture(): Project = Project(version=3,
        types=listOf(BlockType.Empty,
            BlockType.SimpleType("health","Health",PrimitiveValue.IntegerValue(10)),
            BlockType.CompositeType("enemy","Enemy",listOf("health"))),
        tiles=listOf(Tile("tile","Tile",typeId="enemy",overrides=mapOf("health" to PrimitiveValue.IntegerValue(20)))),
        blocks=listOf(Block("root","Root","meta",64,32,children=listOf(
            Placement("a","tile",16,0,mapOf("health" to PrimitiveValue.IntegerValue(30))),
            Placement("b","tile",32,0)))))

    @Test fun wrapsOnlyChosenPlacementAndInheritsItsValues() {
        val original=fixture()
        var n=0
        val p=original.configurePlacement("root","a") {"new-${n++}"}
        val root=p.blocks.first()
        val wrapper=p.blocks.last()
        assertTrue(wrapper.anonymous)
        assertEquals(16,root.children.first().x)
        assertEquals(wrapper.id,root.children.first().assetId)
        assertEquals(original.blocks.first().children.last(),root.children.last())
        assertEquals("tile",wrapper.children.single().assetId)
        assertEquals(0,wrapper.children.single().x)
        assertEquals(PrimitiveValue.IntegerValue(30),p.effectiveValues(wrapper.id)["health"])
        assertEquals(listOf("enemy"),(p.types.last() as BlockType.CompositeType).parentTypes)
        assertEquals(p,p.configurePlacement("root","a") {error("Must reuse private wrapper")})
        assertTrue(p.paletteGroups("root").flatMap {it.assets}.none {it==wrapper.id})
        p.validate()
    }

    @Test fun directPlacementDoesNotCreateWrappers() {
        val p=fixture()
        val filled=p.fillArea("root","tile",MapArea(0,16,16,16),16,true) {"new"}
        assertEquals(p.blocks.size,filled.blocks.size)
        assertEquals(p.types,filled.types)
        assertEquals("tile",filled.blocks.first().children.last().assetId)
    }

    @Test fun groupsByParentsWithoutCreatingThirdLevel() {
        val p=fixture().copy(types=fixture().types+BlockType.CompositeType("collectible","Collectible"))
        val multi=p.copy(types=p.types.map {if(it.id=="enemy") BlockType.CompositeType("enemy","Enemy",listOf("health","collectible")) else it})
        val groups=multi.paletteGroups("root")
        assertEquals(listOf("health","collectible"),groups.map {it.id})
        assertTrue(groups.all {it.assets==listOf("tile")})
    }

    @Test fun preservesPortalPathsThroughWrappedMetaBlocks() {
        val leaf=Block("leaf","Leaf","meta")
        val inner=Block("inner","Inner","meta",children=listOf(Placement("nested","leaf",0,0)))
        val root=Block("root","Root","meta",children=listOf(Placement("placed","inner",0,0)),
            portals=listOf(Portal("Go","level",listOf("placed","nested"))))
        val p=Project(version=3,blocks=listOf(root,inner,leaf),levels=listOf(Level("level","Level","root")))
        var n=0
        val result=p.configurePlacement("root","placed") {"new-${n++}"}
        assertEquals(listOf("placed","new-2","nested"),result.blocks.first().portals.single().targetPath)
        result.validate()
    }
}
