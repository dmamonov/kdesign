package kdesign

import kotlin.test.*
import kotlinx.serialization.encodeToString

class TextReferencesTest {
    @Test fun referencesAcrossAllAuthoredFieldsSurviveRoundTrip() {
        val p=sampleProject().let { it.copy(
            title="Project [[block-mushroom]]",
            tiles=it.tiles.map { t -> if(t.id=="tile-grass") t.copy(name="[[block-mushroom]] ground") else t },
            blocks=it.blocks.map { b -> if(b.id=="meta-entrance") b.copy(name="[[block-mushroom]] door",properties=mapOf("requires [[block-mushroom]]" to "[[block-mushroom]]"),portals=b.portals.map {p->p.copy(label="Enter [[block-mushroom]]")}) else b },
            levels=it.levels.map { l -> if(l.id=="level-woods") l.copy(title="Home of [[block-mushroom]]",notes=levelFields.associateWith {"Find [[block-mushroom]]"}) else l },
            concepts=it.concepts.map { c -> if(c.id=="concept-quest") c.copy(name="Collect [[block-mushroom]]",description="Deliver [[block-mushroom]] and [[block-mushroom]]") else c }
        ) }
        p.validate()
        val copy=json.decodeFromString<Project>(json.encodeToString(p))
        assertEquals(p,copy)
        assertEquals(18,copy.textReferences().count {it.targetId=="block-mushroom"})
        assertEquals(18,copy.usages("block-mushroom").count {it.field!=null})
        assertTrue(copy.usages("block-mushroom").any {it.ownerId=="level-woods" && it.field=="Title"})
    }
    @Test fun resolvesCurrentNamesAfterRename() {
        val p=sampleProject()
        assertEquals("Find Ember mushroom",p.displayText("Find [[block-mushroom]]"))
        val renamed=p.copy(blocks=p.blocks.map {if(it.id=="block-mushroom") it.copy(name="Golden mushroom") else it})
        assertEquals("Find Golden mushroom",renamed.displayText("Find [[block-mushroom]]"))
    }
    @Test fun nameCyclesAreAllowedAndResolveSafely() {
        val p=sampleProject().let {it.copy(tiles=it.tiles.map {t -> when(t.id) {
            "tile-mushroom" -> t.copy(name="[[tile-grass]]")
            "tile-grass" -> t.copy(name="[[tile-mushroom]]")
            else -> t
        } })}
        p.validate()
        assertEquals("tile-mushroom",p.displayText("[[tile-mushroom]]"))
    }
    @Test fun missingReferencesAreRejectedButPlainTextIsUnchanged() {
        val p=sampleProject()
        assertEquals("A @mention and [brackets]",p.displayText("A @mention and [brackets]"))
        assertEquals("Missing: deleted",p.displayText("[[deleted]]"))
        assertFailsWith<IllegalArgumentException> {p.copy(title="[[deleted]]").validate()}
    }
}
