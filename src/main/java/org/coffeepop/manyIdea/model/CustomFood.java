package org.coffeepop.manyIdea.model;

import net.momirealms.craftengine.core.util.Key;

import java.util.Collections;
import java.util.List;

/**
 * 自定义食物附加数据。
 * 营养值/饱食度由 CE items YAML 中的 food 配置处理，此处仅管理额外效果。
 *
 * @param id              CraftEngine 物品 ID
 * @param comfortSeconds  Comfort 效果持续秒数，0 表示无此效果
 * @param effects         食用时附加的药水效果
 */
public record CustomFood(
    Key id,
    int comfortSeconds,
    List<PotionEffectData> effects
) {
    public CustomFood {
        effects = effects != null ? List.copyOf(effects) : Collections.emptyList();
    }

    public record PotionEffectData(
        String type,
        int durationSeconds,
        int amplifier
    ) {}
}
