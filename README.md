# PlannedAgent: an AI planning agent

EECS 3311 Software Design, Fall 2026. Course project by Milad Jafarieh.

PlannedAgent turns a messy to-do list into a time-blocked week with no clashes. You tell it what you need to do in plain language. Its AI agent then:

* reads your tasks and calendar
* finds free time and checks the weather for outdoor tasks
* applies the rules you have taught it
* proposes a plan you can apply, edit in plain language, and undo

PlannedAgent has a JavaFX GUI and a command-line interface. It works with the free Gemini API or a local Qwen model through Ollama.

## Project stages

| Stage | Deliverable | Status |
|---|---|---|
| 1: Design | [Stage 1 Design Report](docs/stage1/Stage1-Design-Report.md): overview, 12 features, UML class / use-case / sequence diagrams, 10 design patterns, traceability | Submitted |
| 2: AI-human collaborative implementation | Source code, setup guide, AI collaboration log | Upcoming |
| 3: Testing and agent validation | JUnit tests, KUMA behavioural tests, failure analysis | Upcoming |

## Repository layout

```
docs/stage1/Stage1-Design-Report.md   Stage 1 report (all required sections)
docs/stage1/diagrams/uxf/             UMLet 15.1 sources for every UML diagram
docs/stage1/diagrams/png/             PNG exports of the same diagrams (embedded in the report)
```

### Diagram index

* **Class diagrams:** `CD00` package overview, then `CD01` to `CD10` (domain, scheduling, agent, tools, LLM, commands/events, facade/services, ports/adapters, GUI/CLI).
* **Use-case diagram:** `UC-use-case-diagram`.
* **Sequence diagrams:** `SD01` to `SD14`.

To regenerate the images, open a `.uxf` file in UMLet and use *File -> Export as...* PNG, keeping the same file name in `docs/stage1/diagrams/png/`. UMLet can also convert every file at once from a terminal (bash):

```
for f in docs/stage1/diagrams/uxf/*.uxf; do java -jar umlet.jar -action=convert -format=png -filename="$f" -output="docs/stage1/diagrams/png/$(basename "${f%.uxf}")"; done
```

## Planned technology

Java 21 (core module built for Java 17), JavaFX, picocli, Gradle, JUnit 5, Jackson, the Gemini API (free tier) or Ollama (local Qwen), Open-Meteo, iCalendar (ical4j) and UMLet.
