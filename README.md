# PlannedAgent: an AI planning agent

EECS 3311 Software Design, Fall 2026. Course project by Milad Jafarieh.

PlannedAgent turns a quick list of things to do into a week plan with no clashes.

* You type your tasks in one sentence, and the agent turns them into tasks.
* It plans your week around your classes, your rules ("no studying after 10pm") and the weather.
* It shows you a proposal that you can apply, change by chat, or undo.

It has a Swing GUI and a command-line interface. It runs on the free Gemini API, or on a local Qwen model through Ollama.

## Project stages

| Stage | Deliverable | Status |
|---|---|---|
| 1: Design | [Stage 1 Design Report](docs/stage1/Stage1-Design-Report.md) (overview, 11 features, class / use-case / sequence diagrams, 7 design patterns, traceability) | Done |
| 2: Implementation | Source code in `src/`, this setup guide | Done |
| 3: Testing | JUnit tests, KUMA agent tests, failure analysis | Next |

## Features

| ID | Feature | GUI | CLI |
|---|---|---|---|
| F01 | Add tasks from plain text (AI) | Tasks panel, Add from text | `add "<text>"` |
| F02 | Manage tasks and their status | New, Edit, Mark done, Delete | `new`, `tasks`, `done <id>`, `delete <id>` |
| F03 | Fixed events and calendar export | Add event, Export .ics | `event add ...`, `events`, `export <file>` |
| F04 | Auto-schedule with a strategy | Strategy + Auto-schedule | `schedule --strategy deadline` |
| F05 | Plan the week with the agent (AI) | Chat panel | `ask "plan my week"` |
| F06 | Check conflicts | Check conflicts | `conflicts` |
| F07 | Change the plan by chat, undo and redo | Chat panel, Undo, Redo | `ask "..."`, `undo`, `redo` |
| F08 | Weather-aware scheduling | Outdoor box on a task | rain warnings in `schedule` / `conflicts` |
| F09 | Remember preferences (AI) | Chat: "I never study after 10pm" | `ask "I never study after 10pm"` |
| F10 | End-of-day review (AI, with fallback) | Review today | `review` |
| F11 | Weekly report (AI, with fallback) | Weekly report | `report` |

The AI never changes your plan directly. Every change is shown as a proposal first; nothing happens until you apply it, and an applied change can be undone.

## Prerequisites

