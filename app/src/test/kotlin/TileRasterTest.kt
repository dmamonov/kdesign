package org.example.app

import java.awt.image.BufferedImage
import kdesign.palette
import kdesign.repairEnclosedTransparency
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
        assertEquals(16*8,pixels.count {it==22})
        assertTrue(pixels.take(16).all {it==-1})
        assertTrue((4..11).all {pixels[it*16]==22 && pixels[it*16+15]==22})
    }
    @Test fun paddedSquareFillsEveryTilePixel() {
        val image=BufferedImage(80,80,BufferedImage.TYPE_INT_ARGB)
        for(y in 20..59) for(x in 20..59) image.setRGB(x,y,color(22))
        for(size in listOf(8,16)) {
            assertEquals(List(size*size) {22},rasterToTile(image,size))
        }
    }
    @Test fun terrainFillsBoundsAndTransparentHoles() {
        val image=BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB)
        for(y in 4..59) for(x in 4..59) image.setRGB(x,y,color(22))
        for(size in listOf(8,16)) assertEquals(List(size*size) {22},rasterToTile(image,size,true))
    }
    @Test fun filledWaterRemovesNeutralFrameAndBackdrop() {
        val pixels=MutableList(256) {32}
        for(y in 3..13) for(x in 2..13) pixels[y*16+x]=18
        for(x in 3..12) pixels[3*16+x]=33
        pixels[8*16+2]=13
        val result=fitWaterMaterial(pixels,16)
        assertTrue(result.all {it==18 || it==33})
        assertTrue(result.contains(33))
        assertEquals(List(256) {22},fitWaterMaterial(List(256) {22},16))
    }
    @Test fun downsamplingDoesNotInventColorsBetweenOutlineAndFill() {
        val image=BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB)
        for(y in 0..63) for(x in 0..63) image.setRGB(x,y,color(if(x%4==0) 13 else 39))
        assertEquals(List(16*16) {39},rasterToTile(image,16))
    }
    @Test fun invisiblePixelsDoNotVoteAndMajorityOutlinesRemainBlack() {
        val image=BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB)
        for(y in 0..63) for(x in 0..63) image.setRGB(x,y,
            if(x%4==0) 0x00ff00ff else color(if(x%4==1) 39 else 13))
        assertEquals(List(16*16) {13},rasterToTile(image,16))
    }
    @Test fun repairsEnclosedFaceHolesButKeepsExteriorTransparent() {
        val pixels=MutableList(256) {-1}
        for(y in 3..12) for(x in 3..12) pixels[y*16+x]=39
        for(y in 6..9) for(x in 6..9) pixels[y*16+x]=-1
        repairEnclosedTransparency(pixels,16)
        for(y in 0..15) for(x in 0..15) assertEquals(
            if(x in 3..12 && y in 3..12) 39 else -1,pixels[y*16+x])
    }
    @Test fun opaqueWhiteFacialDetailsRemainOpaque() {
        val image=BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB)
        for(y in 8..55) for(x in 8..55) image.setRGB(x,y,color(39))
        for(y in 24..39) for(x in 24..39) image.setRGB(x,y,color(32))
        val pixels=rasterToTile(image,16)
        assertTrue(pixels.all {it>=0})
        assertEquals(32,pixels[8*16+8])
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
