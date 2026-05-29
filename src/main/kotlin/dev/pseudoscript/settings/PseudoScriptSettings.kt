package dev.pseudoscript.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Persisted, application-wide PseudoScript settings. */
@State(name = "PseudoScriptSettings", storages = [Storage("pseudoscript.xml")])
class PseudoScriptSettings :
    SimplePersistentStateComponent<PseudoScriptSettings.State>(State()) {

    class State : BaseState() {
        /**
         * Path to the `pds` binary. A bare name (no separators) is resolved on
         * the system `PATH`; an absolute path is used verbatim.
         */
        var pdsPath: String? by string(PseudoScriptSettings.DEFAULT_PATH)
    }

    var pdsPath: String
        get() = state.pdsPath?.takeIf { it.isNotBlank() } ?: DEFAULT_PATH
        set(value) {
            state.pdsPath = value
        }

    companion object {
        /** Default `pds` binary path — a bare name resolved on `PATH`. */
        const val DEFAULT_PATH = "pds"

        fun getInstance(): PseudoScriptSettings =
            ApplicationManager.getApplication().getService(PseudoScriptSettings::class.java)
    }
}
