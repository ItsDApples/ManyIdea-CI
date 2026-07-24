package org.coffeepop.manyIdea.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Cake;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.Vector;

import org.coffeepop.manyIdea.cutting.ToolCategory;

/**
 * 菜刀世界交互 — 对标 FD KnifeItem + KnifeEvents。
 * <p>
 * - 南瓜雕刻
 * - 蛋糕切片
 * - 击退削弱
 */
public final class KnifeListener implements Listener {

    // ================================================================
    // 南瓜雕刻（对标 FD KnifeItem.useOn）
    // ================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCarvePumpkin(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.PUMPKIN) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!ToolCategory.KNIFE.matches(tool)) return;

        event.setCancelled(true);

        // 雕刻南瓜
        Directional carved = (Directional) Material.CARVED_PUMPKIN.createBlockData();
        BlockFace face = event.getBlockFace();
        if (face.getModY() != 0) {
            face = player.getFacing().getOppositeFace();
        }
        carved.setFacing(face);
        block.setBlockData(carved);

        // 掉落 4 个南瓜种子
        block.getWorld().spawnParticle(
            org.bukkit.Particle.ITEM,
            block.getLocation().add(0.5, 0.5, 0.5),
            5, 0.1, 0.1, 0.1, 0.05,
            new ItemStack(Material.PUMPKIN_SEEDS));
        block.getWorld().dropItemNaturally(
            block.getLocation().add(0.5, 0.5, 0.5),
            new ItemStack(Material.PUMPKIN_SEEDS, 4));

        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_PUMPKIN_CARVE, SoundCategory.BLOCKS, 1.0f, 1.0f);

        // 消耗耐久
        damageTool(tool, player);
    }

    // ================================================================
    // 蛋糕切片（对标 FD KnifeEvents.onCakeInteraction）
    // ================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSliceCake(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAKE) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!ToolCategory.KNIFE.matches(tool)) return;

        event.setCancelled(true);

        Cake cake = (Cake) block.getBlockData();
        int bites = cake.getBites();

        if (bites < 6) {
            cake.setBites(bites + 1);
            block.setBlockData(cake);
        } else {
            block.setType(Material.AIR);
        }

        // 掉落蛋糕片 (表示用蜜饯替代，因为我们没蛋糕片物品)
        if (bites < 6) {
            Item dropped = block.getWorld().dropItemNaturally(
                block.getLocation().add(0.3 + bites * 0.1, 0.5, 0.5),
                new ItemStack(Material.COOKIE)); // FD: cake_slice
            dropped.setVelocity(new Vector(-0.05, 0, 0));
        }

        block.getWorld().playSound(block.getLocation(), Sound.ENTITY_PLAYER_BURP,
            SoundCategory.PLAYERS, 0.8f, 0.8f);

        damageTool(tool, player);
    }

    // ================================================================
    // 击退削弱（对标 FD KnifeEvents.onKnifeKnockback）
    // ================================================================

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onKnifeKnockback(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!ToolCategory.KNIFE.matches(weapon)) return;

        // 减少击退：设置受害者的速度为 0（模拟击退减弱）
        // FD 的做法：setStrength(originalStrength - 0.1F)
        // Bukkit 中通过减半击退向量近似
        Vector currentVel = victim.getVelocity();
        if (currentVel.lengthSquared() > 0.01) {
            victim.setVelocity(currentVel.multiply(0.7)); // 削弱 30%
        }
    }

    // ---- 工具方法 ----

    private void damageTool(ItemStack tool, Player player) {
        if (tool.getItemMeta() instanceof Damageable dmg) {
            dmg.setDamage(dmg.getDamage() + 1);
            tool.setItemMeta(dmg);
        }
    }
}
