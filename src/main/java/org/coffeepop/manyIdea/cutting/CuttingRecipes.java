package org.coffeepop.manyIdea.cutting;

import org.bukkit.Sound;

import java.util.*;

import static org.coffeepop.manyIdea.cutting.ToolCategory.*;

/**
 * 砧板配方注册表 — 对标 FD 的 CuttingRecipes。
 * <p>
 * 配方分 7 大类工具，支持多产物 + 独立概率。
 */
public final class CuttingRecipes {

    private static final List<CuttingRecipe> recipes = new ArrayList<>();

    private CuttingRecipes() {}

    // ---- 木材类型 ----

    private static final String[] WOOD_TYPES = {
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
        "mangrove", "cherry", "crimson", "warped", "bamboo"
    };
    private static boolean isBamboo(String wood) { return "bamboo".equals(wood); }
    private static boolean isStem(String wood) { return "crimson".equals(wood) || "warped".equals(wood); }
    private static String logName(String wood) {
        return "minecraft:" + (isStem(wood) ? wood + "_stem" : isBamboo(wood) ? wood + "_block" : wood + "_log");
    }
    private static String strippedName(String wood) {
        return "minecraft:stripped_" + (isStem(wood) ? wood + "_stem" : isBamboo(wood) ? wood + "_block" : wood + "_log");
    }
    private static String plankName(String wood) {
        return "minecraft:" + wood + "_planks";
    }

