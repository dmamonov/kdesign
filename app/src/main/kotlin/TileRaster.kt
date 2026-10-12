package org.example.app

import kdesign.palette
import kdesign.repairEnclosedTransparency
import java.awt.image.BufferedImage
import kotlin.math.*

/** Reduce an image using dominant palette colors, preserving aspect ratio and alpha. */
fun rasterToTile(image: BufferedImage, size: Int, terrain: Boolean = false): List<Int> {
    require(size in listOf(8, 16))
    var left=image.width; var top=image.height; var right=-1; var bottom=-1
    for(y in 0 until image.height) for(x in 0 until image.width) {
        if((image.getRGB(x,y) ushr 24)>=128) {
            left=min(left,x);right=max(right,x);top=min(top,y);bottom=max(bottom,y)
        }
    }
    require(right>=left && bottom>=top) { "Generated image was empty. Try a more specific description." }
    if(terrain) {left=0;top=0;right=image.width-1;bottom=image.height-1}
    val width=right-left+1; val height=bottom-top+1
    val scale=size.toDouble()/max(width,height)
    val outWidth=if(terrain) size else max(1,(width*scale).roundToInt()); val outHeight=if(terrain) size else max(1,(height*scale).roundToInt())
    val offsetX=(size-outWidth)/2; val offsetY=(size-outHeight)/2
    val colors=palette.map {it.removePrefix("#").toInt(16)}
    fun nearest(r: Double,g: Double,b: Double)=colors.indices.minBy { i ->
        val c=colors[i]
        2*(r-(c shr 16 and 255)).pow(2)+3*(g-(c shr 8 and 255)).pow(2)+(b-(c and 255)).pow(2)
    }
    // Quantize before reduction: averaging an outline and a highlight invents muddy colors.
    val quantized=IntArray(32768) {-1}
    fun quantize(rgb: Int): Int {
        val r=rgb shr 16 and 255;val g=rgb shr 8 and 255;val b=rgb and 255
        val key=((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
        if(quantized[key]<0) quantized[key]=nearest(r.toDouble(),g.toDouble(),b.toDouble())
        return quantized[key]
    }
    val pixels=MutableList(size*size) {-1}
    for(y in 0 until outHeight) for(x in 0 until outWidth) {
        val x0=left+x*width/outWidth;val x1=left+(x+1)*width/outWidth
        val y0=top+y*height/outHeight;val y1=top+(y+1)*height/outHeight
        var alpha=0.0;var samples=0
        val votes=DoubleArray(palette.size)
        for(sy in y0 until max(y0+1,y1)) for(sx in x0 until max(x0+1,x1)) {
            val c=image.getRGB(sx,sy);val a=(c ushr 24)/255.0
            alpha+=a;samples++
            if(a>0) votes[quantize(c)]+=a
        }
        if(alpha/samples>=0.4) pixels[(y+offsetY)*size+x+offsetX]=votes.indices.maxBy {votes[it]}
    }
    require(pixels.any {it>=0}) {"Generated image has no visible pixels at this tile size."}
    if(!terrain) repairEnclosedTransparency(pixels,size)
    if(terrain) {
        val visible=pixels.indices.filter {pixels[it]>=0}
        for(i in pixels.indices) if(pixels[i]<0) {
            val nearest=visible.minBy {j -> (i%size-j%size).toDouble().pow(2)+(i/size-j/size).toDouble().pow(2)}
            pixels[i]=pixels[nearest]
        }
    }
    return pixels
}

/** Filled blue water must be material, not a blue object on a neutral backdrop. */
fun fitWaterMaterial(pixels: List<Int>, size: Int): List<Int> {
    fun water(index: Int): Boolean {
        if(index<0) return false
        val rgb=palette[index].drop(1).toInt(16)
        val r=rgb shr 16 and 255;val g=rgb shr 8 and 255;val b=rgb and 255
        return b>r+25 && b>=g && b>70
    }
    val material=pixels.indices.filter {water(pixels[it])}
    // Leave intentionally unusual water colors alone.
    if(material.size<pixels.size/4) return pixels
    val left=material.minOf {it%size};val right=material.maxOf {it%size}
    val top=material.minOf {it/size};val bottom=material.maxOf {it/size}
    return List(size*size) {i ->
        val x=left+(i%size)*(right-left+1)/size
        val y=top+(i/size)*(bottom-top+1)/size
        val at=y*size+x
        if(water(pixels[at])) pixels[at] else pixels[material.minBy {
            (it%size-x)*(it%size-x)+(it/size-y)*(it/size-y)
        }]
    }
}
