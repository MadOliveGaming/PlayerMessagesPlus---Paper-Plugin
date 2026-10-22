package com.madolivegaming.playermessagesplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MessageService {
    public record Selection(Component component, String sourceText) {}

    private final PlayerMessagesPlus plugin;
    public MessageService(PlayerMessagesPlus plugin) { this.plugin = plugin; }

    public Selection select(String type, Player player) {
        PluginSettings settings = plugin.settings().get();
        boolean personalEnabled = switch (type) {
            case "join" -> settings.usePersonalJoinMessages;
            case "leave" -> settings.usePersonalLeaveMessages;
            default -> settings.usePersonalRejoinMessages;
        };
        boolean customEnabled = switch (type) {
            case "join" -> settings.useCustomJoinMessages;
            case "leave" -> settings.useCustomLeaveMessages;
            default -> settings.useCustomRejoinMessages;
        };
        boolean banned = !player.isOp() && plugin.data().isBanned(player.getUniqueId());
        if (personalEnabled && !banned) {
            String personal = plugin.data().personal(player.getUniqueId(), type);
            if (personal != null) return new Selection(personalComponent(personal, player, settings), MiniMessageService.prepareForDiscord(personal, player.getName(), settings, true));
        }
        if (customEnabled) {
            List<String> options = plugin.data().shared(type);
            if (!options.isEmpty()) {
                String chosen = options.get(ThreadLocalRandom.current().nextInt(options.size()));
                return new Selection(MiniMessageService.render(chosen, player.getName(), settings, false), MiniMessageService.prepareForDiscord(chosen, player.getName(), settings, false));
            }
        }
        return null;
    }

    public Component customOrPersonal(String type, Player player) {
        Selection selected = select(type, player);
        return selected == null ? null : selected.component();
    }

    private Component personalComponent(String text, Player player, PluginSettings settings) {
        Component component = MiniMessageService.render(text, player.getName(), settings, true);
        return component.colorIfAbsent(NamedTextColor.YELLOW);
    }

    public void broadcast(Component message) { if (message != null) Bukkit.getServer().broadcast(message); }

    public void broadcast(Component message, String type, String playerName, java.util.UUID uuid, String sourceText) {
        if (message == null) return;
        Bukkit.getServer().broadcast(message);
        if (plugin.discordSrv() != null) plugin.discordSrv().send(type, playerName, uuid, message, sourceText);
    }
}
