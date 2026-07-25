# Cooking Pot NMS GUI — Design Spec

## Overview

Replace the current `CookingPotMenuHandle`（fake furnace with `MenuType.FURNACE`）with a custom `AbstractContainerMenu` using `MenuType.GENERIC_9x6`（54-slot chest），visually simulating the Farmer's Delight cooking pot GUI via stained-glass UI items + NMS slot control. Pure server-side, no client mod required.

## GUI Layout（9×6 grid）

```
     col0    col1    col2    col3    col4    col5    col6    col7    col8
row0 [Glass] [Glass] [ Inp1 ] [ Inp2 ] [ Inp3 ] [Glass] [ Prog ] [ Out ] [Glass]
row1 [Glass] [Glass] [ Inp4 ] [ Inp5 ] [ Inp6 ] [Glass] [Glass] [Glass] [Glass]
row2 [Glass] [Glass] [ Fuel ] [Glass] [Glass] [Glass] [Glass] [Glass] [Glass]
row3 [                    Player Inventory（27 slots）                          ]
row4 [                    Player Inventory（27 slots）                          ]
row5 [                    Player Inventory（27 + hotbar）                        ]
```

| Slot Type | Count | Behavior |
|-----------|-------|----------|
| `GuiGlass` | ~36 | Stained glass pane, locked（`mayPlace=false`, `mayPickup=false`），visual decoration |
| `InputSlot ×6` | 6 | Accept only recipe-matched items, mixed types per slot OK |
| `FuelSlot` | 1 | Blaze rod icon locked（decorative），or optionally real fuel |
| `ProgressSlot` | 1 | Barrier item with swapped display via `setChanged` for progress animation |
| `OutputSlot` | 1 | Unlocks when cooking complete, `onTake` resets pot |
| Player Inv | 36 | Standard（3 rows top + 1 row hotbar）|

Title bar: CE `<image>` tag for pot icon + MiniMessage `烹饪锅`.

## Class Structure

### New files

| Class | Responsibility |
|-------|---------------|
| `CookingPotGuiManager` | Open/close GUI lifecycle, hold per-player menu references |
| `CookingPotMenu` | `AbstractContainerMenu` subclass, 9×6 layout, slot setup |
| `GuiDecorator` | Initialize glass/barrier items in non-functional slots on menu open |
| `InputSlot` | Slot subclass with recipe Predicate filter |
| `FuelSlot` | Locked decorative slot（blaze rod）|
| `OutputSlot` | Unlocks on cook complete, calls reset callback |
| `ProgressSlot` | Non-interactive, item swapped for visual progress |

### Modified files

| File | Change |
|------|--------|
| `CookingPotListener` | Replace `CookingPotMenuHandle.open()` with `CookingPotGuiManager.open()`；6-ingredient recipe matching |
| `CookingPersistenceManager` | Serialize 6 ingredients instead of 1 |

### Deleted files

| File | Reason |
|------|--------|
| `CookingPotMenuHandle.java` | Replaced by `CookingPotMenu` + `CookingPotGuiManager` |

## Data Flow（Furnace-like real-time cooking）

```
1. Player right-clicks pot block
2. → CookingPotListener → CookingPotGuiManager.open(player, location)
3.   → new CookingPotMenu()      // NMS container
4.   → GuiDecorator.decorate()   // fill glass + progress barrier
5.   → ClientboundOpenScreenPacket sent
6. Player places items in InputSlots
7.   → InputSlot.setChanged() → CookingPotMenu.slotsChanged()
8.   → Check if 6 slots match a recipe → yes: startCooking()
9. Tick task（every 10t）advances progress
10.  → ContainerData.set(2, progress) → client arrow updates in real-time
11.  → Done: OutputSlot unlocks, result item placed
12. Player takes output → OutputSlot.onTake() → clear ingredients, reset pot
13. If GUI closes before done → tick continues in background, restore on reopen
```

Key difference from current implementation: cooking starts the moment ingredients match, NOT on GUI close. Progress is visible inside the GUI via `ContainerData`.

## Recipe System

```java
record PotRecipe(List<Material> ingredients, ItemStack result, int cookTime) {}
List<PotRecipe> recipes;
```

Matching logic: collect non-empty items from 6 InputSlots into a multiset, compare against each recipe's ingredient multiset（order-independent）.

## Backward Compatibility

- Persistence format changes（1 ingredient → 6）. Add `version: 2` field to `cooking_state.yml`；if version is missing or 1, skip restore for pot entries with a warning log（no crash）.
- Existing `CookingPotMenuHandle` removed entirely — no fallback needed since this is a full rewrite of the pot GUI path.

## Implementation Notes

- **Glass material**: `GRAY_STAINED_GLASS_PANE` with `ItemMeta.displayName = " "`（empty space to hide name）.
- **Fuel slot item**: `BLAZE_ROD` with `displayName = "燃料"`，locked via slot override.
- **Progress barrier**: `BARRIER` item，base model displays empty. As cooking advances, swap barrier with different `CustomModelData` value（1-10 for 10% steps）or just use `ContainerData` property 2 for the arrow fill（same as current furnace approach）.
- **Title**: `"<image:manyidea:pot_icon> <!i><gold>烹饪锅</gold>"` passed as `Component.literal()` then wrapped via CE's MiniMessage integration.
- **`slotsChanged` trigger**: Override `AbstractContainerMenu.slotsChanged()` in `CookingPotMenu`. Called automatically by NMS when any slot content changes. This is where recipe matching logic runs — check all 6 InputSlots, if they form a valid recipe and no cooking is in progress, start.

## Out of Scope

- Grill / DeepFryingPan GUI — they remain right-click + empty-hand interaction for now. Same framework can be reused later.
- Client mod Screen injection（optional upgrade path，not in this spec）.
