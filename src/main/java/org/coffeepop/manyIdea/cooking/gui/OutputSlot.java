package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;

import java.util.function.BooleanSupplier;

/**
 * Output slot — locked until cooking completes. Fires callback on take.
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
