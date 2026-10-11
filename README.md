# Pixel Pizza

A small game about graphs. You run the help desk of a pizza shop: customers write in, a
[Telar](https://github.com/deeptelar/telar) graph writes back, and you watch
the ticket cross the board node by node.

**[Play it in your browser](https://deeptelar.github.io/telar-demo/)**

![Stage 8: the kitchen and the driver run at the same time](docs/stage.png)

It is the [Telar tutorial](https://github.com/deeptelar/telar/tree/main/docs/tutorial)
made playable. Eight stages, one new move each, the same Pixel Pizza help desk growing from two
nodes to a full agent workflow. One Compose Multiplatform codebase runs it in the browser
(Kotlin/Wasm), on the desktop (JVM) and on Android. No account and no API key needed.

## Stages

| # | Stage | The move |
|---|---|---|
| 1 | A line | Two nodes in a row: `node`, `then`, `invoke` |
| 2 | Choices | `conditionalEdge` picks one of three paths |
| 3 | Loops | An edge that goes back until the reply passes a check |
| 4 | Two at once | Fan-out to nodes with a `work` and an `update`, which run at the same time |
| 5 | Save points | `interruptBefore` stops the run for you; `resume` continues it, also after a reload |
| 6 | The agent | A model that calls tools, as a loop of two nodes |
| 7 | Game over | A node fails; `resume` retries from the last save |
| 8 | The full desk | All of it on one board: four kinds of ticket, four ways through |

Each stage is the level of the tutorial with the same number: stage 3 is level 3.

Watching a run with `stream()`, the second half of level 4, is not only in stage 4. It is the game:
the board, the ticket panel and the run log are drawn from the events of `stream()` as they arrive.
Under each step, the run log lists what the step wrote into the ticket (`topic = "delivery"`,
`facts += "..."`), and after a conditional edge it says which node the edge picked. In the stages
that ask a model, the log shows the answer word by word while the model writes it.

Press a tile on the board to see the code behind it: the node, its arrows and the functions it calls.
The text is cut out of the game's own source files when the app is built, so it is always the code
that runs.

| | |
|---|---|
| ![A run waiting at a save point](docs/save-point.png) | ![A failed node, with a retry from the last save](docs/game-over.png) |

## Bonus stage: Kitchen rush

![The kitchen in the middle of a shift: three stations, and an oven that is full](docs/kitchen.png)

A pizzeria whose chefs are graphs works through one shift: twelve orders in 95 kitchen minutes,
with orders that change and get cancelled and an oven with two places. The orders are the same
every time. What you choose is how the kitchen is built:

| Kitchen | Who does what |
|---|---|
| Solo chef | One agent has every tool and does everything. It is sent the whole conversation with every answer. |
| The line | Three stations work at the same time, as nodes that run in parallel. The model only reads the orders and writes a ticket for each. Plain code makes and bakes the pizzas. |
| The line, a model at every station | The same three stations with the model at each, asked with what it needs to know now. |

The shift has two limits, the clock and a token budget, and it is measured on the orders served in
time and on the tokens used. The shifts you played are listed side by side, so you can run the same
orders in another kitchen or with another model and compare.

Without a key the chefs are scripted and the token counts are a guess. With a real model under
Options, the tokens are the ones the service reports, and a shift costs about 10,000 tokens on the
line and up to 150,000 for the solo chef.

The kitchen is the [kitchen sample](https://deeptelar.github.io/telar/samples.html) of Telar,
copied into `kitchen/`. There it runs from the command line.

## Play

The browser version is at <https://deeptelar.github.io/telar-demo/>. Every push to
`main` publishes it there.

To run it from a clone of this repository:

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # browser, with a dev server
./gradlew :composeApp:run                           # desktop
./gradlew :androidApp:installDebug                  # Android, on a connected device or emulator
```

Requires JDK 17 or newer. The Android app also needs the Android SDK with platform 37: set
`ANDROID_HOME`, or put `sdk.dir=/path/to/Android/Sdk` in a `local.properties` file in this
directory. It runs on Android 7 and newer.

Stages 6 and 8 ask a model, and their cards say so: "scripted or a model". The other stages are
"scripted only". By default the model is a scripted one with fixed answers. To play the two stages
with a real model, pick a service under Options and enter your own key for it:

| Service | What you enter |
|---|---|
| Claude | Your Anthropic API key, and one of the models on the list. They are listed from the cheapest to the most expensive, with their prices. |
| OpenAI | Your OpenAI API key. The model is `gpt-5` unless you name another. |
| Gemini | Your Gemini API key. The model is `gemini-3.8-flash` unless you name another. |
| Other address | The address of any other server with the API of OpenAI, a model, and a key if the server asks for one. It starts with the address of an Ollama on your device. |

The model has to be one that can call tools. The key goes straight from the app to the service and
nowhere else, and it is kept in memory unless you tick "Remember the key on this device". Requests
are billed to your account.

In the browser the game calls the service from the page, so the service has to accept that.
Anthropic, OpenAI, Google, Groq, Mistral and OpenRouter do. An Ollama on your device only answers
the page when it was started with `OLLAMA_ORIGINS=https://deeptelar.github.io`. The desktop
and the Android app have no such limit.

## How it works

- **A stage is a graph and a map.** [`game/HelpDesks.kt`](composeApp/src/commonMain/kotlin/dev/deeptelar/telar/demo/game/HelpDesks.kt)
  and [`game/Agent.kt`](composeApp/src/commonMain/kotlin/dev/deeptelar/telar/demo/game/Agent.kt) hold
  the eight graphs. [`game/Stages.kt`](composeApp/src/commonMain/kotlin/dev/deeptelar/telar/demo/game/Stages.kt)
  gives each one its briefing, its inbox, and the cell of every node on the board.
- **The board draws the graph it is given.** Tiles sit where the stage puts them. The arrows are
  read from `CompiledGraph.topology`, and they light up from the `NodeStarted`, `NodeCompleted` and
  `StepCompleted` events of the run.
- **A save point is a checkpoint.** Runs are saved by the library's checkpointers:
  `LocalStorageCheckpointer` in the browser, `FileCheckpointer` on the desktop and on Android. That
  is why a ticket waiting for your decision survives a reload, and why a failed run can be retried
  from the node that failed.
- **The look is drawn, not themed.** Black, white, a ramp of greys and one red, after
  [nothing.tech](https://nothing.tech). The headings are a 5 by 7 dot-matrix alphabet in
  [`ui/DotMatrix.kt`](composeApp/src/commonMain/kotlin/dev/deeptelar/telar/demo/ui/DotMatrix.kt), the
  pizza is text in [`ui/Sprites.kt`](composeApp/src/commonMain/kotlin/dev/deeptelar/telar/demo/ui/Sprites.kt),
  and the rest is Geist Mono. There is no Material in the app.

## Layout

- `game/`: the ticket (the state), the graphs of the stages, the tools of the agent
- `kitchen/`: the bonus stage: the kitchen as plain code, the fixed shift, and the two graphs that
  run it, `SoloChef.kt` and `Line.kt`
- `llm/`: the scripted model, and the Claude models on offer. The chat model interface, the agent
  loop and the clients come from `telar-agent`, `telar-anthropic` and `telar-openai`. Which service
  answers is decided in `ui/Settings.kt`
- `storage/`: which checkpointer of the library each platform uses, and `KeyValueStore` for the
  settings and the cleared stages
- `ui/`: the screens, the board, `Game.kt`, which runs the graphs of the stages for them, and
  `KitchenController.kt`, which runs a shift of the bonus stage

## Tests

```bash
./gradlew :composeApp:jvmTest             # everything, on the JVM
./gradlew :composeApp:wasmJsBrowserTest   # the common tests again in headless Chrome (needs Chrome)
```

Every pull request runs both, and the game is only published when they pass.

The JVM run also clicks through the game the way a player would, and draws every screen to
`composeApp/build/screenshots`. A pull request has those pictures as the artifact `screenshots` of
its test run. The pictures in this README come from there. So does
`social-preview.png`, the picture a link to the game or to this repository is shown with: copy it
to `composeApp/src/wasmJsMain/resources/` after a change, and upload the same file under Settings,
Social preview.

Building this app is a test of Telar from outside its repository. What that turned up is in
[FINDINGS.md](FINDINGS.md).

## License

[Apache License 2.0](LICENSE). Geist Mono is bundled under the
[SIL Open Font License](licenses/GeistMono-OFL.txt).
