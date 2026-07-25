# Cooking Pot NMS GUI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fake-furnace `CookingPotMenuHandle` with a 9×6 chest-based GUI that visually simulates the Farmer's Delight cooking pot (6 ingredient slots, fuel slot, output slot, progress), using stained-glass UI items + NMS slot control.

**Architecture:** `AbstractContainerMenu` subclass using `MenuType.GENERIC_9x6` with `SimpleContainer(54)`. Functional slots (Input×6, Fuel, Output, Progress) positioned at specific grid indices; remaining slots filled with locked `GRAY_STAINED_GLASS_PANE`. ContainerData drives client-side arrow progress. Real-time cooking starts when 6 slots match a recipe — same as furnace behavior.

**Tech Stack:** Paper NMS (Mojang mappings via paperweight), Bukkit ItemStack/CustomModelData, CraftEngine API.

**Spec:** `docs/superpowers/specs/2025-07-25-cooking-pot-nms-gui-design.md`

---

### Task 1: InputSlot

**Files:**
- Create: `src/main/java/org/coffeepop/manyidea/cooking/gui/InputSlot.java`

- [ ] **Step 1: Write InputSlot class**

A Slot subclass that accepts only items matching the recipe's ingredient filter. Each of the 6 ingredient slots gets its own InputSlot instance, each with a potentially different Predicate (or same one that checks against all known recipe ingredients).

```java
package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

import java.util.function.Predicate;

/**
 * Ingredient slot — only accepts items matching the recipe predicate.
 */
public final class InputSlot extends Slot {

    private final Predicate<org.bukkit.inventory.ItemStack> filter;

    public InputSlot(Container container, int index, int x, int y,
                     Predicate<org.bukkit.inventory.ItemStack> filter) {
        super(container, index, x, y);
        this.filter = filter;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) return true;
        return filter.test(CraftItemStack.asBukkitCopy(stack));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/gui/InputSlot.java
git commit -m "feat: add InputSlot — ingredient slot with recipe filter"
```

---

### Task 2: FuelSlot

**Files:**
- Create: `src/main/java/org/coffeepop/manyIdea/cooking/gui/FuelSlot.java`

- [ ] **Step 1: Write FuelSlot class**

Locked decorative slot — holds a blaze rod as visual indicator, cannot be picked up or replaced.

```java
package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Decorative fuel slot — locked blaze rod, cannot be moved or replaced.
 */
public final class FuelSlot extends Slot {

    public FuelSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
        container.setItem(index, new ItemStack(Items.BLAZE_ROD));
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/gui/FuelSlot.java
git commit -m "feat: add FuelSlot — locked decorative blaze rod slot"
```

---

### Task 3: OutputSlot

**Files:**
- Create: `src/main/java/org/coffeepop/manyIdea/cooking/gui/OutputSlot.java`

- [ ] **Step 1: Write OutputSlot class**

Cannot place items, only pickup when cooking is done. Fires a callback on take.

```java
package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;

import java.util.function.BooleanSupplier;

/**
 * Output slot — locked until cooking completes.  Fires callback on take.
 */
public final class OutputSlot extends Slot {

    private final BooleanSupplier isDone;
    private final Runnable onTake;

    public OutputSlot(Container container, int index, int x, int y,
                      BooleanSupplier isDone, Runnable onTake) {
        super(container, index, x, y);
        this.isDone = isDone;
        this.onTake = onTake;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return isDone.getAsBoolean();
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        super.onTake(player, stack);
        if (onTake != null) {
            Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("ManyIdea"), onTake);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/gui/OutputSlot.java
git commit -m "feat: add OutputSlot — locked output with done-check and onTake callback"
```

---

### Task 4: ProgressSlot

**Files:**
- Create: `src/main/java/org/coffeepop/manyIdea/cooking/gui/ProgressSlot.java`

- [ ] **Step 1: Write ProgressSlot class**

Non-interactive visual slot — can hold a barrier item whose CustomModelData changes to show progress percentage.

