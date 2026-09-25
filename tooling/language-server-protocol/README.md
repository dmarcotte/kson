# Kson Language Server

A Language Server Protocol (LSP) implementation for Kson, written in TypeScript. We support the
[Language Server Protocol (LSP)](https://microsoft.github.io/language-server-protocol/), because it is a standard that allows programming language tooling to be decoupled
from the code editor.

By implementing a language server, this project provides Kson language support that can be used by any LSP-compatible
editor, such as Visual Studio Code, Neovim, or Sublime Text. This approach avoids the need to write a new extension for
each editor and ensures that features are implemented in one place, improving performance and maintainability [1].

## Current Features

* **Real-time Diagnostics:** Identifies syntax errors as you type.
* **Document Formatting:** Automatically formats Kson files.
* **Semantic Highlighting:** Provides rich, context-aware syntax highlighting.

## Getting Started

### Prerequisites

* Node.js (v20.0.0 or higher)
* pnpm (provided by the project's pixi environment)

### Installation

```bash
pnpm install
```

The `kson` and `kson-tooling` dependencies are `file:` imports of the Kotlin/JS builds under `kson-lib` and
`kson-tooling-lib`, and `pnpm install` keeps whatever copies of them it already has.  To install against a
rebuilt core, use the Gradle task, which imports them afresh:

```bash
./gradlew tooling:language-server-protocol:npmInstall
```

### Build

To compile the TypeScript source code, run:

```bash
pnpm run compile
```

### Testing

To run the test suite:

```bash
pnpm test
```

[1] Visual Studio Code. (2025). *Language Server Extension Guide*. Retrieved
from https://code.visualstudio.com/api/language-extensions/language-server-extension-guide 