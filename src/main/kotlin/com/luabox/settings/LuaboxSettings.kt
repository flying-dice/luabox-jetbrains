package com.luabox.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Persisted, application-wide luabox settings. */
@State(name = "LuaboxSettings", storages = [Storage("luabox.xml")])
class LuaboxSettings :
    SimplePersistentStateComponent<LuaboxSettings.State>(State()) {

    class State : BaseState() {
        /**
         * Path to the `luabox` binary. A bare name (no separators) is resolved
         * on the system `PATH` (with a `~/.luabox/bin` fallback); an absolute
         * path is used verbatim.
         */
        var luaboxPath: String? by string(DEFAULT_PATH)
    }

    var luaboxPath: String
        get() = state.luaboxPath?.takeIf { it.isNotBlank() } ?: DEFAULT_PATH
        set(value) {
            state.luaboxPath = value
        }

    companion object {
        /** Default `luabox` binary path — a bare name resolved on `PATH`. */
        const val DEFAULT_PATH = "luabox"

        fun getInstance(): LuaboxSettings =
            ApplicationManager.getApplication().getService(LuaboxSettings::class.java)
    }
}