```java
package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Progress visual slot — non-interactive, cannot place or pickup.
 * ItemStack inside is swapped by the tick task to display progress.
 */
public final class ProgressSlot extends Slot {

    public ProgressSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/gui/ProgressSlot.java
git commit -m "feat: add ProgressSlot — non-interactive visual slot"
```

---

### Task 5: GuiDecorator

**Files:**
- Create: `src/main/java/org/coffeepop/manyIdea/cooking/gui/GuiDecorator.java`

- [ ] **Step 1: Write GuiDecorator class**

On menu open, fills all non-functional slots with `GRAY_STAINED_GLASS_PANE` (empty display name). The progress slot gets a `BARRIER` base item.

Slot layout (functional slots by index in the 54-container):

| Grid Pos | Container Index | Type |
|----------|----------------|------|
| (2, 0)   | 2  | InputSlot 1 |
| (3, 0)   | 3  | InputSlot 2 |
| (4, 0)   | 4  | InputSlot 3 |
| (6, 0)   | 6  | ProgressSlot |
| (7, 0)   | 7  | OutputSlot |
| (2, 1)   | 11 | InputSlot 4 |
| (3, 1)   | 12 | InputSlot 5 |
| (4, 1)   | 13 | InputSlot 6 |
| (2, 2)   | 20 | FuelSlot |

All other indices 0..26 are glass.

```java
package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

import java.util.Set;

/**
 * Fills non-functional slots with gray stained glass pane on menu open.
 */
public final class GuiDecorator {

    private static final Set<Integer> FUNCTIONAL_INDICES = Set.of(
        2, 3, 4,        // row0: input 1-3
        6,              // row0: progress
        7,              // row0: output
        11, 12, 13,     // row1: input 4-6
        20              // row2: fuel
    );

    private static final org.bukkit.inventory.ItemStack GLASS;
    static {
        GLASS = new org.bukkit.inventory.ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = GLASS.getItemMeta();
        meta.setDisplayName(" ");
        GLASS.setItemMeta(meta);
    }

    /**
     * Fill all 27 top-container slots: functional ones leave empty,
     * non-functional get glass pane.
     */
    public static void decorate(SimpleContainer container) {
        for (int i = 0; i < 27; i++) {
            if (!FUNCTIONAL_INDICES.contains(i)) {
                container.setItem(i, CraftItemStack.asNMSCopy(GLASS));
            }
        }
        // Progress slot base item
        container.setItem(6, CraftItemStack.asNMSCopy(
            new org.bukkit.inventory.ItemStack(Material.BARRIER)));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/gui/GuiDecorator.java
git commit -m "feat: add GuiDecorator — fill non-functional slots with gray glass pane"
```

---

### Task 6: PotRecipe

**Files:**
- Create: `src/main/java/org/coffeepop/manyIdea/cooking/gui/PotRecipe.java`

- [ ] **Step 1: Write PotRecipe record**

Multi-ingredient recipe with order-independent matching.

```java
package org.coffeepop.manyIdea.cooking.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Multi-ingredient cooking pot recipe.  Ingredient order does not matter.
 */
public record PotRecipe(Set<Material> ingredients, ItemStack result, int cookTime) {

    public PotRecipe {
        if (ingredients == null || ingredients.isEmpty())
            throw new IllegalArgumentException("ingredients must not be empty");
        if (result == null || result.getType().isAir())
            throw new IllegalArgumentException("result must not be empty/air");
        if (cookTime <= 0)
            throw new IllegalArgumentException("cookTime must be positive");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/gui/PotRecipe.java
git commit -m "feat: add PotRecipe — multi-ingredient recipe with order-independent match"
```

---

### Task 7: CookingPotMenu

**Files:**
- Create: `src/main/java/org/coffeepop/manyIdea/cooking/gui/CookingPotMenu.java`

- [ ] **Step 1: Write CookingPotMenu class**

`AbstractContainerMenu` subclass using `MenuType.GENERIC_9x6`. Contains:
- 27 container slots (rows 0-2): 9 functional + 18 glass
- Player inventory (rows 3-5 + hotbar)
- ContainerData with 4 values: [0]=1, [1]=1, [2]=progress, [3]=cookTime
- `slotsChanged()` override that triggers recipe matching

