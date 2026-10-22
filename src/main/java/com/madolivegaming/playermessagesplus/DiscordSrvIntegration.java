package com.madolivegaming.playermessagesplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional, reflection-based integration so DiscordSRV is never a hard dependency. */
public final class DiscordSrvIntegration {
    public enum CheckResult { DISABLED, AVAILABLE, UNAVAILABLE, INVALID_CONFIG }

    private static final String ENABLE_WARNING = "[PlayerMessages+] DiscordSRV integration has been enabled. Make sure you disabled join- and leave messages in DiscordSRV's configuration!";
    private static final String DISABLE_WARNING = "[PlayerMessages+] DiscordSRV integration has been disabled. You can now enable join- and leave messages in DiscordSRV's configuration.";
    private static final String ENABLE_FAILURE = "[PlayerMessages+] This setting could not be enabled as DiscordSRV is currently unavailable or not installed on the server.";
    public static final String UNAVAILABLE_WARNING = "[PlayerMessages+] Failed to establish DiscordSRV integration. DiscordSRV is currently unavailable or not installed on the server. Disable DiscordSRV integration in this plugin's settings, or install/repair your DiscordSRV installation.";

    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z0-9_?!#-]+)(?::([^<>]*))?>");
    private final PlayerMessagesPlus plugin;
    private final Path configPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private DiscordSrvSettings settings = new DiscordSrvSettings();
    private boolean configValid = true;
    private boolean available;

    public DiscordSrvIntegration(PlayerMessagesPlus plugin) {
        this.plugin = plugin;
        this.configPath = plugin.dataDirectory().toPath().resolve("integrations").resolve("discord-srv.json");
    }

    public boolean enabledInSettings() { return configValid && settings.enabled; }
    public boolean useCustomMessages() { return configValid && settings.useCustomMessages; }
    public boolean isAvailable() { return available; }
    public boolean shouldSend() { return enabledInSettings() && available; }

    public void initialize() {
        load();
        if (enabledInSettings()) checkAvailability(plugin.getLogger()::warning);
        else available = false;
    }

