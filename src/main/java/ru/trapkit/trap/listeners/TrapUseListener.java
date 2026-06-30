package ru.trapkit.trap.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.trapkit.trap.TrapPlugin;
import ru.trapkit.trap.util.Messages;

public class TrapUseListener implements Listener {

    private final TrapPlugin plugin;

    public TrapUseListener(TrapPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        Player user = event.getPlayer();
        ItemStack hand = user.getInventory().getItemInMainHand();

        if (!plugin.getTrapItemManager().isTrapItem(hand)) {
            return;
        }

        event.setCancelled(true);

        if (plugin.getRoomManager().isTrapped(target)) {
            return;
        }

        hand.setAmount(hand.getAmount() - 1);

        plugin.getRoomManager().trapPlayer(target);

        Messages.send(user, "trapped-other", "%player%", target.getName());
        if (!target.equals(user)) {
            Messages.send(target, "trapped");
        }
    }
}