Slot pixel positions follow standard chest layout (18px spacing):

| Row | Y |
|-----|---|
| Container row 0 | 18 |
| Container row 1 | 36 |
| Container row 2 | 54 |
| Player inv top | 85 |
| Player inv mid | 103 |
| Player inv bottom | 121 |
| Hotbar | 161 |

X positions: 8 + col * 18 for col 0..8.

```java
package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.InventoryView;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * 9×6 chest-based cooking pot menu with 6 ingredient slots.
 */
public final class CookingPotMenu extends AbstractContainerMenu {

    static final int CONTAINER_SIZE = 54;
    static final int INPUT_START  = 2;  // indices 2,3,4 in row 0
    static final int INPUT_ROW1   = 11; // indices 11,12,13 in row 1
    static final int PROGRESS     = 6;
    static final int OUTPUT       = 7;
    static final int FUEL         = 20;

    private final SimpleContainer container;
    private final ContainerData data;
    private final ServerPlayer player;
    private final List<PotRecipe> recipes;
    private final BooleanSupplier isCooking;
    private final Runnable onRecipeMatch;

    private static final int[] DATA_VALUES = {1, 1, 0, 200};

    CookingPotMenu(int syncId, Inventory playerInv, SimpleContainer container,
                   ServerPlayer player, List<PotRecipe> recipes,
                   Predicate<org.bukkit.inventory.ItemStack> ingredientFilter,
                   BooleanSupplier isDone, Runnable onOutputTaken,
                   BooleanSupplier isCooking, Runnable onRecipeMatch,
                   int initialProgress, int cookTime) {
        super(MenuType.GENERIC_9x6, syncId);
        this.container = container;
        this.player = player;
        this.recipes = recipes;
        this.isCooking = isCooking;
        this.onRecipeMatch = onRecipeMatch;

        // ContainerData
        this.data = new ContainerData() {
            private final int[] values = {1, 1, initialProgress, cookTime};
            @Override public int get(int i) { return values[i]; }
            @Override public void set(int i, int v) { values[i] = v; }
            @Override public int getCount() { return 4; }
        };
        addDataSlots(this.data);

        // --- Container slots (rows 0-2, indices 0-26) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                int x = 8 + col * 18;
                int y = 18 + row * 18;

                Slot slot;
                if (row == 0 && col == 2) slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 0 && col == 3) slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 0 && col == 4) slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 0 && col == 6) slot = new ProgressSlot(container, idx, x, y);
                else if (row == 0 && col == 7) slot = new OutputSlot(container, idx, x, y, isDone, onOutputTaken);
                else if (row == 1 && col == 2) slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 1 && col == 3) slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 1 && col == 4) slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 2 && col == 2) slot = new FuelSlot(container, idx, x, y);
                else slot = new Slot(container, idx, x, y);

                this.addSlot(slot);
            }
        }

        // --- Player inventory (rows 3-5 + hotbar) ---
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 161));

        // Decorate
        GuiDecorator.decorate(container);
    }

    /** Called by NMS whenever any slot changes content. */
    @Override
    public void slotsChanged(net.minecraft.world.Container inventory) {
        super.slotsChanged(inventory);
        if (isCooking.getAsBoolean()) return;

        Set<org.bukkit.Material> placed = collectIngredients();
        if (placed.isEmpty()) return;

        for (PotRecipe recipe : recipes) {
            if (placed.equals(recipe.ingredients())) {
                onRecipeMatch.run();
                return;
            }
        }
    }

    private Set<org.bukkit.Material> collectIngredients() {
        Set<org.bukkit.Material> set = new HashSet<>();
        for (int idx : new int[]{INPUT_START, INPUT_START + 1, INPUT_START + 2,
                                  INPUT_ROW1, INPUT_ROW1 + 1, INPUT_ROW1 + 2}) {
            Slot slot = this.slots.get(idx);
            if (slot.hasItem()) {
                org.bukkit.Material mat = CraftItemStack.asBukkitCopy(slot.getItem()).getType();
                if (!mat.isAir()) set.add(mat);
            }
        }
        return set;
    }

    // --- Quick move (shift+click) ---
    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack source = slot.getItem().copy();
        // From output → player inventory (slots 27-62)
        if (index == OUTPUT) {
            if (!this.moveItemStackTo(source, 27, 63, true))
                return ItemStack.EMPTY;
        }
        // From player inventory → ingredient slots
        else if (index >= 27) {
            if (!this.moveItemStackTo(source, INPUT_START, INPUT_START + 3, false)
                && !this.moveItemStackTo(source, INPUT_ROW1, INPUT_ROW1 + 3, false))
                return ItemStack.EMPTY;
        }
        // Already in container → player inventory
        else {
            if (!this.moveItemStackTo(source, 27, 63, true))
                return ItemStack.EMPTY;
        }

        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return source;
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }

    @Override
    public InventoryView getBukkitView() {
        return new CraftInventoryView(player.getBukkitEntity(),
            new CraftInventory(container), this);
    }

    // --- Public API ---

    public SimpleContainer container() { return container; }
    public ContainerData data() { return data; }

    public org.bukkit.inventory.ItemStack getInput(int idx) {
        int slotIndex = (idx < 3) ? (INPUT_START + idx) : (INPUT_ROW1 + (idx - 3));
        return CraftItemStack.asBukkitCopy(this.slots.get(slotIndex).getItem());
    }

    public void setOutput(org.bukkit.inventory.ItemStack bukkitStack) {
        container.setItem(OUTPUT, CraftItemStack.asNMSCopy(bukkitStack));
    }

    public void clearIngredients() {
        for (int idx : new int[]{INPUT_START, INPUT_START + 1, INPUT_START + 2,
                                  INPUT_ROW1, INPUT_ROW1 + 1, INPUT_ROW1 + 2}) {
            container.setItem(idx, ItemStack.EMPTY);
        }
    }

    public void clearOutput() {
        container.setItem(OUTPUT, ItemStack.EMPTY);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/gui/CookingPotMenu.java
git commit -m "feat: add CookingPotMenu — 9x6 chest-based cooking pot GUI with 6 ingredient slots"
```

