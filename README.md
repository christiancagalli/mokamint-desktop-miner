# Mokamint Desktop Miner

A JavaFX desktop application for mining on the **Mokamint** blockchain. It replaces the textual feedback of the CLI miner with a visual dashboard that manages multiple cryptographic identities, generates plot files, and runs several live mining sessions in parallel against remote Mokamint nodes.

Developed as a University of Verona thesis project, built on top of the official `io.mokamint` / `io.hotmoka` APIs.

> **Java 21 · Maven.** Code, comments, log output and UI strings are in Italian.

---

## Features

- **Multi-miner management:** a home dashboard lists every saved miner as a card with a status LED. Each miner has its own identity, plot and mining session, and they all run at the same time.
- **Automatic start:** on launch, every miner that has a ready plot and is marked active is started automatically in the background.
- **Persistent state:** miners are saved to an XML file. A miner stopped explicitly by the user stays stopped across restarts; closing the app does not change that flag.
- **Per-miner consoles:** each miner opens its own console window with live logs (challenges, computed deadlines, connection status). Closing a console does **not** stop its miner — the session keeps running and the console re-attaches when reopened.
- **Four-state status LED:** 🟢 connected and mining · 🔴 active but disconnected (network down / error) · 🟡 plot ready but stopped · ⚪ plot still to generate.
- **Automatic reconnection:** the mining service reconnects on its own when the network drops and comes back, with visual feedback (LED + console messages).
- **Identity management:** three cryptographic pipelines integrated with the node's signature algorithm — generate a new `BIP39` mnemonic, load an existing `.pem`, or recover from a 12-word phrase.
- **Deterministic plot creation:** plots are generated with the official `io-mokamint-plotter` APIs, bound to the user's public key and to the node's block-signing key.
- **On-demand wallet balance:** the balance is queried once when a console is first opened and then only on explicit request, with a per-miner cache to avoid repeated calls to the node.
- **Dark themed UI:** a custom CSS stylesheet with a dark scheme and lilac accents, applied consistently to windows and dialogs.

---

## Architecture

The application follows a multi-stage **Model-View-Controller (MVC)** pattern. The single join key across all persisted data is the miner's **UUID**, which maps the XML entry, the `.pem` identity and the `.plot` binary.

### Entry point: the Miner Manager
`Main.java` loads `manager.fxml`, the home dashboard handled by **`MinerManagerController`**:
- lists all saved miners as cards with the four-state status LED (refreshed in real time, only when a state actually changes);
- on startup calls `MinerManager.autoStartAllMiners(...)` to launch every active miner with a ready plot;
- **"Crea nuovo"** opens the creation wizard (Connection → Login);
- **"Apri Console"** rebuilds the miner's `KeyPair` from its `.pem` and opens the mining console, re-attaching to the background session if it is already running;
- **"Elimina"** removes the XML entry and the `.plot`, optionally keeping the `.pem` (which holds the funds);
- **"Rinomina"** renames a miner.

### Creating a miner: ConnectionController → LoginController
- **`ConnectionController`** takes the node URI and a name, performs the node handshake on a background `Task` to fetch the `MiningSpecification` (chain id, signature/hashing algorithms, node block-signing key), builds a temporary `MinerInstance` with a fresh UUID and forwards it to login. Visited URIs are remembered.
- **`LoginController`** handles the three identity pipelines (new mnemonic / load `.pem` / recover phrase). On success it exports the entropy to `miner_storage/identities/<uuid>.pem`, lets the user import or schedule a plot, persists the miner to the XML and returns to the dashboard.

### Per-miner console: MiningController
**`MiningController`** is the dashboard of a single miner. It creates the `.plot` (`Plots.create`, deterministic), starts/stops mining through `MinerManager`, streams the live log into the console area and shows the wallet balance on request.

---

## Core components

