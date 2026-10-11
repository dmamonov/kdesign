package kdesign.web

import kdesign.*
import kotlinx.serialization.json.*

@JsFun("(objects) => window.designer.referenceObjects = JSON.parse(objects)")
external fun referenceObjects(objects: String)
@JsFun("(id, html) => { const el=document.getElementById(id + '-preview'); if(el) el.innerHTML=html; }")
external fun referencePreview(id: String, html: String)
fun text(s: String) = esc(project.displayText(s))
fun rich(s: String): String = buildString {
    var cursor = 0
    referencePattern.findAll(s).forEach { match ->
        append(esc(s.substring(cursor, match.range.first)))
        val id = match.groupValues[1]
        if(id in project.objectIds()) append("<a href='#object-${esc(id)}' class='inline-reference' ${attr("reference-open",id)} title='${esc(project.objectType(id))} · ${esc(id)}'>${text(project.name(id))}</a>")
        else append("<span class='broken-reference' title='Missing object'>${esc(match.value)}</span>")
        cursor = match.range.last + 1
    }
    append(esc(s.substring(cursor)))
}
fun publishReferenceObjects() {
    referenceObjects(JsonArray(project.objectIds().map { id -> buildJsonObject {
        put("id",id); put("name",project.displayText(project.name(id))); put("type",project.objectType(id))
    } }).toString())
}
fun openReference(id: String) {
    usageId=""; modal=""
    if(id.isEmpty()) { modal="Project"; return }
    select(id)
    project.concepts.find { it.id==id }?.let {
        section=it.category;tab="Design";conceptCategory=it.category
    }
}
fun openTextUsage(id: String, field: String) {
    openReference(id)
    if(project.levels.any {it.id==id}) tab="Design brief"
    if(project.tiles.any {it.id==id} || project.blocks.any {it.id==id}) tab="Properties"
    pendingFieldFocus=when {
        id.isEmpty() -> "new-name"
        project.concepts.any {it.id==id} -> if(field=="Name") "new-name" else "concept-description"
        project.levels.any {it.id==id} -> if(field=="Title") "title" else levelFields.getOrNull(fieldLabels.indexOf(field)) ?: "title"
        field=="Name" -> "name"
        else -> "properties"
    }
    message="Text reference in $field"
}

@JsFun("(id, error) => window.designer.tableCellState(id, error)")
external fun tableCellState(id: String, error: String)

var pendingFieldFocus=""
@JsFun("(id) => { const el=document.getElementById(id); if(el) {el.focus();el.scrollIntoView({block:'center'});} }")
external fun focusField(id: String)
