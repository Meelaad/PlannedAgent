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
| 2: Implementation | Source code, setup guide, AI collaboration log | Next |
| 3: Testing | JUnit tests, KUMA agent tests, failure analysis | Later |

## Repository layout

```
docs/stage1/Stage1-Design-Report.md   Stage 1 report
docs/stage1/diagrams/uxf/             UMLet source files for every UML diagram
docs/stage1/diagrams/png/             The same diagrams as images
```

Diagram names:
* Class diagram views: `CD0` (overview) to `CD6`
* Use-case diagram: `UC-use-case-diagram`
* Sequence diagrams: `SD1` to `SD9`

## Planned technology

Java 21, Swing, Maven, JUnit 5, Gson, the Gemini API (free tier) or Ollama with a local Qwen model, and the Open-Meteo weather API.
