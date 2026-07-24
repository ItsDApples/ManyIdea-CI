package org.coffeepop.manyIdea.cutting;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 砧板物品持久化管理器 + ItemDisplay 实体管理。
 * <p>
 * 新增：雕刻模式（carved）— 对标 FD 的 isItemCarvingBoard，
 * 工具展示时用不同渲染姿态。
 */
public final class CuttingBoardManager {

    private final JavaPlugin plugin;
    private final Map<Location, ItemStack> boards = new ConcurrentHashMap<>();
    private final Set<Location> carvedBoards = ConcurrentHashMap.newKeySet();
    private final Map<Location, ItemDisplay> displays = new ConcurrentHashMap<>();
    private final File dataFile;

    public CuttingBoardManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "cutting_boards.yml");
    }

    // ---- 持久化 ----

    public void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            Location loc = yaml.getLocation(key);
            ItemStack item = yaml.getItemStack(key + ".item");
            if (loc != null && item != null && !item.getType().isAir()) {
                boards.put(loc, item);
                if (yaml.getBoolean(key + ".carved", false)) {
                    carvedBoards.add(loc);
                }
            }
        }
        plugin.getLogger().info("Loaded " + boards.size() + " cutting board items");
    }

    public void save() {
        if (boards.isEmpty()) {
            if (dataFile.exists()) dataFile.delete();
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (var entry : boards.entrySet()) {
            String key = String.valueOf(i++);
            Location loc = entry.getKey();
            yaml.set(key + ".world", loc.getWorld().getName());
            yaml.set(key + ".x", loc.getBlockX());
            yaml.set(key + ".y", loc.getBlockY());
            yaml.set(key + ".z", loc.getBlockZ());
            yaml.set(key + ".item", entry.getValue());
            yaml.set(key + ".carved", carvedBoards.contains(loc));
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save cutting board data: " + e.getMessage());
        }
    }

    // ---- 物品存取 ----

    public boolean hasItem(Block block) {
        return boards.containsKey(block.getLocation().toBlockLocation());
    }

    public ItemStack getItem(Block block) {
        return boards.get(block.getLocation().toBlockLocation());
    }

    public void setItem(Block block, ItemStack item) {
        Location loc = block.getLocation().toBlockLocation();
        if (item == null || item.getType().isAir()) {
            boards.remove(loc);
            carvedBoards.remove(loc);
            removeDisplay(loc);
        } else {
            boards.put(loc, item.clone());
            spawnOrUpdateDisplay(block, item);
        }
    }

    /** 放置雕刻工具（对标 FD carveToolOnBoard） */
    public void setCarvedTool(Block block, ItemStack tool) {
        Location loc = block.getLocation().toBlockLocation();
        boards.put(loc, tool.clone());
        carvedBoards.add(loc);
        spawnOrUpdateDisplay(block, tool);
    }

    public void remove(Block block) {
        Location loc = block.getLocation().toBlockLocation();
        boards.remove(loc);
        carvedBoards.remove(loc);
        removeDisplay(loc);
    }

    public boolean isCarved(Block block) {
        return carvedBoards.contains(block.getLocation().toBlockLocation());
    }

    public boolean isCarved(Location loc) {
        return carvedBoards.contains(loc);
    }

    /** 获取所有砧板条目（供比较器任务遍历） */
    public Map<Location, ItemStack> getBoardEntries() {
        return Collections.unmodifiableMap(boards);
    }

    // ---- Display Entity ----

    private void spawnOrUpdateDisplay(Block block, ItemStack item) {
        Location loc = block.getLocation().toBlockLocation();
        boolean carved = carvedBoards.contains(loc);

        // 已有 display → 更新
        ItemDisplay existing = displays.get(loc);
        if (existing != null && existing.isValid()) {
            existing.setItemStack(item);
            existing.setTransformation(carved ? carvedTransform() : flatTransform());
            existing.setInterpolationDuration(0);
            existing.setTeleportDuration(1);
            return;
        }

        // 创建新的 ItemDisplay
        World world = block.getWorld();
        Location spawnLoc = block.getLocation().add(0.5, 0.08, 0.5);

        ItemDisplay display = (ItemDisplay) world.spawnEntity(spawnLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item);
        display.setPersistent(false);
        display.setTransformation(carved ? carvedTransform() : flatTransform());
        display.setInterpolationDuration(0);
        display.setTeleportDuration(1);

        displays.put(loc, display);
    }

    /** 默认平放姿态（食物/方块的扁平渲染） */
    public static Transformation flatTransform() {
        return new Transformation(
            new Vector3f(0, 0, 0),
            new Quaternionf().rotateX((float) Math.toRadians(-90)),
            new Vector3f(0.6f, 0.6f, 0.6f),
            new Quaternionf()
        );
    }

    /** 雕刻工具姿态：抬高 + 缩小 + 倾斜展示，对标 FD BER 的 renderItemCarved */
    public static Transformation carvedTransform() {
        return new Transformation(
            new Vector3f(0, 0.15f, 0),                             // 抬高 0.15
            new Quaternionf().rotateX((float) Math.toRadians(-45)), // 倾斜 45°
            new Vector3f(0.6f, 0.6f, 0.6f),
            new Quaternionf()
        );
    }

    private void removeDisplay(Location loc) {
        ItemDisplay display = displays.remove(loc);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    /** 服务器重载/启动时，清除残存 display 并重建 */
    public void restoreAllDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(ItemDisplay.class)) {
                Location blockLoc = e.getLocation().toBlockLocation();
                if (boards.containsKey(blockLoc)) {
                    e.remove();
                }
            }
        }
        for (var entry : boards.entrySet()) {
            Location loc = entry.getKey();
            World world = loc.getWorld();
            if (world == null) continue;
            Block block = world.getBlockAt(loc);
            spawnOrUpdateDisplay(block, entry.getValue());
        }
        plugin.getLogger().info("Restored " + displays.size() + " cutting board displays");
    }
}
