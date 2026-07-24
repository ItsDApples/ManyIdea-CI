package org.coffeepop.manyIdea.cutting;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * 砧板加工产物 — 对标 FD 的 ChanceResult。
 * <p>
 * 支持 CE 自定义物品（manyidea:xxx）和原版物品（minecraft:xxx）。
 * 含独立概率 + 时运加成。
 */
public record CuttingResult(
    String itemKey,   // "manyidea:tomato_sauce" or "minecraft:bone_meal"
    int count,        // 产出数量（roll 通过时）
    float chance      // 0~1，1 表示必掉
) {

    public CuttingResult {
        if (chance <= 0 || chance > 1) throw new IllegalArgumentException("chance must be in (0, 1]");
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
    }

    public CuttingResult(String itemKey, int count) {
        this(itemKey, count, 1.0f);
    }

    public CuttingResult(String itemKey) {
        this(itemKey, 1, 1.0f);
    }

    /**
     * 掷骰判断是否产出。时运每级 +0.1 概率（上限 1.0）。
     *
     * @param player 用于 CE 物品构建上下文，可为 null（此时使用无玩家构造）
     * @return 非 null 表示产出成功，返回 ItemStack
     */
    public ItemStack roll(Random random, int fortune, Player player) {
        float adjusted = Math.min(1.0f, chance + fortune * 0.1f);
        if (adjusted < 1.0f && random.nextFloat() > adjusted) {
            return null;
        }
        return build(player);
    }

    /** 直接构建 ItemStack（不掷骰） */
    @SuppressWarnings("deprecation")
    public ItemStack build(Player player) {
        Key key = Key.of(itemKey);

        // CE 自定义物品
        if (CraftEngineItems.byId(key) != null) {
            BukkitItemDefinition def = CraftEngineItems.byId(key);
            // player 为 null 时用无参 buildBukkitItem()，避免 CE 内部 NPE
            ItemStack built = player != null
                ? def.buildBukkitItem(player)
                : def.buildBukkitItem();
            built.setAmount(count);
            return built;
        }

        // 原版物品
        Material mat = Material.matchMaterial(itemKey);
        if (mat != null && mat.isItem()) {
            return new ItemStack(mat, count);
        }

        return new ItemStack(Material.AIR);
    }

    /** 构建物品（用于粒子等不需要 Player 上下文的场景） */
    public ItemStack build() {
        return build(null);
    }
}
