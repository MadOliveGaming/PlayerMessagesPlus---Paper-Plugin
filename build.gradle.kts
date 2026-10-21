plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.madolivegaming"
version = "1.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("net.kyori:adventure-text-minimessage:5.2.0")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(mapOf("version" to project.version)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test { useJUnitPlatform() }

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.google.gson", "com.madolivegaming.playermessagesplus.lib.gson")
}

tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }
