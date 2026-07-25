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