---

### Task 8: CookingPotGuiManager

**Files:**
- Create: `src/main/java/org/coffeepop/manyIdea/cooking/gui/CookingPotGuiManager.java`

- [ ] **Step 1: Write CookingPotGuiManager class**

Opens the GUI, tracks open menus per location, sends open packet with `GENERIC_9x6`, monitors close via polling runnable. Provides `updateProgress()` for the tick task.

```java
package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Manages opening / closing / progress-sync for the cooking pot GUI.
 */
public final class CookingPotGuiManager {

    private final Map<Location, CookingPotMenu> openMenus = new HashMap<>();
    private final List<PotRecipe> recipes;

    public CookingPotGuiManager(List<PotRecipe> recipes) {
        this.recipes = recipes;
    }

    /**
     * Open the GUI for a player at a specific pot location.
     */
    public CookingPotMenu open(Player player, Location potLoc,
                               Predicate<org.bukkit.inventory.ItemStack> ingredientFilter,
                               BooleanSupplier isDone,
                               Runnable onOutputTaken,
                               BooleanSupplier isCooking,
                               Runnable onRecipeMatch,
                               int initialProgress, int cookTime) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        SimpleContainer container = new SimpleContainer(CookingPotMenu.CONTAINER_SIZE);

        int syncId = nmsPlayer.nextContainerCounter();
        CookingPotMenu menu = new CookingPotMenu(syncId, nmsPlayer.getInventory(),
            container, nmsPlayer, recipes, ingredientFilter, isDone, onOutputTaken,
            isCooking, onRecipeMatch, initialProgress, cookTime);

        nmsPlayer.containerMenu = menu;
        nmsPlayer.connection.send(new ClientboundOpenScreenPacket(
            syncId, MenuType.GENERIC_9x6, Component.literal("烹饪锅")));
        nmsPlayer.initMenu(menu);

        openMenus.put(potLoc, menu);

        // Polling for close detection
        new BukkitRunnable() {
            @Override
            public void run() {
                if (nmsPlayer.containerMenu != menu) {
                    openMenus.remove(potLoc);
                    cancel();
                }
            }
        }.runTaskTimer(JavaPlugin.getPlugin(
            org.coffeepop.manyIdea.ManyIdea.class), 10L, 10L);

        return menu;
    }

    public void updateProgress(Location potLoc, int cookTime, int progress) {
        CookingPotMenu menu = openMenus.get(potLoc);
        if (menu == null) return;

        menu.data().set(2, progress);
        menu.data().set(3, cookTime);

        ServerPlayer nmsPlayer = ((CraftPlayer) menu.getBukkitView().getPlayer()).getHandle();
        nmsPlayer.connection.send(new ClientboundContainerSetDataPacket(
            menu.containerId, 2, progress));
        nmsPlayer.connection.send(new ClientboundContainerSetDataPacket(
            menu.containerId, 3, cookTime));
    }

    public CookingPotMenu getMenu(Location potLoc) {
        return openMenus.get(potLoc);
    }

    public void close(Location potLoc) {
        CookingPotMenu menu = openMenus.remove(potLoc);
        if (menu != null) {
            menu.getBukkitView().close();
        }
    }

    public boolean isOpen(Location potLoc) {
        return openMenus.containsKey(potLoc);
    }

    public Map<Location, CookingPotMenu> getOpenMenus() {
        return openMenus;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/gui/CookingPotGuiManager.java
git commit -m "feat: add CookingPotGuiManager — GUI lifecycle + progress sync"
```

