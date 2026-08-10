# mTerm

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/32433">
    <img alt="Get from JetBrains Marketplace" src="https://img.shields.io/badge/Get_from-JetBrains_Marketplace-000000?style=for-the-badge&logo=jetbrains&logoColor=white">
  </a>
  <br>
  <a href="https://plugins.jetbrains.com/plugin/32433"><img alt="Version" src="https://img.shields.io/jetbrains/plugin/v/32433?style=flat-square&label=version"></a>
  <a href="https://plugins.jetbrains.com/plugin/32433"><img alt="Downloads" src="https://img.shields.io/jetbrains/plugin/d/32433?style=flat-square&label=downloads"></a>
</p>

Run your terminals — and your AI coding agents — as real editor tabs in
IntelliJ IDEA, including a resizable grid for working with several agents side by
side.

mTerm follows your current IDE theme, so it blends into the colour scheme you
already use.

## Features

- **Terminals as editor tabs** — open a terminal session as a first-class editor
  tab you can split, drag and detach, instead of keeping it in the bottom tool
  window.
- **Multi-agent grid** — run several terminals in one resizable grid. Add or
  remove panes on the fly, choose the column count (`Auto` / `1` / `2` / `3` /
  `4`), drag the gutters to resize, and drag a pane by its header onto another to
  swap their positions.
- **Activity indicator** — every pane header carries a dot that pulses while the
  agent is working and turns amber the moment it needs you, so a wall of four
  terminals tells you at a glance where your attention belongs.
- **Focused pane outline** — the active pane is outlined in its agent's colour,
  so you always know which terminal your keystrokes are going to.
- **Broadcast** — send the same prompt to every pane at once, with a per-pane
  toggle for the ones that should sit it out.
- **Custom agents** — the built-in Claude Code, Codex, Grok Build and system
  shell entries are only defaults. Add your own (name, command, glyph, colour,
  working directory) or give the built-ins extra flags — anything that runs in a
  terminal works.
- **Restored layout** — panes, column count and the proportions you dragged come
  back when you reopen the project.
- **Finish sound & IDE notification** — get an audible chime when an agent
  finishes its turn, and optionally a balloon notification that jumps straight to
  the right pane when the IDE was in the background.
- **Per-pane restart & maximise** — restart a wedged agent in place, or blow one
  pane up to the full grid and back.
- **Live pane titles** — each pane shows what its agent is currently doing.

## Usage

Open the grid from the mTerm icon in the main toolbar, or from
**Tools → mTerm → Open mTerm Grid**. Individual sessions live under the same
**Tools → mTerm** menu.

Configure agents, sounds, notifications and pane behaviour in
**Settings → Tools → mTerm**.

### Shortcuts

These work while the focus is inside the grid:

| Shortcut | Action |
| --- | --- |
| <kbd>Alt</kbd>+<kbd>Shift</kbd>+<kbd>1</kbd>…<kbd>9</kbd> | Focus pane 1–9 |
| <kbd>Alt</kbd>+<kbd>Shift</kbd>+<kbd>A</kbd> | Add agent |
| <kbd>Alt</kbd>+<kbd>Shift</kbd>+<kbd>B</kbd> | Toggle the broadcast bar |
| <kbd>Alt</kbd>+<kbd>Shift</kbd>+<kbd>M</kbd> | Maximise / restore the focused pane |
| <kbd>Alt</kbd>+<kbd>Shift</kbd>+<kbd>R</kbd> | Restart the focused agent |
| <kbd>Alt</kbd>+<kbd>Shift</kbd>+<kbd>W</kbd> | Close the focused pane |

Double-clicking a pane header maximises it too.

## Requirements

IntelliJ IDEA 2024.3 or newer, with the bundled **Terminal** plugin enabled.

## Building from source

```bash
./gradlew runIde        # launch a sandbox IDE with the plugin
./gradlew buildPlugin   # produce build/distributions/*.zip
```

Building requires JDK 21.

## Contributing

Issues and pull requests are welcome.

## License

[MIT](LICENSE) © Artur Kołecki
