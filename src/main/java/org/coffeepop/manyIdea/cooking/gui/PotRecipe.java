package org.coffeepop.manyIdea.cooking.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Multi-ingredient cooking pot recipe. Ingredient order does not matter.
 */
public record PotRecipe(Set<Material> ingredients, ItemStack result, int cookTime) {

    public PotRecipe {
        if (ingredients == null || ingredients.isEmpty())
            throw new IllegalArgumentException("ingredients must not be empty");
        if (result == null || result.getType().isAir())
            throw new IllegalArgumentException("result must not be empty/air");
        if (cookTime <= 0)
            throw new IllegalArgumentException("cookTime must be positive");
    }
}
