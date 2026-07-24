package org.coffeepop.manyIdea.cooking;

import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * 烹饪锅数据 — 一个锅在同一时间只烹饪一个物品。
 */
class PotData {
    ItemStack input;        // 放入的原料
    ItemStack result;       // 烹饪后的产物
    long startTick;         // 开始烹饪的游戏刻
    int cookTime;           // 烹饪所需总 tick（默认 200 = 10秒）
    Item displayItem;       // 锅上悬浮展示的物品实体

    PotData(ItemStack input, ItemStack result, long startTick, int cookTime, Item displayItem) {
        this.input = input.clone();
        this.result = result.clone();
        this.startTick = startTick;
        this.cookTime = cookTime;
        this.displayItem = displayItem;
    }

    boolean isDone(long currentTick) {
        return currentTick - startTick >= cookTime;
    }
}
