package org.coffeepop.manyIdea.util;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * 使用 Bukkit {@link ItemDisplay} 实体创建不可拾取的展示物品。
 * <p>
 * 参考 QuickShop DisplayEntityDisplayItem 方案，替代旧的 {@code Item} 掉落物方案。
 * ItemDisplay 无物理/无拾取/无重力，无需任何 workaround。
 */
public final class DisplayItemUtil {

    /** 默认缩放 */
    private static final Vector3f SCALE = new Vector3f(0.6f, 0.6f, 0.6f);

    private DisplayItemUtil() {}

    /**
     * 在指定位置生成一个展示物品。
     *
     * @param loc    生成位置
     * @param stack  要展示的 ItemStack
     * @param glow   是否发光
     * @return ItemDisplay 引用
     */
    public static ItemDisplay spawn(Location loc, ItemStack stack, boolean glow) {
        return loc.getWorld().spawn(loc.clone(), ItemDisplay.class, display -> {
            display.setItemStack(stack);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            display.setTransformation(new Transformation(
                new Vector3f(),       // translation
                new AxisAngle4f(),    // left rotation
                SCALE,                // scale
                new AxisAngle4f()     // right rotation
            ));
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setGlowing(glow);
        });
    }

    /** 安全移除展示实体 */
    public static void remove(ItemDisplay display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }
}
