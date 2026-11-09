package app.ontelevis.core.data.importer

import android.os.Process
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Runs a coroutine at background thread priority (`withContext(Dispatchers.IO + BackgroundPriority)`),
 * restoring the thread's priority whenever the coroutine leaves it. Imports parse hundreds of
 * thousands of rows: at normal priority they competed with the screen and the video on 4-core TV boxes.
 */
internal object BackgroundPriority : ThreadContextElement<Int> {
    // A separate key object: an object passing itself as its own key is still null while it is being
    // built (AbstractCoroutineContextElement(this) crashed every import in 1.0.0-beta.6).
    private object Key : CoroutineContext.Key<BackgroundPriority>

    override val key: CoroutineContext.Key<*> get() = Key

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
