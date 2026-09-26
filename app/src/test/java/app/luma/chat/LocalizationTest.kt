package app.luma.chat

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Modifier
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLumaApplication::class)
class LocalizationTest {
    private val languages = listOf("en", "ru", "es")

    /** Shared across every language, so the other files do not redeclare it. */
    private val sameInEveryLanguage = setOf("assistant_name")

    private fun localized(tag: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(configuration)
    }

    private fun stringIds() = R.string::class.java.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }

    private fun keysIn(path: String): Set<String> {
        // Unit tests run from the module directory, but do not rely on it.
        val file = listOf(File(path), File("app/$path")).firstOrNull { it.isFile }
            ?: error("cannot find $path from ${File(".").absolutePath}")
        return Regex("""<string name="([^"]+)"""").findAll(file.readText())
            .map { it.groupValues[1] }.toSet()
    }

    @Test fun everyLanguageTranslatesEveryString() {
        val base = keysIn("src/main/res/values/strings.xml")
        assertTrue("the default file should not be empty", base.size > 50)
        mapOf("ru" to "values-ru", "es" to "values-es").forEach { (tag, directory) ->
            val translated = keysIn("src/main/res/$directory/strings.xml")
            assertEquals("$tag is missing translations", emptySet<String>(), base - translated - sameInEveryLanguage)
            assertEquals("$tag declares strings the default file does not", emptySet<String>(), translated - base)
        }
    }

    @Test fun everyLanguageResolvesEveryStringAndNoneNamesTheRoute() {
        // Whatever the language, the UI only ever speaks about Iskra and A/V/S.
        val secrets = listOf("OpenRouter", "openrouter", "Nemotron", "nemotron", "nvidia", "openai", "mistral", "gpt-4o")
        val ids = stringIds()
        assertTrue("no string resources found", ids.isNotEmpty())
        languages.forEach { tag ->
            val context = localized(tag)
            ids.forEach { field ->
                val value = context.getString(field.getInt(null))
                assertTrue("${field.name} is blank in $tag", value.isNotBlank())
                secrets.forEach { secret ->
                    assertFalse("${field.name} leaks '$secret' in $tag", value.contains(secret))
                }
            }
        }
    }

    @Test fun modelNotesAndErrorsAreTranslatedAwayFromTheDefault() {
        val english = localized("en")
        val russian = localized("ru")
        val spanish = localized("es")
        // A sample that must genuinely differ, so a missing file is not silently
        // masked by the fall-back to the default resources.
        listOf(R.string.settings, R.string.retry, R.string.model_a_note, R.string.error_rate_limit).forEach { id ->
            assertFalse("${english.getString(id)} is not translated to Russian", english.getString(id) == russian.getString(id))
            assertFalse("${english.getString(id)} is not translated to Spanish", english.getString(id) == spanish.getString(id))
        }
    }

    @Test fun everyLanguageHasItsOwnThoughtsForTheEmptyChat() {
        val thoughts = languages.associateWith { localized(it).resources.getStringArray(R.array.empty_thoughts).toList() }
        val english = thoughts.getValue("en")
        assertTrue("too few thoughts to rotate through", english.size >= 6)
        thoughts.forEach { (tag, lines) ->
            assertEquals("$tag has a different number of thoughts", english.size, lines.size)
            assertTrue("$tag has a blank thought", lines.all { it.isNotBlank() })
            assertEquals("$tag repeats a thought", lines.size, lines.toSet().size)
            if (tag != "en") assertTrue("$tag falls back to English", lines.none { it in english })
        }
    }

    @Test fun languagePreferenceSurvivesEncoding() {
        LumaLanguage.values().forEach { language ->
            val state = ChatState(preferences = Preferences(language = language.tag))
            assertEquals(language.tag, StateCodec.decode(StateCodec.encode(state)).preferences.language)
        }
        val system = ChatState(preferences = Preferences(language = LANGUAGE_SYSTEM))
        assertEquals(LANGUAGE_SYSTEM, StateCodec.decode(StateCodec.encode(system)).preferences.language)
        // Anything unrecognised in stored state must not leave the app blank.
        assertEquals(LumaModel.Default.key, StateCodec.decode("""{"preferences":{"model":"nope"},"chats":[]}""").preferences.model)
    }
}
