package app.ontelevis.core.data

import android.content.Context
import androidx.core.content.edit
import app.ontelevis.core.data.db.CategoryLockEntity
import app.ontelevis.core.data.db.LibraryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Kind of category a lock applies to. */
enum class CategoryKind(
    val key: String,
) {
    LIVE("live"),
    MOVIE("movie"),
    SERIES("series"),
}

/** A category, as locks know it. */
data class CategoryRef(
    val kind: CategoryKind,
    val playlistId: Long,
    val name: String,
)

/**
 * Parental PIN and locked categories. The PIN is kept as a salted SHA-256 hash, never in clear.
 * A locked category asks for the PIN before showing its content; once entered it stays open until
 * the app goes to the background ([relock]).
 */
@Singleton
class ParentalControl
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val library: LibraryDao,
    ) {
        private val prefs = context.getSharedPreferences("parental", Context.MODE_PRIVATE)
        private val _hasPin = MutableStateFlow(prefs.getString(KEY_HASH, null) != null)
        val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

        // Categories opened with the PIN since the app came to the foreground.
        private val unlocked = MutableStateFlow<Set<CategoryRef>>(emptySet())

        // One database observer shared by every screen (each collector used to start its own). No
        // initial value: a collector waits for the real locks (an empty set first would let the startup
        // resume play a channel of a locked category).
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Every locked category, unlocked for this session or not. */
        val locked: Flow<Set<CategoryRef>> =
            library
                .observeCategoryLocks()
                .map { rows -> rows.mapNotNull { it.toRef() }.toSet() }
                .shareIn(scope, SharingStarted.Eagerly, replay = 1)

        /** Locked categories still closed now: their content stays out of lists, shortcuts and search. */
        val closed: Flow<Set<CategoryRef>> =
            combine(locked, unlocked, hasPin) { locked, open, pin -> if (pin) locked - open else emptySet() }
                .distinctUntilChanged()
                .shareIn(scope, SharingStarted.Eagerly, replay = 1)

        fun checkPin(pin: String): Boolean {
            val hash = prefs.getString(KEY_HASH, null) ?: return true
            val salt = prefs.getString(KEY_SALT, null).orEmpty()
            return hashOf(pin, salt) == hash
        }

        /** Sets or changes the PIN (the caller has checked the old one). */
        fun setPin(pin: String) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            prefs.edit {
                putString(KEY_SALT, salt)
                putString(KEY_HASH, hashOf(pin, salt))
            }
            _hasPin.value = true
        }

        /** Removes the PIN; locked categories open freely until a new PIN is set (their locks are kept). */
        fun clearPin() {
            prefs.edit {
                remove(KEY_SALT)
                remove(KEY_HASH)
            }
            _hasPin.value = false
        }

        suspend fun setLocked(
            category: CategoryRef,
            locked: Boolean,
        ) {
            val row = CategoryLockEntity(category.playlistId, category.kind.key, category.name)
            if (locked) library.addCategoryLock(row) else library.removeCategoryLock(row)
            if (!locked) unlocked.value -= category
        }

        /** The PIN was entered for [category]: it stays open for this session. */
        fun unlock(category: CategoryRef) {
            unlocked.value += category
        }

        /** The app went to the background: every locked category asks for the PIN again. */
        fun relock() {
            unlocked.value = emptySet()
        }

        /** Saved PIN hash and salt, for the backup (the PIN itself is never stored). */
        internal fun pinState(): Pair<String, String>? {
            val hash = prefs.getString(KEY_HASH, null) ?: return null
            return hash to prefs.getString(KEY_SALT, null).orEmpty()
        }

        internal fun restorePinState(state: Pair<String, String>?) {
            prefs.edit {
                if (state == null) {
                    remove(KEY_HASH)
                    remove(KEY_SALT)
                } else {
                    putString(KEY_HASH, state.first)
                    putString(KEY_SALT, state.second)
                }
            }
            _hasPin.value = state != null
            unlocked.value = emptySet()
        }

        private fun hashOf(
            pin: String,
            salt: String,
        ): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest("$salt:$pin".toByteArray())
                .joinToString("") { "%02x".format(it) }

        private companion object {
            const val KEY_HASH = "pin_hash"
            const val KEY_SALT = "pin_salt"
        }
    }

private fun CategoryLockEntity.toRef(): CategoryRef? =
    CategoryKind.entries.firstOrNull { it.key == kind }?.let { CategoryRef(it, playlistId, name) }
