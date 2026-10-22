# PlayerMessages+

Paper plugin for Minecraft Java 26.3. This plugin supports custom join- leave- and rejoin (more on that later) messages for players in 2 forms. The first is a list of messages set by the server admins in a json file (seperate file for each message type with minimessage formatting support), from which a random message is picked if these are enabled. The seconds is personal messages, which each player can set through a command ingame for themselves (also with minimessage support, for both forms we added the <player> tag which will be replaced by the players name). This can also be disabled by server admins. If both are enabled (personal and custom messages, with custom reffering to the json lists), the personal message takes priority. If neither are enabled for that message type, a vanilla (style) message will be displayed. 

The plugin also has integration with DiscordSRV as described below. This is optional, so your server does not NEED DiscordSRV to use this plugin.

## Player features:

The features available to non-opped players consist of: Setting- resetting- and checking their own personal messages.

the following commands are available for them:
- `/pmp help`: Show a list of all player commands.
- `/pmp set <type> <text>`: Set your personal message for join, leave, or rejoin.
- `/pmp see <type> <true|false>`: Show your current personal message for join, leave, or rejoin if one is set. If false is given, the message will display the minimessage tags instead of the formatted message, defaults to true.
- `/pmp reset <type>`: Remove your personal message for join, leave, or rejoin.

## Admin features:

Admin features are currently locked behind operator permissions, which I might change in future updates to be more configurable, but this will be it for the time being. Admins can change settings, see and alter personal messages from other players, disable specific minimessage tags you dont want to allow and ban players from using their personal messages should someone abuse theirs.

To completely silence leave and join messages when players rejoin within the join window, simply disable both useCustomRejoinMessages and usePersonalRejoinMessages. In this instance the game will ignore the suppressLeaveMessageDuringRejoin setting and only send a leave message after the rejoin window has expired.

