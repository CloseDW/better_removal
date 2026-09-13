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

| Option (Configured) | Default | Meaning |
| --- | --- | --- |
| `Auto-detect containers (Experimental)` | off | Master switch for the whole feature |
| `Auto-detect whitelist` | empty | Only the blocks listed here are auto-detected |
| `Active probe mode (Experimental)` | off | Adds an *Active Probe* entry to the mode wheel |
| `Container slot rules` | empty | Hand-written slot maps, one rule per line |

### 1. Declared slot rules

A rule simply says what each slot of a container is, so nothing has to be guessed. Rules come from
three places, highest priority first:

1. `Container slot rules` in Configured — one rule per line, editable in game;
2. `config/better-removal/containers/*.json`;
3. `better-removal/containers/*.json` inside a mod jar.

All three use the same match syntax, case-insensitive: `modid:block_id` matches one block.

Configured line format — `<match> <role>=<slots> ...`, with slots comma separated and `*` meaning
every slot:

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

Roles are `input`, `fuel`, `output` and `ignore` (= `none`). Later assignments win, so
`"output": "*", "input": [1]` means "everything is output except slot 1". Out-of-range slot numbers
are ignored, and a rule that is written wrong is skipped.

Rules do **not** need the whitelist, but they do respect the master switch. A built-in rule ships for
**Iron Furnaces** (`better-removal/containers/iron_furnaces.json`: input `0`, fuel `1`, output `2`,
everything else ignored), so that whole mod works as soon as the experimental switch is on.
`/br reload` re-reads all rule files and the Configured list.

### 2. Auto-detection (fallback guess)

Only blocks listed in the **whitelist** are auto-detected, matched the same way as rule matches.

Three detectors run in order:

1. **GUI menu, read-only.** Builds the container's own menu and reads what the mod author declared
   (can insert / can take / slot visible / stack limit).
2. **Shift-click transfer (optional, needs `Active probe mode`).** Shift-clicking a slot is implemented
   by the menu's own `quickMove`. This step seeds a probe item into the menu's *throwaway* player
   inventory and runs `quickMove`, then looks at which container slot received it. That reads roles
   straight out of the mod's own logic, including machines whose `isValid` answers "yes" to everything.
3. **Vanilla inventory interface.** Fallback that just asks `Inventory.isValid` with a few common
   items and checks `SidedInventory.canExtract`.

Results (and the detector used, e.g. `menu`, `menu+transfer`, `inventory`) are printed to the log as
`[auto-detect] <block> -> 0=INPUT 1=FUEL 2=OUTPUT ...`, so you can check what it decided before
trusting it. Turn `Active probe mode` off if a specific machine reacts badly to being probed.

### 3. Active probe mode (write a rule by hand, quickly)

With `Active probe mode` on, the mode wheel grows one extra entry: **Active Probe**. Hold the modifier
and right-click any container in that mode and the server runs all three detectors above, then prints
their results to chat — one rule JSON per detector, ready to copy:

```
Active probe: somemod:crusher
(1) Menu, read-only: {"match":"somemod:crusher","input":[0,1],"fuel":[2],"output":[3]}
(2) Shift-click transfer: {"match":"somemod:crusher","input":[0],"fuel":[2]}
(3) Inventory interface: {"match":"somemod:crusher","output":[3,4]}
```

Paste the line that looks right into a file under `config/better-removal/containers/` (or into the
Configured `Container slot rules` list) and it applies after `/br reload`. If a detector cannot work
out anything at all it prints `cannot detect` for that line instead. Nothing is written to disk, and
the container itself is left exactly as it was found.

### 4. Scope and limits

- Unusual layouts are fine as long as the slots can be numbered: several inputs and several outputs
  are just more numbers in the same rule (Ad Astra's Fuel Refinery, cooking pots, ovens), and a
  machine with no fuel slot simply has no `fuel` entry.
- What a rule cannot reach is a container the game cannot address slot by slot: a block entity that
  does not implement `Inventory` at all, or a GUI whose slots point at a private inner container
  instead of the block entity itself (Farmer's Delight's cooking pot, Cooking for Blockheads' oven).
  Those keep their dedicated built-in support.

---

## License

MIT License — Copyright (c) 2026 CloseDW

See [LICENSE](LICENSE).
