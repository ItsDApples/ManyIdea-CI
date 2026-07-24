package org.coffeepop.manyIdea.listener;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.coffeepop.manyIdea.framework.AchievementManager;
import org.coffeepop.manyIdea.framework.ComfortSystem;
import org.coffeepop.manyIdea.framework.FoodRegistry;
import org.coffeepop.manyIdea.model.CustomFood;
import org.coffeepop.manyIdea.model.CustomFood.PotionEffectData;

/**
 * 通用食物消费监听 - 自动匹配 FoodRegistry 中的数据并应用效果。
 */
public final class FoodListener implements Listener {

    private final ComfortSystem comfortSystem;
    private final AchievementManager achievements;

    public FoodListener(ComfortSystem comfortSystem, AchievementManager achievements) {
        this.comfortSystem = comfortSystem;
        this.achievements = achievements;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (!CraftEngineItems.isCustomItem(item)) return;

        Key customId = CraftEngineItems.getCustomItemId(item);
        if (customId == null) return;

        CustomFood food = FoodRegistry.get(customId);
        if (food == null) return;

        // 播放进食音效
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);

        // 应用额外药水效果
        for (PotionEffectData data : food.effects()) {
            PotionEffectType type = PotionEffectType.getByName(data.type());
            if (type == null) continue;
            player.addPotionEffect(new PotionEffect(
                type,
                data.durationSeconds() * 20,
                data.amplifier(),
                false, true, true
            ));
        }

        // 应用 Comfort 效果
        if (food.comfortSeconds() > 0) {
            comfortSystem.addComfort(player, food.comfortSeconds());
            // FD 成就：首次吃 nourishing 食物
            achievements.getNourishingFood().grant(player);
        }
    }
}
