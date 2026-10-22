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
        final String playerName;
        final UUID playerUuid;
        final String leaveSourceText;
        boolean leaveSent;
        BukkitTask leaveTask;
        BukkitTask expiryTask;
        DisconnectState(long at, long windowMillis, Component msg, String playerName, UUID playerUuid, String leaveSourceText) { disconnectedAt = at; this.windowMillis = windowMillis; leaveMessage = msg; this.playerName = playerName; this.playerUuid = playerUuid; this.leaveSourceText = leaveSourceText; }
        void cancel() { if (leaveTask != null) leaveTask.cancel(); if (expiryTask != null) expiryTask.cancel(); }
    }

    public ReconnectListener(PlayerMessagesPlus plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PluginSettings s = plugin.settings().get();
        MessageService.Selection selected = plugin.messages().select("leave", player);
        Component vanilla = event.quitMessage();
        Component outgoing = selected != null ? selected.component() : vanilla;
        String sourceText = selected == null ? null : selected.sourceText();
        event.quitMessage(null);

        DisconnectState old = pending.remove(player.getUniqueId());
        if (old != null) old.cancel();
        long now = System.currentTimeMillis();
        int window = s.rejoinMessageWindow;
        DisconnectState state = new DisconnectState(now, window * 1000L, outgoing, player.getName(), player.getUniqueId(), sourceText);
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
            MessageService.Selection selected = plugin.messages().select("join", player);
            if (selected != null) {
                event.joinMessage(null);
                plugin.messages().broadcast(selected.component(), "join", player.getName(), player.getUniqueId(), selected.sourceText());
            } else {
                // The vanilla join message remains assigned to the event, so do not
                // broadcast it ourselves. Forward it only to DiscordSRV, provided
                // Minecraft will actually display a join message.
                Component vanillaJoin = event.joinMessage();
                if (vanillaJoin != null && plugin.discordSrv() != null) {
                    plugin.discordSrv().send("join", player.getName(), player.getUniqueId(), vanillaJoin, null);
                }
            }
            return;
        }

        event.joinMessage(null);
        state.cancel(); pending.remove(player.getUniqueId());
        PluginSettings s = plugin.settings().get();
        if (!s.anyRejoinMessageEnabled()) return; // silent reconnect mode
        MessageService.Selection selected = plugin.messages().select("rejoin", player);
        Component rejoin = selected == null ? Component.text(player.getName() + " rejoined the game", NamedTextColor.YELLOW) : selected.component();
        plugin.messages().broadcast(rejoin, "rejoin", player.getName(), player.getUniqueId(), selected == null ? null : selected.sourceText());
    }

    private void notifyOperatorOnJoin(Player player) {
        if (player.isOp() && plugin.cachedIssueCount() > 0) {
            ChatMessages.send(player, "[PlayerMessages+] " + plugin.cachedIssueCount()
                    + " issues were found in " + plugin.cachedIssueFileCount()
                    + " JSON files. Use /pmpadmin verify for details.");
        }
        if (plugin.discordSrv() != null) plugin.discordSrv().warnAdminOnJoin(player);
    }

    private void sendLeaveIfCurrent(UUID uuid, DisconnectState state) {
        if (pending.get(uuid) == state) sendLeave(state);
    }
    private void sendLeave(DisconnectState state) {
        if (state.leaveSent) return;
        state.leaveSent = true;
        plugin.messages().broadcast(state.leaveMessage, "leave", state.playerName, state.playerUuid, state.leaveSourceText);
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
