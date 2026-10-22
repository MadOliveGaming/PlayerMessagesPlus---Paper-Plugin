package com.madolivegaming.playermessagesplus;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class PlayerMessagesPlus extends JavaPlugin {
    private SettingsManager settings;
    private JsonDataStore data;
    private MessageService messages;
    private DiscordSrvIntegration discordSrv;
    private ReconnectListener reconnectListener;
    private int cachedIssueCount;
    private int cachedIssueFileCount;

    public File dataDirectory() { return new File(getServer().getPluginsFolder(), "PlayerMessagesPlus"); }

    @Override public void onEnable() {
        settings = new SettingsManager(this);
        settings.load(true);
        data = new JsonDataStore(this);
        data.initializeFiles();
        data.loadAll(false);
        messages = new MessageService(this);
        discordSrv = new DiscordSrvIntegration(this);
        discordSrv.initialize();
        reconnectListener = new ReconnectListener(this);
        getServer().getPluginManager().registerEvents(reconnectListener, this);
        PluginCommand command = getCommand("pmp");
        if (command == null) {
            getLogger().severe("Command /pmp is missing from plugin.yml; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CommandHandler handler = new CommandHandler(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        PluginCommand adminCommand = getCommand("pmpadmin");
        if (adminCommand == null) {
            getLogger().severe("Command /pmpadmin is missing from plugin.yml; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        adminCommand.setExecutor(handler);
        adminCommand.setTabCompleter(handler);
        data.refreshIssueCache();
        getLogger().info("PlayerMessages+ enabled. Use /pmp help for commands.");
    }

    @Override public void onDisable() {
        if (reconnectListener != null) reconnectListener.clearTransientState();
    }

    public SettingsManager settings() { return settings; }
    public JsonDataStore data() { return data; }
    public MessageService messages() { return messages; }
    public DiscordSrvIntegration discordSrv() { return discordSrv; }
    public int cachedIssueCount() { return cachedIssueCount; }
    public int cachedIssueFileCount() { return cachedIssueFileCount; }
    public void setIssueCache(int count, int files) { cachedIssueCount = count; cachedIssueFileCount = files; }
    public boolean isAdmin(org.bukkit.command.CommandSender sender) {
        return sender.isOp();
    }
}
