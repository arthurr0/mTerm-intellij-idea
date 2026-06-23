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
- **Claude Code, Codex & system shell** — start a session preconfigured for
  Claude Code or Codex, or a plain system terminal.
- **Finish sound** — get an audible chime when an agent finishes its turn, so you
  can step away while it works. Several built-in sounds, configurable per session
  type.
- **Live pane titles** — each pane shows what its agent is currently doing.

## Usage

Open the grid from the mTerm icon in the main toolbar, or from
**Tools → mTerm → Open mTerm Grid**. Individual sessions live under the same
**Tools → mTerm** menu.

Configure the finish sound and pane-title behaviour in
**Settings → Tools → mTerm**.

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
