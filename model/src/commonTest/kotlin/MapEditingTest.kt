package kdesign
import kotlin.test.*
import kotlinx.serialization.encodeToString
class MapEditingTest {
    private var serial=0
    private fun id()="fill-${serial++}"
    @Test fun fillsAreaOnceWithoutOverlapsAndTracesEachInstance() {
        val p=sampleProject();val area=MapArea(0,0,48,32)
        val filled=p.fillArea("meta-clearing","block-mushroom",area,16,false,::id)
        assertEquals(6,filled.blocks.first {it.id=="meta-clearing"}.children.size-p.blocks.first {it.id=="meta-clearing"}.children.size)
        assertEquals(filled,filled.fillArea("meta-clearing","block-mushroom",area,16,false,::id))
        assertEquals(12,filled.usages("block-mushroom").count {it.levelId!=null})
    }
    @Test fun replacementRespectsSelectionEdgesAndAssetDimensions() {
        val p=sampleProject();val area=MapArea(0,160,40,16)
        val filled=p.fillArea("meta-clearing","block-stone",area,8,true,::id)
        val root=filled.blocks.first {it.id=="meta-clearing"}
        assertTrue(root.children.any {it.id=="ground-2"}) // Partially outside selection, preserved.
        assertEquals(2,root.children.count {it.assetId=="block-stone" && it.y==160})
        assertFalse(root.children.any {it.id=="ground-0"})
    }
    @Test fun clearRejectsBreakingPortalTargetsAtomically() {
        val p=sampleProject()
        assertFails {p.clearArea("meta-clearing",MapArea(448,144,16,16))}
        assertTrue(p.blocks.first {it.id=="meta-clearing"}.children.any {it.id=="cave-door"})
    }
    @Test fun compositionPickerExcludesSelfAndAncestors() {
        val p=sampleProject()
        assertFalse("meta-clearing" in p.compositionAssets("meta-clearing"))
        assertFalse("meta-clearing" in p.compositionAssets("meta-entrance"))
        assertTrue("block-mushroom" in p.compositionAssets("meta-clearing"))
    }
    @Test fun hotkeysPersistAndRejectMissingAssets() {
        val p=sampleProject().copy(hotkeys=mapOf("1" to "block-mushroom"));p.validate()
        assertEquals(p,json.decodeFromString<Project>(json.encodeToString(p)))
        assertFails {p.copy(hotkeys=mapOf("F" to "block-mushroom")).validate()}
        assertFails {p.copy(hotkeys=mapOf("1" to "missing")).validate()}
    }
    @Test fun rejectsAnAssetTooLargeForSelection() {
        assertFails {sampleProject().fillArea("meta-clearing","block-mushroom",MapArea(0,0,8,8),8,false,::id)}
    }
}