---

### Task 9: Modify CookingPotListener

**Files:**
- Modify: `src/main/java/org/coffeepop/manyIdea/cooking/CookingPotListener.java`

- [ ] **Step 1: Replace recipe system**

Replace the single-ingredient `RECIPES` map with `List<PotRecipe>`. Convert existing recipes to the new format.

Delete lines 47-73 (the `RECIPES` static map) and the related `itemToId`, `handleInputPlaced`, `buildItem` methods. Replace with:

```java
import org.coffeepop.manyIdea.cooking.gui.CookingPotGuiManager;
import org.coffeepop.manyIdea.cooking.gui.CookingPotMenu;
import org.coffeepop.manyIdea.cooking.gui.PotRecipe;

// Replace the RECIPES map (old lines 47-73) with:
private static final List<PotRecipe> RECIPES = List.of(
    new PotRecipe(Set.of(Material.BEEF), new ItemStack(Material.COOKED_BEEF), COOK_TIME),
    new PotRecipe(Set.of(Material.CHICKEN), new ItemStack(Material.COOKED_CHICKEN), COOK_TIME),
    new PotRecipe(Set.of(Material.PORKCHOP), new ItemStack(Material.COOKED_PORKCHOP), COOK_TIME),
    new PotRecipe(Set.of(Material.MUTTON), new ItemStack(Material.COOKED_MUTTON), COOK_TIME),
    new PotRecipe(Set.of(Material.RABBIT), new ItemStack(Material.COOKED_RABBIT), COOK_TIME),
    new PotRecipe(Set.of(Material.COD), new ItemStack(Material.COOKED_COD), COOK_TIME),
    new PotRecipe(Set.of(Material.SALMON), new ItemStack(Material.COOKED_SALMON), COOK_TIME),
    new PotRecipe(Set.of(Material.POTATO), new ItemStack(Material.BAKED_POTATO), COOK_TIME)
);
```

- [ ] **Step 2: Replace menu handle references**

Replace all `CookingPotMenuHandle` usage with `CookingPotGuiManager` + `CookingPotMenu`.

In field declarations (around line 76-77):

```java
private final CookingPotGuiManager guiManager = new CookingPotGuiManager(RECIPES);
```

Replace `Map<Location, CookingPotMenuHandle> menuHandles` with just using `guiManager.getOpenMenus()`.

- [ ] **Step 3: Rewrite `onUse` (right-click handler)**

