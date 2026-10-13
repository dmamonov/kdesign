package kdesign.web

import kdesign.*
import kotlinx.serialization.encodeToString

val autosave = AutosaveState()
var saveError = ""
var fileVersion = ""
var saveConflict = false
@JsFun("(method, body, version, done) => window.designer.fileRequest(method, body, version, done)")
external fun fileRequest(method: String, body: String, version: String, done: (Int, String, String) -> Unit)
@JsFun("body => window.designer.exportDraft(body)")
external fun exportDraft(body: String)
val dirty get() = autosave.dirty
val saving get() = autosave.inFlight != null
val revision get() = autosave.revision
@JsFun("(delay, callback) => { clearTimeout(window.designer.saveTimer); window.designer.saveTimer = setTimeout(callback, delay); }")
external fun scheduleSave(delay: Int, callback: () -> Unit)
@JsFun("(text, pending) => window.designer.saveStatus(text, pending)")
external fun saveStatus(text: String, pending: Boolean)
fun statusText() = when {
    saveConflict -> "Save conflict · download draft, then reload"
    saveError.isNotEmpty() -> "Save failed · retrying…"
    saving -> "Saving…"
    tableErrors.isNotEmpty() -> "Invalid input · draft not saved"
    dirty -> "Waiting to save…"
    else -> "✓ All changes saved"
}
fun updateSaveStatus() { saveStatus(statusText(), dirty || tableErrors.isNotEmpty()) }
fun queueSave(delay: Int = 650) { updateSaveStatus();scheduleSave(delay) { save() } }
fun save() {
    if(saveConflict || fileVersion.isEmpty()) { updateSaveStatus();return }
    val savingRevision = autosave.begin() ?: run { updateSaveStatus();return }
    // Only validated model edits reach this queue. Invalid field drafts remain in the editor.
    val body = json.encodeToString(project)
    updateSaveStatus()
    fileRequest("PUT", body, fileVersion) { status, response, version ->
        val ok = status in 200..299 && version.isNotEmpty()
        if(ok) fileVersion = version
        saveConflict = status == 412 || status == 428
        autosave.complete(savingRevision,ok)
        saveError=if(ok) "" else response
        updateSaveStatus()
        if(saveConflict) render()
        if(dirty && !saveConflict) queueSave(if(ok) 0 else (1000 * (1 shl minOf(autosave.failures,5))).coerceAtMost(30000))
    }
}
/** Update existing entities as the user types, without remounting/focusing the UI. */
fun editForm(id: String, content: String) {
    if(editTypedField(id,content)) return
    if(id=="prompt") {generationPrompt=content;return}
    if(id=="generation-mode") {generationMode=content;return}
    if(id=="generation-size") {generationSize=content;return}
    val active = when {
        modal=="Project" -> id=="new-name"
        (modal=="Edit concept" || (modal.isEmpty() && section in categories)) -> id in setOf("new-name","concept-description","category","concept-type")
        modal.isNotEmpty() -> false // New-object dialogs commit via Create.
        tab=="Design brief" -> id=="title" || id=="parent" || id in levelFields
        tab=="Properties" -> id in setOf("name","width","height","properties","concept")
        else -> false
    }
    if(!active) return
    try {
        change { p -> when {
            modal=="Project" -> p.copy(title=content.trim().also {require(it.isNotBlank()) {"Enter a project title"}})
            (modal=="Edit concept" || (modal.isEmpty() && section in categories)) -> p.copy(concepts=p.concepts.map { c -> if(c.id!=selected) c else when(id) {
                "new-name" -> c.copy(name=content.trim().also {require(it.isNotBlank()) {"Enter a name"}})
                "concept-description" -> c.copy(description=content)
                "category" -> c.copy(category=content,type=categories.getValue(content).first())
                else -> c.copy(type=content)
            } })
            tab=="Design brief" -> p.copy(levels=p.levels.map { l -> if(l.id!=selected) l else when(id) {
                "title" -> l.copy(title=content.trim().also {require(it.isNotBlank()) {"Enter a title"}})
                "parent" -> l.copy(parentId=content.ifBlank {null})
                else -> l.copy(notes=l.notes+(id to content))
            } })
            else -> p.copy(tiles=p.tiles.map { t -> if(t.id==selected && id=="name") t.copy(name=content.trim().also {require(it.isNotBlank()) {"Enter a name"}}) else t },
                blocks=p.blocks.map { b -> if(b.id!=selected) b else when(id) {
                    "name" -> b.copy(name=content.trim().also {require(it.isNotBlank()) {"Enter a name"}})
                    "width" -> b.copy(width=content.toInt())
                    "height" -> b.copy(height=content.toInt())
                    "concept" -> b.copy(conceptIds=listOf(content).filter {it.isNotBlank()})
                    else -> b.copy(properties=content.lines().filter {it.isNotBlank()}.associate { line ->
                        val pair=line.split('=',limit=2);require(pair.size==2 && pair[0].isNotBlank()) {"Properties use key=value"}
                        pair[0].trim() to pair[1].trim()
                    })
                } })
        } }
        tableErrors.remove(id);tableCellState(id,"");publishReferenceObjects()
    } catch(e: Exception) {tableErrors.add(id);tableCellState(id,e.message ?: "Invalid input")}
    updateSaveStatus()
}
