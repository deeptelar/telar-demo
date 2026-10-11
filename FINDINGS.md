# Findings for Telar

What building this app showed about [Telar](https://github.com/deeptelar/telar), starting with
`0.1.0-SNAPSHOT` in October 2026. The app uses the library only through its published artifacts.
Until `0.1.0-alpha05` the library was called langgraph-kt; this file uses its current names.

The app was built twice. The first version had three separate screens: a tool agent, an email
approval flow and a parallel research run. The second version is the game in this repository,
with the eight help desks of the library's tutorial as stages. Most of this file is about the
first version; [The second version](#the-second-version-the-game) says what the rebuild added.

## What was checked in the first version

- The app builds against the artifacts from the local Maven repository on the JVM and on wasmJs.
  Gradle picked the right variant for each target with no extra configuration.
- The 24 common tests pass on the JVM and in headless Chrome (wasmJs). One more browser-only test
  pauses a run in `localStorage` and resumes it from a second session.
- The production bundle was driven in headless Chrome with the scripted model: the tool loop, the
  approval flow with a change request, a page reload while paused, and the parallel research run
  all behaved as expected.
- With an invalid key, a request from the browser reached the Claude API and its error came back
  as `NodeExecutionException` naming the `assistant` node.
- Stop and Discard are covered by tests that drive the screen controllers. They found a bug in
  this app, not in the library: stopping a redraft left the approval buttons on screen although
  the saved run stood before `draft`. The controller now re-reads the saved run after a stop.
- The desktop app starts and draws its first screen on Linux.

Not checked: a successful Claude call (no API key was available), the workflows in the desktop
window (only the browser build was clicked through), and any browser other than Chrome.

## Gaps found, now closed in the library

The first version of this app needed three workarounds. All three were fixed in Telar by
[pull request 7](https://github.com/deeptelar/telar/pull/7), which is merged, and the
app uses the new APIs instead.

| Gap | Workaround the app had | Library fix |
|---|---|---|
| A checkpointer for a new storage had to rebuild the checkpoint's JSON envelope | A 50-line `StorageCheckpointer` with its own format | `CheckpointCodec`; the checkpointer is now three one-line methods |
| `stream()` only reported finished steps, so the UI could not show running nodes | A `RunTracker` wrapped around every node action | `GraphEvent.NodeStarted` and `NodeCompleted` |
| A compiled graph could not be inspected, so each graph's layout was listed by hand | A `stages` list next to each graph | `CompiledGraph.topology` |

Writing the codec's tests also uncovered a bug: `FileCheckpointer` never wrote its format version
into the file. That is fixed in the same pull request.

## Smaller observations, addressed

- **There was no way to ask where a thread stands.** The app read the checkpoint itself to decide
  what to show after a reload or a failure. `CompiledGraph.lastResult(config)` now returns the
  thread's last `GraphResult`, and the email screen uses it.
- **The approval pattern deserved a guide.** "Approve, or go back with feedback" needs a
  pass-through `review` node with `interruptBefore` and a conditional edge after it. The library
  README now explains it, and `samples/ReviewLoop.kt` runs it.
- **Multi-turn chat restarts the graph each turn.** `invoke` always starts a new run, so a chat
  passes the whole conversation in as the input state. The README now shows this, with
  `lastResult` supplying the previous turn's state.

## Still open

- **`lastResult` cannot tell a pause from an unfinished step.** Both come back as `Interrupted`.
  The app tells them apart by `nextNodes`, which is enough here. `Checkpoint.interruptedBefore`
  has the exact answer for an app that needs it.
- **`START` and `END` are `__START__` and `__END__`.** The app trims the underscores for display.
- **No model integration outside the JVM.** Closed in `0.1.0-alpha02` by `telar-agent` and
  `telar-anthropic`. See [The third pass](#the-third-pass-the-librarys-agent-modules).

## What worked well

- The three graphs compiled and ran correctly as first written; no library bug turned up.
- The `START then a then b` DSL and `conditionalEdge(from, targets)` read clearly in real graphs.
- Interrupt and resume worked across a page reload on the first attempt, including a second
  interrupt after looping back to `draft`.
- Fan-out with a `Reducer` ran the three branches concurrently in the browser.
- `NodeExecutionException` gave the UI a useful error message with no extra code.

## The second version: the game

What was checked:

- The 31 common tests pass on the JVM and in headless Chrome (wasmJs), with one more browser-only
  test that pauses a run in `localStorage` and resumes it from a second session. They cover the
  graph of every stage and the game behind the screens: a run that lights its path, parallel
  nodes, a save point answered after a "reload", a failed node retried from its checkpoint, Stop,
  and the cleared stages surviving a new session.
- On the JVM, one test clicks through the screens (title, stage select, run, approve, next stage)
  and another draws every screen in dark, light and at phone width.
- The production browser bundle loads and draws the title screen in headless Chrome.

Not checked: clicking through the game in a real browser or in the desktop window, and, as
before, a successful Claude call.

What the rebuild showed about the library:

- **Eight graphs, no library change.** Every level of the tutorial ran as written inside a
  Compose app on both targets, sharing one `@Serializable` state.
- **`topology` is enough to draw a board, but not to lay one out.** The arrows, and which of them
  are conditional, come from `CompiledGraph.topology`. Where a node sits is still the app's
  decision: each stage places its nodes by hand, and a test checks that the placement and the
  graph agree.
- **Events name nodes, not edges.** To light the arrow a run just followed, the app pairs the
  nodes of the last `StepCompleted` with the next `NodeStarted` and looks the pair up in the
  topology. That is three lines here, but an event that says where a node was reached from would
  make it none.
- **`lastResult` after a failure says where a retry starts.** The game-over screen uses it to tell
  the player whether a retry resumes at the failed node or starts over because nothing was saved
  yet. It needed no extra bookkeeping.

## The third pass: the library's agent modules

`0.1.0-alpha02` added `telar-agent` (a chat model interface, tools, a tool-calling loop) and
`telar-anthropic` (a Claude client on Ktor). They grew out of this app's `llm/` package, and
this pass replaced that package with them. It was the first use of those modules from outside the
library's repository.

What changed here:

- The app's own `ChatModel`, its message types and its 110-line Messages API client are gone.
- The agent of stage 6 was two hand-written nodes, a conditional edge and a function that ran the
  tools. It is now one call to `toolLoop`.
- The two tools had JSON schemas written by hand and read their input from a `JsonObject`. Each is
  now a `@Serializable` class with a `@Description`.
- What stayed is what belongs to the game: the scripted model, and the list of Claude models with
  the request fields each one takes.

What was checked:

- The 36 common tests pass on the JVM and in headless Chrome (wasmJs), and the JVM run draws every
  screen. Gradle picked the right variant of the new modules for the browser, the desktop and
  Android with no extra configuration.
- A test runs the agent against a mocked Messages API and checks that Claude's thinking block goes
  back unchanged with the tool result.
- The Android app assembles.

Not checked: a real Claude call from the game. The library's own sample was run against the real
API, but not with the `fallbacks` and `output_config` fields the game adds.

What the pass showed about the library:

- **The model-specific request fields needed no library change.** `AnthropicChatModel` takes extra
  body fields and headers, which covered the effort setting, the fallback beta and the header a
  browser needs.
- **A failed model call did not say which node failed.** The engine wrapped what a node threw in
  `NodeExecutionException`, which names the node, but it passed on an exception of the library
  itself as it was, and `ChatModelException` is one. The game marks the failed node on the board, so
  it fell back to the node that was running. Fixed in `0.1.0-alpha03`: the engine wraps every
  exception of a node, so the failure names its node and the fallback is gone.
- **A conversation that starts from other fields of the state was easy to get wrong.** The ticket
  holds the customer and the message, and the first user message is built from them. `toolLoop`
  reads the conversation with `messages` and adds to it with `append`, and both had to agree on
  that first message: an `append` that added to the stored list dropped it, and nothing failed
  until the model answered a conversation without a question. The app had one function that both
  called. Fixed in
  `0.1.0-alpha03`: `toolLoop` takes a `firstMessage` and stores it with the model's first answer.
  The app's shared function is gone, and `messages` and `append` read and write one list.
- **`toolLoop` has no place for work around the tools.** Every node of the game pretends to take a
  moment. For the tools node, the app wraps each tool. That is three lines, and fine.
- **A text answer that was cut off was not visible to the app.** `ChatResponse.truncated` said so,
  but `append` only receives the messages. The old client failed the run in that case. Fixed in
  `0.1.0-alpha03`: the flag moved to the message (`ChatMessage.Assistant.truncated`), so `append`
  sees it. The game does not use it yet; its answers are two sentences long.
- **Changing the state's shape was handled by the existing checkpoint API.** Old saves of stage 6
  hold the conversation in the old shape. Loading one throws `CheckpointCorruptedException`, and the
  app deletes the save. No migration code was needed.
- **Sealed message types made the run log simpler.** One `when` over `ChatMessage` replaced two
  lookups into raw content blocks.

## The fourth pass: streaming

`0.1.0-alpha04` lets a model's answer arrive piece by piece. This pass tried it in the game before
the release, against a build of the library from its repository, and again with the release from
Maven Central.

What changed here:

- The run log shows the answer a model is writing, word by word, in the stages that ask a model (6
  and 8). When the node finishes, the line goes away and the answer is in the ticket.
- The scripted model writes word by word too, so the game shows this without an API key.

What was checked:

- 40 tests pass on the JVM and 39 in headless Chrome (wasmJs). Three are new on both: the text shows
  while a model writes, it is thrown away when the player presses Stop, and a run against a mocked
  Messages API streams with the fields the game adds for the chosen model.
- A probe in headless Chrome asked a local stand-in for the API that sends four pieces 200 ms apart.
  They arrived at 5, 203, 406 and 605 ms, so the browser client delivers the pieces as they come
  and does not wait for the end. The probe is not part of the suite, because it needs that server.
- The library's sample streamed from the real API on the JVM.

Not checked: a real Claude call from the game in a browser, with streaming.

What the pass showed about the library:

- **The agent needed no change.** Stage 6 is a `toolLoop`, and it streams by itself when the run is
  collected with `stream()`, which the game always does.
- **A node of the app's own needed one word.** The writer of stage 8 called `chat(prompt, system)`.
  It now calls `chatWithProgress(prompt, system)`. The first version of that function only took a
  `ChatRequest`; the overload with a prompt was added to the library because of this node.
- **The new event broke one `when`.** The game matches on every kind of `GraphEvent` without an
  `else`, so it did not compile until it had a branch for `NodeProgress`. That is what a sealed type
  is for, and the changelog says so.
- **A stand-in model streams by overriding one function.** `ChatModel.stream` has a default that
  delivers the answer in one piece, and the scripted model replaces it with one that writes word by
  word.
- **Text that arrives before the model asks for a tool has no place in the state.** Claude sometimes
  writes a sentence before a tool call. The game shows it while it arrives and drops it when the
  node finishes, because the ticket only keeps the final reply. The message with that text is still
  in the conversation. Fine for the game; an app that wants to keep it reads it from there.

## The fifth pass: checkpointers from the library

The game had a checkpointer of its own, `StorageCheckpointer`, because the library had none for a
browser. The library now has `LocalStorageCheckpointer` in `telar-checkpoint-browser`. This
pass tried it in the game before the release, against a build of the library from its repository.

What changed here:

- The browser build saves its runs with `LocalStorageCheckpointer`, and the desktop and Android
  builds with `FileCheckpointer`. `StorageCheckpointer` and its tests are gone.
- `Game` takes a `Checkpointer`. One `expect` function, `platformCheckpointer()`, picks it.

What was checked:

- 47 tests pass on the JVM and 44 in headless Chrome (wasmJs). In the browser, a stage that pauses
  in one `Game` waits at its save point in the next, through the real `localStorage`. On the JVM
  the same holds through a file.
- The browser bundle and the Android app build.

Not checked: the hosted game in a real browser after the change, and the Android app on a device.

What the pass showed:

- **A save in the browser from before the change is still read.** The new checkpointer was given
  the prefix the game already used, and the format of an entry is the library's own, so the keys
  and their contents are the same.
- **On the desktop and on Android a saved run is lost once.** The files have another name and
  place than before. For a game that is acceptable.
- **A test cannot leave a damaged save behind through `MemoryCheckpointer`.** It keeps objects, not
  text. The test that covers a save of an older version of the game has a small checkpointer that
  keeps the text `CheckpointCodec` writes. The codec made that a dozen lines.
- **A file is written on a real thread.** The game starts a run in the background, and a test
  that then calls `advanceUntilIdle()` gets ahead of the file. The test with `FileCheckpointer`
  waits in real time for the stage to reach its save point.

## The sixth pass: the library's new name

`0.1.0-alpha06` is the first release as Telar: the artifacts are `dev.deeptelar:telar-*` and the
package is `dev.deeptelar.telar`. This pass moved the game to it, with the release from Maven
Central.

What changed here:

- The six dependencies have the new coordinates, and the imports the new package. Nothing else in
  the code that uses the library changed.
- The app's own package is `dev.deeptelar.telar.demo`, and it says "built with Telar".
- The settings are stored under `pixelpizza.*`, and the desktop app keeps its files in
  `~/.telar-demo`.

What was checked:

- 47 tests pass on the JVM and 44 in headless Chrome (wasmJs), the same as before the move.
- The browser bundle and the Android app build.

Not checked: the hosted game in a real browser after the change, and the Android app on a device.

What the pass showed:

- **The changelog's steps were the whole move.** New coordinates and one replacement of the package
  name in the imports, and the app compiled for all three targets. The game does not catch the
  renamed base exception, and it gives `LocalStorageCheckpointer` its own prefix, so the other two
  steps did not apply.
- **What was stored is not carried over.** The settings have new names and the desktop app a new
  folder, so both start empty. In the browser the runs keep their prefix, but the game has a new
  address, and a browser has nothing stored for it yet.
- **On Android the game is a new app.** The application id follows the package.

## The seventh pass: a release with new features

`0.1.0-alpha07` adds graphs inside graphs, the history of a run, merge rules for single properties
and two model modules. The game uses none of them yet. This pass moved the game to the release from
Maven Central, to see what such a release asks of an app that only wants the new version.

What changed here:

- The version of the six dependencies.
- One branch in `Game`. It reads the events of a stream in a `when` without an `else`, and a stream
  has a new kind of event, `SubgraphEvent`. No stage has a graph inside a graph; the branch reads a
  model's text the same way as for a node.

What was checked:

- 47 tests pass on the JVM and 44 in headless Chrome (wasmJs), the same as before the move.
- The browser bundle and the Android app build.
- The files of the library that Gradle used have the checksums of the files on Maven Central.

Not checked: the hosted game in a real browser after the change, the Android app on a device, and a
run saved with `0.1.0-alpha06` that is continued with this version. The library's changelog says
that such a save is read.

What the pass showed:

- **The compiler found the one place to change.** A `when` without an `else` does not compile when
  the library adds a kind of event. An app that has an `else` there compiles as before and leaves
  the events of a subgraph out without a word.
- **A checkpointer of the game's own needed no change.** `Checkpointer` has a fourth function,
  `history`, with a default. The small checkpointer of the test that keeps text compiled as it was.
- **On the desktop and on Android a save now holds every step of a run.** `FileCheckpointer` keeps
  the checkpoint of each step, one line of JSON for each, where it kept the last one. A new run of a
  stage starts its file again, so a file holds the steps of one run, and the game leaves the default.
  In the browser `LocalStorageCheckpointer` still keeps only the latest checkpoint.
- **A save does not go back to an older build.** The changelog says that the earlier version cannot
  read a thread with more than one checkpoint. On the desktop and on Android a run saved by this
  build of the game is therefore lost to an older one. For a game that is acceptable.

## The eighth pass: the agent in the full desk

Level 8 of the tutorial sends a question about the menu to an agent. Stage 8 of the game did not: it
had three of the four routes, and "Do you sell salad?" got the reply for everything else. This pass
added the route, with `0.1.0-alpha07`.

What changed here:

- Stage 8 has a `toolLoop` with the price tool of stage 6 and a `send` node after it, as level 8 has.
  Its inbox has a fourth ticket, so that every route can be played.
- `topicOf` knows the topic `menu`. Stages 1 and 2 share the function: stage 1 now files the salad
  question under `menu`, and stage 2 routes it as before.
- The code under a tile follows a helper into another object. The agent of stage 8 is in
  `HelpDesks.kt` and its tool in `Agent.kt`, and the tile shows both.
- The three pictures in the README are new. They still said "Tutorial level 10", from before the
  tutorial had eight levels.

What was checked:

- 50 tests pass on the JVM and 47 in headless Chrome (wasmJs). Three are new on both: the graph
  answers a question about the menu with the price, the game lights the loop and the way out of it,
  and the tiles of the agent show its arrows and its tool.
- The browser bundle builds.
- The JVM run draws stage 8 with the tool at work, and the arrows of the agent do not cross a tile.

Not checked: the new route with Claude. Its prompt has only been answered by the scripted model.
Also not checked: the hosted game in a real browser, and the Android app on a device.

What the pass showed about the library:

- **`toolLoop` went into a graph with three other routes as it is.** What it returns is a target of
  a conditional edge like any node, and `then` names the node that follows the agent. Stage 6 and
  stage 8 keep their conversation in the same field of the ticket.
- **The game drew and logged the new route without a change.** The arrows of the loop come from
  `topology`, and the run log reads the tool calls from the state of each step. The stage only had
  to say where its three new tiles sit.
- **The two nodes of the agent have the names the library gives them.** Stage 8 calls them `model`
  and `tools`, as the tutorial does. Stage 6 names its model node `assistant`, so the same node has
  two names in the game.

## The ninth pass: a release that asks for nothing

`0.1.0-alpha08` adds a decision model on any chat model and a time limit of their own for the model
classes, and it makes a tool agent work with Gemini. This pass moved the game to the release from
Maven Central.

What changed here: the version of the six dependencies, and nothing else.

What was checked:

- 50 tests pass on the JVM and 47 in headless Chrome (wasmJs), the same as before the move.
- The browser bundle and the Android app build.
- The file of `telar-core` that Gradle used has the checksum of the file on Maven Central.

Not checked: a call to Claude from the game, the hosted game in a real browser, and the Android app
on a device.

What the pass showed:

- **A release without a new kind of event needs no change.** The two moves before this one each
  added a branch to the `when` over the events of a stream. This one compiled as it was.
- **The desktop app should no longer cut a long answer off.** It builds its client with
  `HttpClient()` on the CIO engine, which ends a request after 15 seconds. `AnthropicChatModel` now
  sets a limit of five minutes for its own requests. This follows from the changelog of the library
  and was not measured in the game.

## The tenth pass: more models than Claude

The game could ask the scripted model or Claude. This pass adds OpenAI, Gemini and any other server
with the API of OpenAI to the Options, all through `OpenAiChatModel` of `telar-openai`.

What changed here: `Settings` keeps a key and a model for each service and builds the model, the
Options screen lists the services, and `telar-openai` is a seventh dependency. No graph, no stage
and no line of `Game.kt` changed.

What was checked:

- 60 tests pass on the JVM and 56 in headless Chrome (wasmJs). The new ones check the address, the
  key and the model of the request each service gets, and what of the settings is stored.
- From headless Chrome, with a key that is not one, the game's own settings called Anthropic, OpenAI,
  Google, Groq, Mistral and OpenRouter, with `chat` and with `stream`. Each answered with its own
  error about the key, so each accepts a request from a web page.

Not checked: an answer of any of the new services in a stage, which needs a key. Ollama from the
browser. The hosted game in a real browser, and the Android app on a device.

What the pass showed:

- **One interface was enough.** The game knows `ChatModel` and nothing of a service. A new service
  is a line that builds another model, and for the ones with the API of OpenAI not even a new class:
  only the address differs.
- **In a browser, a request without a response is not a `ChatModelException`.** Ktor's engine for
  the browser fails with `kotlin.Error("Fail to fetch")` when the server is not running, the device
  is offline or the server refuses the page. `AnthropicChatModel` and `OpenAiChatModel` catch
  `Exception`, as the engine of the graph does, so the `Error` passes all of them, although both
  classes say they throw `ChatModelException` when the API cannot be reached. `Game.kt` catches
  `Exception` too, so such a run would end without a game over screen; that follows from the code
  and was not watched in a browser. It was true for Claude before this pass, and only showed now,
  with an Ollama that was not running. The game wraps its models in `orUnreachable`
  (`llm/Unreachable.kt`) until the library does this itself.
- **The names of the models are the player's to know.** The game lists Claude's models with their
  prices, which went out of date once already. For the other services it has one field and a
  default, and the service says so when the name is wrong.


## The eleventh pass: a release that takes a workaround away

`0.1.0-alpha09` reports what a browser throws in place of an exception as a failure: a request
that got no response, and an error of JavaScript. This pass moved the game to the release from
Maven Central.

What changed here: the version of the seven dependencies, and `llm/Unreachable.kt` is gone. The
models are no longer wrapped, because the library now does what the wrapper did.

What was checked:

- 63 tests pass on the JVM and 59 in headless Chrome (wasmJs), the same number as before the move.
  One of them gives the game's own settings a client that fails the way a browser does, and expects
  a `ChatModelException` from `chat` and from `stream`. It passes without the wrapper.
- The browser bundle and the Android app build.
- The file of `telar-core` that Gradle used has the checksum of the file on Maven Central.

Not checked: a server that cannot be reached from the hosted game in a real browser, and the
Android app on a device.

What the pass showed:

- **The finding of the tenth pass is fixed where it was found.** The game needed no code of its own
  for it, in a model or in `Game.kt`.
- **The player reads the library's message now.** It names the address and passes on the browser's
  words: `Could not reach the API at http://localhost:11434/v1: Fail to fetch`. The wrapper also
  asked whether the server runs, whether the device is online and whether the server takes requests
  from the page. The browser gives the same two words for all three, so the library cannot tell
  which it was, but a hint that lists them would help a reader who meets `Fail to fetch` for the
  first time.
