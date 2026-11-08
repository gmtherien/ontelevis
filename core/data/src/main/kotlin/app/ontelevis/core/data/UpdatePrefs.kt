package app.ontelevis.core.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Update choices kept on the device. */
@Singleton
class UpdatePrefs
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        private val _betas = MutableStateFlow(prefs.getBoolean(KEY_BETAS, false))

        /** Also offer pre-releases (1.2.0-beta.1), not only stable versions. */
        val betas: StateFlow<Boolean> = _betas.asStateFlow()

        fun setBetas(value: Boolean) {
            prefs.edit { putBoolean(KEY_BETAS, value) }
            _betas.value = value
        }

        private companion object {
            const val KEY_BETAS = "betas"
        }
    }