    public static void init() {
        // ============================================================
        // 1. 刀（KNIFE）— 食物切割、花卉切割
        // ============================================================

        // --- CE 定制食物 ---
        register(KNIFE, "manyidea:tomato",
            r("manyidea:tomato_sauce"),
            cr("manyidea:tomato_seeds", 0.5f));
        register(KNIFE, "manyidea:cabbage",
            r("manyidea:cabbage_leaf", 2));

        // --- 动物切割（对标 FD） ---
        register(KNIFE, "minecraft:beef", r("manyidea:minced_beef"));
        register(KNIFE, "minecraft:porkchop", r("manyidea:bacon", 2));
        register(KNIFE, "minecraft:mutton", r("manyidea:mutton_chops"));
        register(KNIFE, "minecraft:chicken",
            r("manyidea:chicken_cuts"),
            cr("minecraft:bone_meal", 0.5f));
        register(KNIFE, "minecraft:rabbit",
            r("minecraft:rabbit_hide"),
            cr("minecraft:bone", 0.5f));
        register(KNIFE, "minecraft:cooked_chicken",
            r("manyidea:cooked_chicken_cuts"));
        register(KNIFE, "minecraft:cooked_beef",
            r("manyidea:minced_beef", 2));
        register(KNIFE, "minecraft:cooked_porkchop",
            r("manyidea:cooked_bacon", 2));
        register(KNIFE, "minecraft:cooked_mutton",
            r("manyidea:cooked_mutton_chops"));
        register(KNIFE, "minecraft:cooked_rabbit",
            r("minecraft:rabbit_hide", 2));

        // 鱼类加工 → 鱼片
        register(KNIFE, "minecraft:cod",
            r("manyidea:cod_slice", 2));
        register(KNIFE, "minecraft:salmon",
            r("manyidea:salmon_slice", 2));
        register(KNIFE, "minecraft:tropical_fish",
            r("minecraft:bone_meal"),
            cr("minecraft:bone", 0.3f));
        register(KNIFE, "minecraft:cooked_cod",
            r("manyidea:cooked_cod_slice", 2));
        register(KNIFE, "minecraft:cooked_salmon",
            r("manyidea:cooked_salmon_slice", 2));

        // --- 植物食物切割 ---
        register(KNIFE, "minecraft:pumpkin",
            r("manyidea:pumpkin_slice", 4));
        register(KNIFE, "minecraft:melon",
            r("minecraft:melon_slice", 9));
        register(KNIFE, "minecraft:cake",
            r("manyidea:cake_slice", 7));
        register(KNIFE, "minecraft:bread",
            r("manyidea:wheat_dough", 2));       // 面包 → 面团，可以重烤
        register(KNIFE, "minecraft:brown_mushroom",
            r("minecraft:brown_mushroom", 4));
        register(KNIFE, "minecraft:red_mushroom",
            r("minecraft:red_mushroom", 4));
        register(KNIFE, "minecraft:honeycomb_block",
            r("minecraft:honeycomb", 4));
        register(KNIFE, "minecraft:sugar_cane",
            r("minecraft:sugar", 2));

        // --- 派 → 切片（对标 FD cake/pie slicing）---
        register(KNIFE, "manyidea:apple_pie",
            r("manyidea:apple_pie_slice", 4));
        register(KNIFE, "manyidea:chocolate_pie",
            r("manyidea:chocolate_pie_slice", 4));
        register(KNIFE, "manyidea:sweet_berry_cheesecake",
            r("manyidea:sweet_berry_cheesecake_slice", 4));
        register(KNIFE, "minecraft:pumpkin_pie",
            r("manyidea:pumpkin_pie_slice", 4));

        // --- 卷 → 切片（对标 FD kelp_roll_slice）---
        register(KNIFE, "manyidea:kelp_roll",
            r("manyidea:kelp_roll_slice", 4));

        // --- 花卉 → 染料 ---
        registerFlower("minecraft:dandelion",         "minecraft:yellow_dye", 2);
        registerFlower("minecraft:poppy",             "minecraft:red_dye", 2);
        registerFlower("minecraft:blue_orchid",       "minecraft:light_blue_dye", 2);
        registerFlower("minecraft:allium",            "minecraft:magenta_dye", 2);
        registerFlower("minecraft:azure_bluet",       "minecraft:light_gray_dye", 2);
        registerFlower("minecraft:red_tulip",         "minecraft:red_dye", 2);
        registerFlower("minecraft:orange_tulip",      "minecraft:orange_dye", 2);
        registerFlower("minecraft:white_tulip",       "minecraft:light_gray_dye", 2);
        registerFlower("minecraft:pink_tulip",        "minecraft:pink_dye", 2);
        registerFlower("minecraft:oxeye_daisy",       "minecraft:light_gray_dye", 2);
        registerFlower("minecraft:cornflower",        "minecraft:blue_dye", 2);
        registerFlower("minecraft:lily_of_the_valley","minecraft:white_dye", 2);
        registerFlower("minecraft:wither_rose",       "minecraft:black_dye", 2);
        registerFlower("minecraft:sunflower",         "minecraft:yellow_dye", 4);
        registerFlower("minecraft:lilac",             "minecraft:magenta_dye", 4);
        registerFlower("minecraft:rose_bush",         "minecraft:red_dye", 4);
        registerFlower("minecraft:peony",             "minecraft:pink_dye", 4);
        registerFlower("minecraft:torchflower",       "minecraft:orange_dye", 2);
        registerFlower("minecraft:pitcher_plant",     "minecraft:cyan_dye", 4);
        registerFlower("minecraft:spore_blossom",     "minecraft:pink_dye", 2);
        registerFlower("minecraft:open_eyeblossom",   "minecraft:orange_dye", 2);
        registerFlower("minecraft:closed_eyeblossom", "minecraft:gray_dye", 2);
        registerFlower("minecraft:cactus_flower",     "minecraft:pink_dye", 2);
        registerFlower("minecraft:wildflowers",       "minecraft:yellow_dye", 3);
        registerFlower("minecraft:cherry_leaves",     "minecraft:pink_dye", 2);
        registerFlower("minecraft:flowering_azalea_leaves", "minecraft:pink_dye", 2);

        // ============================================================
        // 2. 斧（AXE_STRIP）— 原木去皮 + 铜块除锈
        // ============================================================
        for (String wood : WOOD_TYPES) {
            register(AXE_STRIP, Sound.ITEM_AXE_SCRAPE, logName(wood),
                r(strippedName(wood)),
                cr("manyidea:tree_bark", 0.5f));
        }
        // 铜块除锈
        register(AXE_STRIP, Sound.ITEM_AXE_SCRAPE, "minecraft:weathered_copper",
            r("minecraft:exposed_copper"));
        register(AXE_STRIP, Sound.ITEM_AXE_SCRAPE, "minecraft:exposed_copper",
            r("minecraft:copper_block"));
        register(AXE_STRIP, Sound.ITEM_AXE_SCRAPE, "minecraft:oxidized_copper",
            r("minecraft:weathered_copper"));
        register(AXE_STRIP, Sound.ITEM_AXE_SCRAPE, "minecraft:waxed_weathered_copper",
            r("minecraft:waxed_exposed_copper"));
        register(AXE_STRIP, Sound.ITEM_AXE_SCRAPE, "minecraft:waxed_exposed_copper",
            r("minecraft:waxed_copper_block"));
        register(AXE_STRIP, Sound.ITEM_AXE_SCRAPE, "minecraft:waxed_oxidized_copper",
            r("minecraft:waxed_weathered_copper"));

        // ============================================================
        // 3. 斧（AXE）— 木制家具回收
        // ============================================================
        for (String wood : WOOD_TYPES) {
            String p = plankName(wood);
            registerSalvage("minecraft:" + wood + "_door", p, 2);
            registerSalvage("minecraft:" + wood + "_fence", p, 1);
            registerSalvage("minecraft:" + wood + "_fence_gate", p, 1);
            registerSalvage("minecraft:" + wood + "_trapdoor", p, 1);
            registerSalvage("minecraft:" + wood + "_stairs", p, 2);
            registerSalvage("minecraft:" + wood + "_slab", p, 1);
            registerSalvage("minecraft:" + wood + "_pressure_plate", p, 1);
            registerSalvage("minecraft:" + wood + "_button", p, 1);
            registerSalvage("minecraft:" + wood + "_sign", p, 2);
            registerSalvage("minecraft:" + wood + "_hanging_sign",
                "minecraft:" + (isStem(wood) ? "stripped_" + wood + "_stem" : isBamboo(wood) ? "stripped_bamboo_block" : "stripped_" + wood + "_log"), 2);
            registerSalvage("minecraft:" + wood + "_boat", p, 3);
            registerSalvage("minecraft:" + wood + "_chest_boat", p, 3);
        }
        // 通用木制品回收
        registerSalvage("minecraft:crafting_table", "minecraft:oak_planks", 4);
        registerSalvage("minecraft:bookshelf",
            r("minecraft:book", 3), cr("minecraft:oak_planks", 2, 0.75f));
        registerSalvage("minecraft:ladder", "minecraft:stick", 2);
        registerSalvage("minecraft:bowl", "minecraft:stick", 1);
        registerSalvage("minecraft:chest", "minecraft:oak_planks", 4);
        registerSalvage("minecraft:barrel", "minecraft:oak_planks", 4);
        registerSalvage("minecraft:composter", "minecraft:oak_slab", 3);
        registerSalvage("minecraft:jukebox", "minecraft:oak_planks", 4);
        registerSalvage("minecraft:note_block", "minecraft:oak_planks", 4);
        registerSalvage("minecraft:loom", "minecraft:oak_planks", 2);
        registerSalvage("minecraft:cartography_table", "minecraft:oak_planks", 2);
        registerSalvage("minecraft:fletching_table", "minecraft:oak_planks", 2);
        registerSalvage("minecraft:smithing_table", "minecraft:oak_planks", 2);
        registerSalvage("minecraft:chiseled_bookshelf", "minecraft:oak_planks", 3);
        register(AXE, Sound.ITEM_AXE_SCRAPE, "minecraft:decorated_pot",
            r("minecraft:brick", 4));

        // ============================================================
        // 4. 镐（PICKAXE）— 矿物/石料回收
        // ============================================================
        register(PICKAXE, "minecraft:bricks",            r("minecraft:brick", 4));
        register(PICKAXE, "minecraft:quartz_block",      r("minecraft:quartz", 4));
        register(PICKAXE, "minecraft:nether_bricks",     r("minecraft:nether_brick", 4));
        register(PICKAXE, "minecraft:prismarine_bricks", r("minecraft:prismarine_shard", 4));
        register(PICKAXE, "minecraft:stone_bricks",      r("minecraft:stone", 4));
        register(PICKAXE, "minecraft:mossy_stone_bricks",
            r("minecraft:stone", 3), cr("minecraft:vine", 0.5f));
        register(PICKAXE, "minecraft:cracked_stone_bricks", r("minecraft:stone", 3));
        register(PICKAXE, "minecraft:sandstone",         r("minecraft:sand", 4));
        register(PICKAXE, "minecraft:red_sandstone",     r("minecraft:red_sand", 4));
        register(PICKAXE, "minecraft:prismarine",        r("minecraft:prismarine_shard", 4));
        register(PICKAXE, "minecraft:dark_prismarine",
            r("minecraft:prismarine_shard", 3), cr("minecraft:black_dye", 0.5f));
        register(PICKAXE, "minecraft:end_stone_bricks",  r("minecraft:end_stone", 4));
        register(PICKAXE, "minecraft:purpur_block",      r("minecraft:popped_chorus_fruit", 4));
        register(PICKAXE, "minecraft:glowstone",         r("minecraft:glowstone_dust", 4));
        register(PICKAXE, "minecraft:sea_lantern",
            r("minecraft:prismarine_shard", 2), cr("minecraft:prismarine_crystals", 2, 0.5f));
        register(PICKAXE, "minecraft:amethyst_block",    r("minecraft:amethyst_shard", 4));
        register(PICKAXE, "minecraft:coal_block",        r("minecraft:coal", 4));
        register(PICKAXE, "minecraft:dripstone_block",   r("minecraft:pointed_dripstone", 4));
        register(PICKAXE, "minecraft:bone_block",        r("minecraft:bone_meal", 4));
        register(PICKAXE, "minecraft:snow_block",        r("minecraft:snowball", 4));
        register(PICKAXE, "minecraft:packed_ice",        r("minecraft:ice", 4));

        // ============================================================
        // 5. 铲（SHOVEL）— 挖掘软质方块
        // ============================================================
        register(SHOVEL, "minecraft:clay",
            r("minecraft:clay_ball", 4));
        register(SHOVEL, "minecraft:gravel",
            r("minecraft:gravel"), cr("minecraft:flint", 0.1f));
        register(SHOVEL, "minecraft:soul_sand",
            r("minecraft:soul_soil", 4));
        register(SHOVEL, "minecraft:coarse_dirt",
            r("minecraft:dirt", 2), cr("minecraft:gravel", 0.5f));
        register(SHOVEL, "minecraft:mud",
            r("minecraft:clay_ball", 2));
        register(SHOVEL, "minecraft:rooted_dirt",
            r("minecraft:dirt"), cr("minecraft:hanging_roots", 0.5f));

        // ============================================================
        // 6. 剪刀（SHEARS）— 剪取回收
        // ============================================================
        register(SHEARS, "minecraft:saddle",
            r("minecraft:leather", 2), cr("minecraft:iron_nugget", 2, 0.5f));
        register(SHEARS, "minecraft:cobweb",
            r("minecraft:string", 2));
        register(SHEARS, "minecraft:vine",
            r("minecraft:vine"));
        register(SHEARS, "minecraft:glow_lichen",
            r("minecraft:glow_lichen"));
        register(SHEARS, "minecraft:moss_block",
            r("minecraft:moss_carpet", 2));
        register(SHEARS, "minecraft:white_wool",
            r("minecraft:string", 2));

        // ============================================================
        // 7. 锄（HOE）— 拆解交通工具
        // ============================================================
        register(HOE, "minecraft:chest_minecart",
            r("minecraft:minecart"), r("minecraft:chest"));
        register(HOE, "minecraft:furnace_minecart",
            r("minecraft:minecart"), r("minecraft:furnace"));
        register(HOE, "minecraft:hopper_minecart",
            r("minecraft:minecart"), r("minecraft:hopper"));
        register(HOE, "minecraft:tnt_minecart",
            r("minecraft:minecart"), r("minecraft:tnt"));
    }

