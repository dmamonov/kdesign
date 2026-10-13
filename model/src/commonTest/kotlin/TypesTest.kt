package kdesign

import kotlinx.serialization.encodeToString
import kotlin.test.*

class TypesTest {
    private val health = BlockType.SimpleType("health","Health",PrimitiveValue.IntegerValue(10))
    private val enemy = BlockType.CompositeType("enemy","Enemy",listOf("health"))
    private fun fixture() = Project(version=3,types=listOf(BlockType.Empty,health,enemy),
        tiles=listOf(Tile("tile","Enemy",typeId="enemy",overrides=mapOf("health" to PrimitiveValue.IntegerValue(20)))),
        blocks=listOf(Block("root","Root","meta",32,16,children=listOf(
            Placement("placed","tile",0,0,mapOf("health" to PrimitiveValue.IntegerValue(30)))))))

    @Test fun resolvesDefaultsBlockAndPlacementAndRoundTrips() {
        val p=fixture()
        p.validate()
        assertEquals(PrimitiveValue.IntegerValue(20),p.effectiveValues("tile")["health"])
        assertEquals(PrimitiveValue.IntegerValue(30),p.effectiveValues("tile",p.blocks.single().children.single())["health"])
        assertEquals(p,json.decodeFromString<Project>(json.encodeToString(p)))
        val reset=p.copy(tiles=p.tiles.map {it.copy(overrides=emptyMap())})
        assertEquals(PrimitiveValue.IntegerValue(10),reset.effectiveValues("tile")["health"])
        val updated=reset.copy(types=reset.types.map {if(it.id=="health") health.copy(defaultValue=PrimitiveValue.IntegerValue(40)) else it})
        assertEquals(PrimitiveValue.IntegerValue(40),updated.effectiveValues("tile")["health"])
        assertEquals(PrimitiveValue.IntegerValue(30),updated.effectiveValues("tile",updated.blocks.single().children.single())["health"])
    }

    @Test fun deduplicatesDiamondsAndRejectsCyclesAndNameCollisions() {
        val p=fixture().copy(types=fixture().types+listOf(
            BlockType.CompositeType("other","Other",listOf("health")),
            BlockType.CompositeType("diamond","Diamond",listOf("enemy","other"))))
        assertEquals(listOf(health),p.typeFields("diamond"))
        assertFails {p.copy(types=p.types.map {if(it.id=="enemy") enemy.copy(parentTypes=listOf("diamond")) else it}).validate()}
        assertFails {p.copy(types=p.types+BlockType.SimpleType("duplicate","Health",PrimitiveValue.StringValue(""))+
            BlockType.CompositeType("bad","Bad",listOf("health","duplicate"))).validate()}
    }

    @Test fun validatesEnumsReferencesAndOverrides() {
        val p=fixture()
        assertFails {p.copy(tiles=p.tiles.map {it.copy(overrides=mapOf("health" to PrimitiveValue.StringValue("bad")))}).validate()}
        assertFails {p.copy(types=p.types+BlockType.SimpleType("enum","State",PrimitiveValue.EnumValue("bad",PrimitiveType.PrimitiveEnum(listOf("ok"))))).validate()}
        assertFails {p.copy(types=p.types+BlockType.SimpleType("ref","Target",PrimitiveValue.ReferenceValue("missing"))).validate()}
        assertFails {p.deleteObject("enemy")}
        val linked=p.copy(types=p.types+BlockType.SimpleType("ref","Target",PrimitiveValue.ReferenceValue("tile")))
        assertTrue(linked.usages("tile").any {it.ownerId=="ref"})
        val deleted=linked.deleteObject("tile")
        assertEquals(PrimitiveValue.ReferenceValue(),(deleted.types.last() as BlockType.SimpleType).defaultValue)
    }

    @Test fun migrationCreatesTypesAndPreservesLegacyPropertiesAndPixels() {
        val old=sampleProject().simplifyBlocks()
        val migrated=old.withBlockTypes()
        migrated.validate()
        assertEquals(3,migrated.version)
        assertTrue(migrated.tiles.all {it.typeId!=EMPTY_TYPE_ID})
        assertTrue(migrated.blocks.all {it.typeId!=EMPTY_TYPE_ID && it.properties.isEmpty()})
        assertEquals(old.tiles.map {it.pixels},migrated.tiles.map {it.pixels})
        old.blocks.forEach { block ->
            val defaults=migrated.typeFields(migrated.assetType(block.id)).associate {it.name to it.defaultValue.textValue()}
            assertEquals(block.properties,defaults)
        }
        assertEquals(migrated,migrated.withBlockTypes())
        val added=migrated.addTile(Tile("new","New"))
        assertTrue(added.tiles.last().typeId!=EMPTY_TYPE_ID)
    }

    @Test fun refusesToDiscardTypedValuesWhenInlining() {
        val p=fixture().copy(blocks=fixture().blocks+Block("typed","Typed","meta",16,16,typeId="enemy"))
        val placed=p.copy(blocks=p.blocks.map {if(it.id=="root") it.copy(children=listOf(Placement("use","typed",0,0))) else it})
        assertFails {placed.inlineMetaBlock("typed") {"new"}}
    }
}
