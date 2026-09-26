package app.luma.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import androidx.lifecycle.ViewModelProvider

class ChatFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: ChatViewModel
    private val testAccount = LumaAccount("instrumented", "Тест", "test@example.com")

    @Before fun reset() {
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity)[ChatViewModel::class.java]
            vm.clearHistory()
            vm.settings(Preferences())
            vm.signIn(testAccount)
        }
    }

    @Test fun signedOutShowsOnlyTheGate() {
        compose.runOnUiThread { vm.signOut() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Войти через Google").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Сообщение").assertDoesNotExist()
        compose.runOnUiThread { vm.signIn(testAccount) }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Сообщение").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun settingsAndDraftSurviveNavigation() {
        compose.onNodeWithContentDescription("Сообщение").performTextInput("Черновик")
        compose.onNodeWithContentDescription("Открыть меню").performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Внешний вид").performScrollTo().performClick()
        compose.onNodeWithText("Светлая").performScrollTo().performClick()
        compose.onNodeWithText("Мята").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Назад к чату").performClick()
        compose.onNodeWithText("Черновик").assertExists()
    }

    @Test fun assistantChoiceIsRemembered() {
        compose.onNodeWithContentDescription("Открыть меню").performClick()
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithText("Модель").performScrollTo().performClick()
        compose.onNodeWithText("S-4.5").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Назад к чату").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5000) { compose.onAllNodesWithText("S-4.5").fetchSemanticsNodes().isNotEmpty() }
    }
}
