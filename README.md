# Better Removal

English | [中文](README.zh_cn.md)

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

| Container                          | Extract | Deposit (Input / Fuel) |
|------------------------------------|---------|--------------|
| Furnace / Blast Furnace / Smoker   | yes     | input slot / fuel slot |
| Brewing Stand                      | yes     | ingredient / blaze powder |
| Hopper / Dispenser / Dropper       | yes     | every slot   |
| Farmer's Delight Basket            | yes     | every slot   |
| Farmer's Delight Cooking Pot       | yes     | input        |
| Cooking for Blockheads Oven        | yes     | input / fuel |
| Ad Astra Compressor                | yes     | input        |
| Ad Astra Etrionic Blast Furnace    | yes     | inputs       |
| Ad Astra Fuel Refinery             | yes     | fluid + item inputs |
| Ad Astra Oxygen Loader             | yes     | fluid + item inputs |
| Ad Astra Cryo Freezer              | yes     | inputs       |
| Crabber's Delight Crab Trap        | yes     | bait slot    |
| The Aether Freezer / Altar         | yes     | input / fuel |
| Vinery Fermentation Barrel         | yes     | inputs / wine bottle |
| Vinery Apple Press                 | yes     | inputs / wine bottle |
| Farm & Charm Cooking Pot           | yes     | input / bowl |
| Farm & Charm Roaster               | yes     | input / bowl |
| Farm & Charm Stove                 | yes     | input / fuel |
| Fossils and Archeology Analyzer    | yes     | inputs       |
| Fossils and Archeology Sifter      | yes     | input        |
| Fossils and Archeology Culture Vat | yes     | input / fuel |
| Fossils and Archeology Worktable   | yes     | input / fuel |

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

## Experimental: generic container support

Beyond the built-in per-mod support there is an **experimental** path that makes *any* container
usable without per-mod code. It is **off by default** and has three parts: **declared slot rules**,
**auto-detection**, and the **active probe mode** that writes you a rule to copy.

| Option (Configured) | Default | Meaning                                                         |
| --- | --- |-----------------------------------------------------------------|
| `Auto-detect containers (Experimental)` | off | Master switch for the whole feature                             |
| `Auto-detect whitelist` | empty | Only the blocks listed here are auto-detected (mod_id:block_id) |
| `Active probe mode (Experimental)` | off | Adds an *Active Probe* entry to the mode wheel                  |
| `Container slot rules` | empty | Hand-written slot maps, one rule per line                       |

### 1. Declared slot rules

A rule says exactly what each slot of a container is. Rules come from three places, highest priority
first:

1. `Container slot rules` in Configured — one rule per line;
2. `config/better-removal/containers/*.json`;
3. `better-removal/containers/*.json` inside a mod jar.

Configured line format — slots are comma-separated and `*` means every slot:

```
ironfurnaces input=0 fuel=1 output=2
somemod:crusher,somemod:grinder input=* output=9,10
```

JSON file format (one rule may target several blocks by separating the `match` with commas):

```json
{
  "containers": [
    { "match": "somemod:crusher", "input": [0, 1], "fuel": [2], "output": [3, 4], "ignore": [5] },
    { "match": "somemod:trash_can", "ignore": "*" }
  ]
}
```

Roles are `input`, `fuel`, `output` and `ignore`. Later assignments win, so
`"output": "*", "input": [1]` means "everything is output except slot 1". Out-of-range slot numbers
are ignored, and a rule that is written wrong is skipped. `/br reload` re-reads all rule files and the
Configured list.

On first run an `example.json` template is written to `config/better-removal/containers/`.

### 2. Auto-detection (fallback guess)

Only blocks listed in the **whitelist** are auto-detected.

Two detectors run in order:

1. **GUI menu, read-only.** Builds the container's own menu and reads what the mod author declared
   (can insert / can take / slot visible / stack limit).
2. **Vanilla inventory interface.** Fallback that just asks `Inventory.isValid` with a few common
   items and checks `SidedInventory.canExtract`.

Results (and the detector used, e.g. `menu`, `inventory`) are printed to the log as
`[auto-detect] <block> -> 0=INPUT 1=FUEL 2=OUTPUT ...`, so you can check what it decided before
trusting it.

### 3. Active probe mode (write a rule by hand, quickly)

With `Active probe mode` on, the mode wheel grows one extra entry: **Active Probe**. Hold the modifier
and right-click any container in that mode and the server runs both detectors above, each printing two
click-to-copy lines — **JSON** and **Rule** (the Configured `Container slot rules` format):

```
Active probe: somemod:crusher
(1) Menu, read-only
  JSON: {"match":"somemod:crusher","input":[0,1],"fuel":[2],"output":[3]}
  Rule: somemod:crusher input=0,1 fuel=2 output=3
(2) Inventory interface
  JSON: {"match":"somemod:crusher","output":[3,4]}
  Rule: somemod:crusher output=3,4
```

Paste the JSON line into a file under `config/better-removal/containers/`, or the Rule line into the
Configured `Container slot rules` list; it applies after `/br reload`. If a detector cannot work out
anything at all it prints `cannot detect` for that detector instead.

### 4. Scope and limits

- Unusual layouts are fine as long as the slots can be numbered: several inputs and several outputs
  are just more numbers in the same rule (Ad Astra's Fuel Refinery, cooking pots, ovens), and a
  machine with no fuel slot simply has no `fuel` entry.
- What a rule cannot reach is a container the game cannot address slot by slot: a block entity that
  does not implement `Inventory` at all, or a GUI whose slots point at a private inner container
  instead of the block entity itself (Farmer's Delight's cooking pot, Cooking for Blockheads' oven).
  Those keep their dedicated built-in support.

---

## Development: adding a container

Container support is centralized in `closedw.br.container.ContainerRegistry`. The registry is the single
source of truth: extraction, deposit, restock, FTB Ultimine chaining and the Jade preview all query it.

**A normal container** (a block entity implementing the vanilla `Inventory` interface) is one entry in
`ContainerRegistry.registerAll()`:

```java
reg(new SimpleContainerSupport("crusher", be -> be instanceof CrusherBlockEntity)
        .extract(ExtractionMode.INPUT, 0).extract(ExtractionMode.OUTPUT, 1)
        .deposit(ExtractionMode.INPUT, 0));
```

Then add the config key to `BetterRemovalConfig` (group + default value) and to both `lang` files.
That is all — `ALL` (every slot), restock slots (input ∪ fuel) and the slot read/write layer are
derived automatically.

**A special container** (not an `Inventory`, or reachable only through reflection) implements
`ContainerSupport` for its slot tables plus a `ContainerAccess` for reading/writing, as
`CookingPotSupport`/`CookingPotAccess` and `OvenSupport` do.

Registry order is priority. The experimental rule/auto-detect adapters are registered last, so they only
apply to containers that no built-in support claims. 

---

## License

MIT License — Copyright (c) 2026 CloseDW

See [LICENSE](LICENSE).
