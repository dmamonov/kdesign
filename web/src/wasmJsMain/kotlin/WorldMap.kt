package kdesign.web

import kdesign.*

const val screenWidth = 256
const val screenHeight = 240

fun screenGrid(b: Block, screenWidth: Int, screenHeight: Int): String = "<div class='screen-grid' aria-hidden='true'>" +
    (0 until b.height step screenHeight).joinToString("") { y ->
        (0 until b.width step screenWidth).joinToString("") { x ->
            "<span style='left:${x*100.0/b.width}%;top:${y*100.0/b.height}%;width:${minOf(screenWidth,b.width-x)*100.0/b.width}%;height:${minOf(screenHeight,b.height-y)*100.0/b.height}%'>${x/screenWidth+1},${y/screenHeight+1}</span>"
        }
    } + "</div>"

fun levelMapPreview(level: Level): String {
    val b=project.blocks.first {it.id==level.rootId}
    return "<div class='level-map-preview' style='aspect-ratio:${b.width}/${b.height}'>${svg(b.id)}${screenGrid(b,level.screenWidth,level.screenHeight)}</div>"
}

fun worldMapView(level: Level? = null): String {
    val levels=if(level==null) project.levels else listOf(level)
    if(levels.isEmpty()) return "<section class='panel empty'>${createButton()}</section>"
    return "<div class='world-map'>" + levels.joinToString("") { l ->
        val b=project.blocks.first {it.id==l.rootId}
        val portals=mutableListOf<Portal>()
        fun visit(id: String) {
            val block=project.blocks.find {it.id==id} ?: return
            portals.addAll(block.portals)
            block.children.forEach {visit(it.assetId)}
        }
        visit(b.id)
        "<article class='panel world-level'><div class='panel-bar'>${button(text(l.title),"select",l.id,cls="quiet")}<span>${b.width} × ${b.height} · ${((b.width+l.screenWidth-1)/l.screenWidth)*((b.height+l.screenHeight-1)/l.screenHeight)} screens · ${l.screenWidth} × ${l.screenHeight} px</span></div>" +
            "<button class='open-level-map' aria-label='Open map of ${text(l.title)}' ${attr("select",l.id)}>${levelMapPreview(l)}</button>" +
            "<div class='world-links'>" +
            (l.parentId?.let {button("↑ ${text(project.name(it))}","select",it,cls="quiet")} ?: "") +
            project.levels.filter {it.parentId==l.id}.joinToString("") {button("↳ ${text(it.title)}","select",it.id,cls="quiet")} +
            portals.distinct().joinToString("") {button("${text(it.label)} → ${text(project.name(it.targetLevelId))}","select",it.targetLevelId,cls="quiet")} +
            "</div></article>"
    } + "</div>"
}
