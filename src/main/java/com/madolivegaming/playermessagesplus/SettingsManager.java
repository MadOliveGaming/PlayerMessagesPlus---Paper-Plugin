package com.madolivegaming.playermessagesplus;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingsManager {
    private final PlayerMessagesPlus plugin;
    private PluginSettings active = new PluginSettings();
    private boolean writable = true;

    public SettingsManager(PlayerMessagesPlus plugin) { this.plugin = plugin; }
    public PluginSettings get() { return active; }
    public void setActive(PluginSettings settings) { active = settings; }

    public boolean load(boolean startup) {
        File file = new File(plugin.dataDirectory(), "config.yml");
        if (!file.exists()) {
            try {
                plugin.dataDirectory().mkdirs();
                YamlConfiguration defaults = makeDefaults();
                defaults.save(file);
                writable = true;
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create config.yml: " + e.getMessage());
                if (startup) active = new PluginSettings();
                return false;
            }
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); }
        catch (IOException | InvalidConfigurationException e) {
            writable = false;
            plugin.getLogger().severe("config.yml is malformed; keeping the previous active configuration. Fix the file and run /pmp reload. " + e.getMessage());
            if (startup) active = new PluginSettings();
            return false;
        }
        try {
            PluginSettings next = new PluginSettings();
            next.useCustomJoinMessages = bool(yaml, "useCustomJoinMessages");
            next.usePersonalJoinMessages = bool(yaml, "usePersonalJoinMessages");
            next.useCustomLeaveMessages = bool(yaml, "useCustomLeaveMessages");
            next.usePersonalLeaveMessages = bool(yaml, "usePersonalLeaveMessages");
            next.useCustomRejoinMessages = bool(yaml, "useCustomRejoinMessages");
            next.usePersonalRejoinMessages = bool(yaml, "usePersonalRejoinMessages");
            next.rejoinMessageWindow = integer(yaml, "rejoinMessageWindow", 1, 86400);
            next.suppressLeaveMessageDuringRejoin = integer(yaml, "suppressLeaveMessageDuringRejoin", 0, 86400);
            next.colors = bool(yaml, "minimessageTags.colors");
            next.bold = bool(yaml, "minimessageTags.bold");
            next.italic = bool(yaml, "minimessageTags.italic");
            next.underlined = bool(yaml, "minimessageTags.underlined");
            next.strikethrough = bool(yaml, "minimessageTags.strikethrough");
            next.obfuscated = bool(yaml, "minimessageTags.obfuscated");
            next.gradient = bool(yaml, "minimessageTags.gradient");
            next.rainbow = bool(yaml, "minimessageTags.rainbow");
            active = next;
            writable = true;
            return true;
        } catch (IllegalArgumentException ex) {
            writable = false;
            plugin.getLogger().severe("config.yml contains an invalid setting; keeping the previous active configuration. " + ex.getMessage());
            if (startup) active = new PluginSettings();
            return false;
        }
    }

    private boolean bool(YamlConfiguration y, String key) {
        Object value = y.get(key);
        if (!(value instanceof Boolean b)) throw new IllegalArgumentException(key + " must be true or false (boolean)");
        return b;
    }
    private int integer(YamlConfiguration y, String key, int min, int max) {
        Object value = y.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) throw new IllegalArgumentException(key + " must be an integer");
        Number n = (Number) value;
        int result = n.intValue();
        if (result < min || result > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        return result;
    }

    public boolean save() {
        if (!writable) {
            plugin.getLogger().warning("Refusing to overwrite invalid config.yml. Repair it and run /pmp reload first.");
            return false;
        }
        File file = new File(plugin.dataDirectory(), "config.yml");
        try { makeDefaultsFrom(active).save(file); return true; }
        catch (IOException e) { plugin.getLogger().severe("Could not save config.yml: " + e.getMessage()); return false; }
    }

    private YamlConfiguration makeDefaults() { return makeDefaultsFrom(new PluginSettings()); }
    private YamlConfiguration makeDefaultsFrom(PluginSettings s) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("useCustomJoinMessages", s.useCustomJoinMessages); y.set("usePersonalJoinMessages", s.usePersonalJoinMessages);
        y.set("useCustomLeaveMessages", s.useCustomLeaveMessages); y.set("usePersonalLeaveMessages", s.usePersonalLeaveMessages);
        y.set("useCustomRejoinMessages", s.useCustomRejoinMessages); y.set("usePersonalRejoinMessages", s.usePersonalRejoinMessages);
        y.set("rejoinMessageWindow", s.rejoinMessageWindow); y.set("suppressLeaveMessageDuringRejoin", s.suppressLeaveMessageDuringRejoin);
        y.set("minimessageTags.colors", s.colors); y.set("minimessageTags.bold", s.bold); y.set("minimessageTags.italic", s.italic);
        y.set("minimessageTags.underlined", s.underlined); y.set("minimessageTags.strikethrough", s.strikethrough);
        y.set("minimessageTags.obfuscated", s.obfuscated); y.set("minimessageTags.gradient", s.gradient); y.set("minimessageTags.rainbow", s.rainbow);
        return y;
    }
}
