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
| Manual Mapping | Opens the container's GUI so you can tag its slots |

Deposits respect the container's own slot filters (a furnace's fuel slot only accepts fuel).

**Dependency**: Configured 2.2+ is required; Jade is optional (tooltip preview).

| Command | Effect |
|---|---|
| `/br list [filter]` | Lists the containers you mapped (click `[-]` at the end of a line to delete that mapping) |
| `/br delete <pattern>` | Deletes one mapping (you can also type it, or click `[-]` at the end of a `/br list` line) |
| `/br reload` | Re-reads the slot rules and the mappings (no restart needed after editing config) |

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

> ※ These two are the only containers that **cannot be reached by a rule**: their block entities do not
> implement vanilla `Inventory`, so their slots cannot be addressed by slot number, which is why they are
> hard-coded in the code instead.
> Because those hard-coded implementations come *before* Manual Mapping, **Manual Mapping cannot take
> them over by default** - the same block always hits the built-in support first, and only after that
> container switch is turned off does the manual mapping get a chance.

## Manual Mapping (any other container)

> **Experimental, off by default**: enable **Manual slot mapping** under Experimental in Configured
> first — only then does the mode wheel offer *Manual Mapping*.

Some mods keep their inventory in a private `ItemStackHandler` and never implement `Inventory`. Slot
numbers are meaningless there — but their **GUI menu** can always reach the slots, because a menu
`Slot` holds a direct reference to the container behind it. Manual Mapping uses exactly that.

1. Pick **Manual Mapping** on the mode wheel (hold the mode key and scroll).
2. Hold the modifier and right-click the container: this time the GUI **opens normally** (nothing is
   moved or blocked).
3. **Hold the modifier** and scroll to switch brushes, in the order **Input → Output → Fuel →
   Ignore** (scrolling down moves one step forward; the first scroll selects *Input* to start with).
   Still holding the modifier, **sweep the mouse over / rest it on a slot** (about 0.1 s) to paint the
   current brush onto it. Coloured regions with a small corner letter (I / F / O) show what each slot
   is tagged as. **Releasing the modifier stops both scrolling and painting** — the modifier is the
   master switch for every tagging action.
4. Close the GUI. The mapping is saved to `config/better-removal/menu-maps.json` on the server.

From then on the container behaves like the built-in ones: extract / deposit / restock without its
GUI, and the tags decide which slots each action touches.

Things worth knowing:

- A mapping is keyed by the block id.
- While tagging, the mod verifies it can rebuild the menu **without** the GUI; if it cannot (for example
  the GUI is created by the block instead of the block entity), you are told immediately and the mapping
  is not saved.
- If a container already has a built-in rule, that rule wins. Manual mappings are only consulted when no
  rule matches.
- `/br reload` re-reads `menu-maps.json`.

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
| `sided` | no | `true` to also require `canTakeItemThroughFace` before extracting |

> Only rules with a `config` key show up in Configured, and only those can be turned off.

### Self-checks

Applied after `/br reload`. While loading rules the mod logs: a block id that does not exist, a
config key used by several rules, slot numbers beyond the declared `size`, and rules that declare no
slots at all.

### Active probe: let the mod read the slots for you

With **Active probe mode** enabled (off by default) the mode wheel gains an *Active Probe* entry:
hold the modifier and right-click any container and the mod asks its own inventory interface
(`Inventory.isValid` / `SidedInventory.canExtract`), printing one click-to-copy rule
string you can paste into Configured's *Declared container slot rules*. Probing only prints text and
never takes over a container.

It only covers containers that implement vanilla `Inventory` - slot numbers mean nothing otherwise.
For containers whose GUI has slots but whose block entity does not implement `Inventory`
(cooking pot, machines, ...), use **Manual Mapping** instead.

---

## License

MIT License — Copyright (c) 2026 CloseDW

See [LICENSE](LICENSE).