Replace lines 90-141 (`onUse` method) with:

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onUse(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;

    Block block = event.getClickedBlock();
    if (!isPot(block)) return;

    if (!hasHeatSource(block)) {
        block.getLocation().getWorld().playSound(
            block.getLocation().toCenterLocation(),
            Sound.BLOCK_FIRE_EXTINGUISH,
            SoundCategory.BLOCKS, 0.5f, 1.0f);
        return;
    }

    event.setCancelled(true);
    Player player = event.getPlayer();
    Location loc = block.getLocation().toBlockLocation();

    if (guiManager.isOpen(loc)) {
        player.sendMessage(ChatColor.GRAY + "有人在用这口锅...");
        return;
    }

    PotData existingData = pots.get(loc);
    long currentTick = System.currentTimeMillis() / 50;
    boolean isDone = existingData != null && existingData.isDone(currentTick);
    boolean isCooking = existingData != null && !isDone;
    int progCurrent = existingData != null
        ? (int) Math.min(COOK_TIME, currentTick - existingData.startTick()) : 0;

    Predicate<ItemStack> ingredientFilter = stack -> {
        Material mat = stack.getType();
        for (PotRecipe r : RECIPES) {
            if (r.ingredients().contains(mat)) return true;
        }
        return false;
    };

    CookingPotMenu menu = guiManager.open(
        player, loc,
        ingredientFilter,
        () -> isDone,
        () -> handleOutputTaken(loc),
        () -> isCooking,
        () -> startCooking(loc),
        progCurrent, COOK_TIME
    );

    // Restore existing data into GUI
    if (existingData != null) {
        menu.setOutput(existingData.result().clone());
    }
}
```

- [ ] **Step 4: Add `startCooking` method**

```java
private void startCooking(Location loc) {
    if (pots.containsKey(loc)) return;

    CookingPotMenu menu = guiManager.getMenu(loc);
    if (menu == null) return;

    // Collect 6 ingredients
    List<ItemStack> inputs = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
        org.bukkit.inventory.ItemStack s = menu.getInput(i);
        if (s != null && !s.getType().isAir()) {
            inputs.add(s.clone());
        }
    }
    if (inputs.isEmpty()) return;

    // Find matching recipe
    Set<Material> placed = new HashSet<>();
    for (var s : inputs) placed.add(s.getType());

    for (PotRecipe recipe : RECIPES) {
        if (placed.equals(recipe.ingredients())) {
            ItemStack result = recipe.result().clone();
            long startTick = System.currentTimeMillis() / 50;

            Block block = loc.getBlock();
            ItemStack displayOne = inputs.get(0).clone();
            displayOne.setAmount(1);
            ItemDisplay displayItem = DisplayItemUtil.spawn(
                block.getLocation().add(0.5, 0.65, 0.5), displayOne, true);

            pots.put(loc, new PotData(inputs.get(0), result, startTick, recipe.cookTime(), displayItem));

            loc.getWorld().playSound(loc.toCenterLocation(),
                Sound.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.5f, 0.8f);
            return;
        }
    }
}
```

- [ ] **Step 5: Update tick task to use `guiManager`**

In `PotTickTask.run()` (lines 208-270), replace `menuHandles` references with `guiManager.getOpenMenus()` and `CookingPotMenuHandle` with `CookingPotMenu`:

```java
// Replace "menuHandles" with "guiManager.getOpenMenus()"
// Replace "handle.updateProgress(d.cookTime(), elapsed)"
//   with "guiManager.updateProgress(loc, d.cookTime(), elapsed)"
// Replace "handle.setOutput(d.result().clone())"
//   with "CookingPotMenu menu = guiManager.getMenu(loc); if (menu != null) menu.setOutput(...)"
```

Key spots (replace inline):

Line 249-252 → 
```java
CookingPotMenu menu = guiManager.getMenu(loc);
if (menu != null) {
    menu.setOutput(d.result().clone());
    guiManager.updateProgress(loc, d.cookTime(), d.cookTime());
}
```

Line 262-266 →
```java
CookingPotMenu menu = guiManager.getMenu(loc);
if (menu != null) {
    int elapsed = (int)(tick - d.startTick());
    guiManager.updateProgress(loc, d.cookTime(), elapsed);
}
```

- [ ] **Step 6: Update `onBreak`, `handleClose`, `resetPot`**

Remove `menuHandles` usage. Replace `handleClose` to just remove from pots (GUI close handling is done by the polling runnable in GuiManager). In `onBreak`, replace `menuHandles.remove(loc)` with `guiManager.close(loc)`.

- [ ] **Step 7: Remove `itemToId`, `handleInputPlaced`, `buildItem` methods** (no longer needed).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/CookingPotListener.java
git commit -m "refactor: replace CookingPotMenuHandle with CookingPotGuiManager — 6-ingredient real-time cooking"
```

