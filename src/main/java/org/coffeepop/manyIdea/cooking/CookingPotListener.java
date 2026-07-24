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
 * 烹饪锅交互 - 对标 FD CookingPotBlockEntity。
 * 使用 NMS AbstractContainerMenu 实现真正的容器 GUI，支持：
 * - 输入槽只接受配方物品
 * - 输出槽只允许取出
 * - shift+click 安全路由
 * - ContainerData 进度同步
 * - 关闭/破坏时自动清理
 */
public final class CookingPotListener implements Listener {

    private static final Key POT_KEY = Key.of("manyidea", "cooking_pot");
    static final int COOK_TIME = 200; // 10 秒

    public record PotData(ItemStack input, ItemStack result, long startTick, int cookTime,
                          ItemDisplay displayItem) {
        public boolean isDone(long tick) { return tick - startTick >= cookTime; }
    }

    private static final Map<String, String> RECIPES = new LinkedHashMap<>();
    static {
        RECIPES.put("minecraft:beef", "minecraft:cooked_beef");
        RECIPES.put("minecraft:chicken", "minecraft:cooked_chicken");
        RECIPES.put("minecraft:porkchop", "minecraft:cooked_porkchop");
        RECIPES.put("minecraft:mutton", "minecraft:cooked_mutton");
        RECIPES.put("minecraft:rabbit", "minecraft:cooked_rabbit");
        RECIPES.put("minecraft:cod", "minecraft:cooked_cod");
        RECIPES.put("minecraft:salmon", "minecraft:cooked_salmon");
        RECIPES.put("minecraft:potato", "minecraft:baked_potato");
        RECIPES.put("manyidea:raw_beef_skewer", "manyidea:grilled_beef_skewer");
        RECIPES.put("manyidea:raw_chicken_skewer", "manyidea:grilled_chicken_skewer");
        RECIPES.put("manyidea:raw_cod_skewer", "manyidea:grilled_cod_skewer");
        RECIPES.put("manyidea:raw_salmon_skewer", "manyidea:grilled_salmon_skewer");
        RECIPES.put("manyidea:raw_lamb_skewer", "manyidea:grilled_lamb_skewer");
        RECIPES.put("manyidea:raw_rabbit_skewer", "manyidea:grilled_rabbit_skewer");
        RECIPES.put("manyidea:raw_pork_sausage_skewer", "manyidea:grilled_pork_sausage_skewer");
        RECIPES.put("manyidea:raw_mushroom_skewer", "manyidea:grilled_mushroom_skewer");
        RECIPES.put("manyidea:raw_potato_skewer", "manyidea:grilled_potato_skewer");
        RECIPES.put("manyidea:raw_vegetable_skewer", "manyidea:grilled_vegetable_skewer");
        RECIPES.put("manyidea:raw_fried_dumpling", "manyidea:fried_dumpling");
        RECIPES.put("manyidea:raw_spring_roll", "manyidea:spring_roll");
        RECIPES.put("manyidea:raw_donkey_meat", "manyidea:cooked_donkey_meat");
        RECIPES.put("manyidea:potato_slice", "manyidea:potato_chip");
        RECIPES.put("manyidea:gluten", "manyidea:roast_gluten");
        RECIPES.put("manyidea:raw_gluten", "manyidea:gluten");
    }

    private final Map<Location, PotData> pots = new HashMap<>();
    private final Map<Location, CookingPotMenuHandle> menuHandles = new HashMap<>();
    private final Set<Location> completionsHandled = new HashSet<>();
    private final Random random = new Random();
    private final AchievementManager achievements;
    private final JavaPlugin plugin;

    public CookingPotListener(JavaPlugin plugin, AchievementManager achievements) {
        this.plugin = plugin;
        this.achievements = achievements;
        new PotTickTask().runTaskTimer(plugin, 20L, 10L);
    }

    // ---- 右键打开 GUI ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (!isPot(block)) return;

        if (!hasHeatSource(block)) {
            block.getLocation().getWorld().playSound(
                block.getLocation().toCenterLocation(),
                Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 0.5f, 1.0f);
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        Location loc = block.getLocation().toBlockLocation();

        // 检查是否已有其他玩家打开此 GUI
        if (menuHandles.containsKey(loc)) {
            player.sendMessage(ChatColor.GRAY + "有人在用这口锅...");
            return;
        }

        PotData existingData = pots.get(loc);
        long currentTick = System.currentTimeMillis() / 50;
        boolean isDone = existingData != null && existingData.isDone(currentTick);
        int progCookTime = COOK_TIME;
        int progCurrent = existingData != null ? (int)Math.min(COOK_TIME, currentTick - existingData.startTick()) : 0;

        CookingPotMenuHandle handle = CookingPotMenuHandle.open(
            player,
            bukkitStack -> RECIPES.containsKey(itemToId(bukkitStack)),
            () -> isDone,
            () -> handleClose(loc, player),
            () -> handleOutputTaken(loc),
            progCookTime,
            progCurrent
        );

        menuHandles.put(loc, handle);

        // 恢复已有数据到容器
        if (existingData != null) {
            handle.setInput(existingData.input().clone());
            if (existingData.isDone(System.currentTimeMillis() / 50)) {
                handle.setOutput(existingData.result().clone());
            }
        }
    }

    // ---- NMS 回调 ----

    private void handleClose(Location loc, Player player) {
        CookingPotMenuHandle handle = menuHandles.remove(loc);
        if (handle == null) return;

        PotData data = pots.get(loc);
        long tick = System.currentTimeMillis() / 50;

        if (data == null) return;

        if (!data.isDone(tick)) {
            // 未完成 → 掉落输入物品 + 清理展示
            org.bukkit.inventory.ItemStack input = data.input().clone();
            if (input != null && !input.getType().isAir()) {
                player.getWorld().dropItemNaturally(loc.toCenterLocation().add(0, 0.7, 0), input);
            }
            DisplayItemUtil.remove(data.displayItem());
            pots.remove(loc);
            completionsHandled.remove(loc);
        }
    }

