# Build verification status

The DiscordSRV integration changes have been applied to the source project. A Gradle build could not be completed in this environment: only Java 21 is installed, the project targets Java 25, and outbound DNS/network access is unavailable so the Gradle wrapper and dependencies could not be downloaded.

On a computer with internet access and JDK 25, run `gradlew.bat build` (Windows) or `./gradlew build` (Linux/macOS). Resolve any build errors before installing the generated JAR on a live server, then test with DiscordSRV installed and absent.

The integration is optional and uses reflection so DiscordSRV is not a hard dependency. It sends embeds to DiscordSRV's configured `global` channel, falling back to its main text channel when available.
\n\nDiscordSRV availability-check fix: removed an unnecessary reflection check for `DiscordUtil.queueMessage(TextChannel, String)`. Embed delivery does not use that method, and signature differences could incorrectly mark an installed DiscordSRV as unavailable. Availability now checks the classes and methods used by the embed sender.\n

## Follow-up availability fix
The DiscordSRV availability check now verifies only that Bukkit reports DiscordSRV enabled and that its main API entry point is accessible. It no longer tests JDA/embed method signatures during availability detection, since those can differ across DiscordSRV versions/classloader layouts. Actual send failures remain caught and logged by the integration. This is a source change and has not been compiled or tested against a live server in this environment.


DiscordSRV classloader fix: the embed sender now attempts DiscordSRV's relocated/shaded JDA package (`github.scarsz.discordsrv.dependencies.jda.api`) first, then falls back to the standard `net.dv8tion.jda.api` namespace. This targets the `ClassNotFoundException: net.dv8tion.jda.api.EmbedBuilder` failure reported by the server. Not compiled or tested against the live server.


Discord Markdown display fix: custom message formatting is now placed in the Discord embed description instead of the embed author name, because Discord does not render Markdown in author names. The player's skin avatar is shown as the embed thumbnail. The integration settings view is now available at `/pmpadmin integration DiscordSrv settings`, uses the requested short labels, and omits the editing hint. This source update has not been compiled or tested against a live server.

## Version 1.1.0 update
The plugin version is now `1.1.0`, which is expanded into `plugin.yml` and will be shown by Paper in the enable log as `PlayerMessagesPlus v1.1.0`. The DiscordSRV commands have been removed from `/pmpadmin help` and are now listed by `/pmpadmin integration DiscordSrv help`. Tab completion includes the new `help` subcommand. This source update has not been compiled or tested in this environment.
