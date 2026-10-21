package com.madolivegaming.playermessagesplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/** Consistent plugin chat branding and readable default text colors. */
public final class ChatMessages {
    private static final String PREFIX = "[PlayerMessages+]";
    private ChatMessages() {}

    public static Component prefixed(String message) {
        if (message.startsWith(PREFIX)) {
            return Component.text(PREFIX, NamedTextColor.DARK_AQUA)
                    .append(Component.text(message.substring(PREFIX.length()), NamedTextColor.WHITE));
        }
        return Component.text(message, NamedTextColor.WHITE);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(prefixed(message));
    }

    public static void send(CommandSender sender, Component component) {
        sender.sendMessage(component);
    }
}
