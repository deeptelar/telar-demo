package dev.deeptelar.telar.demo.ui

import io.ktor.client.HttpClient
import dev.deeptelar.telar.demo.game.scriptedModel
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.demo.llm.ClaudeModels
import dev.deeptelar.telar.demo.storage.KeyValueStore
import dev.deeptelar.telar.openai.OpenAiChatModel

/**
 * Who answers in the stages that ask a model: the scripted model, or a service the player has a key for.
 *
 * The settings are stored under the names of these entries, so a new name forgets what a player chose.
 *
 * @property title the name under Options.
 * @property keyLabel what the field for the key is called.
 * @property baseUrl where the service has its API. For [Other] it is the address the field starts with.
 * @property defaultModel the model the field starts with. Empty when the player has to name one.
 */
enum class ModelMode(val title: String, val keyLabel: String = "", val baseUrl: String = "", val defaultModel: String = "") {
    Scripted("Scripted"),
    Claude("Claude", "Anthropic API key", "https://api.anthropic.com/v1", ClaudeModels.default.id),
    OpenAi("OpenAI", "OpenAI API key", OpenAiChatModel.OPENAI_BASE_URL, "gpt-5"),
    Gemini("Gemini", "Gemini API key", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash"),

    /** Any other server with the API of OpenAI, such as Ollama on this device. Only this one needs no key. */
    Other("Other address", "API key, if the server asks for one", OpenAiChatModel.OLLAMA_BASE_URL),
}

/**
 * What the player chose under Options.
 *
 * @property keys the key of each service the player entered one for.
 * @property models the model of each service. A service that is not in it uses its [ModelMode.defaultModel].
 * @property address where the server of [ModelMode.Other] is.
 */
data class Settings(
    val mode: ModelMode = ModelMode.Scripted,
    val keys: Map<ModelMode, String> = emptyMap(),
    val models: Map<ModelMode, String> = emptyMap(),
    val address: String = ModelMode.Other.baseUrl,
    val rememberKey: Boolean = false,
) {
    /** The key of the chosen service. */
    val apiKey: String
        get() = keys[mode].orEmpty()

    /** The model of the chosen service. */
    val modelId: String
        get() = models[mode] ?: mode.defaultModel

    /** Where the chosen service has its API. */
    val baseUrl: String
        get() = if (mode == ModelMode.Other) address.trim() else mode.baseUrl

    /** The host the key is sent to, to tell the player. */
    val host: String
        get() = baseUrl.substringAfter("://").substringBefore('/')

    /** What is still missing before the chosen service can be asked, or `null`. Until then the scripted model answers. */
    val missing: String?
        get() = when {
            mode == ModelMode.Scripted -> null
            mode == ModelMode.Other && baseUrl.isBlank() -> "an address"
            mode != ModelMode.Other && apiKey.isBlank() -> "a key"
            modelId.isBlank() -> "a model"
            else -> null
        }

    /** A model of a service answers, not the scripted one. */
    val usesService: Boolean
        get() = mode != ModelMode.Scripted && missing == null

    val modelLabel: String
        get() = if (usesService) modelId.trim() else "scripted"

    fun withKey(key: String): Settings = copy(keys = keys + (mode to key))

    fun withModel(model: String): Settings = copy(models = models + (mode to model))

    fun chatModel(client: HttpClient): ChatModel = when {
        !usesService -> scriptedModel(delayMillis = 650)
        mode == ModelMode.Claude -> ClaudeModels.byId(modelId).chatModel(client, apiKey.trim())
        // One class for every service with the API of OpenAI: only the address changes.
        else -> OpenAiChatModel(client, apiKey = apiKey.trim().ifEmpty { null }, model = modelId.trim(), baseUrl = baseUrl)
    }
}

/** Persists the settings. The API keys are stored only if the user asked for it. */
class SettingsRepository(private val store: KeyValueStore) {
    suspend fun load(): Settings {
        val services = ModelMode.entries - ModelMode.Scripted
        val keys = services.mapNotNull { mode -> store.get(mode.slot(API_KEY))?.takeIf { it.isNotEmpty() }?.let { mode to it } }.toMap()
        val models = services.mapNotNull { mode -> store.get(mode.slot(MODEL))?.let { mode to it } }.toMap()
        return Settings(
            mode = ModelMode.entries.firstOrNull { it.name == store.get(MODE) } ?: ModelMode.Scripted,
            keys = keys,
            // An id saved by an earlier version may be one the list no longer has.
            models = models + (ModelMode.Claude to ClaudeModels.byId(models[ModelMode.Claude].orEmpty()).id),
            address = store.get(ADDRESS) ?: ModelMode.Other.baseUrl,
            rememberKey = keys.isNotEmpty(),
        )
    }

    suspend fun save(settings: Settings) {
        store.set(MODE, settings.mode.name)
        store.set(ADDRESS, settings.address)
        (ModelMode.entries - ModelMode.Scripted).forEach { mode ->
            settings.models[mode]?.let { store.set(mode.slot(MODEL), it) }
            val key = settings.keys[mode].orEmpty()
            if (settings.rememberKey && key.isNotEmpty()) store.set(mode.slot(API_KEY), key) else store.remove(mode.slot(API_KEY))
        }
    }

    /** Claude was the only service once, and its settings keep the names they had then. */
    private fun ModelMode.slot(name: String): String = if (this == ModelMode.Claude) name else "$name.${this.name}"

    private companion object {
        const val MODE = "pixelpizza.mode"
        const val MODEL = "pixelpizza.model"
        const val API_KEY = "pixelpizza.apiKey"
        const val ADDRESS = "pixelpizza.address"
    }
}
