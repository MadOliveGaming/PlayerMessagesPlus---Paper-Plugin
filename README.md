# PlayerMessages+

Paper plugin for Minecraft Java 26.3. This plugin supports custom join- leave- and rejoin (more on that later) messages for players in 2 forms. The first is a list of messages set by the server admins in a json file (seperate file for each message type with minimessage formatting support), from which a random message is picked if these are enabled. The seconds is personal messages, which each player can set through a command ingame for themselves (also with minimessage support, for both forms we added the <player> tag which will be replaced by the players name). This can also be disabled by server admins. If both are enabled (personal and custom messages, with custom reffering to the json lists), the personal message takes priority. If neither are enabled for that message type, a vanilla (style) message will be displayed. 

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

The following commands are available for them:
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

## Build

This information is only applicable if you want to build the jar file yourself (for exampleafter making some changes for your own use). If you just wish to use the plugin as is, it is reccomended you download a jar rom the current releases.

Requirements: JDK 25 and an internet connection for Gradle to download dependencies (and, if needed, the JDK toolchain).

- Windows: run `gradlew.bat build`
- Linux/macOS: run `./gradlew build`

The plugin JAR is written to `build/libs`.

Install that JAR into the server's `plugins` folder and restart Paper. The plugin creates `plugins/PlayerMessagesPlus/` and its initial configuration/data files on first start.

## Notes

Naming note: Paper's technical plugin name is `PlayerMessagesPlus` because `+` is not allowed in `plugin.yml`. The public display/branding name remains `PlayerMessages+`.
