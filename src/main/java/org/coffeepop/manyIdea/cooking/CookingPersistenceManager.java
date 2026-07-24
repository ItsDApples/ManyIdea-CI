package org.coffeepop.manyIdea.cooking;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 烹饪方块状态持久化管理器。
 * <p>
 * 保存烹饪锅/烤架/油炸锅的烹饪进度到 YAML，服务器重启后恢复。
 * 使用 Bukkit ItemStack 序列化保证 CE 自定义物品正确存储。
 */
public final class CookingPersistenceManager {

    private final JavaPlugin plugin;
    private final File file;

    public CookingPersistenceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cooking_state.yml");
    }

    /**
     * 保存所有烹饪数据。
     */
    public void save(Map<Location, CookingPotListener.PotData> pots,
                     Map<Location, GrillListener.GrillData> grills,
                     Map<Location, DeepFryingPanListener.FryData> pans) {
        YamlConfiguration yaml = new YamlConfiguration();

        saveCookingSection(yaml, "pots", pots, this::serializePot);
        saveCookingSection(yaml, "grills", grills, this::serializeGrill);
        saveCookingSection(yaml, "pans", pans, this::serializeFry);

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "保存烹饪状态失败", e);
        }
    }

    /** 加载烹饪锅数据。返回 world -> Location -> PotData 的映射。</p>需调用方传入 listener 引用用于生成 display item。 */
    public Map<Location, CookingPotListener.PotData> loadPots(CookingPotListener potListener) {
        YamlConfiguration yaml = loadYaml();
        Map<Location, CookingPotListener.PotData> result = new LinkedHashMap<>();
        ConfigurationSection sec = yaml.getConfigurationSection("pots");
        if (sec == null) return result;

        for (String key : sec.getKeys(false)) {
            Location loc = parseLocation(key);
            if (loc == null) continue;
            ConfigurationSection data = sec.getConfigurationSection(key);
            if (data == null) continue;

            ItemStack input = data.getItemStack("input");
            ItemStack resultItem = data.getItemStack("result");
            if (input == null || resultItem == null) continue;

            long startTick = data.getLong("start_tick");
            int cookTime = data.getInt("cook_time");
            // 恢复时重新计算 startTick，补偿服务器关闭期间的时间偏移
            long elapsed = data.getLong("elapsed", 0);
            long correctedStart = System.currentTimeMillis() / 50 - elapsed;

            CookingPotListener.PotData potData = new CookingPotListener.PotData(
                input, resultItem, correctedStart, cookTime, null);
            result.put(loc, potData);
        }
        return result;
    }

    /** 加载烤架数据 */
    public Map<Location, GrillListener.GrillData> loadGrills() {
        YamlConfiguration yaml = loadYaml();
        Map<Location, GrillListener.GrillData> result = new LinkedHashMap<>();
        ConfigurationSection sec = yaml.getConfigurationSection("grills");
        if (sec == null) return result;

        for (String key : sec.getKeys(false)) {
            Location loc = parseLocation(key);
            if (loc == null) continue;
            ConfigurationSection data = sec.getConfigurationSection(key);
            if (data == null) continue;

            ItemStack input = data.getItemStack("input");
            ItemStack resultItem = data.getItemStack("result");
            if (input == null || resultItem == null) continue;

            long elapsed = data.getLong("elapsed", 0);
            long correctedStart = System.currentTimeMillis() / 50 - elapsed;
            int cookTime = data.getInt("cook_time");

            result.put(loc, new GrillListener.GrillData(input, resultItem, correctedStart, cookTime, null));
        }
        return result;
    }

    /** 加载油炸锅数据 */
    public Map<Location, DeepFryingPanListener.FryData> loadPans() {
        YamlConfiguration yaml = loadYaml();
        Map<Location, DeepFryingPanListener.FryData> result = new LinkedHashMap<>();
        ConfigurationSection sec = yaml.getConfigurationSection("pans");
        if (sec == null) return result;

        for (String key : sec.getKeys(false)) {
            Location loc = parseLocation(key);
            if (loc == null) continue;
            ConfigurationSection data = sec.getConfigurationSection(key);
            if (data == null) continue;

            ItemStack input = data.getItemStack("input");
            ItemStack resultItem = data.getItemStack("result");
            if (input == null || resultItem == null) continue;

            long elapsed = data.getLong("elapsed", 0);
            long correctedStart = System.currentTimeMillis() / 50 - elapsed;
            int cookTime = data.getInt("cook_time");

            result.put(loc, new DeepFryingPanListener.FryData(input, resultItem, correctedStart, cookTime, null));
        }
        return result;
    }

    // ---- 内部序列化 ----

    @FunctionalInterface
    private interface Serializer<T> {
        void serialize(ConfigurationSection section, Location loc, T data);
    }

    private <T> void saveCookingSection(YamlConfiguration yaml, String section,
                                         Map<Location, T> map, Serializer<T> serializer) {
        ConfigurationSection sec = yaml.createSection(section);
        for (var entry : map.entrySet()) {
            ConfigurationSection data = sec.createSection(locToKey(entry.getKey()));
            serializer.serialize(data, entry.getKey(), entry.getValue());
        }
    }

    private void serializePot(ConfigurationSection sec, Location loc, CookingPotListener.PotData d) {
        sec.set("input", d.input());
        sec.set("result", d.result());
        sec.set("cook_time", d.cookTime());
        long elapsed = System.currentTimeMillis() / 50 - d.startTick();
        sec.set("elapsed", Math.max(0, elapsed));
    }

    private void serializeGrill(ConfigurationSection sec, Location loc, GrillListener.GrillData d) {
        sec.set("input", d.input());
        sec.set("result", d.result());
        sec.set("cook_time", d.cookTime());
        long elapsed = System.currentTimeMillis() / 50 - d.startTick();
        sec.set("elapsed", Math.max(0, elapsed));
    }

    private void serializeFry(ConfigurationSection sec, Location loc, DeepFryingPanListener.FryData d) {
        sec.set("input", d.input());
        sec.set("result", d.result());
        sec.set("cook_time", d.cookTime());
        long elapsed = System.currentTimeMillis() / 50 - d.startTick();
        sec.set("elapsed", Math.max(0, elapsed));
    }

    // ---- Location 序列化 ----

    private static String locToKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static Location parseLocation(String key) {
        int colon = key.indexOf(':');
        if (colon < 0) return null;
        String worldName = key.substring(0, colon);
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        String[] parts = key.substring(colon + 1).split(",");
        if (parts.length != 3) return null;
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private YamlConfiguration loadYaml() {
        if (!file.exists()) return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(file);
    }
}
