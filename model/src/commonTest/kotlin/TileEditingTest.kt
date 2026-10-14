package kdesign
import kotlin.test.*
class TileEditingTest {
    @Test fun flipsMirrorBothSizesAndPreserveTransparency() {
        for(size in listOf(8,16)) {
            val tile=Tile("t","Test",size,List(size*size) {if(it==0) -1 else it%64})
            val horizontal=tile.flip(true)
            val vertical=tile.flip(false)
            assertEquals(tile.pixels.take(size).reversed(),horizontal.pixels.take(size))
            assertEquals(tile.pixels.takeLast(size),vertical.pixels.take(size))
            assertEquals(-1,horizontal.pixels[size-1])
            assertEquals(-1,vertical.pixels[(size-1)*size])
            assertEquals(tile,horizontal.flip(true))
            assertEquals(tile,vertical.flip(false))
        }
    }

    @Test fun overlappingMoveUsesOriginalPixelsAndClearsSource() {
        val t=Tile("t","Test",8,List(64) {it%64})
        val moved=t.moveArea(MapArea(1,1,3,2),2,1)
        assertEquals(-1,moved.pixels[9]);assertEquals(-1,moved.pixels[17])
        assertEquals(listOf(9,10,11),moved.pixels.subList(10,13))
        assertEquals(listOf(17,18,19),moved.pixels.subList(18,21))
        assertEquals(t,t.moveArea(MapArea(1,1,3,2),1,1))
    }
    @Test fun transparencyMovesAndBoundsAreChecked() {
        val t=Tile("t","Test",8,List(64) {if(it==0) -1 else 22})
        assertEquals(-1,t.moveArea(MapArea(0,0,1,1),7,7).pixels[63])
        assertFails {t.moveArea(MapArea(0,0,2,2),7,7)}
        assertFails {t.moveArea(MapArea(-1,0,2,2),0,0)}
    }
}