* **Java 21** (a JDK, for example Eclipse Temurin 21). Check with `java -version`.
* **An LLM**, one of:
  * **Gemini (default):** a free API key from [Google AI Studio](https://aistudio.google.com/apikey).
  * **Ollama (local, no key):** install [Ollama](https://ollama.com), then run `ollama pull qwen3.5`.
* Internet access for Gemini and for the weather forecast ([Open-Meteo](https://open-meteo.com), free, no key).

Maven does not need to be installed: the project includes the Maven wrapper (`mvnw` / `mvnw.cmd`), which downloads the right Maven version the first time. Dependencies (Gson, JUnit 5) are downloaded by Maven automatically.

## Setup

1. Clone the repository and open a terminal in its folder.
2. Create your local settings file from the example:

   ```sh
   cp .env.example .env        # Windows: copy .env.example .env
   ```

3. Open `.env` and either paste your Gemini key into `GEMINI_API_KEY`, or set `PLANNER_LLM=ollama` to use the local model.

`.env` is listed in `.gitignore` and must never be committed. Only `.env.example`, which has placeholders, is in the repository. Settings can also be given as environment variables, which win over the file.

| Setting | Default | Meaning |
|---|---|---|
| `PLANNER_LLM` | `gemini` | `gemini` or `ollama` |
| `GEMINI_API_KEY` | none | your key (kept only in `.env`) |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Gemini model name |
| `OLLAMA_URL` | `http://localhost:11434` | where Ollama runs |
| `OLLAMA_MODEL` | `qwen3.5` | local model name |
| `PLANNER_LAT`, `PLANNER_LON` | York University | location for the rain forecast |
| `PLANNER_DATA` | `~/.plannedagent` | where your data and the activity log are saved |

Without a working LLM the app still starts: the non-AI features keep working, and the AI features tell you what to fix.

## Build and run

```sh
./mvnw package              # Windows: mvnw.cmd package   (builds target/plannedagent.jar and runs the tests)
```

**GUI:** run with no arguments.

```sh
./plannedagent              # Windows: plannedagent.cmd
java -jar target/plannedagent.jar
```

**CLI:** give a command. Run it from the project folder so it finds `.env`.

```sh
./plannedagent help
./plannedagent add "EECS 3311 report Friday about 3 hours, gym twice, groceries"
./plannedagent event add "EECS 3311 lecture" 2026-11-11T10:00 2026-11-11T11:30
./plannedagent ask "I never study after 9pm"
./plannedagent ask "plan my week"
./plannedagent week
./plannedagent ask "move gym to Wednesday at 7am"
./plannedagent undo
./plannedagent review
./plannedagent report
./plannedagent export week.ics
```

Commands that make a proposal (`schedule`, `ask`, `review`) show it and ask before applying; add `--yes` to apply without asking (useful for scripts). Each `ask` starts a new conversation; the GUI chat keeps the conversation going.

You can also open the folder in IntelliJ IDEA or Eclipse as a Maven project and run `ca.yorku.plannedagent.app.App`.

## Example

```
$ ./plannedagent ask "plan my week"
  ... using get_tasks
  ... using get_schedule
  ... using schedule_tasks
Here is a plan for your week. It keeps your rule of no work after 21:00:
- Mon Nov 9 20:00-21:00: Gym (1/2)
- Tue Nov 10 08:00-11:00: EECS 3311 report
- Tue Nov 10 11:00-12:00: Gym (2/2)
- Tue Nov 10 12:00-12:30: Groceries
Would you like to apply it?

Schedule (deadline first): 4 new blocks.
- Add 'EECS 3311 report' Tue Nov 10 08:00-11:00
- Add 'Gym (1/2)' Mon Nov 9 20:00-21:00
- Add 'Gym (2/2)' Tue Nov 10 11:00-12:00
- Add 'Groceries' Tue Nov 10 12:00-12:30
Apply this? [y/N] y
Applied 4 changes.
```

## Tests

```sh
./mvnw test
```

The JUnit 5 tests cover the model and task states, the scheduler and strategies, conflicts, commands with undo and redo, storage, the LLM and weather adapters (against a local stub server), the agent loop and its tools (with a scripted fake LLM), the facade and the CLI. No test needs an API key or the internet.

## How it is built

```
src/main/java/ca/yorku/plannedagent/
  model/        Task and its states, Workspace, Plan, TimeBlock, FixedEvent, Preference, Conflict
  scheduling/   Scheduler, the three strategies, ConflictChecker
  commands/     PlanCommand, AddBlockCommand, MoveBlockCommand, CommandHistory, Proposal
  agent/        Agent loop, ConversationMemory, Tool and the five tools (agent/tools)
  ai/           LlmClient, GeminiClient, OllamaClient, TaskExtractor
  services/     weather (Open-Meteo), weekly report, .ics export, JSON storage
  app/          App (start-up), PlannerFacade, EventBus, ActivityLog, Config
  ui/           MainWindow, MainController, dialogs (Swing) and PlannerCli
```

| Pattern | Where |
|---|---|
| Strategy | `Scheduler` with `DeadlineFirstStrategy`, `PriorityFirstStrategy`, `ShortestFirstStrategy` |
| State | `Task` with `TodoState`, `ScheduledState`, `MissedState`, `DoneState` |
| Command | `PlanCommand`, `AddBlockCommand`, `MoveBlockCommand`, `CommandHistory` (undo / redo) |
| Observer | `EventBus`, `PlannerObserver`, `MainController`, `ActivityLog` |
| Adapter | `LlmClient` with `GeminiClient` / `OllamaClient`; `WeatherService` with `OpenMeteoWeather` |
| Facade | `PlannerFacade`, used by both the GUI and the CLI |
| MVC | `Workspace` (model), `MainWindow` (view), `MainController` (controller) |

The agent works in steps: the LLM answers with one JSON object (a tool call or a final answer), the agent runs the tool in Java, and the result goes back to the LLM, for at most 6 steps. Exact work (dates, free time, overlaps, deadlines, rain, the report numbers) is always done in Java.

Your data is saved in `~/.plannedagent/workspace.json` (undo history in `history.json`), and every action is written to `~/.plannedagent/activity.log`.
