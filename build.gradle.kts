import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("idea")
    id("net.minecrell.plugin-yml.bukkit") version "0.5.3"
    id("pmd")
    id("java-library")
    id("io.typecraft.gradlesource.spigot") version "1.0.0"
}

group = "com.ravingarinc.expeditions"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    gradlePluginPortal()
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

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")


    maven("https://maven.enginehub.org/repo/")

    maven("https://repo.onarandombox.com/content/groups/public/")

    maven("https://jitpack.io")
}

dependencies {
    library(kotlin("stdlib"))
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")

    library("com.github.shynixn.mccoroutine", "mccoroutine-bukkit-api", "2.11.0")
    library("com.github.shynixn.mccoroutine", "mccoroutine-bukkit-core", "2.11.0")

    implementation("com.ravingarinc.api:common:1.3.1")
    implementation("com.ravingarinc.api:module:1.3.1")
    implementation("com.ravingarinc.api:gui:1.3.1")
    implementation("org.jetbrains:annotations:23.1.0")

    //compileOnly("org.spigotmc:spigot:1.19.4-R0.1-SNAPSHOT:remapped-mojang")
    //compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")

    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.1.0-SNAPSHOT")
    compileOnly("io.lumine:MythicLib-dist:1.5.2-SNAPSHOT")
    compileOnly("net.Indyuce:MMOItems-API:6.9.2-SNAPSHOT")


    compileOnly("com.sk89q.worldedit:worldedit-core:7.2.7") {
        isTransitive = false
    }

    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.7") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.8") {
        isTransitive = false
    }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.8") {
        isTransitive = false
    }

    compileOnly("com.onarandombox.multiversecore:multiverse-core:4.3.2") {
        isTransitive = false
    }
    compileOnly("com.comphenix.protocol:ProtocolLib:5.0.0")
    testImplementation("org.jetbrains:annotations:23.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks {
    idea {
        module {
            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }

    shadowJar {
        archiveBaseName.set("Expeditions")
        archiveClassifier.set("")
        archiveVersion.set("")
        relocate("com.ravingarinc.api", "com.ravingarinc.expeditions.libs.api")
    }

    artifacts {
        archives(shadowJar)
    }

    register<Copy>("copyToDev") {
        from(shadowJar)
        into(project.layout.projectDirectory.dir("../../Desktop/Programming/Server/plugins"))
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
    apiVersion = "1.18"

    // Other possible properties from plugin.yml (optional)
    author = "RAVINGAR"
    depend = listOf("WorldEdit", "WorldGuard", "Multiverse-Core")
    softDepend = listOf("MMOItems", "MythicMobs")

    commands {
        register("expeditions") {
            aliases = listOf("e")
            description = "Expeditions Admin Command"
            usage = "Unknown argument. Try /expeditions ?"
        }
    }
}
