package org.example.app

import java.awt.image.BufferedImage
import kdesign.palette
import kotlin.test.*

class TileRasterTest {
    private fun color(index: Int) = (0xff000000L or palette[index].drop(1).toLong(16)).toInt()
    @Test fun preservesSpatialLayoutAndPaletteForFullBleedTiles() {
        val image=BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB)
        for(y in 0..63) for(x in 0..63) image.setRGB(x,y,color(if(x<32) 22 else 41))
        for(size in listOf(8,16)) {
            val pixels=rasterToTile(image,size)
            assertEquals(size*size,pixels.size)
            for(y in 0 until size) for(x in 0 until size) assertEquals(if(x<size/2) 22 else 41,pixels[y*size+x])
        }
    }
    @Test fun cropsPaddingWithoutStretchingAnObject() {
        val image=BufferedImage(80,80,BufferedImage.TYPE_INT_ARGB)
        for(y in 30..49) for(x in 20..59) image.setRGB(x,y,color(22))
        val pixels=rasterToTile(image,16)
        assertEquals(14*7,pixels.count {it==22})
        assertTrue(pixels.take(16).all {it==-1})
        assertTrue((0..15).all {pixels[it*16]==-1 && pixels[it*16+15]==-1})
    }
    @Test fun ignoresInvisibleRgbAndRejectsEmptyImages() {
        val image=BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB)
        for(y in 0..15) for(x in 0..15) image.setRGB(x,y,0x00ff00ff)
        assertFailsWith<IllegalArgumentException> {rasterToTile(image,16)}
        image.setRGB(8,8,color(22))
        val result=rasterToTile(image,8)
        assertTrue(result.all {it==-1 || it==22})
    }
}
