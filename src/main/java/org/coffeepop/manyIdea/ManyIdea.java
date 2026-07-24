package org.coffeepop.manyIdea;

import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import org.coffeepop.manyIdea.cooking.BasinStorageListener;
import org.coffeepop.manyIdea.cooking.CookingPersistenceManager;
import org.coffeepop.manyIdea.cooking.CookingPotListener;
import org.coffeepop.manyIdea.cooking.DeepFryingPanListener;
import org.coffeepop.manyIdea.cooking.GrillListener;
import org.coffeepop.manyIdea.crop.CropListener;
import org.coffeepop.manyIdea.cutting.CuttingBoardListener;
import org.coffeepop.manyIdea.cutting.CuttingBoardManager;
import org.coffeepop.manyIdea.cutting.CuttingRecipes;
import org.coffeepop.manyIdea.feast.FeastBlockListener;
import org.coffeepop.manyIdea.feast.FeastBlockManager;
import org.coffeepop.manyIdea.framework.AchievementManager;
import org.coffeepop.manyIdea.framework.ComfortSystem;
import org.coffeepop.manyIdea.framework.FoodRegistry;
import org.coffeepop.manyIdea.listener.FoodListener;
import org.coffeepop.manyIdea.listener.KnifeListener;
import org.coffeepop.manyIdea.model.CustomFood;
import org.coffeepop.manyIdea.model.CustomFood.PotionEffectData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ManyIdea extends JavaPlugin {

    private ComfortSystem comfortSystem;
    private CuttingBoardManager cuttingBoardManager;
    private FeastBlockManager feastBlockManager;
    private AchievementManager achievementManager;
    private CookingPersistenceManager cookingPersistence;
    private CookingPotListener cookingPotListener;
    private GrillListener grillListener;
    private DeepFryingPanListener fryListener;

    @Override
    public void onEnable() {
        // 分发：自动解压 CE addon 配置 + 触发 reload
        installAddonResourceIfNeeded();

        comfortSystem = new ComfortSystem(this);
        comfortSystem.start();

        achievementManager = new AchievementManager(this);

        cuttingBoardManager = new CuttingBoardManager(this);
        cuttingBoardManager.load();
        CuttingRecipes.init();

        feastBlockManager = new FeastBlockManager(this);
        feastBlockManager.load();

        Bukkit.getScheduler().runTask(this, () -> cuttingBoardManager.restoreAllDisplays());

        registerFoods();

        // 创建烹饪监听器并保留引用（用于持久化）
        cookingPotListener = new CookingPotListener(this, achievementManager);
        grillListener = new GrillListener(this, achievementManager);
        fryListener = new DeepFryingPanListener(this, achievementManager);

        // 持久化：恢复烹饪进度
        cookingPersistence = new CookingPersistenceManager(this);
        cookingPotListener.restoreFromLoaded(cookingPersistence.loadPots(cookingPotListener));
        grillListener.restoreFromLoaded(cookingPersistence.loadGrills());
        fryListener.restoreFromLoaded(cookingPersistence.loadPans());

        // 定时自动保存（每 60 秒）
        Bukkit.getScheduler().runTaskTimer(this, this::saveCookingState, 1200L, 1200L);

        getServer().getPluginManager().registerEvents(new FoodListener(comfortSystem, achievementManager), this);
        getServer().getPluginManager().registerEvents(new CuttingBoardListener(cuttingBoardManager, this, achievementManager), this);
        getServer().getPluginManager().registerEvents(new KnifeListener(), this);
        getServer().getPluginManager().registerEvents(new FeastBlockListener(feastBlockManager, achievementManager), this);
        getServer().getPluginManager().registerEvents(new CropListener(this), this);
        getServer().getPluginManager().registerEvents(cookingPotListener, this);
        getServer().getPluginManager().registerEvents(grillListener, this);
        getServer().getPluginManager().registerEvents(fryListener, this);
        getServer().getPluginManager().registerEvents(new BasinStorageListener(this), this);

        getLogger().info("ManyIdea enabled! " + FoodRegistry.all().size() + " foods registered.");
    }

    @Override
    public void onDisable() {
        saveCookingState();
        if (comfortSystem != null) comfortSystem.stop();
        if (cuttingBoardManager != null) cuttingBoardManager.save();
        if (feastBlockManager != null) feastBlockManager.save();
        getLogger().info("ManyIdea disabled!");
    }

    private void saveCookingState() {
        if (cookingPersistence == null) return;
        cookingPersistence.save(
            cookingPotListener != null ? cookingPotListener.getPotMap() : Map.of(),
            grillListener != null ? grillListener.getGrillMap() : Map.of(),
            fryListener != null ? fryListener.getPanMap() : Map.of()
        );
    }

    // ---------- CE addon 分发逻辑 ----------

    /**
     * 首次安装或版本更新时，自动将 jar 内置的 CE addon 配置解压到
     * plugins/CraftEngine/resources/ 下，并触发 CE 重新加载资源包。
     * <p>
     * 版本匹配时会校验文件完整性，缺失或大小不匹配的会自动补全。
     */
    private void installAddonResourceIfNeeded() {
        Plugin cePlugin = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (cePlugin == null) {
            getLogger().warning("CraftEngine not found, skipping addon resource install.");
            return;
        }

        File ceDataFolder = cePlugin.getDataFolder(); // plugins/CraftEngine/
        File targetDir = new File(ceDataFolder, "resources");
        File versionFile = new File(targetDir, "manyidea/.version");

        String currentVersion = getDescription().getVersion();
        boolean versionMatch = false;
        if (versionFile.exists()) {
            try {
                String installedVersion = Files.readString(versionFile.toPath()).trim();
                versionMatch = installedVersion.equals(currentVersion);
            } catch (IOException ignored) {}
        }

        try {
            if (versionMatch) {
                // 版本匹配：仅校验 + 补全缺失/损坏的文件
                int fixed = extractAddonFromJar(targetDir, true);
                if (fixed > 0) {
                    getLogger().info("CE addon: repaired " + fixed + " missing/corrupted file(s).");
                }
            } else {
                // 版本更新或首次安装：全量覆盖
                extractAddonFromJar(targetDir, false);
                Files.writeString(versionFile.toPath(), currentVersion);
                getLogger().info("CE addon resources extracted to " + targetDir.getAbsolutePath());
            }
        } catch (IOException e) {
            getLogger().severe("Failed to extract CE addon resources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从当前 jar 中提取 addon/ 目录下的所有文件到目标目录。
     *
     * @param missingOnly true 时只写入缺失或大小不匹配的文件，返回修复的文件数
     * @return 当 missingOnly=true 时返回补充的文件数，否则返回 0
     */
    private int extractAddonFromJar(File targetDir, boolean missingOnly) throws IOException {
        File jarFile;
        try {
            jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IOException("Cannot locate plugin jar", e);
        }

        int repaired = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("addon/") || entry.isDirectory()) continue;

                String relativePath = name.substring("addon/".length());
                File outFile = new File(targetDir, relativePath);

                if (missingOnly) {
                    // 校验：文件存在且大小匹配则跳过
                    if (outFile.isFile() && outFile.length() == entry.getSize()) {
                        continue;
                    }
                    repaired++;
                }

                outFile.getParentFile().mkdirs();
                try (InputStream in = jar.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
            }
        }
        return repaired;
    }

    private void registerFoods() {
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "hamburger"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "barbecue_stick"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "cabbage"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "fried_egg"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "dumplings"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "chicken_soup"), 180,
            List.of(new PotionEffectData(PotionEffectType.ABSORPTION.getName(), 30, 0))));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "fruit_salad"), 60,
            List.of(new PotionEffectData(PotionEffectType.REGENERATION.getName(), 5, 0))));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "mixed_salad"), 60,
            List.of(new PotionEffectData(PotionEffectType.REGENERATION.getName(), 5, 0))));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "magic_apple"), 0,
            List.of(
                new PotionEffectData(PotionEffectType.REGENERATION.getName(), 10, 1),
                new PotionEffectData(PotionEffectType.ABSORPTION.getName(), 120, 1),
                new PotionEffectData(PotionEffectType.SPEED.getName(), 30, 0))));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "tomato"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "tomato_sauce"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "minced_beef"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "chicken_cuts"), 0, List.of()));
        FoodRegistry.register(new CustomFood(Key.of("manyidea", "cooked_chicken_cuts"), 0, List.of()));
    }
}
