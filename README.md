# Better Removal

Fabric 1.20.1: Hold **Left Alt** (rebindable) and **right-click** a container to directly extract its items into your inventory — **without opening the GUI**.

## Modes

Hold **Left Alt** (rebindable) and right-click:

- **Empty hands** to **extract** (or **restock** from your inventory);
- **Holding an item** to **deposit** that item into supported containers.

Pick a preset with the **Mode** keybinding (hold it, scroll to move through the 7 presets, release to apply; **unbound by default**) or the `/br` command:

| Preset | Command | Effect |
|---|---|---|
| Extract · Output (default) | `/br extract output` | Extracts output slots only |
| Extract · Input | `/br extract input` | Extracts input slots only |
| Extract · Fuel | `/br extract fuel` | Extracts fuel slots only |
| Extract · All | `/br extract all` | Extracts every slot |
| Deposit · Input | `/br deposit input` | Deposits into input slots |
| Deposit · Fuel | `/br deposit fuel` | Deposits into fuel slots |
| Restock | `/br restock` | Refills input/fuel slots with matching items from your inventory (empty slots are skipped) |

- `/br now` — shows your current preset.
- `/br` (no arguments) — same as `/br now`.
- Container slot filters are respected when depositing (e.g. a furnace's fuel slot only accepts fuel).

## Supported Containers

### Extraction & Deposit

| Container | Extract | Deposit (Input / Fuel) |
|---|---|---|
| Furnace / Blast Furnace / Smoker | yes | input slot / fuel slot |
| Brewing Stand | yes | ingredient / blaze powder |
| Hopper / Dispenser / Dropper | yes | every slot |
| Farmer's Delight Basket | yes | every slot |
| Farmer's Delight Cooking Pot | yes | input |
| Cooking for Blockheads Oven | yes | input / fuel |
| Ad Astra Compressor | yes | input |
| Ad Astra Etrionic Blast Furnace | yes | inputs |
| Ad Astra Fuel Refinery | yes | fluid + item inputs |
| Ad Astra Oxygen Loader | yes | fluid + item inputs |
| Ad Astra Cryo Freezer | yes | inputs |
| Crabber's Delight Crab Trap | yes | bait slot |
| The Aether Freezer / Altar | yes | input / fuel |
| Vinery Fermentation Barrel | yes | inputs / wine bottle |
| Vinery Apple Press | yes | inputs / wine bottle |
| Fossils and Archeology Analyzer | yes | inputs |
| Fossils and Archeology Sifter | yes | input |
| Fossils and Archeology Culture Vat | yes | input / fuel |
| Fossils and Archeology Worktable | yes | input / fuel |

---

## Configuration

Edit via the **Configured** mod
---

## Compatibility

### FTB Ultimine

With **FTB Ultimine** installed, holding **both** keys — the Better Removal modifier (Left Alt, rebindable) **and** the Ultimine key (default `~`) — while right-clicking a supported container operates on **every supported container in the current Ultimine shape**.
> Note: FTB Ultimine must be installed on both the client and the server, since the key state and the chain shape are managed server-side.

### Jade

With **Jade** installed, holding the modifier key while looking at a supported container shows a highlighted preview in the tooltip — the items that would be extracted (empty hands) or the item that would be deposited (holding an item in deposit mode). When an FTB Ultimine chain extraction would trigger, the preview shows the combined items of all chained containers. This can be toggled via the **Jade Preview** option in the Configured config.

> Note: For the extraction preview to show real container contents, **Jade must be installed on both the client and the server**.

---

## License

MIT License — Copyright (c) 2026 CloseDW

See [LICENSE](LICENSE).
