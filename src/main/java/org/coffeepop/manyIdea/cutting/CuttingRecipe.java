package org.coffeepop.manyIdea.cutting;

import org.bukkit.Sound;

import java.util.Collections;
import java.util.List;

/**
 * 砧板加工配方 — 对标 FD 的 CuttingBoardRecipe。
 * <p>
 * 支持多产物（最多 4 个）、独立概率、工具分类、可选自定义音效。
 *
 * @param input       原料 ID（"manyidea:tomato" 或 "minecraft:beef"）
 * @param toolCategory 所需工具分类
 * @param results     产出列表（最多 4 个 CuttingResult）
 * @param sound       自定义加工音效，null 时根据工具类型自动选择
 */
public record CuttingRecipe(
    String input,
    ToolCategory toolCategory,
    List<CuttingResult> results,
    Sound sound        // null = auto-detect based on tool
) {
    public static final int MAX_RESULTS = 4;

    public CuttingRecipe {
        if (results.size() > MAX_RESULTS)
            throw new IllegalArgumentException("Too many results (max " + MAX_RESULTS + ")");
        results = List.copyOf(results);
    }

    // ---- 便捷构造器 ----

    public CuttingRecipe(String input, ToolCategory tool, Sound sound, CuttingResult... results) {
        this(input, tool, List.of(results), sound);
    }

    public CuttingRecipe(String input, ToolCategory tool, CuttingResult... results) {
        this(input, tool, List.of(results), null);
    }
}
