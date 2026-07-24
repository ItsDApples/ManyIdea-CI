package org.coffeepop.manyIdea.cutting;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.enchantments.Enchantment;
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

import java.util.*;

/**
 * 砧板完整交互 — 对标 FD CuttingBoardBlock + CuttingBoardBlockEntity。
 * <p>
 * 支持 7 类工具、多产物概率产出、雕刻工具展示、红石比较器。
 */
public final class CuttingBoardListener implements Listener {

    private static final Key CUTTING_BOARD = Key.of("manyidea", "cutting_board");

    private final CuttingBoardManager manager;
    private final JavaPlugin plugin;
    private final AchievementManager achievements;
    private final Random random = new Random();

    public CuttingBoardListener(CuttingBoardManager manager, JavaPlugin plugin, AchievementManager achievements) {
        this.manager = manager;
        this.plugin = plugin;
        this.achievements = achievements;
        new ComparatorTask().runTaskTimer(plugin, 20L, 10L);
    }

    // ================================================================
    // 右键交互
    // ================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || !isCuttingBoard(block)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 潜行 + 手持工具 + 砧板为空 → 雕刻工具展示
        if (player.isSneaking() && !mainHand.getType().isAir() && !manager.hasItem(block)) {
            if (isCarveableTool(mainHand)) {
                ItemStack copy = mainHand.clone();
                copy.setAmount(1);
                manager.setCarvedTool(block, copy);
                mainHand.setAmount(mainHand.getAmount() - 1);
                playSound(block, Sound.BLOCK_BAMBOO_WOOD_PLACE, 1.0f, 0.8f);
                return;
            }
            return;
        }

