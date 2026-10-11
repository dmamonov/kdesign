package org.example.app

import kotlin.test.*

class TileGenerationTest {
    @Test fun selectsTerrainForWaterAndAllowsOverrides() {
        assertTrue(isTerrainTile("Filled water block with waves on top","auto"))
        assertFalse(isTerrainTile("Water monster","auto"))
        assertFalse(isTerrainTile("Water","sprite"))
        assertTrue(isTerrainTile("Marble","terrain"))
        assertFails {isTerrainTile("Water","unknown")}
    }
    @Test fun terrainInstructionsDoNotAskForSpriteSilhouettes() {
        val prompt=tileGenerationPrompt("Water",16,true)
        assertTrue(prompt.contains("completely opaque and edge to edge"))
        assertFalse(prompt.contains("Use a readable dark outline"))
        assertTrue(tileGenerationPrompt("Chest",16,false).contains("transparent surroundings"))
    }
}
