package kdesign.web

import kdesign.*

var inliningId = ""

fun inlineConfirmationView(): String {
    val count = project.placementUsageCount(inliningId)
    val placements = if(count == 1) "1 placement" else "$count placements"
    return "<div class='overlay'><section class='dialog' role='dialog' aria-modal='true' " +
        "aria-label='Confirm inline usages'><h2>Inline usages of ${text(project.name(inliningId))}?</h2>" +
        "<p>This will replace $placements with the meta block’s contents, preserving their positions.</p>" +
        "<p>The reusable definition is kept. Changes save automatically and can be undone.</p>" +
        (if(message.isNotBlank()) "<p class='modal-error' role='alert'>${text(message)}</p>" else "") +
        "<div class='dialog-actions'>${button("Cancel","inline-cancel")}" +
        "${button("Inline usages","inline-confirm",inliningId,cls="primary")}</div></section></div>"
}
