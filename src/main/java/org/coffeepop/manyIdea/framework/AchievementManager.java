package org.coffeepop.manyIdea.framework;

import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.events.PlayerLoadingCompletedEvent;
import com.fren_gor.ultimateAdvancementAPI.visibilities.ParentGrantedVisibility;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * 成就系统 — 对标 Farmer's Delight 原版 3 条分支结构。
 * <p>
 * 树结构：
 * <pre>
 * manyidea:root
 * ├─ [Cooking] 烹饪分支
 * │   ├─ place_cooking_pot  [GOAL]   放置烹饪锅
 * │   │   ├─ eat_nourishing_food      享用滋养食物
 * │   │   │   └─ place_feast          放置宴席方块
 * │   │   ├─ grill                    使用烧烤架
 * │   │   ├─ fry                      使用炸锅
 * │   │   └─ cheese          [GOAL]   芝士发酵
 * │   └─ (none)
 * ├─ [Tools] 工具/收获分支
 * │   ├─ craft_knife                  制作一把刀
 * │   │   ├─ get_ham                  获得火腿
 * │   │   └─ use_cutting_board        使用砧板加工食材
 * │   │       └─ obtain_netherite_knife [CHALLENGE] 获得下界合金刀
 * │   └─ knife_vs_knife     [CHALLENGE] 刀刀对决
 * └─ [Special]
 *     └─ durian              [GOAL]   戴上榴莲壳头盔
 * </pre>
 */
public final class AchievementManager {

    private final JavaPlugin plugin;
    private final UltimateAdvancementAPI api;
    private final AdvancementTab tab;

    // Root
    private final RootAdvancement root;

    // ---------- Cooking branch ----------

    /** 放置烹饪锅 (FD: place_cooking_pot) */
    private final BaseAdvancement placeCookingPot;
    /** 享用滋养食物 (FD: eat_nourishing_food) */
    private final BaseAdvancement eatNourishingFood;
    /** 放置宴席方块 (FD: place_feast) */
    private final BaseAdvancement placeFeast;
    /** 使用烧烤架 (BarbequesDelight) */
    private final BaseAdvancement grill;
    /** 使用炸锅 (CasualnessDelight) */
    private final BaseAdvancement fry;
    /** 芝士发酵 (CasualnessDelight: cheese_wheel) */
    private final BaseAdvancement cheese;

    // ---------- Tools branch ----------

    /** 制作一把刀 (FD: craft_knife) */
    private final BaseAdvancement craftKnife;
    /** 获得火腿 (FD: get_ham) */
    private final BaseAdvancement getHam;
    /** 使用砧板加工 (FD: use_cutting_board) */
    private final BaseAdvancement useCuttingBoard;
    /** 获得下界合金刀 (FD: obtain_netherite_knife) */
    private final BaseAdvancement obtainNetheriteKnife;
    /** 刀刀对决 (ManyIdea 彩蛋) */
    private final BaseAdvancement knifeVsKnife;

    // ---------- Special ----------

    /** 戴上榴莲壳头盔 (FruitsDelight) */
    private final BaseAdvancement durian;

    // 已知的 CE 刀 ID 集合，用于 CraftListener
    private static final Set<Key> KNIFE_IDS = Set.of(
        Key.of("manyidea", "flint_knife"),
        Key.of("manyidea", "iron_knife"),
        Key.of("manyidea", "golden_knife"),
        Key.of("manyidea", "diamond_knife"),
        Key.of("manyidea", "netherite_knife")
    );

    private static final Key NETHERITE_KNIFE = Key.of("manyidea", "netherite_knife");
    private static final Key HAM = Key.of("manyidea", "ham");

    public AchievementManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.api = UltimateAdvancementAPI.getInstance(plugin);
        this.tab = api.createAdvancementTab("manyidea");

        // ================================================================
        // Root
        // ================================================================

        AdvancementDisplay rootDisplay = new AdvancementDisplay(
            Material.APPLE, "ManyIdea",
            AdvancementFrameType.TASK, false, false,
            2, 0,
            "Farmer's Delight 风格的食品扩展");
        root = new RootAdvancement(tab, "root", rootDisplay,
            "textures/block/custom/roast_chicken.png");

        // ================================================================
        // Cooking 分支
        // ================================================================

