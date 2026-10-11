package dev.deeptelar.telar.demo

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.agent.ChatModelException
import dev.deeptelar.telar.agent.ChatRequest
import dev.deeptelar.telar.demo.game.Agent
import dev.deeptelar.telar.demo.llm.ClaudeModels
import dev.deeptelar.telar.demo.storage.MemoryStore
import dev.deeptelar.telar.demo.ui.ModelMode
import dev.deeptelar.telar.demo.ui.Settings
import dev.deeptelar.telar.demo.ui.SettingsRepository

/** Which model the settings give the game, and what of them is kept on the device. */
class SettingsTest {
    private val sent = mutableListOf<HttpRequestData>()

    /** Answers every request the way a server with the API of OpenAI does. */
    private val client = HttpClient(
        MockEngine { request ->
            sent += request
            respond(
                """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Hi"}}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )

    private suspend fun ask(settings: Settings): String =
        settings.chatModel(client).chat(ChatRequest(listOf(ChatMessage.User("Hello")), tools = listOf(Agent.menuPrice.spec))).message.text

    private fun sentModel(): String = Json.parseToJsonElement((sent.single().body as TextContent).text).jsonObject["model"]!!.jsonPrimitive.content

    @Test
    fun openAiIsAskedAtItsAddressWithThePlayersKey() = runTest {
        assertEquals("Hi", ask(Settings(ModelMode.OpenAi).withKey(" sk-test ")))

        val request = sent.single()
        assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
        assertEquals("Bearer sk-test", request.headers[HttpHeaders.Authorization])
        assertEquals("gpt-5", sentModel())
        val tools = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["tools"]!!.jsonArray
        assertEquals("menu_price", tools.single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun geminiIsAskedAtGooglesAddressWithTheModelThePlayerNamed() = runTest {
        ask(Settings(ModelMode.Gemini).withKey("key").withModel(" gemini-3.8-pro "))

        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", sent.single().url.toString())
        assertEquals("Bearer key", sent.single().headers[HttpHeaders.Authorization])
        assertEquals("gemini-3.8-pro", sentModel())
    }

    @Test
    fun anotherServerNeedsAModelButNoKey() = runTest {
        val ollama = Settings(ModelMode.Other)
        assertEquals("a model", ollama.missing)
        assertEquals("scripted", ollama.modelLabel)

        ask(ollama.withModel("llama3.2"))

        assertEquals("http://localhost:11434/v1/chat/completions", sent.single().url.toString())
        assertNull(sent.single().headers[HttpHeaders.Authorization])
        assertEquals("llama3.2", sentModel())
    }

    @Test
    fun anotherServerIsAskedAtTheAddressThePlayerEntered() = runTest {
        val groq = Settings(ModelMode.Other, address = " https://api.groq.com/openai/v1/ ").withKey("gsk").withModel("llama-3.3-70b-versatile")
        assertEquals("api.groq.com", groq.host)

        ask(groq)

        assertEquals("https://api.groq.com/openai/v1/chat/completions", sent.single().url.toString())
        assertEquals("Bearer gsk", sent.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun withoutWhatAServiceNeedsTheScriptedModelAnswers() = runTest {
        listOf(
            Settings(ModelMode.Claude) to "a key",
            Settings(ModelMode.OpenAi) to "a key",
            Settings(ModelMode.Gemini).withKey("  ") to "a key",
            Settings(ModelMode.OpenAi).withKey("sk").withModel(" ") to "a model",
            Settings(ModelMode.Other, address = "").withModel("llama3.2") to "an address",
        ).forEach { (settings, missing) ->
            assertEquals(missing, settings.missing)
            assertFalse(settings.usesService)
            assertEquals("scripted", settings.modelLabel)
            settings.chatModel(client)
        }
        assertNull(Settings().missing)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aRequestTheBrowserCouldNotSendIsAFailureOfTheModel() = runTest {
        // What Ktor's engine throws in a browser when a request gets no response. The library reports
        // it as a failure of the model since 0.1.0-alpha09, and a failure is what ends a run with a
        // game over screen.
        val offline = HttpClient(MockEngine { throw Error("Fail to fetch") })
        val request = ChatRequest(listOf(ChatMessage.User("Hello")))

        listOf(
            Settings(ModelMode.Other).withModel("llama3.2") to "Could not reach the API at http://localhost:11434/v1: Fail to fetch",
            Settings(ModelMode.Claude).withKey("key") to "Could not reach the Claude API: Fail to fetch",
        ).forEach { (settings, message) ->
            val model = settings.chatModel(offline)
            assertEquals(message, assertFailsWith<ChatModelException> { model.chat(request) }.message)
            assertEquals(message, assertFailsWith<ChatModelException> { model.stream(request).collect {} }.message)
        }
    }

    @Test
    fun eachServiceKeepsItsOwnKeyAndModel() {
        val settings = Settings(ModelMode.OpenAi).withKey("sk-openai").withModel("gpt-5-mini")
            .copy(mode = ModelMode.Gemini).withKey("gemini-key")

        assertEquals("gemini-key", settings.apiKey)
        assertEquals("gemini-3.8-flash", settings.modelId)
        assertEquals("generativelanguage.googleapis.com", settings.host)
        assertEquals("sk-openai", settings.copy(mode = ModelMode.OpenAi).apiKey)
        assertEquals("gpt-5-mini", settings.copy(mode = ModelMode.OpenAi).modelLabel)
        assertEquals(ClaudeModels.default.id, settings.copy(mode = ModelMode.Claude).modelId)
    }

    @Test
    fun theKeysAreStoredOnlyWhenThePlayerAsksForIt() = runTest {
        val store = MemoryStore()
        val repository = SettingsRepository(store)
        val settings = Settings(ModelMode.Other, address = "https://api.groq.com/openai/v1").withKey("gsk").withModel("llama-3.3-70b-versatile")
            .copy(mode = ModelMode.OpenAi).withKey("sk-openai")

        repository.save(settings)
        val forgotten = repository.load()
        assertEquals(ModelMode.OpenAi, forgotten.mode)
        assertEquals("", forgotten.apiKey)
        assertFalse(forgotten.rememberKey)
        assertEquals("https://api.groq.com/openai/v1", forgotten.address)
        assertEquals("llama-3.3-70b-versatile", forgotten.copy(mode = ModelMode.Other).modelId)

        repository.save(settings.copy(rememberKey = true))
        val remembered = repository.load()
        assertEquals("sk-openai", remembered.apiKey)
        assertEquals("gsk", remembered.copy(mode = ModelMode.Other).apiKey)
        assertTrue(remembered.rememberKey)

        // Unticking the box takes the keys off the device again.
        repository.save(remembered.copy(rememberKey = false))
        assertNull(store.get("pixelpizza.apiKey.OpenAi"))
        assertNull(store.get("pixelpizza.apiKey.Other"))
    }

    @Test
    fun whatAnEarlierVersionStoredForClaudeIsStillRead() = runTest {
        val store = MemoryStore()
        store.set("pixelpizza.mode", "Claude")
        store.set("pixelpizza.apiKey", "sk-ant-old")
        store.set("pixelpizza.model", "claude-haiku-4-5-20251001")

        val settings = SettingsRepository(store).load()

        assertEquals(ModelMode.Claude, settings.mode)
        assertEquals("sk-ant-old", settings.apiKey)
        assertEquals("claude-haiku-4-5", settings.modelId)
        assertTrue(settings.rememberKey)
        assertEquals(Settings(), SettingsRepository(MemoryStore()).load().copy(models = emptyMap()))
    }
}
