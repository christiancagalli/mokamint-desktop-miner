# Mokamint Desktop Miner

A desktop application for the Mokamint blockchain that implements plot file creation, secure credential management, and mining activity monitor with a modern graphical interface.

The project was developed as part of a university thesis at the University of Verona, with the goal of replacing the textual feedback of the existing CLI miner with a secure, responsive, and visual JavaFX dashboard built on top of the official Mokamint APIs.

## Features

- **Dynamic Identity Management:** Flexible authentication system featuring three distinct cryptographic pipelines, deeply integrated with the remote node's specific algorithm via `miningSpecification.getSignatureForDeadlines()`:
  - **New Identity Generation:** Generates a compliant 12-word recovery phrase (`BIP39`) using `io.hotmoka.crypto`, derives the public/private key pairs dynamically according to the node's required signature algorithm, and securely exports the raw entropy state locally into a `.pem` file via `entropy.dump()`.
  - **Secure Local Key Storage (.pem):** Standardized file-based login that allows users to re-import a previously generated `.pem` identity file using `Entropies.load()`, reconstructing the active session keys without exposing the raw seed words.
  - **Mnemonic Phrase Recovery:** Direct seed login via a 12-word text area input, backed by dictionary validation inside `MinerService` to reconstruct credentials on the fly.
- **Plot File Creation:** Fully deterministic generation of plots utilizing the official `io-mokamint-plotter` algorithms.
- **Dynamic Plot Size:** Configurable size inputs (number of nonces) directly handled from the UI with real-time feedback on expected file weight (MB/GB).
- **Asynchronous Mining Monitor:** Real-time logging of challenge-response loops and computed deadlines connected to a live Mokamint node.
- **Live Wallet Balance:** Background thread execution (`JavaFX Task`) that continuously queries the remote node to fetch and update the wallet balance (`MOK` tokens) safely without freezing the UI.
- **Modern Dark UI:** Tailored CSS stylesheet featuring a futuristic dark scheme with vibrant lilac accent details.

---

## Application Architecture & Lifecycle

The application follows a strict multi-stage **Model-View-Controller (MVC)** pattern. Data is safely passed down through sequential controllers, ensuring that the application state is dynamically built and verified based on the remote node's specific network properties.

### 1. The Gateway: Main & ConnectionController
The execution starts from `Main.java`. The initial user interface presents the user with the connection and routing panel:
- **Node & Path Setup:** The user inputs the target remote node URI and defines the local directory path for the plot binary.
- **History Management:** Features a built-in persistence system to manage previously visited URIs, allowing the user to seamlessly select or clear cached endpoints from the UI.
- **Node Handshake:** Before unlocking the identity phase, the controller communicates with the remote node to fetch and download its official `MiningSpecifications`.
- **Data Forwarding:** Once specifications are successfully retrieved, the controller invokes `setConnectionData()` inside the `LoginController`. This routine forwards the URI, path, and node specifications, while simultaneously initializing the central `MinerService` cache (storing path, URI, cryptographic signature algorithm, and the network `ChainID`).

### 2. The Identity Phase: LoginController
The `LoginController` coordinates secure cryptographic credential management before any mining activity can be authorized. It handles three mutually exclusive authentication pipelines:
- **New Identity Generation:** The controller requests a fresh 12-word mnemonic phrase from `MinerService`. Based on the active node specifications, it derives the key pairs and exports them locally into a secure `.pem` file for future sessions.
- **Load Existing `.pem` File:** The user loads a pre-existing key file from disk. The controller extracts the underlying entropy, reconstructs the valid cryptographic keys based on the active node specifications, and prepares them for the dashboard.
- **Mnemonic Phrase Recovery (BIP39):** The user inputs an existing 12-word recovery phrase. The controller passes the phrase to `MinerService` to validate dictionary compliance; if successful, it generates the appropriate key pair to be handed over to the final view.

### 3. The Cockpit: MiningController
Once the identity is verified, the `LoginController` forwards the generated key pair to the `MiningController` via `setMiningData()`, immediately rendering the primary dashboard stage.
- **Plot Enforcement:** The controller checks if a valid `.plot` binary file exists at the specified path. If missing, it spawns an asynchronous background JavaFX Task to safely execute `Plots.create()` based on the user's verified public key.
- **Active Miner Ignition:** It instantiates and activates the real-time mining engine (`DesktopMinerService`), binding it to the network socket.
- **Live User Feedback:** The controller orchestrates immediate diagnostic log streams within the UI terminal area and triggers background polling daemons to display periodic, thread-safe updates of the wallet's `Current Balance` in MOK tokens.

---

## Core Components Breakdown

- **`MinerService`:** The shared state engine and identity helper. It caches session invariants (URI, Path, Chain ID) and encapsulates the BIP39 word dictionary logic to validate recovery phrases and stream random mnemonic entropy.
- **`DesktopMinerService`:** The live network router. It extends `AbstractReconnectingMinerService` to handle the low-level stateful WebSocket loops with the University's remote node and translates raw blockchain deadlines into GUI-friendly interface events via a custom `MinerListener`.

---
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