package app.luma.chat

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

const val LANGUAGE_SYSTEM = "system"

/** The languages Luma ships. `tag` doubles as the stored preference value. */
enum class LumaLanguage(val tag: String, val label: String) {
    English("en", "English"),
    Russian("ru", "Русский"),
    Spanish("es", "Español");

    companion object {
        fun of(tag: String?): LumaLanguage? = values().firstOrNull { it.tag == tag }
    }
}

/**
 * Runs [content] with the chosen language applied.
 *
 * Android 13 can carry a per-app locale for us, but the app supports API 26, so
 * the configuration is overridden here instead — one path that behaves the same
 * on every version. On [LANGUAGE_SYSTEM] nothing is overridden and the device
 * language wins, falling back to English for anything Luma does not translate.
 */
@Composable fun LumaLocale(language: String, content: @Composable () -> Unit) {
    val chosen = LumaLanguage.of(language)
    if (chosen == null) { content(); return }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(context, chosen, configuration) {
        // Only this subtree is re-pointed. Setting the process-wide default
        // locale instead would reach code that has nothing to do with the UI.
        // Preserve the Activity in the ContextWrapper chain. A detached
        // configuration context loses ActivityResultRegistryOwner, which crashes
        // photo/permission/sign-in launchers when a saved locale is restored.
        android.view.ContextThemeWrapper(context, 0).apply {
            applyOverrideConfiguration(
                Configuration(configuration).apply { setLocale(Locale.forLanguageTag(chosen.tag)) }
            )
        }
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        content = content
    )
}
