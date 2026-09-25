# Better Removal

English | [中文](README.zh_cn.md)

Fabric 1.20.1: hold **Left Alt** (rebindable) and **right-click** a container to move items in or
out of it — **without opening its GUI**.
- **Empty hands** = extract; **holding an item** = deposit.
- Hold the **mode key** and scroll to switch presets; release to apply:

| Preset | Effect |
|---|---|
| Extract · Output (default) | Extracts output slots only |
| Extract · Input / Fuel / All | Extracts the named slots |
| Deposit · Input / Fuel | Deposits into the named slots |
| Restock | Refills input/fuel slots with matching items from your inventory (empty slots are skipped) |

Deposits respect the container's own slot filters (a furnace's fuel slot only accepts fuel).

| Command | Effect |
|---|---|
| `/br` or `/br now` | Shows your current preset |
| `/br reload` | Re-reads the slot rules (no restart needed after editing config) |

## Supported Containers

Slot layouts are data-driven from `better-removal/containers/*.json` (see the next section), so this
is what the built-in rules cover.

| Container | Extract | Deposit (Input / Fuel) |
|---|---|---|
| Furnace · Blast Furnace · Smoker | yes | input / fuel |
| Brewing Stand | yes | ingredient / blaze powder |
| Hopper · Dispenser · Dropper | yes | every slot |
| Farmer's Delight Basket | yes | every slot |
| Farmer's Delight Cooking Pot ※ | yes | input |
| Cooking for Blockheads Oven ※ | yes | input / fuel |
| Ad Astra Compressor · Etrionic Blast Furnace · Fuel Refinery · Oxygen Loader · Cryo Freezer | yes | inputs (refinery/loader include fluid inputs) |
| Crabber's Delight Crab Trap | yes | bait |
| The Aether Freezer · Altar | yes | input / fuel |
| Vinery Fermentation Barrel · Apple Press | yes | inputs / wine bottle |
| Farm & Charm Cooking Pot · Roaster | yes | input / dish |
| Farm & Charm Stove | yes | input / fuel |
| Fossils and Archeology Analyzer | yes | inputs |
| Fossils and Archeology Sifter | yes | input |
| Fossils and Archeology Culture Vat · Worktable | yes | input / fuel |
| Iron Furnaces (all tiers) | yes | input / fuel |

> ※ These two are the only containers that **must stay hardcoded**: their block entities do not
> implement vanilla `Inventory`, so their slots cannot be addressed by a rule.

## Compatibility

**FTB Ultimine**: hold the modifier *and* the Ultimine key while right-clicking to operate on every
supported container in the current chain shape. (Required on both sides — key state and chain shape
are server-side.)

**Jade**: hold the modifier and look at a container to preview what would be extracted or deposited.
A chain shows the combined items of all chained containers. Toggle it with **Jade Preview** in the
config. (Real contents need Jade on both sides.)

## Configuration

Edit via the **Configured** mod. Every container is enabled by default, and only the mods you
actually have installed show up as categories.

---

## Adding Container Support

To support a container that is not covered, you do not need code changes or a mod update — write one
line in the Configured `Declared container slot rules` list. Declared rules are explicit
declarations, so they are **always active** and unaffected by the experimental switches.

### One-line format

```
ironfurnaces input=0 fuel=1 output=2
somemod:crusher,somemod:grinder input=* output=9,10
```

Slots are comma-separated and `*` means every slot. Roles are `input`, `fuel`, `output`, `ignore`.
Later assignments win, so `output=* ignore=5` means "everything is output except slot 5". A rule
written wrong is skipped, and the reason is logged.

### JSON format

Used for built-in compatibility and mod self-declaration, as `better-removal/containers/*.json`:

```json
{
  "containers": [
    {
      "match": "somemod:crusher, @somemod.common.block.entity.CrusherBlockEntity",
      "config": "crusher",
      "size": 4,
      "extract": { "input": [0, 1], "fuel": [2], "output": [3] },
      "deposit": { "input": [0, 1], "fuel": [2] }
    }
  ]
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `match` | yes | Match patterns; several separated by commas, any hit counts as a match |
| `extract` / `deposit` | `extract` required | Slot maps for each direction; only split them when the two differ |
| `config` | no | Configured switch key; omit it for a rule with no switch |
| `group` | no | Switch group; inferred from the `match` namespace, and **required** when `match` only uses `@class` |
| `default` | no | Default value of that switch (defaults to `true`) |
| `size` | no | Total slot count; enables out-of-range checks at startup and mismatch warnings at runtime |
| `sided` | no | `true` to also require `canExtract` before extracting |

> Only rules with a `config` key show up in Configured, and only those can be turned off.

### Self-checks

Applied after `/br reload`. While loading rules the mod logs: a block id that does not exist, a
config key used by several rules, slot numbers beyond the declared `size`, and rules that declare no
slots at all.

### Experimental: auto-detection

Instead of writing a rule, you can let the mod guess. Off by default; enable `Auto-detect containers`
and add blocks to the whitelist.

- **Auto-detection**: reads the container's own GUI menu first, then falls back to
  `Inventory.isValid` heuristics. The result is logged as
  `[container] <block> -> 0=INPUT 1=FUEL 2=OUTPUT ...` so you can check before trusting it.
- **Active probe mode**: adds an *Active Probe* entry to the mode wheel. Hold the modifier and
  right-click any container and both detectors run, each printing one click-to-copy rule string you
  can paste straight into Configured.

The only containers a rule cannot reach are those the game cannot address slot by slot: a block
entity that does not implement `Inventory`, or a GUI whose slots point at a private inner container.
Those keep their dedicated built-in support.

---

## License

MIT License — Copyright (c) 2026 CloseDW

See [LICENSE](LICENSE).
