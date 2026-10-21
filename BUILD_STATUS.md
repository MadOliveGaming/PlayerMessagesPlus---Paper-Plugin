# Build verification status

The project is configured for Paper 26.3 and Java 25. This environment only has Java 21 and cannot reach external Maven/Gradle repositories, so a Gradle compile and server runtime test could not be completed here.

On a computer with internet access, run `gradlew.bat build` (Windows) or `./gradlew build` (Linux/macOS). The launcher downloads Gradle 9.2.1; the Foojay toolchain resolver can obtain JDK 25 if it is not already installed. Resolve any build errors before installing the generated JAR on a live server.


Naming note: Paper's technical plugin name is `PlayerMessagesPlus` because `+` is not allowed in `plugin.yml`. The public display/branding name remains `PlayerMessages+`.