        // 空手 + 砧板有物品 → 取出物品
        if (mainHand.getType().isAir()) {
            if (!manager.hasItem(block)) return;
            ItemStack stored = manager.getItem(block);
            manager.remove(block);
            player.getInventory().addItem(stored).forEach((k, v) ->
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), v));
            playSound(block, Sound.ENTITY_ITEM_PICKUP, 0.25f, 0.5f);
            return;
        }

        // 砧板有物品 + 手持工具 → 加工
        if (manager.hasItem(block)) {
            // 彩蛋：刀切刀
            ItemStack stored = manager.getItem(block);
            if (stored != null && isCarveableTool(stored) && isCarveableTool(mainHand)) {
                achievements.getKnifeVsKnife().grant(player);
                playSound(block, Sound.ENTITY_ITEM_BREAK, 0.5f, 1.5f);
                // 取出雕刻的工具
                manager.remove(block);
                player.getInventory().addItem(stored).forEach((k, v) ->
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), v));
                return;
            }

            // 尝试加工
            if (tryProcess(block, player, mainHand)) return;
        }

        // 砧板空 + 主手工具 + 副手食材 → 副手直接加工
        if (!manager.hasItem(block)) {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand.getType() != Material.AIR) {
                if (tryProcessOffhand(block, player, mainHand, offHand)) return;
            }
        }

        // 砧板空 + 主手有物品 → 放置物品
        if (!manager.hasItem(block) && !mainHand.getType().isAir()) {
            // 如果主手是刀具但找不到配方，也不要放刀
            if (isCarveableTool(mainHand)) return;

            ItemStack p = mainHand.clone();
            p.setAmount(1);
            manager.setItem(block, p);
            mainHand.setAmount(mainHand.getAmount() - 1);
            playSound(block, Sound.BLOCK_WOOD_PLACE, 1.0f, 0.8f);
        }
    }

    // ================================================================
    // 破坏
    // ================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isCuttingBoard(block) || !manager.hasItem(block)) return;
        block.getWorld().dropItemNaturally(
            block.getLocation().add(0.5, 0.5, 0.5), manager.getItem(block));
        manager.remove(block);
    }

    // ================================================================
    // 加工逻辑（对标 FD processStoredItemUsingTool）
    // ================================================================

    /**
     * 尝试用主手工具加工砧板上的物品。
     *
     * @return true 表示成功加工
     */
    private boolean tryProcess(Block block, Player player, ItemStack tool) {
        ItemStack ingredient = manager.getItem(block);
        if (ingredient == null || ingredient.getType().isAir()) return false;

        String inputId = itemToInputId(ingredient);
        ToolCategory toolCat = matchToolCategory(tool);
        if (toolCat == null) return false;

        var recipe = CuttingRecipes.match(inputId, toolCat);
        if (recipe.isEmpty()) return false;

        doProcess(block, player, tool, recipe.get());

        // 消耗 1 个原料
        ingredient.setAmount(ingredient.getAmount() - 1);
        if (ingredient.getAmount() <= 0) manager.remove(block);
        else manager.setItem(block, ingredient);

        return true;
    }

    /** 副手直接加工（不需要先放砧板） */
    private boolean tryProcessOffhand(Block block, Player player, ItemStack tool, ItemStack offHand) {
        String inputId = itemToInputId(offHand);
        ToolCategory toolCat = matchToolCategory(tool);
        if (toolCat == null) return false;

        var recipe = CuttingRecipes.match(inputId, toolCat);
        if (recipe.isEmpty()) return false;

        doProcess(block, player, tool, recipe.get());
        offHand.setAmount(offHand.getAmount() - 1);
        return true;
    }

    /** 执行加工：多产物掷骰、掉落、音效、粒子、耐久 */
    private void doProcess(Block block, Player player, ItemStack tool, CuttingRecipe recipe) {
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        Location dropLoc = block.getLocation().add(0.5, 0.3, 0.5);

        // 掷骰产出所有 results
        boolean anyDropped = false;
        Material particleMat = null;
        for (CuttingResult result : recipe.results()) {
            ItemStack rolled = result.roll(random, fortune, player);
            if (rolled != null && !rolled.getType().isAir()) {
                block.getWorld().dropItemNaturally(dropLoc, rolled);
                anyDropped = true;
                if (particleMat == null) particleMat = rolled.getType();
            }
        }
        if (!anyDropped) return;

        // 工具扣耐久
        tool.damage(1, player);

        // 粒子效果
        if (particleMat != null) {
            block.getWorld().spawnParticle(Particle.ITEM,
                block.getLocation().add(0.5, 0.2, 0.5),
                5, 0.1, 0.1, 0.1, 0.05, new ItemStack(particleMat));
        }

        // 音效：配方指定 > 自动检测
        Sound sound = recipe.sound();
        if (sound == null) {
            sound = autoDetectSound(recipe.toolCategory(), manager.getItem(block));
        }
        playSound(block, sound, 0.8f, 1.0f);

        // 成就
        achievements.getCuttingBoard().grant(player);
    }

    // ================================================================
    // 音效自动检测（对标 FD playProcessingSound）
    // ================================================================

    /**
     * 根据工具分类自动选择加工音效。
     * 优先级：刀 > 剪刀 > 配方输入物品类型 > 默认木头
     */
    private Sound autoDetectSound(ToolCategory toolCat, ItemStack ingredient) {
        return switch (toolCat) {
            case KNIFE -> Sound.ENTITY_SHEEP_SHEAR;
            case SHEARS -> Sound.ENTITY_SHEEP_SHEAR;
            case AXE, AXE_STRIP -> Sound.ITEM_AXE_SCRAPE;
            case PICKAXE -> ingredient != null && ingredient.getType().isBlock()
                ? ingredient.getType().createBlockData().getSoundGroup().getBreakSound()
                : Sound.BLOCK_STONE_BREAK;
            case SHOVEL -> Sound.BLOCK_GRAVEL_BREAK;
            case HOE -> Sound.ITEM_HOE_TILL;
        };
    }

    // ================================================================
    // 工具匹配
    // ================================================================

    /** 判断物品是否可雕刻（对标 FD：TieredItem / TridentItem / ShearsItem） */
    private boolean isCarveableTool(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (ToolCategory.KNIFE.matches(item)) return true;
        String name = item.getType().name();
        return name.contains("_SWORD") || name.contains("_AXE")
            || item.getType() == Material.SHEARS
            || item.getType() == Material.TRIDENT;
    }

    /** 将物品匹配到 ToolCategory，返回 null 表示不匹配任何工具分类 */
    private ToolCategory matchToolCategory(ItemStack item) {
        for (ToolCategory cat : ToolCategory.values()) {
            if (cat.matches(item)) return cat;
        }
        return null;
    }

    /** 将砧板上的物品转成配方输入 ID（"manyidea:xxx" 或 "minecraft:xxx"） */
    private String itemToInputId(ItemStack item) {
        if (CraftEngineItems.isCustomItem(item)) {
            Key id = CraftEngineItems.getCustomItemId(item);
            if (id != null) return id.toString();
        }
        return "minecraft:" + item.getType().name().toLowerCase(Locale.ROOT);
    }

    // ================================================================
    // 红石比较器
    // ================================================================

    private class ComparatorTask extends BukkitRunnable {
        @Override
        public void run() {
            for (var entry : manager.getBoardEntries().entrySet()) {
                Location loc = entry.getKey();
                ItemStack item = entry.getValue();
                if (item == null || item.getType().isAir()) continue;

                World world = loc.getWorld();
                if (world == null) continue;
                Block board = world.getBlockAt(loc);

                int maxStack = Math.min(64, item.getMaxStackSize());
                int power = (int) ((float) item.getAmount() / maxStack * 14) + 1;
                if (power > 15) power = 15;

                for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                    Block neighbor = board.getRelative(face);
                    if (neighbor.getType() != Material.COMPARATOR) continue;
                    if (neighbor.getBlockData() instanceof Directional dir
                        && dir.getFacing() == face) {
                        Block output = neighbor.getRelative(dir.getFacing().getOppositeFace());
                        setRedstonePower(output, power);
                    }
                }
            }
        }
    }

    private void setRedstonePower(Block block, int power) {
        if (power < 0) power = 0;
        if (power > 15) power = 15;
        if (block.getBlockData() instanceof RedstoneWire wire) {
            wire.setPower(power);
            block.setBlockData(wire, false);
        }
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private boolean isCuttingBoard(Block block) {
        if (!CraftEngineBlocks.isCustomBlock(block)) return false;
        var s = CraftEngineBlocks.getCustomBlockState(block);
        return s != null && s.owner() != null && s.owner().value() != null
            && CUTTING_BOARD.equals(s.owner().value().id());
    }

    private void playSound(Block b, Sound s, float v, float p) {
        b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), s, SoundCategory.BLOCKS, v, p);
    }
}
