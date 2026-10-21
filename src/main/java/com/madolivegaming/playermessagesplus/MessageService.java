package com.madolivegaming.playermessagesplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MessageService {
    private final PlayerMessagesPlus plugin;
    public MessageService(PlayerMessagesPlus plugin) { this.plugin = plugin; }

    public Component customOrPersonal(String type, Player player) {
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
            if (personal != null) return personalComponent(personal, player, settings);
        }
        if (customEnabled) {
            List<String> options = plugin.data().shared(type);
            if (!options.isEmpty()) {
                String chosen = options.get(ThreadLocalRandom.current().nextInt(options.size()));
                return MiniMessageService.render(chosen, player.getName(), settings, false);
            }
        }
        return null;
    }

    private Component personalComponent(String text, Player player, PluginSettings settings) {
        Component component = MiniMessageService.render(text, player.getName(), settings, true);
        return component.colorIfAbsent(NamedTextColor.YELLOW);
    }

    public void broadcast(Component message) { if (message != null) Bukkit.getServer().broadcast(message); }
}
