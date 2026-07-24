package org.coffeepop.manyIdea.framework;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comfort 系统 - 模拟农夫乐事的"舒适"效果。
 * <p>
 * 原版机制：饥饿值低时仍持续回血，最低 4 秒 1HP。
 * 这里通过 Saturation（维持饱食度）+ Regeneration（回血）来近似实现。
 * <p>
 * 使用时在食物消费后调用 {@link #addComfort}，
 * 系统会自动管理 effect 的计时和移除。
 */
public final class ComfortSystem {

    private final JavaPlugin plugin;
    private final Map<UUID, ComfortState> states = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ComfortSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒 tick
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        states.clear();
    }

    /**
     * 为玩家添加 Comfort 效果（秒）。
     */
    public void addComfort(Player player, int seconds) {
        if (seconds <= 0) return;
        UUID uuid = player.getUniqueId();
        ComfortState state = states.computeIfAbsent(uuid, k -> new ComfortState());
        state.ticksRemaining = Math.max(state.ticksRemaining, seconds * 20);
    }

    private void tick() {
        for (var entry : states.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                states.remove(entry.getKey());
                continue;
            }

            ComfortState state = entry.getValue();
            state.ticksRemaining--;

            if (state.ticksRemaining <= 0) {
                states.remove(entry.getKey());
                continue;
            }

            // 每 4 秒（80 ticks）触发一次
            if (state.ticksRemaining % 80 == 0) {
                // Saturation 维持饱食度
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SATURATION, 20 * 3, 0,
                    false, false, true
                ));
                // Regeneration 回血
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION, 20 * 5, 0,
                    false, false, true
                ));
            }
        }
    }

    private static class ComfortState {
        int ticksRemaining;
    }
}