        placeCookingPot = new ManyIdeaAdv("place_cooking_pot",
            new AdvancementDisplay(Material.CAULDRON, "初级厨师",
                AdvancementFrameType.GOAL, true, false,
                2, 1,
                "放置一个烹饪锅"),
            root);

        eatNourishingFood = new ManyIdeaAdv("eat_nourishing_food",
            new AdvancementDisplay(Material.GOLDEN_APPLE, "滋养美食",
                AdvancementFrameType.TASK, true, false,
                2, 2,
                "享用滋补食物"),
            placeCookingPot);

        placeFeast = new ManyIdeaAdv("place_feast",
            new AdvancementDisplay(Material.COOKED_CHICKEN, "丰盛宴席",
                AdvancementFrameType.TASK, true, false,
                2, 3,
                "放置一个宴席方块并取食"),
            eatNourishingFood);

        grill = new ManyIdeaAdv("grill",
            new AdvancementDisplay(Material.CAMPFIRE, "烧烤大师",
                AdvancementFrameType.TASK, true, false,
                1, 2,
                "用烧烤架烤制食物"),
            placeCookingPot);

        fry = new ManyIdeaAdv("fry",
            new AdvancementDisplay(Material.COOKED_BEEF, "油炸专家",
                AdvancementFrameType.TASK, true, false,
                3, 2,
                "用炸锅油炸食物"),
            placeCookingPot);

        cheese = new ManyIdeaAdv("cheese",
            new AdvancementDisplay(Material.HONEYCOMB, "芝士发酵",
                AdvancementFrameType.GOAL, true, false,
                1, 3,
                "让生芝士轮熟成为芝士轮"),
            placeCookingPot);

        // ================================================================
        // Tools 分支
        // ================================================================

        craftKnife = new ManyIdeaAdv("craft_knife",
            new AdvancementDisplay(Material.IRON_SWORD, "锋利厨具",
                AdvancementFrameType.TASK, true, false,
                4, 1,
                "制作一把刀"),
            root);

        getHam = new ManyIdeaAdv("get_ham",
            new AdvancementDisplay(Material.COOKED_PORKCHOP, "火腿",
                AdvancementFrameType.TASK, true, false,
                3, 2,
                "获得一份火腿"),
            craftKnife);

        useCuttingBoard = new ManyIdeaAdv("use_cutting_board",
            new AdvancementDisplay(Material.CRAFTING_TABLE, "砧板大师",
                AdvancementFrameType.TASK, true, false,
                4, 2,
                "使用砧板加工食材"),
            craftKnife);

        obtainNetheriteKnife = new ManyIdeaAdv("obtain_netherite_knife",
            new AdvancementDisplay(Material.NETHERITE_SWORD, "终极厨刀",
                AdvancementFrameType.CHALLENGE, true, false,
                4, 3,
                "获得一把下界合金刀"),
            useCuttingBoard);

        knifeVsKnife = new ManyIdeaAdv("knife_vs_knife",
            new AdvancementDisplay(Material.IRON_SWORD, "刀刀对决!",
                AdvancementFrameType.CHALLENGE, true, false,
                5, 2,
                "在砧板上用刀切刀"),
            craftKnife);

        // ================================================================
        // Special
        // ================================================================

        durian = new ManyIdeaAdv("durian",
            new AdvancementDisplay(Material.LEATHER_HELMET, "榴莲武装",
                AdvancementFrameType.GOAL, true, false,
                0, 1,
                "戴上榴莲壳头盔"),
            root);

        // ================================================================
        // 注册
        // ================================================================

        tab.registerAdvancements(root,
            placeCookingPot, eatNourishingFood, placeFeast, grill, fry, cheese,
            craftKnife, getHam, useCuttingBoard, obtainNetheriteKnife,
            knifeVsKnife, durian);

        tab.getEventManager().register(tab, PlayerLoadingCompletedEvent.class, event -> {
            tab.showTab(event.getPlayer());
            tab.grantRootAdvancement(event.getPlayer());
        });

        plugin.getServer().getPluginManager().registerEvents(new DurianEquipListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new CraftListener(), plugin);