    // ---- 注册辅助 ----

    public static void register(ToolCategory tool, String input, CuttingResult... results) {
        register(tool, null, input, results);
    }

    public static void register(ToolCategory tool, Sound sound, String input, CuttingResult... results) {
        recipes.add(new CuttingRecipe(input, tool, List.of(results), sound));
    }

    private static void registerFlower(String flowerId, String dyeId, int count) {
        register(KNIFE, Sound.BLOCK_AZALEA_LEAVES_BREAK, flowerId, r(dyeId, count));
    }

    private static void registerSalvage(String furnitureId, String outputId, int count) {
        register(AXE, Sound.ITEM_AXE_SCRAPE, furnitureId, cr(outputId, count, 0.75f));
    }
    private static void registerSalvage(String furnitureId, CuttingResult... results) {
        register(AXE, Sound.ITEM_AXE_SCRAPE, furnitureId, results);
    }

    // ---- CuttingResult 快捷工厂 ----

    private static CuttingResult r(String itemKey, int count) { return new CuttingResult(itemKey, count); }
    private static CuttingResult r(String itemKey) { return new CuttingResult(itemKey); }
    private static CuttingResult cr(String itemKey, float chance) { return new CuttingResult(itemKey, 1, chance); }
    private static CuttingResult cr(String itemKey, int count, float chance) { return new CuttingResult(itemKey, count, chance); }

    // ---- 查询 ----

    public static Optional<CuttingRecipe> match(String inputId, ToolCategory toolCategory) {
        return recipes.stream()
            .filter(r -> r.input().equals(inputId) && r.toolCategory() == toolCategory)
            .findFirst();
    }

    public static List<CuttingRecipe> all() {
        return Collections.unmodifiableList(recipes);
    }
}
