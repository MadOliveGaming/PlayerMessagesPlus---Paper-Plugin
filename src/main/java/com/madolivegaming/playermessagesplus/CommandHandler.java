package com.madolivegaming.playermessagesplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class CommandHandler implements CommandExecutor, TabCompleter {
    private final PlayerMessagesPlus plugin;
    private static final Map<String, String> TYPES = Map.of("joinmessage", "join", "leavemessage", "leave", "rejoinmessage", "rejoin");
    private static final Set<String> ADMIN_TOP = Set.of("usecustomjoinmessages", "usepersonaljoinmessages", "usecustomleavemessages", "usepersonalleavemessages", "usecustomrejoinmessages", "usepersonalrejoinmessages", "rejoinmessagewindow", "suppressleavemessageduringrejoin", "minimessagetags", "settings", "reload", "verify", "ban", "unban", "banlist");

    public CommandHandler(PlayerMessagesPlus plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean adminRoot = command.getName().equalsIgnoreCase("pmpadmin");
        if (args.length == 0) { help(sender, adminRoot); return true; }
        String top = args[0].toLowerCase(Locale.ROOT);
        if (top.equals("help")) { help(sender, adminRoot); return true; }
        if (top.equals("set") || top.equals("see") || top.equals("reset")) {
            if (adminRoot && !plugin.isAdmin(sender)) { ChatMessages.send(sender, "[PlayerMessages+] You do not have permission to use this command."); return true; }
            handlePersonal(sender, top, args, adminRoot); return true;
        }
        if (!adminRoot) { ChatMessages.send(sender, "[PlayerMessages+] Unknown command. Use /pmp help."); return true; }
        if (!plugin.isAdmin(sender)) { ChatMessages.send(sender, "[PlayerMessages+] You do not have permission to use this command."); return true; }
        switch (top) {
            case "settings" -> settings(sender);
            case "reload" -> reload(sender);
            case "verify" -> verify(sender);
            case "usecustomjoinmessages", "usepersonaljoinmessages", "usecustomleavemessages", "usepersonalleavemessages", "usecustomrejoinmessages", "usepersonalrejoinmessages" -> setBoolean(sender, top, args);
            case "rejoinmessagewindow", "suppressleavemessageduringrejoin" -> setInteger(sender, top, args);
            case "minimessagetags" -> setTagSetting(sender, args);
            case "ban" -> ban(sender, args, true);
            case "unban" -> ban(sender, args, false);
            case "banlist" -> banList(sender);
            default -> ChatMessages.send(sender, "[PlayerMessages+] Unknown command. Use /pmpadmin help.");
        }
        return true;
    }

    private void handlePersonal(CommandSender sender, String action, String[] args, boolean adminRoot) {
        if (args.length < 2) { ChatMessages.send(sender, "[PlayerMessages+] Usage: " + (adminRoot ? "/pmpadmin " : "/pmp ") + action + " <joinMessage|leaveMessage|rejoinMessage> ..."); return; }
        String type = TYPES.get(args[1].toLowerCase(Locale.ROOT));
        if (type == null) { ChatMessages.send(sender, "[PlayerMessages+] Message type must be joinMessage, leaveMessage, or rejoinMessage."); return; }
        boolean admin = adminRoot && plugin.isAdmin(sender);
        if (sender instanceof Player player && !player.isOp() && plugin.data().isBanned(player.getUniqueId())) {
            ChatMessages.send(sender, "[PlayerMessages+] You do not have permission to use personal messages. You can still use /pmp help.");
            return;
        }
        UUID targetUuid;
        String targetName;
        int rest;
        if (action.equals("set")) {
            if (admin && args.length >= 4) {
                OfflinePlayer target = resolveKnownPlayer(args[2]);
                if (target == null) { ChatMessages.send(sender, "[PlayerMessages+] Unknown player: " + args[2]); return; }
                targetUuid = target.getUniqueId(); targetName = knownName(target); rest = 3;
            } else if (admin && args.length == 3 && resolveKnownPlayer(args[2]) != null) {
                OfflinePlayer target = resolveKnownPlayer(args[2]);
                targetUuid = target.getUniqueId(); targetName = knownName(target); rest = 3;
            } else {
                if (!(sender instanceof Player p)) { ChatMessages.send(sender, "[PlayerMessages+] Console must specify a player: /pmpadmin set " + args[1] + " <player> <text>"); return; }
                targetUuid = p.getUniqueId(); targetName = p.getName(); rest = 2;
            }
            if (args.length <= rest) { showPersonal(sender, targetUuid, targetName, type, true); return; }
            if (!personalEnabled(type)) { ChatMessages.send(sender, "[PlayerMessages+] Personal " + type + " messages are disabled by the server."); return; }
            String message = joinArgs(args, rest);
            if (message.isBlank()) { ChatMessages.send(sender, "[PlayerMessages+] Your message cannot be empty."); return; }
            if (message.length() > 256) { ChatMessages.send(sender, "[PlayerMessages+] Your message is " + message.length() + " characters long; the maximum is 256."); return; }
            MiniMessageService.Validation validation = MiniMessageService.validatePersonal(message, plugin.settings().get());
            if (!validation.invalidTags().isEmpty()) ChatMessages.send(sender, "[PlayerMessages+] Your message contains invalid or unsupported MiniMessage tags and was not saved.");
            if (!validation.disabledCategories().isEmpty()) ChatMessages.send(sender, "[PlayerMessages+] Your message was not saved. The following formatting is disabled by the server: " + String.join(", ", validation.disabledCategories()) + ".");
            if (!validation.valid()) return;
            if (!plugin.data().setPersonal(targetUuid, type, message)) { ChatMessages.send(sender, "[PlayerMessages+] Could not save player-messages.json. The file may be damaged; back it up and repair it first."); return; }
            ChatMessages.send(sender, "[PlayerMessages+] Saved the personal " + type + " message for " + targetName + ".");
            return;
        }

        if (action.equals("see")) {
            boolean rendered = true;
            if (admin && args.length >= 3 && !args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
                OfflinePlayer target = resolveKnownPlayer(args[2]);
                if (target == null) { ChatMessages.send(sender, "[PlayerMessages+] Unknown player: " + args[2]); return; }
                targetUuid = target.getUniqueId(); targetName = knownName(target); rest = 3;
                if (args.length >= 4) {
                    if (!isBoolean(args[3])) { ChatMessages.send(sender, "[PlayerMessages+] Expected true or false."); return; }
                    rendered = Boolean.parseBoolean(args[3]);
                }
            } else {
                if (!(sender instanceof Player p)) { ChatMessages.send(sender, "[PlayerMessages+] Console must specify a player: /pmpadmin see " + args[1] + " <player> [true|false]"); return; }
                targetUuid = p.getUniqueId(); targetName = p.getName(); rest = 2;
                if (args.length >= 3) {
                    if (!isBoolean(args[2])) { ChatMessages.send(sender, "[PlayerMessages+] Expected true or false."); return; }
                    rendered = Boolean.parseBoolean(args[2]);
                }
            }
            showPersonal(sender, targetUuid, targetName, type, rendered);
            return;
        }

        if (admin && args.length >= 3) {
            OfflinePlayer target = resolveKnownPlayer(args[2]);
            if (target == null) { ChatMessages.send(sender, "[PlayerMessages+] Unknown player: " + args[2]); return; }
            targetUuid = target.getUniqueId(); targetName = knownName(target);
        } else {
            if (!(sender instanceof Player p)) { ChatMessages.send(sender, "[PlayerMessages+] Console must specify a player: /pmpadmin reset " + args[1] + " <player>"); return; }
            targetUuid = p.getUniqueId(); targetName = p.getName();
        }
        if (!plugin.data().setPersonal(targetUuid, type, null)) { ChatMessages.send(sender, "[PlayerMessages+] Could not save player-messages.json. Repair the file first."); return; }
        ChatMessages.send(sender, "[PlayerMessages+] Reset the personal " + type + " message for " + targetName + ".");
    }

    private void showPersonal(CommandSender sender, UUID uuid, String name, String type, boolean rendered) {
        String value = plugin.data().personal(uuid, type);
        if (value == null) { ChatMessages.send(sender, "[PlayerMessages+] " + name + " has no personal " + type + " message set."); return; }
        sender.sendMessage(Component.text("[PlayerMessages+]", NamedTextColor.DARK_AQUA)
                .append(Component.text(" " + name + "'s personal " + type + " message: ", NamedTextColor.WHITE))
                .append(rendered ? MiniMessageService.render(value, name, plugin.settings().get(), true).colorIfAbsent(NamedTextColor.YELLOW) : Component.text(value, NamedTextColor.WHITE)));
    }

    private boolean personalEnabled(String type) {
        PluginSettings s = plugin.settings().get();
        return switch (type) { case "join" -> s.usePersonalJoinMessages; case "leave" -> s.usePersonalLeaveMessages; default -> s.usePersonalRejoinMessages; };
    }

    private void setBoolean(CommandSender sender, String key, String[] args) {
        if (args.length != 2 || !isBoolean(args[1])) { ChatMessages.send(sender, "[PlayerMessages+] Usage: /pmpadmin " + key + " <true|false>"); return; }
        PluginSettings old = plugin.settings().get(); PluginSettings next = old.copy(); boolean value = Boolean.parseBoolean(args[1]);
        switch (key) {
            case "usecustomjoinmessages" -> next.useCustomJoinMessages = value;
            case "usepersonaljoinmessages" -> next.usePersonalJoinMessages = value;
            case "usecustomleavemessages" -> next.useCustomLeaveMessages = value;
            case "usepersonalleavemessages" -> next.usePersonalLeaveMessages = value;
            case "usecustomrejoinmessages" -> next.useCustomRejoinMessages = value;
            case "usepersonalrejoinmessages" -> next.usePersonalRejoinMessages = value;
        }
        applySettings(sender, old, next, key + " = " + value);
    }

    private void setInteger(CommandSender sender, String key, String[] args) {
        if (args.length != 2) { ChatMessages.send(sender, "[PlayerMessages+] Usage: /pmpadmin " + key + " <seconds>"); return; }
        double parsed;
        try { parsed = Double.parseDouble(args[1]); } catch (NumberFormatException e) { ChatMessages.send(sender, "[PlayerMessages+] Please provide a number of seconds."); return; }
        if (!Double.isFinite(parsed)) { ChatMessages.send(sender, "[PlayerMessages+] Please provide a finite number of seconds."); return; }
        int min = key.equals("rejoinmessagewindow") ? 1 : 0;
        if (parsed < min || parsed > 86400) { ChatMessages.send(sender, "[PlayerMessages+] Value must be between " + min + " and 86400 seconds."); return; }
        long rounded = (long) Math.floor(parsed + 0.5d);
        if (rounded < min || rounded > 86400) { ChatMessages.send(sender, "[PlayerMessages+] Value must be between " + min + " and 86400 seconds."); return; }
        PluginSettings old = plugin.settings().get(); PluginSettings next = old.copy();
        if (key.equals("rejoinmessagewindow")) next.rejoinMessageWindow = (int) rounded; else next.suppressLeaveMessageDuringRejoin = (int) rounded;
        applySettings(sender, old, next, key + " = " + rounded + " seconds");
    }

    private void setTagSetting(CommandSender sender, String[] args) {
        if (args.length != 3 || !isBoolean(args[2])) { ChatMessages.send(sender, "[PlayerMessages+] Usage: /pmpadmin minimessageTags <colors|bold|italic|underlined|strikethrough|obfuscated|gradient|rainbow> <true|false>"); return; }
        String category = args[1].toLowerCase(Locale.ROOT); PluginSettings old = plugin.settings().get(); PluginSettings next = old.copy(); boolean value = Boolean.parseBoolean(args[2]);
        switch (category) {
            case "colors" -> next.colors = value; case "bold" -> next.bold = value; case "italic" -> next.italic = value;
            case "underlined" -> next.underlined = value; case "strikethrough" -> next.strikethrough = value;
            case "obfuscated" -> next.obfuscated = value; case "gradient" -> next.gradient = value; case "rainbow" -> next.rainbow = value;
            default -> { ChatMessages.send(sender, "[PlayerMessages+] Unknown MiniMessage category: " + category); return; }
        }
        applySettings(sender, old, next, "minimessageTags." + category + " = " + value);
    }

    private void applySettings(CommandSender sender, PluginSettings old, PluginSettings next, String changed) {
        plugin.settings().setActive(next);
        if (!plugin.settings().save()) { plugin.settings().setActive(old); ChatMessages.send(sender, "[PlayerMessages+] Setting was not changed because config.yml could not be saved."); return; }
        ChatMessages.send(sender, "[PlayerMessages+] Updated " + changed + ".");
    }

    private void reload(CommandSender sender) {
        boolean configOk = plugin.settings().load(false);
        plugin.data().loadAll(true);
        plugin.data().refreshIssueCache();
        ChatMessages.send(sender, "[PlayerMessages+] Reload completed. JSON data was checked and valid files were reloaded.");
        if (!configOk) ChatMessages.send(sender, "[PlayerMessages+] config.yml was invalid; the previous active configuration was retained. See console for details.");
        if (plugin.cachedIssueCount() > 0) ChatMessages.send(sender, "[PlayerMessages+] " + plugin.cachedIssueCount() + " issues were found in " + plugin.cachedIssueFileCount() + " JSON files. Use /pmpadmin verify for details.");
    }

    private void verify(CommandSender sender) {
        List<String> lines = plugin.data().describeValidation();
        for (String line : lines) ChatMessages.send(sender, line);
        ChatMessages.send(sender, "[PlayerMessages+] Issue cache updated.");
    }

    private void settings(CommandSender sender) {
        PluginSettings s = plugin.settings().get();
        sender.sendMessage(Component.text("[PlayerMessages+]", NamedTextColor.DARK_AQUA)
                .append(Component.text(" Settings:", NamedTextColor.AQUA)));
        settingLine(sender, "useCustomJoinMessages", Boolean.toString(s.useCustomJoinMessages));
        settingLine(sender, "usePersonalJoinMessages", Boolean.toString(s.usePersonalJoinMessages));
        settingLine(sender, "useCustomLeaveMessages", Boolean.toString(s.useCustomLeaveMessages));
        settingLine(sender, "usePersonalLeaveMessages", Boolean.toString(s.usePersonalLeaveMessages));
        settingLine(sender, "useCustomRejoinMessages", Boolean.toString(s.useCustomRejoinMessages));
        settingLine(sender, "usePersonalRejoinMessages", Boolean.toString(s.usePersonalRejoinMessages));
        settingLine(sender, "rejoinMessageWindow", s.rejoinMessageWindow + " seconds");
        settingLine(sender, "suppressLeaveMessageDuringRejoin", s.suppressLeaveMessageDuringRejoin + " seconds");
    }

    private void settingLine(CommandSender sender, String name, String value) {
        sender.sendMessage(Component.text(name + ": ", NamedTextColor.WHITE)
                .append(Component.text(value, NamedTextColor.GRAY)));
    }

    private void ban(CommandSender sender, String[] args, boolean value) {
        if (args.length != 2) { ChatMessages.send(sender, "[PlayerMessages+] Usage: /pmpadmin " + (value ? "ban" : "unban") + " <player>"); return; }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null) { ChatMessages.send(sender, "[PlayerMessages+] Unknown player: " + args[1]); return; }
        if (target.isOp()) { ChatMessages.send(sender, "[PlayerMessages+] You cannot ban another admin from using the plugin"); return; }
        boolean alreadyBanned = plugin.data().bannedPlayers().contains(target.getUniqueId());
        if (value && alreadyBanned) { ChatMessages.send(sender, "[PlayerMessages+] " + knownName(target) + " is already on the banlist."); return; }
        if (!value && !alreadyBanned) { ChatMessages.send(sender, "[PlayerMessages+] " + knownName(target) + " is not on the banlist."); return; }
        if (!plugin.data().setBanned(target.getUniqueId(), value)) { ChatMessages.send(sender, "[PlayerMessages+] Could not update banned-players.json. Repair the file first."); return; }
        String actor = sender instanceof Player p ? p.getName() : "Console";
        ChatMessages.send(sender, "[PlayerMessages+] " + (value ? "Banned " : "Unbanned ") + knownName(target) + ".");
        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) ChatMessages.send(online, "[PlayerMessages+] " + actor + (value ? " has revoked your rights to use personal join-, rejoin- and leave messages" : " has returned your rights to use personal join-, rejoin- and leave messages"));
    }

    private void banList(CommandSender sender) {
        Set<UUID> ids = plugin.data().bannedPlayers();
        ChatMessages.send(sender, "[PlayerMessages+] Banned players (" + ids.size() + "):");
        if (ids.isEmpty()) { ChatMessages.send(sender, "- none"); return; }
        ids.stream().sorted().forEach(id -> ChatMessages.send(sender, "- " + Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString()) + " (" + id + ")"));
    }

    private OfflinePlayer resolveKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }
    private String knownName(OfflinePlayer p) { return p.getName() == null ? p.getUniqueId().toString() : p.getName(); }
    private boolean isBoolean(String s) { return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"); }
    private String joinArgs(String[] args, int from) { return String.join(" ", Arrays.copyOfRange(args, from, args.length)); }

    private void help(CommandSender sender, boolean adminRoot) {
        if (!adminRoot) {
            sender.sendMessage(Component.text("[PlayerMessages+]", NamedTextColor.DARK_AQUA).append(Component.text(" Player commands:", NamedTextColor.AQUA)));
            ChatMessages.send(sender, "/pmp help");
            ChatMessages.send(sender, "/pmp set <joinMessage|leaveMessage|rejoinMessage> <text>");
            ChatMessages.send(sender, "/pmp set <joinMessage|leaveMessage|rejoinMessage>");
            ChatMessages.send(sender, "/pmp see <joinMessage|leaveMessage|rejoinMessage> [true|false]");
            ChatMessages.send(sender, "/pmp reset <joinMessage|leaveMessage|rejoinMessage>");
            return;
        }
        if (!plugin.isAdmin(sender)) { ChatMessages.send(sender, "[PlayerMessages+] You do not have permission to use this command."); return; }
        sender.sendMessage(Component.text("[PlayerMessages+]", NamedTextColor.DARK_AQUA).append(Component.text(" Admin commands:", NamedTextColor.AQUA)));
        ChatMessages.send(sender, "/pmpadmin help");
        ChatMessages.send(sender, "/pmpadmin set <type> <player> <text> | /pmpadmin see <type> <player> [true|false] | /pmpadmin reset <type> <player>");
        ChatMessages.send(sender, "/pmpadmin useCustomJoinMessages <true|false> | /pmpadmin usePersonalJoinMessages <true|false>");
        ChatMessages.send(sender, "/pmpadmin useCustomLeaveMessages <true|false> | /pmpadmin usePersonalLeaveMessages <true|false>");
        ChatMessages.send(sender, "/pmpadmin useCustomRejoinMessages <true|false> | /pmpadmin usePersonalRejoinMessages <true|false>");
        ChatMessages.send(sender, "/pmpadmin rejoinMessageWindow <seconds> | /pmpadmin suppressLeaveMessageDuringRejoin <seconds>");
        ChatMessages.send(sender, "/pmpadmin minimessageTags <category> <true|false>");
        ChatMessages.send(sender, "/pmpadmin ban <player> | /pmpadmin unban <player> | /pmpadmin banlist");
        ChatMessages.send(sender, "/pmpadmin settings | /pmpadmin reload | /pmpadmin verify");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean adminRoot = command.getName().equalsIgnoreCase("pmpadmin");
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(adminRoot ? List.of("help", "set", "see", "reset") : List.of("help", "set", "see", "reset"));
            if (adminRoot && plugin.isAdmin(sender)) roots.addAll(ADMIN_TOP);
            return prefix(roots, args[0]);
        }
        if (args.length == 2 && List.of("set", "see", "reset").contains(args[0].toLowerCase(Locale.ROOT))) return prefix(TYPES.keySet(), args[1]);
        if (adminRoot && plugin.isAdmin(sender) && args.length == 3 && List.of("set", "see", "reset").contains(args[0].toLowerCase(Locale.ROOT))) {
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (!adminRoot && args.length >= 3 && args[0].equalsIgnoreCase("set")) return List.of("<text>");
        if (adminRoot && plugin.isAdmin(sender) && args.length >= 4 && args[0].equalsIgnoreCase("set")) return List.of("<text>");
        if (adminRoot && plugin.isAdmin(sender) && args.length == 4 && args[0].equalsIgnoreCase("see")) return prefix(List.of("true", "false"), args[3]);
        if (adminRoot && plugin.isAdmin(sender) && args.length == 2 && List.of("rejoinmessagewindow", "suppressleavemessageduringrejoin").contains(args[0].toLowerCase(Locale.ROOT))) return List.of("<seconds>");
        if (adminRoot && args.length == 2 && plugin.isAdmin(sender)) {
            String t = args[0].toLowerCase(Locale.ROOT);
            if (t.startsWith("usecustom") || t.startsWith("usepersonal")) return prefix(List.of("true", "false"), args[1]);
            if (t.equals("minimessagetags")) return prefix(List.of("colors", "bold", "italic", "underlined", "strikethrough", "obfuscated", "gradient", "rainbow"), args[1]);
        }
        if (adminRoot && args.length == 3 && args[0].equalsIgnoreCase("minimessagetags")) return prefix(List.of("true", "false"), args[2]);
        if (adminRoot && args.length == 2 && List.of("ban", "unban").contains(args[0].toLowerCase(Locale.ROOT))) return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        return List.of();
    }
    private List<String> prefix(Collection<String> options, String token) { return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT))).sorted().collect(Collectors.toList()); }
}
