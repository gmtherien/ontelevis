package app.ontelevis.core.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** General choices kept on the device. */
@Singleton
class AppSettings
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        private val _startOnHome = MutableStateFlow(prefs.getBoolean(KEY_START_HOME, false))

        /** True: the app opens on Home; false (default): on TV, playing the last channel. */
        val startOnHome: StateFlow<Boolean> = _startOnHome.asStateFlow()

        fun setStartOnHome(value: Boolean) {
            prefs.edit { putBoolean(KEY_START_HOME, value) }
            _startOnHome.value = value
        }

        private companion object {
            const val KEY_START_HOME = "start_on_home"
        }
    }
