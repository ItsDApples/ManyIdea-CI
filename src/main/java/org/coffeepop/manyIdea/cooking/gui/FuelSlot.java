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