        plugin.getLogger().info("AchievementManager initialized with UltimateAdvancementAPI (FD tree)");
    }

    // ================================================================
    // Getters — 保持向后兼容
    // ================================================================

    /** @deprecated use {@link #getUseCuttingBoard()} */
    @Deprecated
    public BaseAdvancement getCuttingBoard() { return useCuttingBoard; }
    public BaseAdvancement getUseCuttingBoard() { return useCuttingBoard; }

    /** @deprecated use {@link #getPlaceCookingPot()} */
    @Deprecated
    public BaseAdvancement getCookingPot() { return placeCookingPot; }
    public BaseAdvancement getPlaceCookingPot() { return placeCookingPot; }

    public BaseAdvancement getKnifeVsKnife() { return knifeVsKnife; }

    /** @deprecated use {@link #getPlaceFeast()} */
    @Deprecated
    public BaseAdvancement getEatFeast() { return placeFeast; }
    public BaseAdvancement getPlaceFeast() { return placeFeast; }

    public BaseAdvancement getGrill() { return grill; }
    public BaseAdvancement getFry() { return fry; }
    public BaseAdvancement getCheese() { return cheese; }
    public BaseAdvancement getDurian() { return durian; }

    /** @deprecated use {@link #getEatNourishingFood()} */
    @Deprecated
    public BaseAdvancement getNourishingFood() { return eatNourishingFood; }
    public BaseAdvancement getEatNourishingFood() { return eatNourishingFood; }

    public BaseAdvancement getCraftKnife() { return craftKnife; }
    public BaseAdvancement getGetHam() { return getHam; }
    public BaseAdvancement getObtainNetheriteKnife() { return obtainNetheriteKnife; }

    // ================================================================
    // has() — 更新所有 achievement ID
    // ================================================================

    public boolean has(Player player, String id) {
        return switch (id) {
            case "cutting_board", "use_cutting_board" -> useCuttingBoard.isGranted(player);
            case "cooking_pot", "place_cooking_pot" -> placeCookingPot.isGranted(player);
            case "knife_vs_knife" -> knifeVsKnife.isGranted(player);
            case "eat_feast", "place_feast" -> placeFeast.isGranted(player);
            case "grill" -> grill.isGranted(player);
            case "fry" -> fry.isGranted(player);
            case "cheese" -> cheese.isGranted(player);
            case "durian" -> durian.isGranted(player);
            case "eat_nourishing_food" -> eatNourishingFood.isGranted(player);
            case "craft_knife" -> craftKnife.isGranted(player);
            case "get_ham" -> getHam.isGranted(player);
            case "obtain_netherite_knife" -> obtainNetheriteKnife.isGranted(player);
            default -> false;
        };
    }

    // ================================================================
    // Inner classes
    // ================================================================

    private static class ManyIdeaAdv extends BaseAdvancement implements ParentGrantedVisibility {
        ManyIdeaAdv(String key, AdvancementDisplay display, Advancement parent) {
            super(key, display, parent);
        }
    }

    /** 检测玩家戴上榴莲壳头盔 */
    private class DurianEquipListener implements Listener {
        private final Key DURIAN_HELMET = Key.of("manyidea", "durian_helmet");

        @EventHandler
        public void onEquip(InventoryClickEvent event) {
            if (event.getSlotType() != InventoryType.SlotType.ARMOR
                || event.getSlot() != 39) return; // helmet slot

            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType().isAir()) return;

            if (!CraftEngineItems.isCustomItem(cursor)) return;
            Key id = CraftEngineItems.getCustomItemId(cursor);
            if (id == null || !DURIAN_HELMET.equals(id)) return;

            Player player = (Player) event.getWhoClicked();
            durian.grant(player);
        }
    }

    /** 检测玩家合成刀具 / 火腿 */
    private class CraftListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onCraft(CraftItemEvent event) {
            ItemStack result = event.getRecipe().getResult();
            if (!CraftEngineItems.isCustomItem(result)) return;

            Key id = CraftEngineItems.getCustomItemId(result);
            if (id == null) return;

            Player player = (Player) event.getWhoClicked();

            // 制作任意刀 → craft_knife
            if (KNIFE_IDS.contains(id)) {
                craftKnife.grant(player);
                // 下界合金刀 → obtain_netherite_knife
                if (NETHERITE_KNIFE.equals(id)) {
                    obtainNetheriteKnife.grant(player);
                }
            }

            // 获得火腿 → get_ham
            if (HAM.equals(id)) {
                getHam.grant(player);
            }
        }
    }
}
