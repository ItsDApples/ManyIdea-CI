package org.coffeepop.manyIdea.framework;

import net.momirealms.craftengine.core.util.Key;
import org.coffeepop.manyIdea.model.CustomFood;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 食物注册表 - 静态管理所有的自定义食物数据。
 * 在 onEnable 时注册，食物消费时查询。
 */
public final class FoodRegistry {

    private static final Map<Key, CustomFood> FOODS = new HashMap<>();

    private FoodRegistry() {}

    public static void register(CustomFood food) {
        FOODS.put(food.id(), food);
    }

    public static CustomFood get(Key id) {
        return FOODS.get(id);
    }

    public static boolean contains(Key id) {
        return FOODS.containsKey(id);
    }

    public static Map<Key, CustomFood> all() {
        return Collections.unmodifiableMap(FOODS);
    }
}
