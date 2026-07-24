package org.coffeepop.manyIdea.feast;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import org.coffeepop.manyIdea.framework.AchievementManager;

import java.util.Map;

/**
 * 宴席方块交互 — 对标 FD FeastBlock 的 useWithoutItem。
 * <p>
 * 右键取走一份食物，吃光后方块消失。
 */
public final class FeastBlockListener implements Listener {

    private final FeastBlockManager manager;
    private final AchievementManager achievements;

    // 每个宴席方块对应的掉落物品 (feast_key → food_item)
    private static final Map<Key, String> FEAST_DROPS = Map.ofEntries(
        Map.entry(Key.of("manyidea", "roast_chicken_block"),           "manyidea:chicken_cuts"),
        Map.entry(Key.of("manyidea", "honey_glazed_ham_block"),        "manyidea:cooked_bacon"),
        Map.entry(Key.of("manyidea", "stuffed_pumpkin_block"),         "manyidea:pumpkin_slice"),
        Map.entry(Key.of("manyidea", "stargazy_pie_block"),            "manyidea:stargazy_pie_slice"),
        Map.entry(Key.of("manyidea", "quiche_lorraine_block"),         "manyidea:quiche_lorraine_slice"),
        Map.entry(Key.of("manyidea", "raw_cheese_wheel_block"),        "manyidea:cheese_wheel_slice"),
        Map.entry(Key.of("manyidea", "cheese_wheel_block"),            "manyidea:cheese_wheel_slice"),
        Map.entry(Key.of("manyidea", "paper_wrapped_fish_block"),      "manyidea:bowl_of_paper_wrapped_fish"),
        Map.entry(Key.of("manyidea", "bobo_chicken_block"),            "manyidea:chicken_bobo_chicken"),
        Map.entry(Key.of("manyidea", "spring_roll_medley_block"),      "manyidea:spring_roll"),
        Map.entry(Key.of("manyidea", "plate_of_fried_dumpling_block"), "manyidea:bowl_of_fried_dumpling"),
        Map.entry(Key.of("manyidea", "sweet_rice_block"),              "manyidea:bowl_of_sweet_rice"),
        Map.entry(Key.of("manyidea", "shepherds_pie_block"),          "manyidea:shepherds_pie"),
        Map.entry(Key.of("manyidea", "gleaming_salad_block"),         "manyidea:gleaming_salad"),
        Map.entry(Key.of("manyidea", "rice_roll_medley_block"),       "manyidea:kelp_roll_slice")
    );

    // 每个宴席初始份数
    private static final int DEFAULT_SERVINGS = 4;

    public FeastBlockListener(FeastBlockManager manager, AchievementManager achievements) {
        this.manager = manager;
        this.achievements = achievements;
    }

    // 放置时初始化
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Key feastKey = getFeastBlockKey(block);
        if (feastKey == null) return;
        manager.place(block, feastKey, DEFAULT_SERVINGS);

        // 芝士发酵成就：放置生芝士轮
        if (feastKey.equals(Key.of("manyidea", "raw_cheese_wheel_block"))) {
            achievements.getCheese().grant(event.getPlayer());
        }
    }

    // 右键取餐
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        Key feastKey = getFeastBlockKey(block);
        if (feastKey == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        int remaining = manager.takeServing(block);
        if (remaining == 0 && manager.getServings(block) == 0) {
            // 最后一份被取走了，破坏方块
            dropServing(feastKey, block, player);
            block.setType(Material.AIR);
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.8f, 1.0f);
        } else if (remaining >= 0) {
            // 还有剩余
            dropServing(feastKey, block, player);
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.6f, 0.8f);
        }

        // 成就
        achievements.getEatFeast().grant(player);
    }

    // 破坏掉存储
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (getFeastBlockKey(block) == null) return;
        manager.remove(block);
    }

    private void dropServing(Key feastKey, Block block, Player player) {
        String foodId = FEAST_DROPS.get(feastKey);
        if (foodId == null) return;

        BukkitItemDefinition def = CraftEngineItems.byId(foodId);
        if (def != null) {
            ItemStack food = def.buildBukkitItem(player);
            block.getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5), food);
        }
    }

    private Key getFeastBlockKey(Block block) {
        if (!CraftEngineBlocks.isCustomBlock(block)) return null;
        var s = CraftEngineBlocks.getCustomBlockState(block);
        if (s == null || s.owner() == null || s.owner().value() == null) return null;
        Key id = s.owner().value().id();
        if (!id.namespace().equals("manyidea")) return null;
        String val = id.value();
        if (val.contains("_block") && !val.equals("cutting_board")) {
            return id;
        }
        return null;
    }
}
