import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("idea")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.3"
    id("pmd")
    id("java-library")
    id("io.papermc.paperweight.userdev") version "1.5.10"
}

group = "com.ravingarinc.expeditions"
version = "1.8.0"

repositories {
    gradlePluginPortal()
    mavenLocal()
    mavenCentral()

    maven("https://repo.dmulloy2.net/repository/public/") {
        content {
            includeGroup("com.comphenix.protocol")
        }
    }

    maven("https://mvn.lumine.io/repository/maven-public/") {
        content {
            includeGroup("io.lumine")
        }
    }

    maven("https://nexus.phoenixdevt.fr/repository/maven-public/") {
        content {
            includeGroup("net.Indyuce")
            includeGroup("io.lumine")
        }
    }

    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        content {
            includeGroup("me.clip")
        }
    }

    maven("https://maven.citizensnpcs.co/repo") {
        content {
            includeGroup("net.citizensnpcs")
        }
    }

    maven("https://repo.alessiodp.com/releases/") {
        content {
            includeGroup("com.alessiodp.parties")
        }
    }

    maven("https://mvn.lumine.io/repository/maven-public/") {
        content {
            includeGroup("com.ticxo.modelengine")
        }
    }

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")


    maven("https://maven.enginehub.org/repo/")

    maven("https://repo.onarandombox.com/content/groups/public/")

    maven("https://jitpack.io")

    maven("https://nexus.betonquest.org/repository/betonquest/")
}

dependencies {
    library(kotlin("stdlib"))
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")

    library("com.github.shynixn.mccoroutine", "mccoroutine-bukkit-api", "2.16.0")
    library("com.github.shynixn.mccoroutine", "mccoroutine-bukkit-core", "2.16.0")
    library("com.google.guava:guava:18.0")

    implementation("com.github.RAVINGAR.RavinAPI:common:1.5.0")
    implementation("com.github.RAVINGAR.RavinAPI:module:1.5.0")
    implementation("com.github.RAVINGAR.RavinAPI:module-kotlin:1.5.0")
    implementation("com.github.RAVINGAR.RavinAPI:gui:1.5.0")
    implementation("com.github.RAVINGAR.RavinAPI:version:1.5.0")

    compileOnly("org.jetbrains:annotations:23.1.0")

    //compileOnly("org.spigotmc:spigot:1.19.4-R0.1-SNAPSHOT:remapped-mojang")
    //compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")

    paperweight.paperDevBundle("1.19.4-R0.1-SNAPSHOT")

    compileOnly("net.kyori:adventure-api:4.13.1")
    //compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.1.0-SNAPSHOT")
    compileOnly("io.lumine:MythicLib-dist:1.5.2-SNAPSHOT")
    compileOnly("net.Indyuce:MMOItems-API:6.9.2-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.2")
    compileOnly("com.ticxo.modelengine:ModelEngine:R4.0.4")

    compileOnly("org.betonquest:betonquest:2.0.0")

    compileOnly("com.alessiodp.parties:parties-api:3.2.13")

    compileOnly("net.citizensnpcs:citizens-main:2.0.30-SNAPSHOT")

    compileOnly("com.onarandombox.multiversecore:multiverse-core:4.3.2") {
        isTransitive = false
    }
    compileOnly("com.comphenix.protocol:ProtocolLib:5.0.0")
}

tasks {
    idea {
        module {
            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }

    jar {
        archiveFileName.set("Expeditions-parent.jar")
        archiveBaseName.set("Expeditions-parent")
        archiveVersion.set(null as String?)
        archiveClassifier.set("original")
    }

    shadowJar {
        archiveFileName.set("Expeditions.jar")
        archiveBaseName.set("Expeditions")
        archiveVersion.set(null as String?)
        archiveClassifier.set("")
        relocate("com.ravingarinc.api", "com.ravingarinc.expeditions.libs.api")
    }

    /*
    artifacts {
        archives(shadowJar)
    }
    */

    assemble {
        dependsOn(reobfJar)
    }

    register<Copy>("copyToDev") {
        from(shadowJar)
        into(project.layout.projectDirectory.dir("../../Desktop/Programming/Servers/1.20.4/plugins"))
        //into "E:/Documents/Workspace/Servers/1.18.2-TEST/plugins/"
    }

    assemble {
        dependsOn(shadowJar)
        finalizedBy("copyToDev")
    }
    test {
        useJUnitPlatform()
        // Ensure testing is never "up-to-date" (in Gradle-speak), which means it can never be skipped,
        // as it would otherwise be.
        outputs.upToDateWhen { false }

        // Ensure we get all the useful test output.
        testLogging {
            events("failed", "passed", "skipped")
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

bukkit {

    name = "Expeditions"
    version = project.version as String
    description = "Expeditions plugins!"
    main = "com.ravingarinc.expeditions.Expeditions"

    // API version (should be set for 1.13+)
    apiVersion = "1.19"

    // Other possible properties from plugin.yml (optional)
    author = "RAVINGAR"
    depend = listOf("Multiverse-Core", "ProtocolLib")
    softDepend = listOf("MMOItems", "MythicMobs", "MythicCrucible", "PlaceholderAPI", "Citizens", "Parties", "BetonQuest")

    commands {
        register("expeditions") {
            aliases = listOf("e")
            description = "Expeditions Admin Command"
            usage = "Unknown argument. Try /expeditions ?"
        }
    }
}