The following commands are available for them (integration related commands are specified in their related sections below):
- `/pmpadmin help`: Show a list of all admin commands.
- `/pmpadmin settings`: Shows all the current server settings, excluding MiniMessage tag statusses.
- `/pmpadmin verify`: Checks if the message json files, bans json file and config.yaml file are valid and/or contain invalid entries and informs you of issues.
- `/pmpadmin reload`: Reloads all json files and the config.yaml without needing to restart the server. Will not reload malformed files, but will pass files with malformed entiries with a warning. It is recommended to run verify before this.
- `/pmp ban <player>`: Bans a player from using personal messages. Their stored messages will not be removed, but will simply not be displayed.
- `/pmpadmin unban <player>`: Restore's a banned players ability to use personal messages.
- `/pmpadmin banlist`: Gives a list of all currently banned players (all the ban commands only affect this plugin, it does not stop players from joining the server).
- `/pmpadmin useCustomJoinMessages <true\|false>`: Enable or disable the join messages from the servers json file.
- `/pmpadmin useCustomRejoinMessages <true\|false>`: Enable or disable the rejoin messages from the servers json file.
- `/pmpadmin useCustomLeaveMessages <true\|false>`: Enable or disable the leave messages from the servers json file.
- `/pmpadmin usePersonalJoinMessages <true\|false>`: Enable or disable the use of personal join messages.
- `/pmpadmin usePersonalRejoinMessages <true\|false>`: Enable or disable the use of personal rejoin messages.
- `/pmpadmin usePersonalLeaveMessages <true\|false>`: Enable or disable the use of personal leave messages.
- `/pmpadmin rejoinMessageWindow <seconds>`: Set for how long after a disconnect the next join counts as "rejoining".
- `/pmpadmin suppressLeaveMessageDuringRejoin <seconds>`: Set for how long leave messages will be held back whilst the server waits to see if the next join becomnes a "rejoin". When set to 0 the leave message is send immediately as usual.
- `/pmpadmin minimessageTags <category> <true\|false>`: Enable or disable a MiniMesssage formatting tag for personal messages (does not affect messages from the server's custom json list). Available categories: colors, bold, italic, underlined, strikethrough, obfuscated, gradient, rainbow. Disabling colours will also stop tags like rainbow and gradient from applying their colour values.

## DiscordSRV Integration:

DiscordSRV integration is disabled by default and cannot be enabled unless the plugin detects DiscordSRV in your plugins folder. You can [find DiscordSRV on this Modrith page](https://modrinth.com/plugin/discordsrv)!

PlayerMessages+ supports DiscordSRV integration, which  establishes the following: The ability to show the custom and personal join-, rejoin- and leave messages instead of the default DiscordSRV messages and in fact to send rejoin messages at all (which discordSRV will not do if this integration is disabled). It is also what allows PlayerMessages+ to control when DiscordSRV does and doesn't send join- leave or rejoin messages, which is needed for the cases where we permanently or temporaraly supress leave and join messages whilst waiting for rejoins.

Due to the way the messages are send to discord, MiniMessage tags cannot be used there. Therefore any custom and personal messages send to discord will be stripped of all MiniMessage tags, retaining only the text. The <player> tag will be replaced by the player's minecraft name before tags are stripped, so is the only tag that "works" when send to discord.

The following commands are available for server admins to control this integration:
- `/pmpadmin integration DiscordSrv help`: Shows a list of all admin commands related to DiscordSRV integration.
- `/pmpadmin integration DiscordSrv settings`: Shows all the current DiscordSRV integration settings and if the plugin was able to find DiscordSRV on your server.
- `/pmpadmin integration DiscordSrv enabled <true|false>`: Enable or disable DiscordSRV integration. The command will refuse to enable if no instance of DiscordSRV could be found.
- `/pmpadmin integration DiscordSrv useCustomMessages <true|false>`: Enable or disable wether DiscordSRV uses the exact content of custom and personal messages in discord. If disabled a default message will be send instead which is identical to what DiscordSRV would send without this integration.

**Important:** If you enable this integration it is highly reccomended you disable join- firstjoin- and leave messages in DiscordSRV's messages.yaml. Failing to do so may result in multiple messages being send per join or leave! So either disable those and let PlayerMessages+ handle when to send the messages, or choose not to use the integration at all and let DiscordSRV do its default thing (meaning what minecraft chat and discord chat sees might differ).


## Default settings

These are the plugins default setting. If any are not to your liking make sure to change them, either directly in the config files or with the commands listed above. If you want to edit the config or the custom message lists, they will be in the "plugins -> PlayerMessagesPlus".

- useCustomJoinMessages: true
- usePersonalJoinMessages: true
- useCustomLeaveMessages: true
- usePersonalLeaveMessages: true
- useCustomRejoinMessages: true
- usePersonalRejoinMessages: true
- rejoinMessageWindow: 30
- suppressLeaveMessageDuringRejoin: 0

minimessageTags:
  - colors: true
  - bold: true
  - italic: true
  - underlined: true
  - strikethrough: true
  - obfuscated: true
  - gradient: true
  - rainbow: true

DiscordSRV integration:
  - enabled: false
  - useCustomMessages: true

## Build

This information is only applicable if you want to build the jar file yourself (for exampleafter making some changes for your own use). If you just wish to use the plugin as is, it is reccomended you download a jar rom the current releases.

Requirements: JDK 25 and an internet connection for Gradle to download dependencies (and, if needed, the JDK toolchain).

- Windows: run `gradlew.bat build`
- Linux/macOS: run `./gradlew build`

The plugin JAR is written to `build/libs`.

Install that JAR into the server's `plugins` folder and restart Paper. The plugin creates `plugins/PlayerMessagesPlus/` and its initial configuration/data files on first start.

## Notes

Naming note: Paper's technical plugin name is `PlayerMessagesPlus` because `+` is not allowed in `plugin.yml`. The public display/branding name remains `PlayerMessages+`.
