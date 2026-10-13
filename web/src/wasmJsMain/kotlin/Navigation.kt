package kdesign.web

import kdesign.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@JsFun("() => JSON.stringify(Object.fromEntries(new URLSearchParams(location.hash.slice(1))))")
external fun readRoute(): String
@JsFun("(route, replace) => window.designer.syncRoute(route, replace)")
external fun writeRoute(route: String, replace: Boolean)
var replaceRoute = true

fun restoreRoute() {
    val route=runCatching {json.parseToJsonElement(readRoute()).jsonObject}.getOrNull() ?: return
    fun value(key: String)=route[key]?.jsonPrimitive?.content ?: ""
    section=value("section").let {if(it=="Blocks") "Tiles" else it}.takeIf {it in sections} ?: "Levels"
    selected="";usageId="";highlightPath=emptyList();treeQuery=""
    modal="";deletingId="";hotkeyConflict=null;mapSelection=null
    val id=value("object")
    if(id in project.objectIds()) select(id)
    val tabs=when {
        selected.isEmpty() -> if(section=="Levels") listOf("World map","Table") else listOf("Library")
        currentLevel()!=null -> listOf("Canvas","Overview","Design brief")
        project.concepts.any {it.id==selected} -> listOf("Design")
        else -> listOf("Editor","Properties")
    }
    tab=value("tab").takeIf {it in tabs} ?: tabs.first()
    usageId=value("usages").takeIf {it in project.objectIds()} ?: ""
    val path=runCatching {json.decodeFromString<List<String>>(value("path"))}.getOrDefault(emptyList())
    var block=currentBlock()
    for(step in path) {
        val child=block?.children?.find {it.id==step} ?: break
        highlightPath=highlightPath+step
        block=project.blocks.find {it.id==child.assetId}
    }
    replaceRoute=true
}
fun syncRoute() {
    val route=buildJsonObject {
        put("section",section);put("object",selected);put("tab",tab)
        if(usageId.isNotEmpty()) put("usages",usageId)
        if(highlightPath.isNotEmpty()) put("path",json.encodeToString(highlightPath))
    }
    writeRoute(route.toString(),replaceRoute)
    replaceRoute=false
}
