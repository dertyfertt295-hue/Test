package app.luma.chat

import kotlinx.coroutines.delay

class MemoryCredentials(private var value: String? = "sk-or-test-not-a-real-secret") : CredentialStore {
    override fun read() = value
    override fun save(value: String) { this.value = value }
    override fun clear() { value = null }
    override fun hasKey() = value != null
}

class MemoryAccounts(private var account: LumaAccount? = SIGNED_IN) : AccountStore {
    override fun current() = account
    override fun save(account: LumaAccount) { this.account = account }
    override fun clear() { account = null }
    companion object {
        val SIGNED_IN = LumaAccount("test-id", "Тестовый Пользователь", "test@example.com")
    }
}

class TestLumaApplication : LumaApplication() {
    override val syncEnabled = false
    override val credentials: CredentialStore = MemoryCredentials()
    // UI tests start past the gate unless a test clears this.
    override val accounts: AccountStore = MemoryAccounts()
    var agentDelayMillis = 100L
    var agentRequests = 0
    var failNextReply = false
    var lastModel: LumaModel? = null
    var lastEffort: Effort? = null
    var replyText = "Начните с маленького шага.\n\nВыберите одно простое действие, которое можно повторять каждый день. Привяжите его к привычному моменту — например, к утреннему чаю.\n\nЧто вы хотели бы попробовать первым?"
    /** Null keeps the placeholder name, so tests that do not care are untouched. */
    var generatedTitle: String? = null
    var titleRequests = 0
    var titleDelayMillis = 20L
    override val gateway: ChatGateway = object : ChatGateway {
        override suspend fun reply(messages: List<Message>, model: LumaModel, effort: Effort): ModelReply {
            lastModel = model
            lastEffort = effort
            delay(100)
            if (failNextReply) { failNextReply = false; throw ChatFailure(R.string.error_rate_limit) }
            return ModelReply(replyText)
        }
        override suspend fun transcribe(audio: ByteArray) = "Распознанный текст"
        override suspend fun agent(messages: List<Message>, progress: (List<AgentStep>) -> Unit): ModelReply {
            agentRequests++
            val steps = listOf(AgentStep("plan", done = true), AgentStep("search", "Material 3 Android"))
            progress(steps)
            delay(agentDelayMillis)
            return reply(messages, LumaModel.A45).copy(steps = steps.map { it.copy(done = true) })
        }
        override suspend fun title(messages: List<Message>): String {
            titleRequests++
            delay(titleDelayMillis)
            return generatedTitle.orEmpty()
        }
    }
}
