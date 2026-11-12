package com.lumen.explorer

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lazy "N items" numbers for folders. Listing a folder no longer counts every sub-folder up front (that made big folders slow);
 * each visible folder asks for its own number, one tiny state per path so only that row redraws.
 * Main-thread only.
 */
object ChildCounts {
    private val states = HashMap<String, MutableIntState>()
    private val gate = Semaphore(2)

    /** bumped by clear(); rows re-count when it changes */
    var version by mutableIntStateOf(0)
        private set

    fun stateFor(path: String): MutableIntState {
        if (states.size > 4000) states.clear()
        return states.getOrPut(path) { mutableIntStateOf(-1) }
    }

    suspend fun load(path: String) {
        val st = stateFor(path)
        if (st.intValue >= 0) return
        val showHidden = Settings.s.showHidden
        val n = withContext(Dispatchers.IO) { gate.withPermit { FileOps.countChildren(File(path), showHidden) } }
        st.intValue = n
    }

    /** after any change on disk or a "show hidden" switch */
    fun clear() {
        for (s in states.values) s.intValue = -1
        version++
    }
}
