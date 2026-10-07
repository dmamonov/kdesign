package kdesign

/** Tracks acknowledgements independently of newer edits; only one write may be in flight. */
class AutosaveState {
    var revision = 0; private set
    var savedRevision = 0; private set
    var inFlight: Int? = null; private set
    var failures = 0; private set
    val dirty get() = revision != savedRevision
    fun edited() { revision++ }
    fun begin(): Int? {
        if(!dirty || inFlight != null) return null
        return revision.also { inFlight = it }
    }
    fun complete(revision: Int, success: Boolean) {
        require(inFlight == revision) { "Unexpected save acknowledgement" }
        inFlight = null
        if(success) { savedRevision = revision; failures = 0 } else failures++
    }
}
