package kdesign

/** Keep exterior transparency, but repair holes fully enclosed by a sprite silhouette. */
fun repairEnclosedTransparency(pixels: MutableList<Int>, size: Int) {
    val exterior=mutableSetOf<Int>()
    val queue=ArrayDeque<Int>()
    fun neighbors(i: Int): List<Int> = buildList {
        if(i%size>0) add(i-1)
        if(i%size<size-1) add(i+1)
        if(i>=size) add(i-size)
        if(i<size*(size-1)) add(i+size)
    }
    for(i in pixels.indices) if(pixels[i]<0 && (i<size || i>=size*(size-1) || i%size==0 || i%size==size-1)) {
        if(exterior.add(i)) queue.add(i)
    }
    while(queue.isNotEmpty()) for(n in neighbors(queue.removeFirst())) {
        if(pixels[n]<0 && exterior.add(n)) queue.add(n)
    }
    val remaining=pixels.indices.filter {pixels[it]<0 && it !in exterior}.toMutableSet()
    while(remaining.isNotEmpty()) {
        val region=mutableListOf<Int>()
        val border=mutableListOf<Int>()
        queue.add(remaining.first().also {remaining.remove(it)})
        while(queue.isNotEmpty()) {
            val i=queue.removeFirst();region.add(i)
            for(n in neighbors(i)) {
                if(remaining.remove(n)) queue.add(n)
                else if(pixels[n]>=0) border.add(pixels[n])
            }
        }
        if(border.isNotEmpty()) {
            val fill=border.groupingBy {it}.eachCount().maxBy {it.value}.key
            region.forEach {pixels[it]=fill}
        }
    }
}
