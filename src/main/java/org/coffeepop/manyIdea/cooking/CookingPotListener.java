package org.coffeepop.manyIdea.cooking;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
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

import org.coffeepop.manyIdea.cooking.gui.CookingPotGuiManager;
import org.coffeepop.manyIdea.cooking.gui.CookingPotMenu;
import org.coffeepop.manyIdea.cooking.gui.PotRecipe;
import org.coffeepop.manyIdea.framework.AchievementManager;
import org.coffeepop.manyIdea.util.DisplayItemUtil;

import java.util.*;
import java.util.function.Predicate;

/**
 * 烹饪锅交互 - 9×6 大箱子 GUI，6 个原料槽，对标 FD 实时烹饪。
 */
public final class CookingPotListener implements Listener {

    private static final Key POT_KEY = Key.of("manyidea", "cooking_pot");
    static final int COOK_TIME = 200; // 10 秒

    public record PotData(ItemStack input, ItemStack result, long startTick, int cookTime,
                          ItemDisplay displayItem) {
        public boolean isDone(long tick) { return tick - startTick >= cookTime; }
    }

    private static final List<PotRecipe> RECIPES = List.of(
        new PotRecipe(Set.of(Material.BEEF), new ItemStack(Material.COOKED_BEEF), COOK_TIME),
        new PotRecipe(Set.of(Material.CHICKEN), new ItemStack(Material.COOKED_CHICKEN), COOK_TIME),
        new PotRecipe(Set.of(Material.PORKCHOP), new ItemStack(Material.COOKED_PORKCHOP), COOK_TIME),
        new PotRecipe(Set.of(Material.MUTTON), new ItemStack(Material.COOKED_MUTTON), COOK_TIME),
        new PotRecipe(Set.of(Material.RABBIT), new ItemStack(Material.COOKED_RABBIT), COOK_TIME),
        new PotRecipe(Set.of(Material.COD), new ItemStack(Material.COOKED_COD), COOK_TIME),
        new PotRecipe(Set.of(Material.SALMON), new ItemStack(Material.COOKED_SALMON), COOK_TIME),
        new PotRecipe(Set.of(Material.POTATO), new ItemStack(Material.BAKED_POTATO), COOK_TIME)
    );

    private final Map<Location, PotData> pots = new HashMap<>();
    private final Set<Location> completionsHandled = new HashSet<>();
    private final Random random = new Random();
    private final AchievementManager achievements;
    private final JavaPlugin plugin;
    private final CookingPotGuiManager guiManager = new CookingPotGuiManager(RECIPES);

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

        if (guiManager.isOpen(loc)) {
            player.sendMessage(ChatColor.GRAY + "有人在用这口锅...");
            return;
        }

        PotData existingData = pots.get(loc);
        long currentTick = System.currentTimeMillis() / 50;
        boolean isDone = existingData != null && existingData.isDone(currentTick);
        boolean isCooking = existingData != null && !isDone;
        int progCurrent = existingData != null
            ? (int) Math.min(COOK_TIME, currentTick - existingData.startTick()) : 0;

        Predicate<ItemStack> ingredientFilter = stack -> {
            Material mat = stack.getType();
            for (PotRecipe r : RECIPES) {
                if (r.ingredients().contains(mat)) return true;
            }
            return false;
        };

        guiManager.open(
            player, loc,
            ingredientFilter,
            () -> isDone,
            () -> resetPot(loc),
            () -> isCooking,
            () -> startCooking(loc),
            progCurrent, COOK_TIME
        );

        if (existingData != null && existingData.isDone(currentTick)) {
            CookingPotMenu menu = guiManager.getMenu(loc);
            if (menu != null) {
                menu.setOutput(existingData.result().clone());
            }
        }
    }

    private void startCooking(Location loc) {
        if (pots.containsKey(loc)) return;

        CookingPotMenu menu = guiManager.getMenu(loc);
        if (menu == null) return;

        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ItemStack s = menu.getInput(i);
            if (s != null && !s.getType().isAir()) {
                inputs.add(s.clone());
            }
        }
        if (inputs.isEmpty()) return;

        Set<Material> placed = new HashSet<>();
        for (var s : inputs) placed.add(s.getType());

        for (PotRecipe recipe : RECIPES) {
            if (placed.equals(recipe.ingredients())) {
                ItemStack result = recipe.result().clone();
                long startTick = System.currentTimeMillis() / 50;

                Block block = loc.getBlock();
                ItemStack displayOne = inputs.get(0).clone();
                displayOne.setAmount(1);
                ItemDisplay displayItem = DisplayItemUtil.spawn(
                    block.getLocation().add(0.5, 0.65, 0.5), displayOne, true);

                pots.put(loc, new PotData(inputs.get(0), result, startTick, recipe.cookTime(), displayItem));

                loc.getWorld().playSound(loc.toCenterLocation(),
                    Sound.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.5f, 0.8f);
                return;
            }
        }
    }

    // ---- 重置 ----

    private void resetPot(Location loc) {
        pots.remove(loc);
        completionsHandled.remove(loc);
        CookingPotMenu menu = guiManager.getMenu(loc);
        if (menu != null) {
            menu.clearIngredients();
            menu.clearOutput();
        }
    }

    // ---- 方块破坏 ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isPot(block)) return;

        Location loc = block.getLocation().toBlockLocation();

        guiManager.close(loc);

        PotData data = pots.remove(loc);
        completionsHandled.remove(loc);
        if (data != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.7, 0.5), data.input());
            DisplayItemUtil.remove(data.displayItem());
        }
    }

    // ---- Tick 任务 ----

    private class PotTickTask extends BukkitRunnable {
        @Override
        public void run() {
            long tick = System.currentTimeMillis() / 50;

            var it = pots.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                PotData d = entry.getValue();
                Location loc = entry.getKey();
                World w = loc.getWorld();
                if (w == null) continue;

                Block block = w.getBlockAt(loc);

                if (d.isDone(tick)) {
                    if (completionsHandled.add(loc)) {
                        w.playSound(loc.toCenterLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                            SoundCategory.BLOCKS, 0.5f, 1.5f);
                        spawnSteam(block);
                        achievements.getCookingPot().grant(getNearbyPlayer(loc));

                        DisplayItemUtil.remove(d.displayItem());
                        Location displayLoc = block.getLocation().add(0.5, 0.65, 0.5);
                        ItemDisplay doneItem = DisplayItemUtil.spawn(displayLoc, d.result().clone(), true);
                        pots.put(loc, new PotData(d.input(), d.result(), d.startTick(), d.cookTime(), doneItem));

                        CookingPotMenu menu = guiManager.getMenu(loc);
                        if (menu != null) {
                            menu.setOutput(d.result().clone());
                            guiManager.updateProgress(loc, d.cookTime(), d.cookTime());
                        }
                    }
                } else {
                    float progress = (float) (tick - d.startTick()) / d.cookTime();
                    if (random.nextFloat() < 0.4 + progress * 0.3) {
                        spawnSteam(block);
                    }
                    guiManager.updateProgress(loc, d.cookTime(), (int)(tick - d.startTick()));
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

    // ---- 持久化 ----

    public Map<Location, PotData> getPotMap() {
        return pots;
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
}
