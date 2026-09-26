package app.luma.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestLumaApplication::class, sdk = [35], qualifiers = "ru-rRU-w412dp-h892dp-420dpi")
class NativeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun reset() {
        compose.runOnUiThread {
            ViewModelProvider(compose.activity)[ChatViewModel::class.java].apply { clearHistory(); settings(Preferences()) }
        }
    }
    private fun capture(name: String) { compose.onRoot().captureRoboImage("build/previews/$name.png") }
    @Test @Config(shadows = [CameraUriShadow::class])
    fun cameraResultSurvivesRecreationAndCancellationKeepsAttachment() {
        compose.onNodeWithContentDescription("Прикрепить фото").performClick()
        compose.onNodeWithText("Сделать фото").assertIsDisplayed()
        compose.onNodeWithText("Выбрать фото").assertIsDisplayed()
        capture("26-attachment-menu")
        compose.onNodeWithText("Сделать фото").performClick()
        compose.waitForIdle()
        val started = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE, started.intent.action)
        val uri = started.intent.getParcelableExtra<android.net.Uri>(android.provider.MediaStore.EXTRA_OUTPUT)!!
        assertEquals("content", uri.scheme)
        assertTrue(started.intent.flags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        val bitmap = android.graphics.Bitmap.createBitmap(120, 160, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(90, 150, 120))
        java.io.File(compose.activity.cacheDir, "camera/${uri.lastPathSegment}").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        compose.activityRule.scenario.recreate()
        compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(started.requestCode, android.app.Activity.RESULT_OK, null) }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Удалить фото").fetchSemanticsNodes().isNotEmpty() }
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        val previous = vm.state.value.photo
        assertTrue(previous!!.startsWith("data:image/jpeg;base64,"))
        compose.onNodeWithContentDescription("Прикрепить фото").performClick()
        compose.onNodeWithText("Сделать фото").performClick()
        compose.waitForIdle()
        val cancelled = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult
        compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(cancelled.requestCode, android.app.Activity.RESULT_CANCELED, null) }
        compose.waitForIdle()
        assertEquals(previous, vm.state.value.photo)
        assertTrue(java.io.File(compose.activity.cacheDir, "camera").listFiles().orEmpty().isEmpty())
    }
    @Test fun keyboardExpandsComposerAndKeepsDraftWhenClosed() {
        fun keyboard(bottom: Int) = compose.runOnUiThread {
            fun dispatch(view: android.view.View) {
                androidx.core.view.ViewCompat.dispatchApplyWindowInsets(view,
                    androidx.core.view.WindowInsetsCompat.Builder()
                        .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, bottom))
                        .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), bottom > 0).build())
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) dispatch(view.getChildAt(i))
            }
            dispatch(compose.activity.window.decorView)
        }
        compose.onNodeWithContentDescription("Сообщение").performClick().performTextInput("Проверка черновика")
        keyboard(600)
        compose.waitForIdle()
        val input = compose.onNodeWithContentDescription("Сообщение").fetchSemanticsNode().boundsInRoot
        val plus = compose.onNodeWithContentDescription("Прикрепить фото").fetchSemanticsNode().boundsInRoot
        assertTrue("Tools belong below the editor when the keyboard is open", plus.top > input.top)
        capture("24-composer-expanded")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Сообщение").assertIsNotFocused()
        keyboard(0)
        compose.waitForIdle()
        compose.onNodeWithText("Проверка черновика").assertExists()
        compose.onNodeWithContentDescription("Сообщение").assertIsNotFocused()
        val compactInput = compose.onNodeWithContentDescription("Сообщение").fetchSemanticsNode().boundsInRoot
        val compactPlus = compose.onNodeWithContentDescription("Прикрепить фото").fetchSemanticsNode().boundsInRoot
        val compactMic = compose.onNodeWithContentDescription("Голосовой ввод").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(compactInput.center.y - compactPlus.center.y) < 3f)
        assertTrue(kotlin.math.abs(compactInput.center.y - compactMic.center.y) < 3f)
        capture("25-composer-compact")
    }
    @Test fun restoredExplicitLanguageKeepsPhotoLauncherAfterLogin() {
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.runOnUiThread { vm.settings(vm.state.value.preferences.copy(language = "ru")) }
        compose.onNodeWithContentDescription("Прикрепить фото").assertIsDisplayed()
        compose.runOnUiThread { vm.signOut() }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnUiThread { vm.signIn(MemoryAccounts.SIGNED_IN) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Прикрепить фото").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Голосовой ввод").assertIsDisplayed()
    }
    @Test fun photoOnlyMessagePersistsAndComposerShowsMediaButtons() {
        val bitmap = android.graphics.Bitmap.createBitmap(240, 180, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(120, 90, 180))
        val bytes = java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, this) }.toByteArray()
        bitmap.recycle()
        val photo = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(bytes)
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("Прикрепить фото").assertIsDisplayed()
        compose.onNodeWithContentDescription("Голосовой ввод").assertIsDisplayed()
        compose.runOnUiThread { vm.photo(photo) }
        compose.onNodeWithContentDescription("Удалить фото").assertIsDisplayed()
        capture("22-photo-draft")
        compose.onNodeWithContentDescription("Отправить сообщение").assertIsEnabled().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Скопировать ответ").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(photo, vm.state.value.active!!.messages.first().photo)
        assertEquals(null, vm.state.value.photo)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Фото").assertExists()
        capture("23-photo-history")
    }
    @Test fun o5SwitchResearchAndHistory() {
        val app = compose.activity.application as TestLumaApplication
        app.replyText = "Material 3 — дизайн-система Android. [Источник](https://developer.android.com)"
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("O5").performClick()
        compose.onNodeWithText("Что исследуем?").assertIsDisplayed()
        capture("19-o5-start")
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Найди информацию о Material 3")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Работа завершена").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Работа завершена").performClick()
        compose.onNodeWithText("Material 3 Android").assertIsDisplayed()
        capture("20-o5-result")
        assertEquals(1, app.agentRequests)
        assertEquals(LumaModel.A45, app.lastModel)
        assertTrue(vm.state.value.active!!.messages.last().agent)
        compose.activityRule.scenario.recreate()
        assertTrue(ViewModelProvider(compose.activity)[ChatViewModel::class.java].state.value.agent)
        compose.onNodeWithContentDescription("Чат").performClick()
        assertTrue(!ViewModelProvider(compose.activity)[ChatViewModel::class.java].state.value.agent)
    }

    @Test fun stoppingO5DoesNotAddAnAnswer() {
        val app = compose.activity.application as TestLumaApplication
        app.agentDelayMillis = 60000
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("O5").performClick()
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Исследуй Material 3")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Ищу в интернете").fetchSemanticsNodes().isNotEmpty() }
        capture("21-o5-working")
        compose.onNodeWithContentDescription("Чат").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Остановить ответ").performClick()
        assertEquals(1, vm.state.value.active!!.messages.size)
        assertTrue(vm.pending.value.isEmpty())
        assertTrue(vm.agentSteps.value.isEmpty())
    }
    @Test fun newAppearanceOptionsPersistAndKeepMaterialControls() {
        compose.onNodeWithContentDescription("Открыть меню").performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Внешний вид").performScrollTo().performClick()
        compose.onNodeWithText("Синий").performScrollTo().performClick()
        compose.onNodeWithText("Чёрный фон").performScrollTo().performClick()
        capture("17-custom-colors")
        compose.onNodeWithText("Внешний вид").performScrollTo().performClick()
        compose.onNodeWithText("Комфорт").performScrollTo().performClick()
        compose.onNodeWithText("Компактный чат").performScrollTo().performClick()
        compose.onNodeWithText("Анимация текста").performScrollTo().performClick()
        compose.onNodeWithText("Время ответа").performScrollTo().performClick()
        capture("18-custom-comfort")
        compose.activityRule.scenario.recreate()
        val prefs = ViewModelProvider(compose.activity)[ChatViewModel::class.java].state.value.preferences
        assertEquals("blue", prefs.accent)
        assertTrue(prefs.blackBackground && prefs.compactChat)
        assertTrue(!prefs.animateReplies && !prefs.showResponseTime)
    }
    @Test fun assistantTableRendersAndCopiesAllCells() {
        val app = compose.activity.application as TestLumaApplication
        app.replyText = "Сравнение моделей:\n\n| Модель | Скорость | Назначение |\n| :--- | :---: | --- |\n| **A-4.5** | Высокая | Анализ больших текстов |\n| V-4.4.1 | Очень высокая | Повседневные задачи |\n| S-4.5 | Высокая | Короткие вопросы |\n\nВыберите подходящую модель."
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Сравни модели таблицей")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Скопировать ответ").fetchSemanticsNodes().isNotEmpty() }
        capture("15-table-dark")
        compose.onNodeWithContentDescription("Скопировать ответ").performClick()
        val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(copied.contains("Модель\tСкорость\tНазначение"))
        assertTrue(copied.contains("S-4.5\tВысокая\tКороткие вопросы"))
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.runOnUiThread { vm.settings(vm.state.value.preferences.copy(theme = "light", largeText = true)) }
        compose.waitForIdle()
        capture("16-table-light-large")
    }
    @Test fun emptyStartAndDrawer() {
        compose.onNodeWithText("С чего начнём?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Отправить сообщение").assertIsNotEnabled()
        capture("01-start")
        compose.onNodeWithContentDescription("Открыть меню").performClick()
        compose.onNodeWithText("Пока тихо").assertIsDisplayed()
        // New chat and settings float at the bottom; settings is the account's avatar.
        compose.onNodeWithText("Новый диалог").assertIsDisplayed()
        compose.onNodeWithContentDescription("Настройки").assertIsDisplayed().assert(hasText("Т"))
        compose.onAllNodesWithText("Для вопросов", substring = true).assertCountEquals(0)
        capture("02-empty-menu")
        compose.onNodeWithText("Новый диалог").performClick()
        compose.onNodeWithText("С чего начнём?").assertIsDisplayed()
    }
    @Test fun sendHistoryRenameAndDelete() {
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Как превратить идею в привычку?")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Iskra AI").fetchSemanticsNodes().isNotEmpty() }
        val app = compose.activity.application as LumaApplication
        compose.waitUntil(5000) {
            app.history.load(app.accounts.current()?.id).active?.messages?.size == 2
        }
        assertEquals("Как превратить идею в привычку?", ChatViewModel(compose.activity.application).state.value.active?.title)
        capture("03-conversation")
        compose.onNodeWithContentDescription("Новый диалог").performClick()
        compose.onNodeWithText("С чего начнём?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Открыть меню").performClick()
        compose.onNodeWithText("Как превратить идею в привычку?").assertExists()
        capture("04-history")
        compose.onNodeWithContentDescription("Действия с диалогом Как превратить идею в привычку?").performClick()
        compose.onNodeWithText("Переименовать").performClick()
        // Drive the dialog's text-cursor animation explicitly in the native JVM renderer.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1000)
        compose.onNode(hasSetTextAction() and hasText("Как превратить идею в привычку?")).performTextReplacement("Моя идея")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Сохранить").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Моя идея").assertExists()
        compose.onNodeWithContentDescription("Действия с диалогом Моя идея").performClick()
        compose.onNodeWithText("Удалить диалог").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Моя идея").assertExists()
        compose.onNodeWithContentDescription("Действия с диалогом Моя идея").performClick()
        compose.onNodeWithText("Удалить диалог").performClick()
        compose.onNodeWithText("Удалить").performClick()
        compose.onNodeWithText("Пока тихо").assertExists()
    }
    @Test fun settingsAndDraft() {
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Моя неотправленная мысль")
        compose.onNodeWithContentDescription("Открыть меню").performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("По-вашему.").assertIsDisplayed()
        capture("05-settings")
        compose.onNodeWithText("Светлая").assertDoesNotExist()
        compose.onNodeWithText("Внешний вид").performScrollTo().performClick()
        compose.onNodeWithText("Светлая").performScrollTo().performClick()
        compose.onNodeWithText("Мята").performScrollTo().performClick()
        capture("06-light-settings")
        compose.onNodeWithContentDescription("Назад к чату").performClick()
        compose.onNodeWithText("Моя неотправленная мысль").assertExists()
        capture("07-light-chat")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Моя неотправленная мысль").assertExists()
    }
    @Test fun effortDialPicksTheLevelOfTheNextAnswer() {
        val app = compose.activity.application as TestLumaApplication
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("Уровень размышлений: Instant").performClick()
        // The default A-4.5 cannot think, and the picker says so instead of pretending.
        compose.onNodeWithText("A-4.5 отвечает без размышлений", substring = true).assertIsDisplayed()
        compose.runOnUiThread { vm.settings(vm.state.value.preferences.copy(model = LumaModel.S45.key)) }
        compose.onNodeWithText("Отвечает сразу, без размышлений").assertIsDisplayed()
        // A tap near the right end of the track lands on the last stop.
        compose.onNodeWithContentDescription("Уровень размышлений").performTouchInput { click(centerRight - androidx.compose.ui.geometry.Offset(40f, 0f)) }
        compose.waitForIdle()
        assertEquals(Effort.Ultra, vm.state.value.preferences.effortLevel)
        compose.onNodeWithText("Ultra").assertIsDisplayed()
        compose.onNodeWithText("Максимум размышлений", substring = true).assertIsDisplayed()
        capture("26-effort-picker")
        compose.runOnUiThread { vm.settings(vm.state.value.preferences.copy(theme = "light")) }
        compose.waitForIdle()
        capture("28-effort-picker-light")
        compose.runOnUiThread { vm.settings(vm.state.value.preferences.copy(theme = "dark")) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Уровень размышлений: Ultra").assertIsDisplayed()
        compose.onAllNodesWithText("Ultra").assertCountEquals(0)
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Подумай как следует")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Iskra AI").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Effort.Ultra, app.lastEffort)
        capture("27-effort-dial")
        // O5 runs on its own settings, so the dial steps aside there.
        compose.onNodeWithContentDescription("O5").performClick()
        compose.onAllNodesWithContentDescription("Уровень размышлений", substring = true).assertCountEquals(0)
        compose.activityRule.scenario.recreate()
        assertEquals(Effort.Ultra, ViewModelProvider(compose.activity)[ChatViewModel::class.java].state.value.preferences.effortLevel)
    }
    @Test fun theEmptyHeadlineTurnsToAThoughtAndFiveTapsSpinTheMark() {
        compose.onNodeWithText("С чего начнём?").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(5200)
        compose.onAllNodesWithText("С чего начнём?").assertCountEquals(0)
        val thoughts = compose.activity.resources.getStringArray(R.array.empty_thoughts)
        assertTrue(thoughts.any { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() })
        capture("29-empty-thought")
        // Quick taps, with the clock held so they land well inside the streak window.
        repeat(5) { compose.onNodeWithTag("luma-hero").performClick() }
        compose.mainClock.advanceTimeBy(900)
        capture("30-mark-spinning")
        compose.mainClock.advanceTimeBy(4000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("luma-hero").assertIsDisplayed()
    }
    @Test fun aDraftReachesTheDiskWhenTheAppLeavesTheScreen() {
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Мысль на потом")
        val app = compose.activity.application as LumaApplication
        val id = app.accounts.current()!!.id
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        // A second store reads only what was written down, as after a process kill.
        assertEquals("Мысль на потом", AccountHistory(app, null).load(id).newDraft)
    }
    @Test
    @Config(sdk = [35], qualifiers = "ru-rRU-w360dp-h640dp-320dpi")
    fun compactScreenWithLargeText() {
        compose.runOnUiThread { ViewModelProvider(compose.activity)[ChatViewModel::class.java].settings(Preferences(largeText = true)) }
        compose.onNodeWithContentDescription("Сообщение").assertIsDisplayed()
        compose.onNodeWithText("С чего начнём?").assertIsDisplayed()
        capture("08-compact-large-text")
        compose.onNodeWithContentDescription("Открыть меню").performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Данные").performScrollTo().performClick()
        compose.onNodeWithText("Очистить историю").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Очистить историю").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithContentDescription("Назад к чату").performClick()
        compose.onNodeWithText("С чего начнём?").assertIsDisplayed()
    }
    @Test fun signingOutClosesTheAssistantAndSigningBackInKeepsTheDraft() {
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Сохранить эту мысль")
        compose.runOnUiThread { vm.signOut() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Войти через Google").fetchSemanticsNodes().isNotEmpty() }
        // The sign-in screen twinkles forever, so it never goes idle: drive the
        // clock by hand past the entrance instead of waiting for stillness.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1200)
        // The gate is the whole app, not a banner: there is nowhere to type.
        assertTrue(compose.onAllNodesWithContentDescription("Сообщение").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithContentDescription("Открыть меню").fetchSemanticsNodes().isEmpty())
        capture("11-sign-in")
        compose.mainClock.autoAdvance = true
        compose.runOnUiThread { vm.signIn(MemoryAccounts.SIGNED_IN) }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Сообщение").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Сохранить эту мысль").assertExists()
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Iskra AI").fetchSemanticsNodes().isNotEmpty() }
    }
    // Waiting on a plain Kotlin condition does not pump the Robolectric looper,
    // so these wait on nodes: the drawer list is always composed, which makes a
    // conversation's name observable without opening the drawer.
    @Test fun completedAnswerShowsItsSavedDurationInsteadOfModelName() {
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Привет")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Думала", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val answer = vm.state.value.active!!.messages.last()
        assertTrue(answer.responseDurationMs != null && answer.responseDurationMs >= 0)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Думала", substring = true).assertExists()
        // Only the model picker still displays the selected model.
        compose.onAllNodesWithText("A-4.5").assertCountEquals(1)
    }
    @Test fun theModelNamesTheChatAfterTheFirstExchange() {
        val app = compose.activity.application as TestLumaApplication
        app.generatedTitle = "Идея в привычку"
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Как превратить идею в привычку?")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Идея в привычку").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Идея в привычку", vm.state.value.active?.title)
        assertEquals(1, app.titleRequests)
    }
    @Test fun failedNamingIsRetriedAfterTheNextReply() {
        val app = compose.activity.application as TestLumaApplication
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Первый вопрос")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Скопировать ответ").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, app.titleRequests)
        assertTrue(vm.state.value.active!!.needsTitle)
        app.generatedTitle = "Тема разговора"
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Продолжим")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Тема разговора").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, app.titleRequests)
        assertEquals(false, vm.state.value.active!!.needsTitle)
    }
    @Test fun aRenamedChatIsNotOverwrittenByTheGeneratedName() {
        val app = compose.activity.application as TestLumaApplication
        app.generatedTitle = "Придуманное имя"
        // Long enough that the rename below lands first, short enough that the
        // request still completes while the answer is being revealed.
        app.titleDelayMillis = 400
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Первый вопрос")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Iskra AI").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { vm.state.value.active?.id?.let { vm.rename(it, "Моё имя") } }
        // The reveal runs well past the title delay, so the generated name has
        // had every chance to land by the time this returns.
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Скопировать ответ").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, app.titleRequests)
        assertEquals("Моё имя", vm.state.value.active?.title)
        assertTrue(compose.onAllNodesWithText("Придуманное имя").fetchSemanticsNodes().isEmpty())
    }
    @Test fun choosingAnotherAssistantRoutesTheNextAnswer() {
        val app = compose.activity.application as TestLumaApplication
        compose.onNodeWithContentDescription("Открыть меню").performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Модель").performScrollTo().performClick()
        compose.onNodeWithText("V-4.4.1").performScrollTo().performClick()
        capture("12-models")
        compose.onNodeWithContentDescription("Назад к чату").performClick()
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Какую модель ты используешь?")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Iskra AI").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(LumaModel.V441, app.lastModel)
    }
    @Test fun retryAfterErrorDoesNotDuplicateUserMessage() {
        (compose.activity.application as TestLumaApplication).failNextReply = true
        val vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Повторяем вопрос")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Повторить запрос").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Повторить запрос").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Iskra AI").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, vm.state.value.active!!.messages.count { it.fromUser })
        assertEquals(1, vm.state.value.active!!.messages.count { !it.fromUser })
    }
    @Test fun formattedAnswerRendersInBothThemes() {
        (compose.activity.application as TestLumaApplication).replyText = "**Про математика:**\n— Сколько будет 2 + 2?\n— Четыре. Даже в понедельник.\n\n### Небольшой список\n- Первый пункт с **выделением**\n- Второй с *курсивом*\n\n```kotlin\nval greeting = \"Привет!\"\nprintln(greeting)\n```\n\n> Начнём день с улыбки."
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Шутка и пример кода")
        compose.onNodeWithContentDescription("Отправить сообщение").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Iskra AI").fetchSemanticsNodes().isNotEmpty() }
        // The answer is revealed word by word, and the copy button only appears
        // once it is whole — so waiting for it also proves the reveal finishes.
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Скопировать ответ").fetchSemanticsNodes().isNotEmpty() }
        capture("09-markdown-dark")
        compose.onNodeWithContentDescription("Скопировать ответ").performScrollTo().performClick()
        val clip = (compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
        org.junit.Assert.assertFalse(clip.contains("**Про математика:**"))
        org.junit.Assert.assertTrue(clip.contains("Про математика:"))
        org.junit.Assert.assertTrue(clip.contains("val greeting"))
        compose.runOnUiThread { ViewModelProvider(compose.activity)[ChatViewModel::class.java].settings(Preferences(theme = "light")) }
        capture("10-markdown-light")
    }
}
