package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
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
    static final int INPUT_START  = 2;
    static final int INPUT_ROW1   = 11;
    static final int PROGRESS     = 6;
    static final int OUTPUT       = 7;
    static final int FUEL         = 20;

    private final SimpleContainer container;
    private final ContainerData data;
    private final ServerPlayer player;
    private final BooleanSupplier isCooking;
    private final Runnable onRecipeMatch;

    CookingPotMenu(int syncId, Inventory playerInv, SimpleContainer container,
                   ServerPlayer player, List<PotRecipe> recipes,
                   Predicate<org.bukkit.inventory.ItemStack> ingredientFilter,
                   BooleanSupplier isDone, Runnable onOutputTaken,
                   BooleanSupplier isCooking, Runnable onRecipeMatch,
                   int initialProgress, int cookTime) {
        super(MenuType.GENERIC_9x6, syncId);
        this.container = container;
        this.player = player;
        this.isCooking = isCooking;
        this.onRecipeMatch = onRecipeMatch;

        this.data = new ContainerData() {
            private final int[] values = {1, 1, initialProgress, cookTime};
            @Override public int get(int i) { return values[i]; }
            @Override public void set(int i, int v) { values[i] = v; }
            @Override public int getCount() { return 4; }
        };
        addDataSlots(this.data);

        // Container slots rows 0-2 (indices 0-26)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                int x = 8 + col * 18;
                int y = 18 + row * 18;

                Slot slot;
                if (row == 0 && col == 2)      slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 0 && col == 3)  slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 0 && col == 4)  slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 0 && col == 6)  slot = new ProgressSlot(container, idx, x, y);
                else if (row == 0 && col == 7)  slot = new OutputSlot(container, idx, x, y, isDone, onOutputTaken);
                else if (row == 1 && col == 2)  slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 1 && col == 3)  slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 1 && col == 4)  slot = new InputSlot(container, idx, x, y, ingredientFilter);
                else if (row == 2 && col == 2)  slot = new FuelSlot(container, idx, x, y);
                else slot = new Slot(container, idx, x, y);

                this.addSlot(slot);
            }
        }

        // Player inventory
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 161));

        GuiDecorator.decorate(container);
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container inventory) {
        super.slotsChanged(inventory);
        if (isCooking.getAsBoolean()) return;

        Set<org.bukkit.Material> placed = collectIngredients();
        if (placed.isEmpty()) return;

        onRecipeMatch.run();
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

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack source = slot.getItem().copy();
        if (index == OUTPUT) {
            if (!this.moveItemStackTo(source, 27, 63, true)) return ItemStack.EMPTY;
        } else if (index >= 27) {
            if (!this.moveItemStackTo(source, INPUT_START, INPUT_START + 3, false)
                && !this.moveItemStackTo(source, INPUT_ROW1, INPUT_ROW1 + 3, false))
                return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(source, 27, 63, true)) return ItemStack.EMPTY;
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

    // ---- Public API ----

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
