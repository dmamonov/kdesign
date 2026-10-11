package org.example.app

import kdesign.palette
import java.awt.image.BufferedImage
import kotlin.math.*

/** Reduce an image spatially, preserving aspect ratio and alpha, then map to the NES palette. */
fun rasterToTile(image: BufferedImage, size: Int): List<Int> {
    require(size in listOf(8, 16))
    var left=image.width; var top=image.height; var right=-1; var bottom=-1
    for(y in 0 until image.height) for(x in 0 until image.width) {
        if((image.getRGB(x,y) ushr 24)>=128) {
            left=min(left,x);right=max(right,x);top=min(top,y);bottom=max(bottom,y)
        }
    }
    require(right>=left && bottom>=top) { "Generated image was empty. Try a more specific description." }
    val width=right-left+1; val height=bottom-top+1
    val fullBleed=left==0 && top==0 && right==image.width-1 && bottom==image.height-1
    val available=if(fullBleed) size else size-2
    val scale=available.toDouble()/max(width,height)
    val outWidth=max(1,(width*scale).roundToInt()); val outHeight=max(1,(height*scale).roundToInt())
    val offsetX=(size-outWidth)/2; val offsetY=(size-outHeight)/2
    val colors=palette.map {it.removePrefix("#").toInt(16)}
    fun nearest(r: Double,g: Double,b: Double)=colors.indices.minBy { i ->
        val c=colors[i]
        2*(r-(c shr 16 and 255)).pow(2)+3*(g-(c shr 8 and 255)).pow(2)+(b-(c and 255)).pow(2)
    }
    val pixels=MutableList(size*size) {-1}
    for(y in 0 until outHeight) for(x in 0 until outWidth) {
        val x0=left+x*width/outWidth;val x1=left+(x+1)*width/outWidth
        val y0=top+y*height/outHeight;val y1=top+(y+1)*height/outHeight
        var alpha=0.0;var r=0.0;var g=0.0;var b=0.0;var samples=0
        for(sy in y0 until max(y0+1,y1)) for(sx in x0 until max(x0+1,x1)) {
            val c=image.getRGB(sx,sy);val a=(c ushr 24)/255.0
            alpha+=a;r+=(c shr 16 and 255)*a;g+=(c shr 8 and 255)*a;b+=(c and 255)*a;samples++
        }
        if(alpha/samples>=0.4) pixels[(y+offsetY)*size+x+offsetX]=nearest(r/alpha,g/alpha,b/alpha)
    }
    require(pixels.any {it>=0}) {"Generated image has no visible pixels at this tile size."}
    return pixels
}
