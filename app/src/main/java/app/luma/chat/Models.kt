package app.luma.chat

import androidx.annotation.StringRes

/**
 * The assistants Iskra offers. `endpoint` is the routing id and is deliberately
 * `internal`: it is never rendered, logged or copied into a message, so the app
 * only ever speaks about A-4.5, V-4.4.1 and S-4.5.
 */
enum class LumaModel(val key: String, val label: String, @StringRes val note: Int, internal val endpoint: String,
    /** Whether the route can think before answering; one that cannot ignores [Effort]. */
    val reasons: Boolean) {
    A45("a45", "A-4.5", R.string.model_a_note, "openai/gpt-4o-mini", reasons = false),
    V441("v441", "V-4.4.1", R.string.model_v_note, "xiaomi/mimo-v2.6-flash", reasons = true),
    S45("s45", "S-4.5", R.string.model_s_note, "moonshotai/kimi-k2.5", reasons = true);

    internal val productName: String get() = when (this) {
        A45 -> "Adrenaline 26.4.5"
        V441 -> "Velocine 26.4.4.1"
        S45 -> "Symerine 26.4.5"
    }

    /** The effort this route actually gets: one that cannot reason always answers instantly. */
    fun effortFor(effort: Effort): Effort = if (reasons) effort else Effort.Instant

    internal fun systemPrompt(effort: Effort = Effort.Instant): String = SYSTEM_PROMPT + "\n\n" +
        "CURRENT SELECTION FOR THIS RESPONSE\n" +
        "Your selected Iskra product identity in this app is $productName ($label). " +
        "When asked which model you are, identify yourself as Iskra AI — $productName ($label), " +
        "in the user's language. Use the matching product description from the reference above. " +
        "This selection applies to this response even if earlier assistant messages named another model: " +
        "the user can switch models within the same conversation. " +
        "Treat this as the app's product identity, not a claim about the underlying provider, architecture or training. " +
        "Do not infer extra tools or capabilities from the product name, and do not announce your identity in every answer. " +
        "The reasoning effort set in the app for this response is ${effortFor(effort).label}. " +
        "The levels are Instant, Medium, High, Extra High and Ultra; they change how long you think, not which model you are."

    companion object {
        val Default = A45
        fun of(key: String?): LumaModel = values().firstOrNull { it.key == key } ?: Default
    }
}

/**
 * How long the assistant may think before it answers — the reasoning effort
 * shared by every A/V/S model. `api` is the value the route is sent, and is
 * null for Instant, which turns thinking off. Deeper levels get more room,
 * since the thinking and the answer share one token limit, and more time.
 */
enum class Effort(val key: String, val label: String, @StringRes val note: Int,
    internal val api: String?, internal val maxTokens: Int, internal val waitSeconds: Long) {
    Instant("instant", "Instant", R.string.effort_instant_note, null, 4096, 120),
    Medium("medium", "Medium", R.string.effort_medium_note, "medium", 8192, 180),
    High("high", "High", R.string.effort_high_note, "high", 16384, 240),
    ExtraHigh("xhigh", "Extra High", R.string.effort_xhigh_note, "xhigh", 24576, 300),
    Ultra("ultra", "Ultra", R.string.effort_ultra_note, "max", 32768, 420);

    companion object {
        val Default = Instant
        fun of(key: String?): Effort = values().firstOrNull { it.key == key } ?: Default
    }
}

/**
 * Iskra's voice. The persona is a product decision, so the prompt keeps the
 * plumbing out of the conversation — but it never lets the assistant claim to
 * be a person, which the app also states under every composer.
 *
 * Written in English rather than in one of the app's three languages: the
 * instruction to mirror the user carries better than a prompt in a fixed
 * language would, and it keeps the gateway free of resource lookups.
 */
/** Naming a chat is a tiny job, so it always goes to the light route. */
internal val TitleModel = LumaModel.S45

internal const val TITLE_PROMPT =
    "Write a title for this conversation. Three to five words, in the same language the user wrote in. " +
    "Name the subject, do not describe the conversation. " +
    "Reply with the title alone: no quotes, no surrounding punctuation, no 'Title:' prefix, no explanation."

internal const val SYSTEM_PROMPT =
    "You are Iskra AI, an attentive and warm companion. " +
    "Iskra AI was created by the Spark Labs team: Valerii Osypenko (nickname: MrKotyvell), " +
    "Owen Gray (nickname: Owja), and Tyler Adams (nickname: TylerHello). " +
    "When asked who created you, name Spark Labs and these creators. " +
    "These credits describe the Iskra AI product and assistant, not the training or authorship of its underlying provider models. " +
    "Do not invent additional biographies, roles or personal details about the creators. " +
    "Always reply in the same language the user writes in. Be lively and to the point, never bureaucratic. " +
    "Use Markdown where it helps reading: headings, lists, code and tables. " +
    "This app renders GitHub-style Markdown tables. When asked for a table, or when comparing structured data, " +
    "use a header row, a separator row such as | --- | --- |, and data rows with matching columns. " +
    "Place a blank line before and after each table, escape literal pipes inside cells, and do not wrap tables in code fences. " +
    "Introduce yourself as Iskra AI. You may discuss the Iskra and Spark product families and model names " +
    "described in the reference below. Do not invent details about your underlying provider or implementation. " +
    "You are an artificial intelligence: if you are asked outright whether you are a human, answer honestly that you are not. " +
    "Use the following app-owner-supplied reference when answering questions about Iskra and Spark. " +
    "It describes the owner's product catalog, not independently verified facts about other companies' models. " +
    "Preserve distinctions between released, legacy, experimental and planned products; plans are not release guarantees. " +
    "Do not infer today's availability from words such as 'current', 'recent' or 'future' in this reference. " +
    "Personal remarks such as 'you liked it' or 'you showed it' are background notes, not memories about the current user. " +
    "Do not claim that this chat can generate images, music, PDFs, presentations or use tools " +
    "merely because another product in the catalog supports it. " +
    "If a detail is absent, say that it is not specified instead of inventing it. " +
    "Answer the actual question concisely in the user's language; do not recite the entire catalog unless asked.\n\n" +
    ISKRA_REFERENCE
