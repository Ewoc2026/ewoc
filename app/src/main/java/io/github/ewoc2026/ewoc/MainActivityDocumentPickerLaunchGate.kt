package io.github.ewoc2026.ewoc

/**
 * Serializes SAF picker launches so repeated button presses or repeated callback paths cannot stack
 * multiple system picker activities on top of each other.
 *
 * Invariants:
 * - At most one SAF launch may be in flight at a time across open/create/tree pickers owned by the
 *   same activity.
 * - Any picker result, including cancellation, releases the gate because the system will not
 *   deliver a second callback for the same launch.
 * - Launch failures must release the gate immediately so a rejected launch does not deadlock later
 *   picker requests.
 */
internal class MainActivityDocumentPickerLaunchGate(
    private val log: (String) -> Unit = {},
) {
    private var activeRequestTag: String? = null

    fun tryLaunch(requestTag: String, launch: () -> Unit): Boolean {
        val active = activeRequestTag
        if (active != null) {
            log("Ignoring SAF launch for $requestTag because $active is already in flight.")
            return false
        }
        activeRequestTag = requestTag
        return try {
            log("Launching SAF request: $requestTag")
            launch()
            true
        } catch (t: Throwable) {
            activeRequestTag = null
            log("SAF launch failed for $requestTag; gate released.")
            throw t
        }
    }

    fun onResultDelivered(requestTag: String) {
        val active = activeRequestTag
        if (active == null) {
            log("SAF result for $requestTag arrived with no active launch.")
            return
        }
        if (active != requestTag) {
            log("SAF result for $requestTag released gate owned by $active.")
        } else {
            log("SAF result delivered for $requestTag; gate released.")
        }
        activeRequestTag = null
    }
}