    private void handleOutputTaken(Location loc) {
        resetPot(loc);
    }

    // ---- 方块破坏 ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isPot(block)) return;

        Location loc = block.getLocation().toBlockLocation();

        // 强制关闭打开此 GUI 的玩家
        CookingPotMenuHandle handle = menuHandles.remove(loc);
        if (handle != null) {
            handle.close();
        }

        // 掉落物品 + 清理
        PotData data = pots.remove(loc);
        completionsHandled.remove(loc);
        if (data != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.7, 0.5), data.input());
            DisplayItemUtil.remove(data.displayItem());
        }
    }

    // ---- 重置 ----

    private void resetPot(Location loc) {
        CookingPotMenuHandle handle = menuHandles.get(loc);
        pots.remove(loc);
        completionsHandled.remove(loc);
        if (handle != null) {
            handle.clearInput();
            handle.clearOutput();
        }
    }

    // ---- Tick 任务 ----

    private class PotTickTask extends BukkitRunnable {
        @Override
        public void run() {
            long tick = System.currentTimeMillis() / 50;

            // 检测 NMS 输入槽：有物品但无 PotData → 自动开始烹饪
            for (var menuEntry : new ArrayList<>(menuHandles.entrySet())) {
                Location loc = menuEntry.getKey();
                if (pots.containsKey(loc)) continue;
                CookingPotMenuHandle handle = menuEntry.getValue();
                net.minecraft.world.item.ItemStack nmsInput = handle.getInputSlow();
                if (nmsInput != null && !nmsInput.isEmpty()) {
                    org.bukkit.inventory.ItemStack bukkitInput =
                        org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nmsInput);
                    handleInputPlaced(loc, bukkitInput);
                }
            }

            var it = pots.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                PotData d = entry.getValue();
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
                        achievements.getCookingPot().grant(getNearbyPlayer(loc));

                        // 展示物品 → 成品
                        DisplayItemUtil.remove(d.displayItem());
                        Location displayLoc = block.getLocation().add(0.5, 0.65, 0.5);
                        ItemDisplay doneItem = DisplayItemUtil.spawn(displayLoc, d.result().clone(), true);
                        pots.put(loc, new PotData(d.input(), d.result(), d.startTick(), d.cookTime(), doneItem));

                        // 更新 GUI 输出格 + 进度
                        CookingPotMenuHandle handle = menuHandles.get(loc);
                        if (handle != null) {
                            handle.setOutput(d.result().clone());
                            handle.updateProgress(d.cookTime(), d.cookTime());
                        }
                    }
                } else {
                    // 烹饪中 → 蒸汽粒子 + 进度
                    float progress = (float) (tick - d.startTick()) / d.cookTime();
                    if (random.nextFloat() < 0.4 + progress * 0.3) {
                        spawnSteam(block);
                    }
                    // 更新炉子进度箭头
                    CookingPotMenuHandle handle = menuHandles.get(loc);
                    if (handle != null) {
                        int elapsed = (int)(tick - d.startTick());
                        handle.updateProgress(d.cookTime(), elapsed);
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

    private boolean isPot(Block block) {
        if (!CraftEngineBlocks.isCustomBlock(block)) return false;
        var s = CraftEngineBlocks.getCustomBlockState(block);
        return s != null && s.owner() != null && s.owner().value() != null
            && POT_KEY.equals(s.owner().value().id());
    }

    private Player getNearbyPlayer(Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;
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

    // ---- 持久化 ----

    public Map<Location, PotData> getPotMap() {
        return pots;
    }

    /**
     * 接收任意输入物品并开始烹饪（内部使用，由 NMS InputSlot 回调触发）。
     */
    void handleInputPlaced(Location loc, ItemStack input) {
        // 已在烹饪中 → 忽略
        if (pots.containsKey(loc)) return;

        String inputId = itemToId(input);
        String outputId = RECIPES.get(inputId);
        if (outputId == null) return;

        Block block = loc.getBlock();
        Player player = getNearbyPlayer(loc);
        ItemStack result = player != null ? buildItem(outputId, player) : null;
        if (result == null || result.getType().isAir()) return;

        ItemStack one = input.clone();
        one.setAmount(1);

        // 生成展示物品
        Location displayLoc = block.getLocation().add(0.5, 0.65, 0.5);
        ItemDisplay displayItem = DisplayItemUtil.spawn(displayLoc, one.clone(), true);

        pots.put(loc, new PotData(one, result, System.currentTimeMillis() / 50, COOK_TIME, displayItem));

        loc.getWorld().playSound(loc.toCenterLocation(), Sound.BLOCK_BREWING_STAND_BREW,
            SoundCategory.BLOCKS, 0.5f, 0.8f);
    }

    public void restoreFromLoaded(Map<Location, PotData> loaded) {
        long tick = System.currentTimeMillis() / 50;
        for (var entry : loaded.entrySet()) {
            Location loc = entry.getKey();
            PotData d = entry.getValue();
            Block block = loc.getBlock();
            if (!isPot(block)) continue;

            boolean done = d.isDone(tick);
            if (done) completionsHandled.add(loc);

            ItemStack displayStack = done ? d.result().clone() : d.input().clone();
            ItemDisplay displayItem = DisplayItemUtil.spawn(
                block.getLocation().add(0.5, 0.65, 0.5), displayStack, true);

            pots.put(loc, new PotData(d.input(), d.result(), d.startTick(), d.cookTime(), displayItem));
        }
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
}
