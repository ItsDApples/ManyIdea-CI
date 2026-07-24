package org.coffeepop.manyIdea.cooking;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

public final class BasinStorageListener implements Listener {
    private static final Key BASIN_KEY = Key.of("manyidea", "basin");
    private static final Key TRAY_KEY = Key.of("manyidea", "tray");

    private final Map<Location, ItemStack> basinItems = new HashMap<>();
    private final Map<Location, ItemStack> trayItems = new HashMap<>();
    private final Map<Location, Item> displayItems = new HashMap<>();

    public BasinStorageListener(JavaPlugin plugin) {}

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Location loc = block.getLocation().toBlockLocation();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // Basin
        if (isKey(block, BASIN_KEY)) {
            event.setCancelled(true);
            handleStorage(block, loc, player, hand, basinItems, 0.7);
            return;
        }
        // Tray
        if (isKey(block, TRAY_KEY)) {
            event.setCancelled(true);
            handleStorage(block, loc, player, hand, trayItems, 0.5);
        }
    }

    private void handleStorage(Block block, Location loc, Player player, ItemStack hand,
                                Map<Location, ItemStack> storage, double displayY) {
        // Empty hand → take item
        if (hand.getType().isAir()) {
            ItemStack stored = storage.remove(loc);
            if (stored != null) {
                player.getInventory().addItem(stored).forEach((k, v) ->
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, displayY, 0.5), v));
                removeDisplay(loc);
                block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.3f, 0.5f);
            }
            return;
        }
        // Already has item → reject
        if (storage.containsKey(loc)) return;

        // Put item
        ItemStack copy = hand.clone();
        copy.setAmount(1);
        storage.put(loc, copy);
        hand.setAmount(hand.getAmount() - 1);

        // Show display
        Location displayLoc = block.getLocation().add(0.5, displayY, 0.5);
        Item display = block.getWorld().dropItem(displayLoc, copy.clone());
        display.setPickupDelay(Integer.MAX_VALUE);
        display.setUnlimitedLifetime(true);
        display.setVelocity(new Vector(0, 0, 0));
        display.setGravity(false);
        display.setInvulnerable(true);
        displayItems.put(loc, display);

        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.5f, 0.8f);
    }

    private void removeDisplay(Location loc) {
        Item display = displayItems.remove(loc);
        if (display != null && display.isValid()) display.remove();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation().toBlockLocation();
        if (isKey(block, BASIN_KEY)) {
            dropStored(block, basinItems.remove(loc), 0.7);
            removeDisplay(loc);
        } else if (isKey(block, TRAY_KEY)) {
            dropStored(block, trayItems.remove(loc), 0.5);
            removeDisplay(loc);
        }
    }

    private void dropStored(Block block, ItemStack stored, double y) {
        if (stored != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, y, 0.5), stored);
        }
    }

    private boolean isKey(Block block, Key key) {
        if (!CraftEngineBlocks.isCustomBlock(block)) return false;
        var s = CraftEngineBlocks.getCustomBlockState(block);
        return s != null && s.owner() != null && s.owner().value() != null
            && key.equals(s.owner().value().id());
    }
}
