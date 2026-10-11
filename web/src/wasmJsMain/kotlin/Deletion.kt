package kdesign.web

import kdesign.*

var deletingId=""
fun deletionView(): String {
    val id=deletingId
    val roots=project.levels.filter {it.rootId==id}
    val candidate=if(roots.isEmpty()) runCatching {project.deleteObject(id)} else null
    val after=candidate?.getOrNull()
    val explanation=when {
        roots.isNotEmpty() -> "This meta block is the root of ${roots.joinToString {project.displayText(it.title)}}. Delete those levels first. Their graphic definitions are retained."
        candidate?.isFailure==true -> candidate.exceptionOrNull()?.message ?: "Cannot delete this object"
        else -> "The following changes will be saved automatically. Undo can restore the object and its links during this session."
    }
    val details=if(after==null) "" else "<ul>" +
        "<li>${project.blocks.sumOf {it.children.size}-after.blocks.sumOf {it.children.size}} placements removed, including contents of the deleted definition.</li>" +
        "<li>${project.blocks.sumOf {it.portals.size}-after.blocks.sumOf {it.portals.size}} connections removed.</li>" +
        "<li>${project.textReferences().count {it.targetId==id && it.ownerId!=id}} text references become plain names.</li>" +
        "<li>Concept links and hotkeys pointing to this object are removed.</li>" +
        (if(project.levels.any {it.id==id}) "<li>Sublevels move to this level’s parent. Graphic definitions are kept.</li>" else "<li>Child definitions are kept.</li>") + "</ul>"
    return "<div class='overlay'><section class='dialog' role='dialog' aria-modal='true' aria-label='Delete object'><h2>Delete ${text(project.name(id))}?</h2><p>${esc(explanation)}</p>$details<div class='dialog-actions'>${button("Cancel","delete-cancel")}${if(after!=null) button("Delete object","delete-confirm",id,cls="danger") else ""}</div></section></div>"
}
