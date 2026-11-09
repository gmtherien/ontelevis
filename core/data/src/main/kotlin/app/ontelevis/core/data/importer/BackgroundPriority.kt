package app.ontelevis.core.data.importer

import android.os.Process
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Runs a coroutine at background thread priority (`withContext(Dispatchers.IO + BackgroundPriority)`),
 * restoring the thread's priority whenever the coroutine leaves it. Imports parse hundreds of
 * thousands of rows: at normal priority they competed with the screen and the video on 4-core TV boxes.
 */
internal object BackgroundPriority :
    AbstractCoroutineContextElement(BackgroundPriority),
    ThreadContextElement<Int>,
    CoroutineContext.Key<BackgroundPriority> {
    override fun updateThreadContext(context: CoroutineContext): Int {
        val before = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        return before
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: Int,
    ) {
        Process.setThreadPriority(oldState)
    }
}
