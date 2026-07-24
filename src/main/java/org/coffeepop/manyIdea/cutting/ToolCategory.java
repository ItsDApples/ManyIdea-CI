package org.coffeepop.manyIdea.cutting;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;

import java.util.Set;

/**
 * 工具分类 — 对应 Farmer's Delight 的 ItemAbility 系统。
 * <p>
 * 每个分类定义一组匹配规则：先检查 CE 自定义物品 ID，再检查原版 Material。
 */
public enum ToolCategory {

    /** 刀 — 用于切割食物、花卉 */
    KNIFE {
        @Override
        public boolean matches(ItemStack item) {
            if (CraftEngineItems.isCustomItem(item)) {
                Key id = CraftEngineItems.getCustomItemId(item);
                return id != null && knifeKeys.contains(id);
            }
            return item.getType().name().contains("_SWORD");
        }
    },

    /** 镐 — 矿物回收 */
    PICKAXE {
        @Override
        public boolean matches(ItemStack item) {
            if (CraftEngineItems.isCustomItem(item)) return false;
            return item.getType().name().contains("_PICKAXE");
        }
    },

    /** 斧 — 木头回收（非剥皮） */
    AXE {
        @Override
        public boolean matches(ItemStack item) {
            if (CraftEngineItems.isCustomItem(item)) return false;
            return item.getType().name().contains("_AXE");
        }
    },

    /** 斧（剥皮模式）— 原木去皮 */
    AXE_STRIP {
        @Override
        public boolean matches(ItemStack item) {
            if (CraftEngineItems.isCustomItem(item)) return false;
            return item.getType().name().contains("_AXE");
        }
    },

    /** 铲 — 挖掘软质方块 */
    SHOVEL {
        @Override
        public boolean matches(ItemStack item) {
            if (CraftEngineItems.isCustomItem(item)) return false;
            return item.getType().name().contains("_SHOVEL");
        }
    },

    /** 锄 — 拆解交通工具 */
    HOE {
        @Override
        public boolean matches(ItemStack item) {
            if (CraftEngineItems.isCustomItem(item)) return false;
            return item.getType().name().contains("_HOE");
        }
    },

    /** 剪刀 — 剪取回收 */
    SHEARS {
        @Override
        public boolean matches(ItemStack item) {
            if (CraftEngineItems.isCustomItem(item)) return false;
            return item.getType() == Material.SHEARS;
        }
    };

    /** 所有 CE 刀具的 Key 集合 */
    private static final Set<Key> knifeKeys = Set.of(
        Key.of("manyidea", "flint_knife"),
        Key.of("manyidea", "iron_knife"),
        Key.of("manyidea", "golden_knife"),
        Key.of("manyidea", "diamond_knife"),
        Key.of("manyidea", "netherite_knife")
    );

    /**
     * 判断给定物品是否属于此工具分类。
     */
    public abstract boolean matches(ItemStack item);

    /** 是否为刀类（含原版剑和 CE 刀） */
    public boolean isKnifeLike() {
        return this == KNIFE;
    }

    /** 是否为斧类 */
    public boolean isAxeLike() {
        return this == AXE || this == AXE_STRIP;
    }
}
