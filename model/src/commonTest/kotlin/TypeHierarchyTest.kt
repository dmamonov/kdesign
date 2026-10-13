package kdesign

import kotlin.test.*

class TypeHierarchyTest {
    @Test fun groupsParentlessTypesAndShowsEveryInheritanceEdge() {
        val field = BlockType.SimpleType("health","Health",PrimitiveValue.IntegerValue(10))
        val base = BlockType.CompositeType("base","Base")
        val enemy = BlockType.CompositeType("enemy","Enemy",listOf("base","health"))
        val boss = BlockType.CompositeType("boss","Boss",listOf("enemy"))
        val p = Project(types=listOf(BlockType.Empty,field,base,enemy,boss))
        p.validate()
        val hierarchy = p.typeHierarchyChildren()
        assertEquals(listOf(field,base),hierarchy[EMPTY_TYPE_ID])
        assertEquals(listOf(enemy),hierarchy["base"])
        assertEquals(listOf(enemy),hierarchy["health"])
        assertEquals(listOf(boss),hierarchy["enemy"])
        assertFalse(hierarchy.values.flatten().any {it.id==EMPTY_TYPE_ID})
        assertTrue(base.parentTypes.isEmpty())
    }
}
