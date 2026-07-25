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

        var bukkitPlayer = menu.getBukkitView().getPlayer();
        if (bukkitPlayer == null) return;
        ServerPlayer nmsPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
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
