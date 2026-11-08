package app.ontelevis.core.data

import android.content.Context
import androidx.core.content.edit
import app.ontelevis.core.model.VodKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Movies / series display choices kept on the device: the sort order, one per kind. */
@Singleton
class VodPrefs
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("vod", Context.MODE_PRIVATE)
        private val sorts =
            VodKind.entries.associateWith { MutableStateFlow(prefs.getString(key(it), null)) }

        /** Name of the chosen sort for [kind], null for the default (provider order). */
        fun sort(kind: VodKind): StateFlow<String?> = sorts.getValue(kind).asStateFlow()

        fun setSort(
            kind: VodKind,
            sort: String,
        ) {
            prefs.edit { putString(key(kind), sort) }
            sorts.getValue(kind).value = sort
        }

        /** Every saved sort, for the backup (kind name -> sort name). */
        fun allSorts(): Map<String, String> =
            VodKind.entries.mapNotNull { kind -> sorts.getValue(kind).value?.let { kind.name to it } }.toMap()

        fun restoreSorts(values: Map<String, String>) {
            VodKind.entries.forEach { kind ->
                val value = values[kind.name]
                prefs.edit { if (value == null) remove(key(kind)) else putString(key(kind), value) }
                sorts.getValue(kind).value = value
            }
        }

        private fun key(kind: VodKind) = "sort_${kind.name.lowercase()}"
    }
