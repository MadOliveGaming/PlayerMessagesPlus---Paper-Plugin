package com.madolivegaming.playermessagesplus;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ReconnectListener implements Listener {
    private final PlayerMessagesPlus plugin;
    private final Map<UUID, DisconnectState> pending = new HashMap<>();

    private static final class DisconnectState {
        final long disconnectedAt;
        final long windowMillis;
        final Component leaveMessage;
        boolean leaveSent;
        BukkitTask leaveTask;
        BukkitTask expiryTask;
        DisconnectState(long at, long windowMillis, Component msg) { disconnectedAt = at; this.windowMillis = windowMillis; leaveMessage = msg; }
        void cancel() { if (leaveTask != null) leaveTask.cancel(); if (expiryTask != null) expiryTask.cancel(); }
    }

    public ReconnectListener(PlayerMessagesPlus plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PluginSettings s = plugin.settings().get();
        Component selected = plugin.messages().customOrPersonal("leave", player);
        Component vanilla = event.quitMessage();
        Component outgoing = selected != null ? selected : vanilla;
        event.quitMessage(null);

        DisconnectState old = pending.remove(player.getUniqueId());
        if (old != null) old.cancel();
        long now = System.currentTimeMillis();
        int window = s.rejoinMessageWindow;
        DisconnectState state = new DisconnectState(now, window * 1000L, outgoing);
        pending.put(player.getUniqueId(), state);


        int configuredDelay = s.suppressLeaveMessageDuringRejoin;
        int effectiveDelay = s.anyRejoinMessageEnabled() ? Math.min(configuredDelay, window) : window;
        if (effectiveDelay <= 0) sendLeave(state);
        else state.leaveTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendLeaveIfCurrent(player.getUniqueId(), state), effectiveDelay * 20L);
        state.expiryTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> expire(player.getUniqueId(), state), window * 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        notifyOperatorOnJoin(player);
        DisconnectState state = pending.get(player.getUniqueId());
        boolean reconnect = state != null && System.currentTimeMillis() - state.disconnectedAt < state.windowMillis;
        if (!reconnect) {
            if (state != null) {
                if (!state.leaveSent) sendLeave(state);
                state.cancel(); pending.remove(player.getUniqueId());
            }
            Component selected = plugin.messages().customOrPersonal("join", player);
            if (selected != null) { event.joinMessage(null); plugin.messages().broadcast(selected); }
            return;
        }

        event.joinMessage(null);
        state.cancel(); pending.remove(player.getUniqueId());
        PluginSettings s = plugin.settings().get();
        if (!s.anyRejoinMessageEnabled()) return; // silent reconnect mode
        Component rejoin = plugin.messages().customOrPersonal("rejoin", player);
        if (rejoin == null) rejoin = Component.text(player.getName() + " rejoined the game", NamedTextColor.YELLOW);
        plugin.messages().broadcast(rejoin);
    }

    private void notifyOperatorOnJoin(Player player) {
        if (!player.isOp() || plugin.cachedIssueCount() <= 0) return;
        ChatMessages.send(player, "[PlayerMessages+] " + plugin.cachedIssueCount()
                + " issues were found in " + plugin.cachedIssueFileCount()
                + " JSON files. Use /pmpadmin verify for details.");
    }

    private void sendLeaveIfCurrent(UUID uuid, DisconnectState state) {
        if (pending.get(uuid) == state) sendLeave(state);
    }
    private void sendLeave(DisconnectState state) {
        if (state.leaveSent) return;
        state.leaveSent = true;
        plugin.messages().broadcast(state.leaveMessage);
    }
    private void expire(UUID uuid, DisconnectState state) {
        if (pending.get(uuid) != state) return;
        if (!state.leaveSent) sendLeave(state);
        if (state.leaveTask != null) state.leaveTask.cancel();
        pending.remove(uuid);
    }

    public void clearTransientState() {
        for (DisconnectState state : pending.values()) state.cancel();
        pending.clear();
    }
}