---

### Task 10: Modify CookingPersistenceManager

**Files:**
- Modify: `src/main/java/org/coffeepop/manyIdea/cooking/CookingPersistenceManager.java`

- [ ] **Step 1: Add version field to save**

In `save()` (line 35-49), add version marker after creating the YAML:

```java
yaml.set("version", 2);
// ... before yaml.save(file);
```

- [ ] **Step 2: Skip old-format pot entries on load**

In `loadPots()` (line 52-79), check version first:

```java
public Map<Location, CookingPotListener.PotData> loadPots(CookingPotListener potListener) {
    YamlConfiguration yaml = loadYaml();
    Map<Location, CookingPotListener.PotData> result = new LinkedHashMap<>();

    int version = yaml.getInt("version", 1);
    if (version < 2) {
        plugin.getLogger().warning("cooking_state.yml version < 2, skipping pot data restore.");
        return result;
    }
    // ... rest unchanged
```

- [ ] **Step 3: Serialize 6 ingredients in `serializePot`**

Replace `serializePot` (line 149-154) to store the 6 ingredient list:

```java
private void serializePot(ConfigurationSection sec, Location loc, CookingPotListener.PotData d) {
    sec.set("input", d.input());
    sec.set("result", d.result());
    sec.set("cook_time", d.cookTime());
    long elapsed = System.currentTimeMillis() / 50 - d.startTick();
    sec.set("elapsed", Math.max(0, elapsed));
}
```

(PotData still stores a single "display" input — the 6 ingredients are in the container slots, not persisted separately. For now, keep the single-input serialization — matching the current behavior. Multi-ingredient persistence is a future enhancement when recipes have >1 ingredient.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/coffeepop/manyIdea/cooking/CookingPersistenceManager.java
git commit -m "refactor: add version field to cooking_state.yml, skip old-format pot restore"
```

---

### Task 11: Delete CookingPotMenuHandle

**Files:**
- Delete: `src/main/java/org/coffeepop/manyIdea/cooking/CookingPotMenuHandle.java`

- [ ] **Step 1: Delete the file**

```bash
git rm src/main/java/org/coffeepop/manyIdea/cooking/CookingPotMenuHandle.java
```

- [ ] **Step 2: Commit**

```bash
git commit -m "refactor: remove CookingPotMenuHandle, replaced by CookingPotMenu + CookingPotGuiManager"
```

---

### Task 12: Update imports in ManyIdea.java

**Files:**
- Modify: `src/main/java/org/coffeepop/manyIdea/ManyIdea.java`

- [ ] **Step 1: Remove `CookingPotMenuHandle` import** (line not present, but verify no references remain)

Check that `ManyIdea.java` has no references to `CookingPotMenuHandle` (it shouldn't — the handle was instantiated inside `CookingPotListener`).

- [ ] **Step 2: Commit if changed**

No changes expected — skip commit if clean.

---

### Verification

- [ ] **Step 1: Build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL, no compilation errors.

- [ ] **Step 2: Run server and test**

```bash
./gradlew runServer
```

Test checklist:
1. Place cooking pot block, right-click → GUI opens with glass-decorated 9×6 layout
2. Put beef in any ingredient slot → cooking starts, arrow animates
3. Cooking completes → output slot fills, bell sound plays
4. Take output → ingredients clear, pot resets
5. Put multiple different ingredients → cooking starts only when they match a recipe
6. Close GUI mid-cook, reopen → progress continues from where it left off
7. Break pot block → GUI closes, items drop
8. Server restart → cooking state persists and resumes
