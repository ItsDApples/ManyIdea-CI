package org.coffeepop.manyIdea.cooking.gui;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
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
     * Fill all 27 top-container slots: functional ones stay empty,
     * non-functional get glass pane. Progress slot gets a barrier.
     */
    public static void decorate(SimpleContainer container) {
        for (int i = 0; i < 27; i++) {
            if (!FUNCTIONAL_INDICES.contains(i)) {
                container.setItem(i, CraftItemStack.asNMSCopy(GLASS));
            }
        }
        container.setItem(6, CraftItemStack.asNMSCopy(
            new org.bukkit.inventory.ItemStack(Material.BARRIER)));
    }
}
