import com.matthewprenger.cursegradle.CurseArtifact
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseRelation
import com.matthewprenger.cursegradle.Options
import gg.essential.gradle.util.noServerRunConfigs

plugins {
    alias(libs.plugins.kotlin)
    id(egt.plugins.multiversion.get().pluginId)
    id(egt.plugins.defaults.get().pluginId)
    alias(libs.plugins.shadow)
    alias(libs.plugins.blossom)
    alias(libs.plugins.minotaur)
    alias(libs.plugins.cursegradle)
}

val mod_name: String by project
val mod_version: String by project
val mod_id: String by project

preprocess {
    vars.put("MODERN", if (project.platform.mcMinor >= 16) 1 else 0)
}

blossom {
    replaceToken("@NAME@", mod_name)
    replaceToken("@ID@", mod_id)
    replaceToken("@VER@", mod_version)
}

version = mod_version
group = "toni"

base {
    archivesName.set("$mod_name (${getMcVersionStr()}-${platform.loaderStr})")
}

loom.noServerRunConfigs()
loom {
    if (project.platform.isLegacyForge) runConfigs {
        "client" { programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker") }
    }
    if (project.platform.isForge) forge {
        mixinConfig("mixins.${mod_id}.json")
        accessTransformer("../../src/main/resources/META-INF/accesstransformer.cfg")
    }

    if (project.platform.isFabric) {
        accessWidenerPath = file("../../src/main/resources/${mod_id}.accesswidener")
    }

    mixin.defaultRefmapName.set("mixins.${mod_id}.refmap.json")
}

repositories {
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://repo.essential.gg/repository/maven-public/")
    maven("https://maven.dediamondpro.dev/releases")
    maven("https://maven.isxander.dev/releases")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://api.modrinth.com/maven")
    mavenCentral()
}

val shade: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {

}

tasks.processResources {
    inputs.property("id", mod_id)
    inputs.property("name", mod_name)
    val java = if (project.platform.mcMinor >= 18) {
        17
    } else {
        if (project.platform.mcMinor == 17) 16 else 8
    }
    val compatLevel = "JAVA_${java}"
    inputs.property("java", java)
    inputs.property("java_level", compatLevel)
    inputs.property("version", mod_version)
    inputs.property("mcVersionStr", project.platform.mcVersionStr)
    filesMatching(listOf("mcmod.info", "mods.toml", "fabric.mod.json")) {
        expand(
            mapOf(
                "id" to mod_id,
                "name" to mod_name,
                "java" to java,
                "java_level" to compatLevel,
                "version" to mod_version,
                "mcVersionStr" to getInternalMcVersionStr()
            )
        )
    }
}

tasks {
    withType<Jar> {
        if (project.platform.isFabric) {
            exclude("mcmod.info", "mods.toml", "pack.mcmeta")
        } else {
            exclude("fabric.mod.json")
            if (project.platform.isLegacyForge) {
                exclude("mods.toml")
            } else {
                exclude("mcmod.info")
            }
        }
        from(rootProject.file("LICENSE"))
        from(rootProject.file("LICENSE.LESSER"))
    }
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("dev")
        configurations = listOf(shade)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    remapJar {
        input.set(shadowJar.get().archiveFile)
        archiveClassifier.set("")
        finalizedBy("copyJar")
    }
    jar {
        if (project.platform.isLegacyForge) {
            manifest {
                attributes(
                    mapOf(
                        "ModSide" to "CLIENT",
                        "TweakOrder" to "0",
                        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
                        "ForceLoadAsMod" to true
                    )
                )
            }
        }
        dependsOn(shadowJar)
        archiveClassifier.set("")
        enabled = false
    }
    register<Copy>("copyJar") {
        File("${project.rootDir}/jars").mkdir()
        from(remapJar.get().archiveFile)
        into("${project.rootDir}/jars")
    }
    clean { delete("${project.rootDir}/jars") }
    project.modrinth {
        token.set(System.getenv("MODRINTH_TOKEN"))
        projectId.set("8KWb3iU0")
        versionNumber.set(mod_version)
        versionName.set("[${getMcVersionStr()}-${platform.loaderStr}] PridefulAnimals $mod_version")
        uploadFile.set(remapJar.get().archiveFile as Any)
        gameVersions.addAll(getMcVersionList())
        if (platform.isFabric) {
            loaders.add("fabric")
            loaders.add("quilt")
        } else if (platform.isForge) {
            loaders.add("forge")
            if (platform.mcMinor >= 20) loaders.add("neoforge")
        }
        changelog.set(file("../../changelog.md").readText())
        dependencies {
            if (platform.isFabric) required.project("fabric-api")
            required.project("yacl")
        }
    }
    project.curseforge {
        project(closureOf<CurseProject> {
            apiKey = System.getenv("CURSEFORGE_TOKEN")
            id = "973648"
            changelog = file("../../changelog.md")
            changelogType = "markdown"
            relations(closureOf<CurseRelation> {
                if (platform.isFabric) requiredDependency("fabric-api")
                requiredDependency("yacl")
            })
            gameVersionStrings.addAll(getMcVersionList())
            if (platform.isFabric) {
                addGameVersion("Fabric")
                addGameVersion("Quilt")
            } else if (platform.isForge) {
                addGameVersion("Forge")
                if (platform.mcMinor >= 20) addGameVersion("NeoForge")
            }
            releaseType = "release"
            mainArtifact(remapJar.get().archiveFile, closureOf<CurseArtifact> {
                displayName = "[${getMcVersionStr()}-${platform.loaderStr}] PridefulAnimals $mod_version"
            })
        })
        options(closureOf<Options> {
            javaVersionAutoDetect = false
            javaIntegration = false
            forgeGradleIntegration = false
        })
    }
    register("publish") {
        dependsOn(modrinth)
        dependsOn(curseforge)
    }
}

fun getMcVersionStr(): String {
    return when (project.platform.mcVersionStr) {
        else -> {
            val dots = project.platform.mcVersionStr.count { it == '.' }
            if (dots == 1) "${project.platform.mcVersionStr}.x"
            else "${project.platform.mcVersionStr.substringBeforeLast(".")}.x"
        }
    }
}

fun getInternalMcVersionStr(): String {
    return when (project.platform.mcVersionStr) {
        else -> {
            val dots = project.platform.mcVersionStr.count { it == '.' }
            if (dots == 1) "${project.platform.mcVersionStr}.x"
            else "${project.platform.mcVersionStr.substringBeforeLast(".")}.x"
        }
    }
}

fun getMcVersionList(): List<String> {
    return when (project.platform.mcVersionStr) {
        "1.20.1" -> listOf("1.20", "1.20.1")
        "1.19.2" -> listOf("1.19.2")
        "1.18.2" -> listOf("1.18.2")
        else -> error("Unknown version")
    }
}