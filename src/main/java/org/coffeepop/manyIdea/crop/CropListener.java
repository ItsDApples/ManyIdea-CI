package org.coffeepop.manyIdea.crop;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.*;
import org.bukkit.block.Block;
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

import java.util.*;

/**
 * 作物种植/收获监听器。
 * 种植：种子右键农田。骨粉：CE crop_block 自动催熟。
 * 收获：仅成熟作物（age=4）右键收获。
 */
public final class CropListener implements Listener {

    private static final Map<Key, CropInfo> CROPS = new LinkedHashMap<>();

    static {
        CROPS.put(Key.of("manyidea", "tomato_crop"), new CropInfo(
            "manyidea:tomato", "manyidea:tomato_seeds", 2, 4));
        CROPS.put(Key.of("manyidea", "cabbage_crop"), new CropInfo(
            "manyidea:cabbage", "manyidea:cabbage_seeds", 1, 4));
        CROPS.put(Key.of("manyidea", "onion_crop"), new CropInfo(
            "manyidea:onion", "manyidea:onion_seeds", 1, 4));
        CROPS.put(Key.of("manyidea", "blueberry_bush"), new CropInfo(
            "manyidea:blueberry", "manyidea:blueberry_bush", 2, 2));
        CROPS.put(Key.of("manyidea", "cranberry_bush"), new CropInfo(
            "manyidea:cranberry", "manyidea:cranberry_bush", 2, 4));
        CROPS.put(Key.of("manyidea", "pineapple_crop"), new CropInfo(
            "manyidea:pineapple", "manyidea:pineapple_sapling", 1, 5));
    }

    private final JavaPlugin plugin;

    public CropListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 种子右键农田 → 种植 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FARMLAND) return;

        Block above = block.getRelative(0, 1, 0);
        if (!above.getType().isAir()) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!CraftEngineItems.isCustomItem(hand)) return;

        Key cropKey = seedToCrop(CraftEngineItems.getCustomItemId(hand));
        if (cropKey == null) return;

        event.setCancelled(true);
        CraftEngineBlocks.place(above.getLocation(), cropKey, false);
        hand.setAmount(hand.getAmount() - 1);
        above.getWorld().playSound(above.getLocation(), Sound.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /** 右键收获 — 仅成熟作物 + 非骨粉时触发 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || !CraftEngineBlocks.isCustomBlock(block)) return;

        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null || state.owner() == null || state.owner().value() == null) return;

        Key blockId = state.owner().value().id();
        CropInfo info = CROPS.get(blockId);
        if (info == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // 骨粉不拦截 → 让 CE crop_block 处理催熟
        if (hand.getType() == Material.BONE_MEAL) return;

        // 仅成熟作物可收获
        int age = getCropAge(state);
        if (age < info.maxAge()) return;

        event.setCancelled(true);

        // 掉落果实
        BukkitItemDefinition fruitDef = CraftEngineItems.byId(info.fruitId);
        if (fruitDef != null) {
            ItemStack fruit = fruitDef.buildBukkitItem(player);
            fruit.setAmount(info.fruitCount);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), fruit);
        }
        // 50% 额外种子
        if (Math.random() < 0.5) {
            BukkitItemDefinition seedDef = CraftEngineItems.byId(info.seedId);
            if (seedDef != null) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), seedDef.buildBukkitItem(player));
            }
        }

        block.setType(Material.AIR);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /** 破坏时掉落种子 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!CraftEngineBlocks.isCustomBlock(block)) return;

        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null || state.owner() == null || state.owner().value() == null) return;

        Key blockId = state.owner().value().id();
        CropInfo info = CROPS.get(blockId);
        if (info == null) return;

        BukkitItemDefinition seedDef = CraftEngineItems.byId(info.seedId);
        if (seedDef != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), seedDef.buildBukkitItem(event.getPlayer()));
        }
    }

    /** 从 CE block state 读取 crop_block 的 age 属性 */
    private int getCropAge(ImmutableBlockState state) {
        try {
            var prop = state.getProperty("age");
            if (prop != null) {
                Object val = state.get(prop);
                if (val instanceof Integer i) return i;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private Key seedToCrop(Key seedId) {
        return switch (seedId.toString()) {
            case "manyidea:tomato_seeds" -> Key.of("manyidea", "tomato_crop");
            case "manyidea:cabbage_seeds" -> Key.of("manyidea", "cabbage_crop");
            case "manyidea:onion_seeds" -> Key.of("manyidea", "onion_crop");
            case "manyidea:blueberry_bush" -> Key.of("manyidea", "blueberry_bush");
            case "manyidea:cranberry_bush" -> Key.of("manyidea", "cranberry_bush");
            case "manyidea:pineapple_sapling" -> Key.of("manyidea", "pineapple_crop");
            default -> null;
        };
    }

    record CropInfo(String fruitId, String seedId, int fruitCount, int maxAge) {}
}