- **`MinerManager`** (singleton) — owns a `ConcurrentHashMap<uuid, DesktopMinerService>` of background mining sessions and a per-miner balance cache. Centralising start/stop here lets a session survive closing its console window. Auto-start runs on a dedicated thread and starts only miners that are active and have a plot. A user stop persists `active=false`; the app-shutdown stop leaves the flag untouched.
- **`DesktopMinerService`** (`core/`) — extends `AbstractReconnectingMinerService`. Wraps a `LocalMiners` instance over the loaded plot, manages the reconnecting WebSocket to the node, counts deadlines and surfaces events through a swappable `MinerListener` (so logs reach the right console). It keeps a short message history (replayed when a console attaches), a heartbeat and a watchdog to detect stalls.
- **`MinerInstance`** — the data model persisted to XML. `getPemPath()` / `getPlotPath()` derive the paths from the UUID, so paths are a function of identity, not stored state.
- **`MinerXmlManager`** — static load/save/add/remove against `miner_storage/miners.xml`, whose schema mirrors the node's mining-specification format and includes the `active` auto-start flag.
- **`MinerService`** — per-session config holder plus BIP39 helpers (mnemonic generation and validation).
- **`MinerPrefsManager`** — persists the list of visited node URIs.
- **`DialogUtils`** — applies the app's dark stylesheet to dialogs created from code.

---

## Persistence layout

The whole `miner_storage/` folder is created at runtime (and is git-ignored, since it holds local data and private keys):

```
miner_storage/
├── miners.xml              # miner metadata, keyed by UUID (includes the active flag)
├── identities/<uuid>.pem   # entropy dump: re-derives the KeyPair and holds the funds
└── data/<uuid>.plot        # the mining plot binary
```

---

## Design choices

- **Multi-miner via a central singleton.** The `MinerManager` keeps the mining sessions alive independently of the windows, so a console can be closed and reopened without interrupting mining, and every miner can run in parallel.
- **Never block the JavaFX Application Thread.** Node handshakes, plot creation (multi-GB I/O), starting a miner and balance queries all run inside `javafx.concurrent.Task`; UI updates are dispatched back via `Platform.runLater(...)`.
- **A plot is bound to its node.** The plot's prolog contains the node's block-signing public key, taken from the node's `MiningSpecification`. Moving a miner to a different node requires regenerating its plot.
- **Persistent enable/disable.** The explicit stop of a miner is remembered, so it is not silently restarted at the next launch.
- **Clean shutdown.** Closing the dashboard stops every miner and terminates the app (the mining WebSocket threads are non-daemon, so an explicit shutdown is required); closing a single console leaves its miner running.

---

## Prerequisites

- **Java 21**
- **Maven 3.8+**

## How to run

```bash
git clone https://github.com/christiancagalli/mokamint-desktop-miner.git
cd mokamint-desktop-miner
mvn clean install   # build
mvn javafx:run      # launch the GUI (main class: it.univr.mokamintminer.gui.Main)
```

The GUI opens on the Miner Manager dashboard. A reachable Mokamint node is required to create a miner and to mine; the node used during development is `ws://lipari.hotmoka.io:8025`.

---

## Project structure

```
mokamint-desktop-miner/
├── src/
│   └── main/
│       ├── java/it/univr/mokamintminer/
│       │   ├── core/
│       │   │   └── DesktopMinerService.java
│       │   ├── gui/
│       │   │   ├── Main.java
│       │   │   ├── MinerManagerController.java
│       │   │   ├── ConnectionController.java
│       │   │   ├── LoginController.java
│       │   │   └── MiningController.java
│       │   ├── services/
│       │   │   ├── MinerManager.java
│       │   │   ├── MinerInstance.java
│       │   │   ├── MinerXmlManager.java
│       │   │   └── MinerService.java
│       │   └── utils/
│       │       ├── MinerPrefsManager.java
│       │       └── DialogUtils.java
│       └── resources/
│           └── layout/
│               ├── manager.fxml
│               ├── connection.fxml
│               ├── login.fxml
│               ├── mining.fxml
│               └── style.css
├── pom.xml
└── README.md
```

---

## Dependencies

| Artifact | Version | Purpose |
|:---|:---|:---|
| `io-mokamint-plotter` | 1.6.1 | Plot file generation |
| `io-mokamint-miner-local` | 1.6.1 | Local mining engine |
| `io-mokamint-miner-service` | 1.6.1 | Reconnecting WebSocket miner service |
| `io-hotmoka-crypto` | 1.5.x | Entropy, BIP39 parsing and key-pair generation |
| `javafx-controls` | 21 | GUI components |
| `javafx-fxml` | 21 | FXML layout loading |
