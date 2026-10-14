package kdesign

import kotlinx.serialization.encodeToString
import kotlin.test.*

class ScreenGridTest {
    @Test fun legacyLevelsDefaultAndCustomDimensionsRoundTrip() {
        val level=json.decodeFromString<Level>("""{"id":"level","title":"Test","rootId":"root"}""")
        assertEquals(256,level.screenWidth)
        assertEquals(240,level.screenHeight)
        val project=sampleProject()
        val resized=project.copy(levels=project.levels.map {it.copy(screenWidth=320,screenHeight=180)})
        resized.validate()
        assertEquals(resized,json.decodeFromString<Project>(json.encodeToString(resized)))
        assertEquals(project.blocks,resized.blocks)
        for(size in listOf(0,-1,4097)) {
            assertFailsWith<IllegalArgumentException> {
                project.copy(levels=project.levels.map {it.copy(screenWidth=size)}).validate()
            }
            assertFailsWith<IllegalArgumentException> {
                project.copy(levels=project.levels.map {it.copy(screenHeight=size)}).validate()
            }
        }
    }
}
