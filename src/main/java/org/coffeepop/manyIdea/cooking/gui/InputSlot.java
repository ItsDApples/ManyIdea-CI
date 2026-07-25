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
