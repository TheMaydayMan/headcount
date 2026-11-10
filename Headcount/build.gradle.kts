plugins {
    java
}

group = "com.gradychastain.headcount"
version = "1.0.0"
description = "Head-economy cycles: collect heads, score, shop, outcomes."

val paperVersion = "26.3.build.246-stable"
val testServerDir = rootProject.layout.projectDirectory.dir("../TestServer")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.org/repository/maven-public/")          // PvPManager
    maven("https://repo.extendedclip.com/releases/")                   // PlaceholderAPI
    maven("https://repo.essentialsx.net/releases/")                    // EssentialsX
    maven("https://dl.cloudsmith.io/public/anand-beh/affero-gpl3/maven/") // LibertyBans
    maven("https://dl.cloudsmith.io/public/anand-beh/lesser-gpl3/maven/") // Omnibus (LibertyBans API access)
    maven("https://nexus.scarsz.me/content/groups/public/")            // DiscordSRV
    maven("https://repo.oraxen.com/releases/")                         // Oraxen
    maven("https://repo.nexomc.com/releases/")                         // Nexo
    maven("https://repo.opencollab.dev/main/")                         // Geyser / Floodgate
    maven("https://jitpack.io") {                                      // Vault, LiteBans, AdvancedBan, ItemsAdder
        content {
            includeGroup("com.github.MilkBowl")
            includeGroup("com.gitlab.ruany")
            includeGroup("com.github.DevLeoko.AdvancedBan")
            includeGroup("com.github.LoneDev6")
        }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")

    // Soft dependencies: compiled against, never shaded. Transitive dependencies are
    // disabled so third-party APIs cannot drag conflicting libraries onto the classpath.
    fun soft(notation: String) = compileOnly(notation) { isTransitive = false }
    soft("me.chancesd.pvpmanager:pvpmanager:4.0.30")
    soft("me.clip:placeholderapi:2.12.3")
    soft("com.github.MilkBowl:VaultAPI:1.7.1")
    soft("net.luckperms:api:5.5")
    soft("net.essentialsx:EssentialsX:2.21.2")
    soft("com.gitlab.ruany:LiteBansAPI:0.6.1")
    soft("com.github.DevLeoko.AdvancedBan:AdvancedBan-Core:v2.3.0")
    soft("space.arim.libertybans:bans-api:1.1.4")
    soft("space.arim.omnibus:omnibus:1.1.0-RC2")
    soft("com.discordsrv:discordsrv:1.30.5")
    soft("com.github.LoneDev6:API-ItemsAdder:3.6.1")
    soft("io.th0rgal:oraxen:1.220.1")
    soft("com.nexomc:nexo:1.30.0")
    soft("org.geysermc.geyser:api:2.12.1-SNAPSHOT")
    soft("org.geysermc.api:base-api:1.0.3")
    soft("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    soft("org.geysermc.cumulus:cumulus:2.0.0-SNAPSHOT")
    soft("org.geysermc.event:events:1.1-SNAPSHOT")

    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial,-classfile,-options", "-parameters"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveFileName.set("Headcount-${project.version}.jar")
    from("LICENSE")
}

// Copies the built jar into the local test server's plugins folder.
val deployToTestServer = tasks.register<Copy>("deployToTestServer") {
    description = "Copies the Headcount jar into ../TestServer/plugins"
    group = "distribution"
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(testServerDir.dir("plugins"))
    doFirst {
        // Remove older Headcount jars so the server never loads two copies.
        testServerDir.dir("plugins").asFile.listFiles { f -> f.name.startsWith("Headcount-") && f.name.endsWith(".jar") }
            ?.forEach { it.delete() }
    }
}

tasks.build { finalizedBy(deployToTestServer) }
