import org.apache.commons.lang3.SystemUtils

plugins {
    idea
    java
    kotlin("jvm") version "2.2.0"
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("net.kyori.blossom") version "1.3.2"
}

val baseGroup = project.properties["mod.group"].toString()
val mcVersion = project.properties["minecraft.version"].toString()
val modId = project.properties["mod.id"].toString()
val modName = project.properties["mod.name"].toString()
val modVersion = project.properties["mod.version"].toString()
val transformerFile = file("src/main/resources/accesstransformer.cfg")

blossom {
    replaceToken("@mod_id@", modId)
    replaceToken("@mod_name@", modName)
    replaceToken("@mod_version@", modVersion)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

loom {
    launchConfigs {
        "client" {
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) vmArgs.remove("-XstartOnFirstThread")
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.$modId.json")
        if (transformerFile.exists()) accessTransformer(transformerFile)
    }
    mixin {
        defaultRefmapName.set("mixins.$modId.refmap.json")
    }
}

tasks.compileJava {
    dependsOn(tasks.processResources)
}

sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
    java.srcDir(layout.projectDirectory.dir("src/main/kotlin"))
    kotlin.destinationDirectory.set(java.destinationDirectory)
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    maven("https://repo.essential.gg/repository/maven-public")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.deftu.dev/releases")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")

    shadowImpl("org.reflections:reflections:0.10.2")
    shadowImpl("gg.essential:elementa:710")
    shadowImpl("gg.essential:universalcraft-1.8.9-forge:430")
    shadowImpl("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")

    shadowImpl("xyz.meowing:vexel-1.8.9-forge:110") {
        exclude("org.lwjgl")
    }
    shadowImpl("com.squareup.okhttp3:okhttp-jvm:5.2.1")

    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    archiveBaseName.set("zen-1.8.9-forge")
    manifest.attributes.run {
        this["Main-Class"] = "xyz.meowing.zen.Installer"
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modId.json"
        if (transformerFile.exists()) this["FMLAT"] = "${modId}_at.cfg"
    }
}

tasks.processResources {
    inputs.property("mod_version", project.version)
    inputs.property("mc_version", mcVersion)
    inputs.property("mod_id", modId)
    inputs.property("mod_group", baseGroup)

    filesMatching(listOf("mcmod.info", "mixins.$modId.json")) {
        expand(inputs.properties)
    }

    rename("accesstransformer.cfg", "META-INF/${modId}_at.cfg")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xlambdas=class")
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    archiveBaseName.set("zen-1.8.9-forge-${modVersion}")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.register("generateLists") {
    val srcDir = file("src/main/kotlin/xyz/meowing/zen")
    val featureOutput = file("build/generated/resources/features.list")
    val commandOutput = file("build/generated/resources/commands.list")

    val moduleRegex = Regex("@Zen\\.Module\\s*(?:\\n|\\s)*(?:object|class)\\s+(\\w+)")
    val commandRegex = Regex("@Zen\\.Command\\s*(?:\\n|\\s)*(?:object|class)\\s+(\\w+)")
    val pkgRegex = Regex("package\\s+([\\w.]+)")

    inputs.dir(srcDir).optional(true)
    outputs.files(featureOutput, commandOutput)

    doLast {
        val featureClasses = mutableListOf<String>()
        val commandClasses = mutableListOf<String>()

        if (!srcDir.exists()) return@doLast

        srcDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension in listOf("kt", "java")) {
                val text = file.readText()
                val pkg = pkgRegex.find(text)?.groupValues?.get(1) ?: return@forEach

                moduleRegex.findAll(text).forEach { match ->
                    featureClasses += "${pkg}.${match.groupValues[1]}"
                }

                commandRegex.findAll(text).forEach { match ->
                    commandClasses += "${pkg}.${match.groupValues[1]}"
                }
            }
        }

        featureOutput.parentFile.mkdirs()
        commandOutput.parentFile.mkdirs()
        featureOutput.writeText(featureClasses.joinToString("\n"))
        commandOutput.writeText(commandClasses.joinToString("\n"))
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("generateLists")
    from("build/generated/resources")
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("archiveJars"))
    archiveClassifier.set("deps")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    configurations = listOf(shadowImpl)
    exclude("META-INF/versions/**")
    fun relocate(name: String) = relocate(name, "$baseGroup.deps.$name")
    relocate("gg.essential.elementa")
    relocate("gg.essential.universal")
    mergeServiceFiles()
}

tasks.assemble.get().dependsOn(tasks.remapJar)