    /** Loads settings. Malformed files are not overwritten and fail closed. */
    public boolean load() {
        try {
            Files.createDirectories(configPath.getParent());
            if (!Files.exists(configPath)) {
                settings = new DiscordSrvSettings();
                configValid = saveSettings(settings);
                available = false;
                return configValid;
            }
            JsonObject root = JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("enabled") || !root.get("enabled").isJsonPrimitive() || !root.getAsJsonPrimitive("enabled").isBoolean()
                    || !root.has("useCustomMessages") || !root.get("useCustomMessages").isJsonPrimitive() || !root.getAsJsonPrimitive("useCustomMessages").isBoolean()) {
                throw new IllegalArgumentException("required fields 'enabled' and 'useCustomMessages' must be booleans");
            }
            DiscordSrvSettings next = new DiscordSrvSettings();
            next.enabled = root.get("enabled").getAsBoolean();
            next.useCustomMessages = root.get("useCustomMessages").getAsBoolean();
            settings = next;
            configValid = true;
            available = false;
            return true;
        } catch (Exception e) {
            configValid = false;
            available = false;
            plugin.getLogger().warning("Could not load integrations/discord-srv.json; DiscordSRV messaging is suspended and the file was not overwritten: " + e.getMessage());
            return false;
        }
    }

    public boolean setEnabled(boolean value, org.bukkit.command.CommandSender sender) {
        if (!configValid && !load()) {
            ChatMessages.send(sender, "[PlayerMessages+] Cannot update DiscordSRV integration because integrations/discord-srv.json is invalid. Repair the file first.");
            return false;
        }
        if (!value) {
            if (!settings.enabled) {
                available = false;
                ChatMessages.send(sender, "[PlayerMessages+] DiscordSRV integration is already disabled.");
                return true;
            }
            DiscordSrvSettings next = copySettings();
            next.enabled = false;
            if (!saveSettings(next)) {
                ChatMessages.send(sender, "[PlayerMessages+] Could not save integrations/discord-srv.json; the setting was not changed.");
                return false;
            }
            settings = next;
            available = false;
            ChatMessages.send(sender, DISABLE_WARNING);
            return true;
        }

        if (configValid && settings.enabled) {
            if (checkAvailability(null) == CheckResult.AVAILABLE) {
                ChatMessages.send(sender, "[PlayerMessages+] DiscordSRV integration is already enabled.");
                return true;
            }
        }
        if (!isDiscordSrvLoaded()) {
            available = false;
            ChatMessages.send(sender, ENABLE_FAILURE);
            return false;
        }
        DiscordSrvSettings next = configValid ? copySettings() : new DiscordSrvSettings();
        next.enabled = true;
        if (!saveSettings(next)) {
            ChatMessages.send(sender, "[PlayerMessages+] Could not save integrations/discord-srv.json; the setting was not changed.");
            return false;
        }
        settings = next;
        configValid = true;
        available = true;
        ChatMessages.send(sender, ENABLE_WARNING);
        return true;
    }

    public boolean setUseCustomMessages(boolean value, org.bukkit.command.CommandSender sender) {
        if (!configValid && !load()) {
            ChatMessages.send(sender, "[PlayerMessages+] Cannot update DiscordSRV integration because integrations/discord-srv.json is invalid. Repair the file first.");
            return false;
        }
        if (settings.useCustomMessages == value) {
            ChatMessages.send(sender, "[PlayerMessages+] DiscordSRV useCustomMessages is already " + value + ".");
            return true;
        }
        DiscordSrvSettings next = copySettings();
        next.useCustomMessages = value;
        if (!saveSettings(next)) {
            ChatMessages.send(sender, "[PlayerMessages+] Could not save integrations/discord-srv.json; the setting was not changed.");
            return false;
        }
        settings = next;
        ChatMessages.send(sender, "[PlayerMessages+] DiscordSRV useCustomMessages = " + value + ".");
        return true;
    }

    public CheckResult recheck(org.bukkit.command.CommandSender sender, boolean consoleIfStartup) {
        if (!configValid) {
            available = false;
            if (sender != null) ChatMessages.send(sender, UNAVAILABLE_WARNING);
            else if (consoleIfStartup) plugin.getLogger().warning(UNAVAILABLE_WARNING);
            return CheckResult.INVALID_CONFIG;
        }
        if (!settings.enabled) {
            available = false;
            return CheckResult.DISABLED;
        }
        return checkAvailability(message -> {
            if (sender != null) ChatMessages.send(sender, message);
            else if (consoleIfStartup) plugin.getLogger().warning(message);
        });
    }

    private CheckResult checkAvailability(java.util.function.Consumer<String> warning) {
        if (!configValid || !settings.enabled) { available = false; return CheckResult.DISABLED; }
        available = isDiscordSrvLoaded();
        if (available) return CheckResult.AVAILABLE;
        if (warning != null) warning.accept(UNAVAILABLE_WARNING);
        return CheckResult.UNAVAILABLE;
    }

    public void warnAdminOnJoin(Player player) {
        if (player.isOp() && configValid && settings.enabled && !available) ChatMessages.send(player, UNAVAILABLE_WARNING);
    }

    private boolean isDiscordSrvLoaded() {
        Plugin discord = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discord == null || !discord.isEnabled()) {
            return false;
        }
        try {
            // Availability means the optional plugin is running and exposes its
            // main entry point. Do not preflight JDA method signatures here:
            // DiscordSRV bundles/shares JDA differently across releases, and
            // reflection signature checks can falsely report a running plugin
            // as unavailable. Any actual send/API incompatibility is logged by
            // send() and safely prevents that notification from being delivered.
            ClassLoader loader = discord.getClass().getClassLoader();
            Class<?> main = Class.forName("github.scarsz.discordsrv.DiscordSRV", false, loader);
            main.getMethod("getPlugin");
            return true;
        } catch (Throwable e) {
            plugin.getLogger().warning("DiscordSRV is enabled in Bukkit, but PlayerMessages+ could not access its API entry point: "
                    + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            return false;
        }
    }

    public void send(String type, String playerName, UUID uuid, Component actualComponent, String rawMessage) {
        if (!shouldSend() || actualComponent == null) return;
        try {
            String content;
            if (settings.useCustomMessages) {
                String raw = rawMessage == null ? PlainTextComponentSerializer.plainText().serialize(actualComponent) : rawMessage;
                // Discord embed author names are plain text, so deliberately strip
                // MiniMessage tags instead of translating them into Discord Markdown.
                // Resolve <player> first so the player's actual username is preserved.
                content = stripMiniMessageTags(raw.replaceAll("(?i)<player>", Matcher.quoteReplacement(playerName)));
            } else {
                content = switch (type) {
                    case "join" -> playerName + " joined the server";
                    case "leave" -> playerName + " left the server";
                    default -> playerName + " rejoined the server";
                };
                content = escapeMarkdown(content);
            }
            if (content.isBlank()) return;
            content = truncate(content, 256);
            sendEmbed(content, type, uuid);
        } catch (Throwable e) {
            plugin.getLogger().warning("Could not send a " + type + " notification through DiscordSRV: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    private void sendEmbed(String content, String type, UUID uuid) throws Exception {
        Plugin discord = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discord == null || !discord.isEnabled()) {
            available = false;
            plugin.getLogger().warning("DiscordSRV became unavailable; PlayerMessages+ Discord notifications are suspended until the next availability check.");
            return;
        }
        ClassLoader loader = discord.getClass().getClassLoader();
        Class<?> main = Class.forName("github.scarsz.discordsrv.DiscordSRV", true, loader);
        Object discordPlugin = main.getMethod("getPlugin").invoke(null);
        Object channel = null;
        try { channel = main.getMethod("getDestinationTextChannelForGameChannelName", String.class).invoke(discordPlugin, "global"); }
        catch (ReflectiveOperationException ignored) { }
        if (channel == null) {
            try { channel = main.getMethod("getMainTextChannel").invoke(discordPlugin); }
            catch (ReflectiveOperationException ignored) { }
        }
        if (channel == null) throw new IllegalStateException("DiscordSRV has no configured global/main text channel");

        // DiscordSRV relocates/shades JDA in many releases. Prefer its relocated
        // namespace, then fall back to the unrelocated namespace for versions
        // that expose the standard JDA package.
        Class<?> embedBuilderClass = loadJdaClass(loader, "EmbedBuilder");
        Object builder = embedBuilderClass.getConstructor().newInstance();
        String avatar = uuid == null ? "https://mc-heads.net/avatar/Steve/64" : "https://mc-heads.net/avatar/" + uuid + "/64";
        // Keep the message in the embed author line for a compact appearance.
        // MiniMessage formatting tags have already been stripped to plain text.
        invokeAuthorMethod(embedBuilderClass, builder, content, avatar);
        Color color = switch (type) {
            case "join" -> new Color(0x43E65B);
            case "leave" -> new Color(0xF04444);
            default -> new Color(0xF5C542);
        };
        embedBuilderClass.getMethod("setColor", Color.class).invoke(builder, color);
        Object embed = embedBuilderClass.getMethod("build").invoke(builder);
        // Do not load TextChannel or RestAction by a hard-coded package name.
        // DiscordSRV versions can relocate JDA and can expose different JDA major
        // versions. Discover the public API methods from the actual channel/action
        // objects returned by DiscordSRV instead.
        Method sendEmbeds = findPublicMethod(channel.getClass(), "sendMessageEmbeds", 1, true);
        if (sendEmbeds == null) {
            throw new NoSuchMethodException("DiscordSRV text channel has no compatible sendMessageEmbeds(Collection) method; channel class: " + channel.getClass().getName());
        }
        Object action = sendEmbeds.invoke(channel, List.of(embed));
        Method queue = findPublicMethod(action.getClass(), "queue", 0, false);
        if (queue == null) {
            throw new NoSuchMethodException("DiscordSRV message action has no queue() method; action class: " + action.getClass().getName());
        }
        queue.invoke(action);
    }

    /** Invokes a one-string-like-argument method across JDA versions (String or CharSequence). */
    private void invokeStringArgumentMethod(Class<?> type, Object target, String name, String value) throws Exception {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(String.class)) {
                method.invoke(target, value);
                return;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "(String-compatible argument)");
    }

    /** Finds an API method through public interfaces first, avoiding inaccessible implementation classes. */
    private Method findPublicMethod(Class<?> type, String name, int parameterCount, boolean collectionArgument) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
                Method found = findPublicMethodRecursive(iface, name, parameterCount, collectionArgument);
                if (found != null) return found;
            }
            for (Method method : current.getMethods()) {
                if (matchesMethod(method, name, parameterCount, collectionArgument)) return method;
            }
        }
        return null;
    }

    private Method findPublicMethodRecursive(Class<?> type, String name, int parameterCount, boolean collectionArgument) {
        for (Method method : type.getMethods()) {
            if (matchesMethod(method, name, parameterCount, collectionArgument)) return method;
        }
        for (Class<?> parent : type.getInterfaces()) {
            Method found = findPublicMethodRecursive(parent, name, parameterCount, collectionArgument);
            if (found != null) return found;
        }
        return null;
    }

    private boolean matchesMethod(Method method, String name, int parameterCount, boolean collectionArgument) {
        if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) return false;
        if (!collectionArgument) return true;
        return method.getParameterTypes()[0].isAssignableFrom(List.class)
                || java.util.Collection.class.isAssignableFrom(method.getParameterTypes()[0]);
    }

    private Class<?> loadJdaClass(ClassLoader loader, String suffix) throws ClassNotFoundException {
        String relocated = "github.scarsz.discordsrv.dependencies.jda.api." + suffix;
        String standard = "net.dv8tion.jda.api." + suffix;
        try {
            return Class.forName(relocated, true, loader);
        } catch (ClassNotFoundException ignored) {
            return Class.forName(standard, true, loader);
        }
    }

    /** Calls setAuthor(String, String, String) without assuming the JDA classloader's exact String-like signature. */
    private void invokeAuthorMethod(Class<?> type, Object target, String author, String avatar) throws Exception {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("setAuthor") || method.getParameterCount() != 3) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[0].isAssignableFrom(String.class)
                    && (parameters[1].isAssignableFrom(String.class) || parameters[1] == Object.class)
                    && parameters[2].isAssignableFrom(String.class)) {
                method.invoke(target, author, null, avatar);
                return;
            }
        }
        throw new NoSuchMethodException(type.getName() + ".setAuthor(String-compatible, String-compatible, String-compatible)");
    }

    /** Removes MiniMessage tags while retaining the text inside formatting/color tags. */
    private String stripMiniMessageTags(String raw) {
        return TAG.matcher(raw).replaceAll("");
    }

    private String toDiscordMarkdown(String raw) {
        Matcher matcher = TAG.matcher(raw);
        StringBuilder out = new StringBuilder();
        // Track formatting tags so nested instances of the same decoration do not
        // emit duplicate Markdown delimiters. For example, bold inside bold stays
        // bold instead of producing a run of four or more asterisks.
        List<String> active = new java.util.ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            out.append(escapeMarkdown(raw.substring(cursor, matcher.start())));
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            boolean closing = !matcher.group(1).isEmpty();
            String mark = decoration(name);
            if (mark != null) {
                if (!closing) {
                    if (!active.contains(mark)) out.append(mark);
                    active.add(mark);
                } else {
                    int index = active.lastIndexOf(mark);
                    if (index >= 0) {
                        active.remove(index);
                        if (!active.contains(mark)) out.append(mark);
                    }
                }
            }
            // Colors, gradients, rainbow and obfuscation have no Discord equivalent;
            // strip their tags but retain all text between them.
            cursor = matcher.end();
        }
        out.append(escapeMarkdown(raw.substring(cursor)));
        // A malformed/unclosed tag should not leave Discord formatting open.
        for (int i = active.size() - 1; i >= 0; i--) {
            String mark = active.get(i);
            if (active.lastIndexOf(mark) == i) out.append(mark);
        }
        return out.toString();
    }

    private String decoration(String name) {
        return switch (name) {
            case "bold", "b" -> "**";
            case "italic", "i" -> "*";
            case "underlined", "underline", "u" -> "__";
            case "strikethrough", "strike", "st" -> "~~";
            default -> null;
        };
    }

    private String escapeMarkdown(String text) {
        return text.replace("\\", "\\\\").replace("_", "\\_").replace("*", "\\*").replace("~", "\\~").replace("|", "\\|").replace(">", "\\>").replace("`", "\\`");
    }

    private String truncate(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }

    private DiscordSrvSettings copySettings() {
        DiscordSrvSettings copy = new DiscordSrvSettings();
        copy.enabled = settings.enabled;
        copy.useCustomMessages = settings.useCustomMessages;
        return copy;
    }

    private boolean saveSettings(DiscordSrvSettings next) {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, gson.toJson(next) + System.lineSeparator(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save integrations/discord-srv.json: " + e.getMessage());
            return false;
        }
    }
}
