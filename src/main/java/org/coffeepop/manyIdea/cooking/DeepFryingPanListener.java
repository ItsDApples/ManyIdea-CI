package org.coffeepop.manyIdea.cooking;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
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
import org.bukkit.scheduler.BukkitRunnable;

import org.coffeepop.manyIdea.framework.AchievementManager;
import org.coffeepop.manyIdea.util.DisplayItemUtil;

import java.util.*;

/**
 * 油炸锅交互 - 对标 CasualnessDelight DeepFryingPanEntity
 * 右键放原料 → 自炸 → 空手取成品，需要下方热源。
 */
public final class DeepFryingPanListener implements Listener {

    private static final Key PAN_KEY = Key.of("manyidea", "deep_frying_pan");
    private static final int COOK_TIME = 180; // 9 秒

    record FryData(ItemStack input, ItemStack result, long startTick, int cookTime, ItemDisplay displayItem) {
        boolean isDone(long tick) { return tick - startTick >= cookTime; }
    }

    private static final Map<String, String> RECIPES = new LinkedHashMap<>();
    static {
        RECIPES.put("manyidea:raw_fried_dumpling", "manyidea:fried_dumpling");
        RECIPES.put("manyidea:raw_spring_roll", "manyidea:spring_roll");
        RECIPES.put("manyidea:potato_slice", "manyidea:potato_chip");
        RECIPES.put("minecraft:chicken", "manyidea:fried_chicken_chip");
        RECIPES.put("minecraft:cod", "manyidea:fried_fish");
        RECIPES.put("minecraft:porkchop", "manyidea:tonkatsu");
        RECIPES.put("manyidea:raw_donkey_meat", "manyidea:cooked_donkey_meat");
    }

    private final Map<Location, FryData> pans = new HashMap<>();
    private final Set<Location> completionsHandled = new HashSet<>();
    private final Random random = new Random();
    private final JavaPlugin plugin;
    private final AchievementManager achievements;

    public DeepFryingPanListener(JavaPlugin plugin, AchievementManager achievements) {
        this.plugin = plugin;
        this.achievements = achievements;
        new FryTickTask().runTaskTimer(plugin, 20L, 10L);
    }

    // ---- 右键交互 ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || !isPan(block)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Location loc = block.getLocation().toBlockLocation();

        // 检查热源
        if (!hasHeatSource(block)) {
            loc.getWorld().playSound(loc.toCenterLocation(), Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 0.5f, 1.0f);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        FryData data = pans.get(loc);
        long tick = System.currentTimeMillis() / 50;

        // 空手 → 取成品
        if (hand.getType().isAir()) {
            if (data != null && data.isDone(tick)) {
                takeResult(player, loc, data);
            }
            return;
        }

        // 已在烹饪中 → 不允许放入新原料
        if (data != null) return;

        // 有物品在手 → 尝试放入原料
        String inputId = itemToId(hand);
        String outputId = RECIPES.get(inputId);
        if (outputId == null) return;

        // 消耗 1 个原料
        ItemStack input = hand.clone();
        input.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);

        // 构建产物
        ItemStack result = buildItem(outputId, player);
        if (result == null || result.getType().isAir()) {
            player.getInventory().addItem(input).forEach((k, v) ->
                player.getWorld().dropItemNaturally(player.getLocation(), v));
            return;
        }

        // 生成悬浮展示物品
        Location displayLoc = block.getLocation().add(0.5, 0.65, 0.5);
        ItemDisplay displayItem = DisplayItemUtil.spawn(displayLoc, input.clone(), false);

        pans.put(loc, new FryData(input, result, System.currentTimeMillis() / 50, COOK_TIME, displayItem));

