# Better Removal

Fabric 1.20.1: **Sneak + empty-hand right-click** a container to directly extract its items into your inventory — **without opening the GUI**.

## Extraction Modes

By default only the **output** slots are extracted. You can switch the extraction mode with the `/br` command or a keybinding:

| Mode | Command | Extracts |
|---|---|---|
| Output (default) | `/br output` | Output slots only |
| Input | `/br input` | Input slots only |
| Fuel | `/br fuel` | Fuel slots only |
| All | `/br all` | Every slot |

- `/br now` — shows your current extraction mode.
- A **keybinding** (unbound by default) cycles through the modes: `Output → Input → Fuel → All → Output ...`

## Supported Containers

### Vanilla

| Container |
|---|
| Furnace / Blast Furnace / Smoker |
| Brewing Stand |
| Hopper / Dispenser / Dropper |

### Farmer's Delight

| Container |
|---|
| Cooking Pot |
| Wooden / Bamboo Basket |

### Ad Astra

| Container |
|---|
| Compressor |
| Etrionic Blast Furnace |
| Fuel Refinery |
| Oxygen Loader |
| Cryo Freezer |

### Crabber's Delight

| Container |
|---|
| Crab Trap |

### The Aether

| Container |
|---|
| Freezer |
| Altar |

### Vinery

| Container |
|---|
| Fermentation Barrel |
| Apple Press |

### Fossils and Archeology: Revival

| Container |
|---|
| Analyzer |
| Sifter |
| Culture Vat |
| Archeology Workbench |

---

## Configuration

Edit via the **Configured** mod.

---

## Compatibility

When this mod is installed together with **Carry On**, its activation is changed to **Left Alt + empty-hand right-click**.

### FTB Ultimine

With **FTB Ultimine** installed, holding **both** modifier keys — the Better Removal modifier (Sneak, or Left Alt with Carry On) **and** the Ultimine key (default `~`) — while right-clicking a supported container with empty hands extracts the items from **every supported container in the current Ultimine shape** , according to the current extraction mode. 

> Note: FTB Ultimine must be installed on both the client and the server, since the key state and the chain shape are managed server-side.

### Jade

With **Jade** installed, holding the modifier key (Sneak, or Left Alt with Carry On) while looking at a supported container with empty hands shows a highlighted "Will extract:" line in the tooltip, previewing the items that would be extracted for the current mode. When an FTB Ultimine chain extraction would trigger (both keys held), the preview shows the combined items of all chained containers. This can be toggled via the **Jade Preview** option in the Configured config.

> Note: For the preview to show real container contents, **Jade must be installed on both the client and the server**. 

---

## License

MIT License — Copyright (c) 2026 CloseDW

See [LICENSE](LICENSE).
