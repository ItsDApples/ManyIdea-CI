package org.coffeepop.manyIdea.cooking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;

/**
 * 基于 NMS AbstractFurnaceMenu 的烹饪锅 GUI。
 * <p>
 * 对标 FD CookingPotScreen：
 * - 炉子原生进度箭头动画（DataSlot index 2/3）
 * - 火焰图标始终满（外部热源，DataSlot index 0/1 恒为 1）
 * - 输入槽只接受配方物品、输出槽只允许取出
 */
public final class CookingPotMenuHandle {

    static final int SLOT_INPUT  = 0; // 炉子输入槽
    static final int SLOT_FUEL   = 1; // 炉子燃料槽（锁定）
    static final int SLOT_OUTPUT = 2; // 炉子输出槽
    static final int CONTAINER_SIZE = 3;

    public final SimpleContainer container;
    public final ContainerData data;
    public final AbstractContainerMenu menu;
    private final ServerPlayer serverPlayer;

    private CookingPotMenuHandle(ServerPlayer serverPlayer, SimpleContainer container,
                                 ContainerData data, AbstractContainerMenu menu) {
        this.serverPlayer = serverPlayer;
        this.container = container;
        this.data = data;
        this.menu = menu;
    }

    /**
     * 打开烹饪锅 GUI。
     *
     * @param player      玩家
     * @param recipeCheck 物品是否为有效配方输入
     * @param isDone      烹饪是否已完成
     * @param onClosed    容器关闭回调
     * @param onOutputTaken 取出成品回调
     * @param cookTime    烹饪总 tick
     * @param currentProgress 当前已烹饪 tick
     */
    public static CookingPotMenuHandle open(Player player,
                                             java.util.function.Predicate<org.bukkit.inventory.ItemStack> recipeCheck,
                                             java.util.function.BooleanSupplier isDone,
                                             Runnable onClosed, Runnable onOutputTaken,
                                             int cookTime, int currentProgress) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        SimpleContainer container = new SimpleContainer(CONTAINER_SIZE);

        // 4 个 DataSlot：0=burnTime, 1=burnTotal, 2=progress, 3=cookTotal
        ContainerData progressData = new ContainerData() {
            private final int[] values = {1, 1, currentProgress, cookTime};
            @Override public int get(int i) { return values[i]; }
            @Override public void set(int i, int v) { values[i] = v; }
            @Override public int getCount() { return 4; }
        };

        nmsPlayer.openMenu(new MenuProvider() {
            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv,
                                                     net.minecraft.world.entity.player.Player p) {
                // 先创建标准炉子菜单
                FurnaceMenu fm = new FurnaceMenu(syncId, inv, container, progressData);
                // 替换默认槽位为自定义槽位
                replaceSlots(fm, container, recipeCheck, isDone, onClosed, onOutputTaken);
                return fm;
            }
            @Override
            public Component getDisplayName() {
                return Component.literal("烹饪锅");
            }
        });

        return new CookingPotMenuHandle(nmsPlayer, container, progressData, nmsPlayer.containerMenu);
    }

    /** 替换 FurnaceMenu 的默认槽位为自定义限制槽位，注入回调和 BukkitView */
    private static void replaceSlots(FurnaceMenu menu, SimpleContainer container,
                                      java.util.function.Predicate<org.bukkit.inventory.ItemStack> recipeCheck,
                                      java.util.function.BooleanSupplier isDone,
                                      Runnable onClosed, Runnable onOutputTaken) {
        // 清除非玩家槽位（前 3 个）
        menu.slots.subList(0, 3).clear();
        // 重新添加自定义槽位（顺序必须和原来一致）
        menu.slots.add(0, new InputSlot(container, SLOT_INPUT, 56, 17, recipeCheck));
        menu.slots.add(1, new FuelLockedSlot(container, SLOT_FUEL, 56, 53));
        menu.slots.add(2, new OutputSlot(container, SLOT_OUTPUT, 116, 35, isDone, onOutputTaken));

        // 注入关闭和输出回调到 menu 实例（用匿名子类委托）
        // 通过改写 removed 行为：监听 containerMenu 的关闭
        // 这里用 Bukkit 调度器检测容器关闭
        new org.bukkit.scheduler.BukkitRunnable() {
            private boolean closed = false;
            @Override
            public void run() {
                if (closed) { cancel(); return; }
                for (var viewer : menu.getBukkitView().getTopInventory().getViewers()) {
                    if (((org.bukkit.entity.HumanEntity) viewer).getOpenInventory().getTopInventory()
                            != menu.getBukkitView().getTopInventory()) {
                        closed = true;
                        if (onClosed != null) onClosed.run();
                        cancel();
                        break;
                    }
                }
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("ManyIdea"), 10L, 10L);
    }

    public void close() {
        serverPlayer.closeContainer();
    }

    /** 更新进度（DataSlot index 2/3），客户端自动渲染箭头 */
    public void updateProgress(int cookTime, int progress) {
        data.set(2, progress);
        data.set(3, cookTime);
        serverPlayer.containerMenu.broadcastChanges();
    }

    /** 获取输入槽（NMS） */
    public ItemStack getInputSlow() {
        return container.getItem(SLOT_INPUT);
    }

    public void setInput(org.bukkit.inventory.ItemStack bukkitStack) {
        container.setItem(SLOT_INPUT, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(bukkitStack));
    }

    public void setOutput(org.bukkit.inventory.ItemStack bukkitStack) {
        container.setItem(SLOT_OUTPUT, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(bukkitStack));
    }

    public void clearInput() { container.setItem(SLOT_INPUT, ItemStack.EMPTY); }
    public void clearOutput() { container.setItem(SLOT_OUTPUT, ItemStack.EMPTY); }

    // ================================================================
    // 自定义槽位
    // ================================================================

    static class InputSlot extends Slot {
        private final java.util.function.Predicate<org.bukkit.inventory.ItemStack> recipeCheck;
        InputSlot(net.minecraft.world.Container c, int idx, int x, int y,
                  java.util.function.Predicate<org.bukkit.inventory.ItemStack> recipeCheck) {
            super(c, idx, x, y);
            this.recipeCheck = recipeCheck;
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return recipeCheck.test(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(stack));
        }
    }

    /** 燃料槽：锁定，始终显示火焰（用烈焰棒代替） */
    static class FuelLockedSlot extends Slot {
        FuelLockedSlot(net.minecraft.world.Container c, int idx, int x, int y) {
            super(c, idx, x, y);
            // 放入火焰指示物品
            c.setItem(idx, new ItemStack(Items.BLAZE_POWDER));
        }
        @Override public boolean mayPlace(ItemStack s) { return false; }
        @Override public boolean mayPickup(net.minecraft.world.entity.player.Player p) { return false; }
    }

    static class OutputSlot extends Slot {
        private final java.util.function.BooleanSupplier isDone;
        private final Runnable onOutputTaken;
        OutputSlot(net.minecraft.world.Container c, int idx, int x, int y,
                   java.util.function.BooleanSupplier isDone, Runnable onOutputTaken) {
            super(c, idx, x, y);
            this.isDone = isDone;
            this.onOutputTaken = onOutputTaken;
        }
        @Override public boolean mayPlace(ItemStack s) { return false; }
        @Override
        public boolean mayPickup(net.minecraft.world.entity.player.Player p) {
            return isDone.getAsBoolean();
        }
        @Override
        public void onTake(net.minecraft.world.entity.player.Player p, ItemStack stack) {
            super.onTake(p, stack);
            if (onOutputTaken != null) {
                org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("ManyIdea"), onOutputTaken);
            }
        }
    }
}