        loc.getWorld().playSound(loc.toCenterLocation(), Sound.BLOCK_BREWING_STAND_BREW,
            SoundCategory.BLOCKS, 0.5f, 0.8f);
    }

    // ---- 取出成品 ----

    private void takeResult(Player player, Location loc, FryData data) {
        player.getInventory().addItem(data.result().clone()).forEach((k, v) ->
            player.getWorld().dropItemNaturally(player.getLocation(), v));
        DisplayItemUtil.remove(data.displayItem());
        pans.remove(loc);
        completionsHandled.remove(loc);
        loc.getWorld().playSound(loc.toCenterLocation(), Sound.ENTITY_ITEM_PICKUP,
            SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    // ---- 方块破坏 ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isPan(block)) return;

        Location loc = block.getLocation().toBlockLocation();
        FryData data = pans.remove(loc);
        completionsHandled.remove(loc);
        if (data != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.7, 0.5), data.input());
            DisplayItemUtil.remove(data.displayItem());
        }
    }

    // ---- Tick 任务 ----

    private class FryTickTask extends BukkitRunnable {
        @Override
        public void run() {
            long tick = System.currentTimeMillis() / 50;
            for (var entry : new ArrayList<>(pans.entrySet())) {
                FryData d = entry.getValue();
                Location loc = entry.getKey();
                World w = loc.getWorld();
                if (w == null) continue;

                Block block = w.getBlockAt(loc);

                if (d.isDone(tick)) {
                    // 烹饪完成（只触发一次）
                    if (completionsHandled.add(loc)) {
                        w.playSound(loc.toCenterLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                            SoundCategory.BLOCKS, 0.5f, 1.5f);
                        spawnSteam(block);

                        // 成就
                        achievements.getFry().grant(getNearbyPlayer(loc, w));

                        // 更新展示物品 → 成品
                        DisplayItemUtil.remove(d.displayItem());
                        Location displayLoc = block.getLocation().add(0.5, 0.65, 0.5);
                        ItemDisplay doneItem = DisplayItemUtil.spawn(displayLoc, d.result().clone(), false);
                        pans.put(loc, new FryData(d.input(), d.result(), d.startTick(), d.cookTime(), doneItem));
                    }
                } else {
                    // 烹饪中 → 蒸汽粒子
                    float progress = (float) (tick - d.startTick()) / d.cookTime();
                    if (random.nextFloat() < 0.4 + progress * 0.3) {
                        spawnSteam(block);
                    }
                }
            }
        }
    }

    // ---- 热源检查 ----

    private boolean hasHeatSource(Block block) {
        Block below = block.getRelative(0, -1, 0);
        Material type = below.getType();
        if (type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE
            || type == Material.FIRE || type == Material.SOUL_FIRE
            || type == Material.LAVA || type == Material.MAGMA_BLOCK) {
            return true;
        }
        // Check for custom stove
        if (CraftEngineBlocks.isCustomBlock(below)) {
            var s = CraftEngineBlocks.getCustomBlockState(below);
            if (s != null && s.owner() != null && s.owner().value() != null) {
                return s.owner().value().id().equals(Key.of("manyidea", "stove"));
            }
        }
        return false;
    }

    // ---- 粒子 ----

    private void spawnSteam(Block block) {
        Location loc = block.getLocation().add(0.5, 0.75, 0.5);
        block.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
            loc, 2, 0.15, 0.1, 0.15, 0.02);
    }

    // ---- 工具方法 ----

    private boolean isPan(Block block) {
        if (!CraftEngineBlocks.isCustomBlock(block)) return false;
        var s = CraftEngineBlocks.getCustomBlockState(block);
        return s != null && s.owner() != null && s.owner().value() != null
            && PAN_KEY.equals(s.owner().value().id());
    }

    private Player getNearbyPlayer(Location loc, World w) {
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(loc.toCenterLocation()) < 25) {
                return p;
            }
        }
        return null;
    }

    private String itemToId(ItemStack item) {
        if (CraftEngineItems.isCustomItem(item)) {
            Key id = CraftEngineItems.getCustomItemId(item);
            return id != null ? id.toString() : null;
        }
        return "minecraft:" + item.getType().name().toLowerCase(Locale.ROOT);
    }

    private ItemStack buildItem(String id, Player player) {
        Key key = Key.of(id);
        if (CraftEngineItems.byId(key) != null) {
            BukkitItemDefinition def = CraftEngineItems.byId(key);
            return def.buildBukkitItem(player);
        }
        Material mat = Material.matchMaterial(id.substring("minecraft:".length()));
        return mat != null ? new ItemStack(mat) : null;
    }

    // ---- 持久化 ----

    public Map<Location, FryData> getPanMap() {
        return pans;
    }

    public void restoreFromLoaded(Map<Location, FryData> loaded) {
        long tick = System.currentTimeMillis() / 50;
        for (var entry : loaded.entrySet()) {
            Location loc = entry.getKey();
            FryData d = entry.getValue();
            Block block = loc.getBlock();
            if (!isPan(block)) continue;

            boolean done = d.isDone(tick);
            if (done) completionsHandled.add(loc);

            ItemStack displayStack = done ? d.result().clone() : d.input().clone();
            ItemDisplay displayItem = DisplayItemUtil.spawn(
                block.getLocation().add(0.5, 0.65, 0.5), displayStack, false);

            pans.put(loc, new FryData(d.input(), d.result(), d.startTick(), d.cookTime(), displayItem));
        }
    }
}
