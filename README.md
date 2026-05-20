# Mokamint Desktop Miner

A desktop application for the Mokamint blockchain that implements plot file creation, secure credential management, and mining activity monitor with a modern graphical interface.

The project was developed as part of a university thesis at the University of Verona, with the goal of replacing the textual feedback of the existing CLI miner with a secure, responsive, and visual JavaFX dashboard built on top of the official Mokamint APIs.

## Features

- **Mnemonic Login (BIP39):** Secure login and key pair generation starting from a 12-word recovery phrase using `io.hotmoka.crypto`.
- **Key Storage:** Automated persistence of generated keys into a local `.pem` file for seamless subsequent sessions.
- **Plot File Creation:** Fully deterministic generation of plots utilizing the official `io-mokamint-plotter` algorithms.
- **Dynamic Plot Size:** Configurable size inputs (number of nonces) directly handled from the UI with real-time feedback on expected file weight (MB/GB).
- **Asynchronous Mining Monitor:** Real-time logging of challenge-response loops and computed deadlines connected to a live Mokamint node.
- **Live Wallet Balance:** Background thread execution (`JavaFX Task`) that continuously queries the remote node to fetch and update the wallet balance (`MOK` tokens) safely without freezing the UI.
- **Modern Dark UI:** Tailored CSS stylesheet featuring a futuristic dark scheme with vibrant lilac accent details.

## Architecture

The application implements a robust **Model-View-Controller (MVC)** architectural pattern decoupled into separate presentation modules:

- **Main / ConnectionController:** The application gateway. Handles connection testing to the remote node URI via WebSockets.
- **LoginController:** Coordinates key recovery from the 12 words, handles cryptography setup, and manages local `.pem` file exports.
- **MiningController:** The main cockpit. Handles asynchronous background Tasks for both plot building (`Plots.create`) and real-time ledger pooling for active balance updates.
- **DesktopMinerService:** Extends `AbstractReconnectingMinerService` from the core library. It hooks into the node's WebSocket network layer and safely dispatches structural events (connection, disconnection, computed deadlines) to the GUI thread via a custom `MinerListener`.

## Prerequisites

- **Java 21**
- **Maven 3.8+**

## How to Run

### 1. Clone the repository:
```bash
   git clone https://github.com/christiancagalli/mokamint-desktop-miner.git
   cd mokamint-desktop-miner
```
   
### 2. Build the project

```bash 
  mvn clean install
```

### 3. Run the GUI

```bash
  mvn javafx:run
```

---

## Design Choices

### 1. Multi-Stage Architectural Pattern (MVC)
Unlike the initial prototype which relied on a single monolithic view-controller, the application has been refactored into a modular **Model-View-Controller (MVC)** structure. Responsibilities are now cleanly separated into distinct stages:
- `ConnectionController`: Manages the remote WebSocket handshake logic.
- `LoginController`: Secures cryptographic identity via a 12-word mnemonic phrase (`BIP39`).
- `MiningController`: Main dashboard supervising parallel background tasks.

### 2. Asynchronous Thread Management & UI Safety
Network interactions (polling the remote node for active mining challenges) and heavy disk I/O operations (generating multi-gigabyte `.plot` binary files) are highly blocking processes.
To prevent the *JavaFX Application Thread* from freezing, these operations are offloaded to background workers using `javafx.concurrent.Task`. When a background execution finishes (e.g., retrieving a new wallet balance or computing a deadline), thread synchronization and UI updates are safely dispatched back to the main thread via `Platform.runLater()` hooks.

### 3. Fully Real Production Integration
All mock behaviors and simulation fallbacks have been deprecated. The miner features an uncompromised production-ready integration with the live network:
- **Plot Creation:** Generates cryptographically valid `.plot` files directly on disk through `Plots.create()`, uniquely bound to the user's public key using `ed25519` and `sha256`.
- **Live Node Consensus:** Establishes a true WebSocket stateful connection to the University of Verona's official node (`ws://lipari.hotmoka.io:8025`).
- **Wallet Ledger Sync:** Performs real-time, event-driven pooling of the blockchain ledger to track block rewards and display the precise wallet balance in `MOK` tokens.

---

## Project Structure
```
mokamint-desktop-miner/
├── src/
│   └── main/
│       ├── java/
│       │   └── it/univr/mokamintminer/
│       │       ├── core/
│       │       │   └── DesktopMinerService.java
│       │       ├── gui/
│       │       │   ├── ConnectionController.java
│       │       │   ├── LoginController.java
│       │       │   ├── MiningController.java
│       │       │   └── Main.java
│       │       └── services/
│       │           └── MinerService.java
│       └── resources/
│           ├── layout/
│           │   ├── connection.fxml
│           │   ├── login.fxml
│           │   └── mining.fxml
│           └── style.css
├── pom.xml
└── README.md
```

---

## Dependencies

| Artifact | Version | Purpose |
|:---|:---|:---|
| `io-mokamint-plotter` | 1.6.1 | Plot file structural compilation |
| `io-mokamint-miner-local` | 1.6.1 | Local engine mining execution |
| `io-mokamint-miner-service` | 1.6.1 | Base reconnecting WebSocket loop abstraction |
| `io-hotmoka-crypto` | Core | Entropy, BIP39 parsing, and key pair generation |
| `javafx-controls` | 21 | Graphical interface native elements |
| `javafx-fxml` | 21 | FXML layout asynchronous rendering |