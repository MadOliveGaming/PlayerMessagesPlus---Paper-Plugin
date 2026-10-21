package com.madolivegaming.playermessagesplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiniMessageService {
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z0-9_?!#-]+)(?::([^<>]*))?>");
    private static final Set<String> COLORS = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "color", "colour", "reset");
    private static final Set<String> BOLD = Set.of("bold", "b");
    private static final Set<String> ITALIC = Set.of("italic", "i");
    private static final Set<String> UNDERLINE = Set.of("underlined", "underline", "u");
    private static final Set<String> STRIKE = Set.of("strikethrough", "strike", "st");
    private static final Set<String> OBFUSCATED = Set.of("obfuscated", "obf");
    private static final Set<String> INTERACTIVE = Set.of("click", "hover", "insertion", "open_url", "run_command", "suggest_command", "copy_to_clipboard");

    public record Validation(boolean valid, Set<String> invalidTags, Set<String> disabledCategories) {}

    private MiniMessageService() {}

    public static Validation validateShared(String input) { return validate(input, allEnabled()); }

    public static Validation validatePersonal(String input, PluginSettings settings) { return validate(input, settings); }

    private static PluginSettings allEnabled() { return new PluginSettings(); }

    private static Validation validate(String input, PluginSettings settings) {
        Set<String> invalid = new LinkedHashSet<>();
        Set<String> disabled = new LinkedHashSet<>();
        Matcher matcher = TAG.matcher(input);
        while (matcher.find()) {
            String close = matcher.group(1), name = matcher.group(2).toLowerCase(Locale.ROOT), arg = matcher.group(3);
            if (name.equals("player")) continue;
            if (INTERACTIVE.contains(name) || !known(name, arg, !close.isEmpty())) { invalid.add(name); continue; }
            String category = category(name, arg);
            if (category != null && (category.equals("gradient") || category.equals("rainbow"))) {
                if (!settings.colors) disabled.add("colors");
                if ((category.equals("gradient") && !settings.gradient) || (category.equals("rainbow") && !settings.rainbow)) disabled.add(category);
            } else if (category != null && !enabled(settings, category)) disabled.add(category);
        }
        // Catch tag-shaped strings that the tokenizer cannot recognize, without treating ordinary angle brackets as tags.
        Matcher possible = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9_-]*)(?::[^<>]*)?>").matcher(input);
        while (possible.find()) {
            String name = possible.group(2).toLowerCase(Locale.ROOT);
            if (!isKnownTagName(name)) invalid.add(name);
        }
        return new Validation(invalid.isEmpty() && disabled.isEmpty(), invalid, disabled);
    }

    private static boolean known(String name, String arg, boolean closing) {
        if (name.startsWith("#")) return name.matches("#[0-9a-fA-F]{6}");
        if (name.equals("color") || name.equals("colour")) return closing || validColorArgument(arg);
        if (COLORS.contains(name)) return true;
        if (BOLD.contains(name) || ITALIC.contains(name) || UNDERLINE.contains(name) || STRIKE.contains(name) || OBFUSCATED.contains(name)) return true;
        if (name.equals("gradient")) return closing || arg != null && !arg.isBlank();
        if (name.equals("rainbow")) return true;
        return false;
    }

    private static boolean validColorArgument(String arg) {
        if (arg == null || arg.isBlank()) return false;
        String value = arg.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) return value.matches("#[0-9a-f]{6}");
        return COLORS.contains(value) && !value.equals("color") && !value.equals("colour") && !value.equals("reset");
    }

    private static boolean isKnownTagName(String name) {
        return name.equals("player") || COLORS.contains(name) || name.startsWith("#") || BOLD.contains(name) || ITALIC.contains(name) || UNDERLINE.contains(name) || STRIKE.contains(name) || OBFUSCATED.contains(name) || name.equals("gradient") || name.equals("rainbow") || INTERACTIVE.contains(name);
    }

    private static String category(String name, String arg) {
        if (name.startsWith("#") || COLORS.contains(name)) return "colors";
        if (name.equals("gradient")) return "gradient";
        if (name.equals("rainbow")) return "rainbow";
        if (BOLD.contains(name)) return "bold";
        if (ITALIC.contains(name)) return "italic";
        if (UNDERLINE.contains(name)) return "underlined";
        if (STRIKE.contains(name)) return "strikethrough";
        if (OBFUSCATED.contains(name)) return "obfuscated";
        return null;
    }

    private static boolean enabled(PluginSettings s, String category) {
        return switch (category) {
            case "colors" -> s.colors;
            case "bold" -> s.bold;
            case "italic" -> s.italic;
            case "underlined" -> s.underlined;
            case "strikethrough" -> s.strikethrough;
            case "obfuscated" -> s.obfuscated;
            case "gradient" -> s.gradient && s.colors;
            case "rainbow" -> s.rainbow && s.colors;
            default -> true;
        };
    }

    public static Component render(String input, String playerName, PluginSettings settings, boolean personal) {
        String prepared = input.replace("<player>", "<player>").replace("<PLAYER>", "<player>");
        if (personal) prepared = stripDisabledFormatting(prepared, settings);
        TagResolver resolver = TagResolver.builder()
                .resolver(StandardTags.color())
                .resolver(StandardTags.decorations())
                .resolver(StandardTags.gradient())
                .resolver(StandardTags.rainbow())
                .resolver(StandardTags.reset())
                .resolver(Placeholder.unparsed("player", playerName))
                .build();
        MiniMessage mm = MiniMessage.builder().tags(resolver).build();
        try { return mm.deserialize(prepared); }
        catch (Exception e) { return Component.text(prepared.replace("<player>", playerName)); }
    }

    private static String stripDisabledFormatting(String input, PluginSettings s) {
        Matcher m = TAG.matcher(input); StringBuffer out = new StringBuffer();
        while (m.find()) {
            String name = m.group(2).toLowerCase(Locale.ROOT), arg = m.group(3);
            String category = category(name, arg);
            boolean remove = category != null && !enabled(s, category);
            m.appendReplacement(out, Matcher.quoteReplacement(remove ? "" : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
