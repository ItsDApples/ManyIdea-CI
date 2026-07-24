package org.coffeepop.manyIdea.cooking;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

/**
 * 基于 NMS AbstractContainerMenu + MenuType.FURNACE 的烹饪锅 GUI。
 * <p>
 * 对标 FD CookingPotScreen：
 * - 客户端渲染原生炉子进度箭头（用 MenuType.FURNACE 触发）
 * - DataSlot 0/1 恒为满值（燃烧图标始终亮）
 * - DataSlot 2/3 控制箭头进度
 * - 不依赖 FurnaceMenu（避免 getBukkitView ClassCastException）
 */
public final class CookingPotMenuHandle {

    static final int SLOT_INPUT  = 0;
    static final int SLOT_FUEL   = 1;
    static final int SLOT_OUTPUT = 2;
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

    public static CookingPotMenuHandle open(Player player,
                                             java.util.function.Predicate<org.bukkit.inventory.ItemStack> recipeCheck,
                                             java.util.function.BooleanSupplier isDone,
                                             Runnable onClosed, Runnable onOutputTaken,
                                             int cookTime, int currentProgress) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        SimpleContainer container = new SimpleContainer(CONTAINER_SIZE);

        ContainerData progressData = new ContainerData() {
            private final int[] values = {1, 1, currentProgress, cookTime};
            @Override public int get(int i) { return values[i]; }
            @Override public void set(int i, int v) { values[i] = v; }
            @Override public int getCount() { return 4; }
        };

        // 自定义 AbstractContainerMenu 子类，使用 MenuType.FURNACE 触发客户端渲染
        int syncId = nmsPlayer.nextContainerCounter();
        FurnaceLikeMenu menu = new FurnaceLikeMenu(syncId, nmsPlayer.getInventory(),
            container, progressData);

        // 添加自定义槽位
        menu.publicAddSlot(new InputSlot(container, SLOT_INPUT, 56, 17, recipeCheck));
        menu.publicAddSlot(new FuelLockedSlot(container, SLOT_FUEL, 56, 53));
        menu.publicAddSlot(new OutputSlot(container, SLOT_OUTPUT, 116, 35, isDone, onOutputTaken));

        // 打开GUI — 用 Furnace type 让客户端渲染进度箭头
        nmsPlayer.containerMenu = menu;
        nmsPlayer.connection.send(new ClientboundOpenScreenPacket(
            syncId, MenuType.FURNACE, Component.literal("烹饪锅")));
        nmsPlayer.initMenu(menu);

        // 监听关闭
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (nmsPlayer.containerMenu != menu) {
                    if (onClosed != null) onClosed.run();
                    cancel();
                }
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().getPlugin("ManyIdea"), 10L, 10L);

        return new CookingPotMenuHandle(nmsPlayer, container, progressData, menu);
    }

    public void close() {
        serverPlayer.closeContainer();
    }

    public void updateProgress(int cookTime, int progress) {
        data.set(2, progress);
        data.set(3, cookTime);
        // 直接发数据包更新客户端箭头
        serverPlayer.connection.send(new ClientboundContainerSetDataPacket(
            menu.containerId, 2, progress));
        serverPlayer.connection.send(new ClientboundContainerSetDataPacket(
            menu.containerId, 3, cookTime));
    }

    public org.bukkit.inventory.ItemStack getInput() {
        ItemStack nms = container.getItem(SLOT_INPUT);
        if (nms.isEmpty()) return null;
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(nms);
    }

    public void setInput(org.bukkit.inventory.ItemStack bukkitStack) {
        container.setItem(SLOT_INPUT,
            org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(bukkitStack));
    }

    public void setOutput(org.bukkit.inventory.ItemStack bukkitStack) {
        container.setItem(SLOT_OUTPUT,
            org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(bukkitStack));
    }

    public void clearInput() { container.setItem(SLOT_INPUT, ItemStack.EMPTY); }
    public void clearOutput() { container.setItem(SLOT_OUTPUT, ItemStack.EMPTY); }

    // ================================================================
    // 自定义 AbstractContainerMenu 子类
    // ================================================================

    static class FurnaceLikeMenu extends AbstractContainerMenu {
        private final SimpleContainer container;
        private final ContainerData data;

        FurnaceLikeMenu(int syncId, Inventory playerInv, SimpleContainer container, ContainerData data) {
            super(MenuType.FURNACE, syncId);
            this.container = container;
            this.data = data;
            addDataSlots(data);

            // 玩家背包槽位（炉子布局）
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 9; col++)
                    this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        void publicAddSlot(Slot slot) {
            this.addSlot(slot); // 子类内可访问 protected addSlot
        }

        @Override
        public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int index) {
            // 简易 shift+click：输出槽 → 玩家背包
            Slot slot = this.slots.get(index);
            if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
            ItemStack source = slot.getItem().copy();

            if (index == SLOT_OUTPUT) {
                if (!this.moveItemStackTo(source, 3, 39, true)) return ItemStack.EMPTY;
            } else if (index >= 3) {
                if (!this.moveItemStackTo(source, SLOT_INPUT, SLOT_INPUT + 1, false))
                    return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }

            if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            return source;
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }

        @Override
        public InventoryView getBukkitView() {
            return new CraftInventoryView(
                null, // Paper doesn't always need a real HumanEntity
                new CraftInventory(container),
                this
            );
        }
    }

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

    static class FuelLockedSlot extends Slot {
        FuelLockedSlot(net.minecraft.world.Container c, int idx, int x, int y) {
            super(c, idx, x, y);
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
