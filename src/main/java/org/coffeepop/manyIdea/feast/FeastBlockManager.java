package org.coffeepop.manyIdea.feast;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宴席方块持久化管理器 — 对标 FD FeastBlock。
 * <p>
 * 每个宴席方块存储剩余份数（servings），0 时方块被破坏。
 */
public final class FeastBlockManager {

    private final JavaPlugin plugin;
    private final Map<Location, FeastData> feasts = new ConcurrentHashMap<>();
    private final File dataFile;

    public FeastBlockManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "feast_blocks.yml");
    }

    public void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            Location loc = yaml.getLocation(key);
            if (loc == null) continue;
            int servings = yaml.getInt(key + ".servings", 0);
            String feastKey = yaml.getString(key + ".key", "");
            if (servings > 0 && !feastKey.isEmpty()) {
                feasts.put(loc, new FeastData(Key.of(feastKey), servings));
            }
        }
        plugin.getLogger().info("Loaded " + feasts.size() + " feast blocks");
    }

    public void save() {
        if (feasts.isEmpty()) {
            if (dataFile.exists()) dataFile.delete();
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (var entry : feasts.entrySet()) {
            String key = String.valueOf(i++);
            Location loc = entry.getKey();
            yaml.set(key + ".world", loc.getWorld().getName());
            yaml.set(key + ".x", loc.getBlockX());
            yaml.set(key + ".y", loc.getBlockY());
            yaml.set(key + ".z", loc.getBlockZ());
            yaml.set(key + ".servings", entry.getValue().servings);
            yaml.set(key + ".key", entry.getValue().feastKey.toString());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save feast data: " + e.getMessage());
        }
    }

    public void place(Block block, Key feastKey, int servings) {
        feasts.put(block.getLocation().toBlockLocation(), new FeastData(feastKey, servings));
    }

    public int getServings(Block block) {
        FeastData data = feasts.get(block.getLocation().toBlockLocation());
        return data != null ? data.servings : 0;
    }

    public Key getFeastKey(Block block) {
        FeastData data = feasts.get(block.getLocation().toBlockLocation());
        return data != null ? data.feastKey : null;
    }

    /** 取走一份，返回剩余份数；0 表示已吃光 */
    public int takeServing(Block block) {
        Location loc = block.getLocation().toBlockLocation();
        FeastData data = feasts.get(loc);
        if (data == null || data.servings <= 0) return 0;
        data.servings--;
        if (data.servings <= 0) {
            feasts.remove(loc);
            return 0;
        }
        return data.servings;
    }

    public void remove(Block block) {
        feasts.remove(block.getLocation().toBlockLocation());
    }

    /** 重载时清理不存在的方块 */
    public void restoreAll() {
        for (var entry : feasts.entrySet()) {
            Location loc = entry.getKey();
            World world = loc.getWorld();
            if (world == null) continue;
            Block block = world.getBlockAt(loc);
            if (!isFeastBlock(block)) {
                feasts.remove(loc);
            }
        }
    }

    private boolean isFeastBlock(Block block) {
        if (!CraftEngineBlocks.isCustomBlock(block)) return false;
        var s = CraftEngineBlocks.getCustomBlockState(block);
        return s != null && s.owner() != null && s.owner().value() != null
            && s.owner().value().id().namespace().equals("manyidea")
            && s.owner().value().id().value().contains("_block");
    }

    // ---- 内部类 ----

    static class FeastData {
        final Key feastKey;
        int servings;

        FeastData(Key feastKey, int servings) {
            this.feastKey = feastKey;
            this.servings = servings;
        }
    }
}
