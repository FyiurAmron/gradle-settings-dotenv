package com.sinch.gradle.dotenv

import com.sinch.gradle.dotenv.DotenvPluginExtension.Companion.DOTENV_EXTENSION_NAME
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.internal.extensions.core.extra

open class DotenvPluginExtension {
    companion object {
        const val DOTENV_EXTENSION_NAME = "dotenv"
    }

    var vars: Map<String, String> = mapOf()
}

@Suppress("unused")
class DotenvPlugin : Plugin<Settings> {
    companion object {
        val logger: Logger = Logging.getLogger(DotenvPlugin::class.java)

        @Suppress("MemberVisibilityCanBePrivate")
        fun parseDotenv(lines: List<String>) =
            lines
                .filterNot { it.isBlank() || it.startsWith("#") }
                .associate {
                    val limit = 2
                    val parts = it.split("=", limit = limit)
                    if (parts.size < limit) {
                        throw IllegalArgumentException("input strings need to have two parts separated by '=', instead got\n$it")
                    }
                    if (parts[0].isBlank()) {
                        throw IllegalArgumentException("input strings need to have non-blank key before '=', instead got\n$it")
                    }
                    val (k, v) = parts

                    k to v
                }
    }

    override fun apply(settings: Settings) {
        val extraProperties = settings.extensions.extraProperties
        val properties = extraProperties.properties

        fun getProp(
            k: String,
            def: String,
        ) = properties.getOrDefault(k, def).toString()

        val dotenvFilename = getProp("dotenv.filename", ".env")

        val dotenvMergeIntoSettingsProperties = getProp("dotenv.mergeIntoSettingsProperties", "true").toBoolean()
        val dotenvMergeEnvIntoSettingsProperties = getProp("dotenv.mergeEnvIntoSettingsProperties", "true").toBoolean()
        val dotenvMergeIntoProjectProperties = getProp("dotenv.mergeIntoProjectProperties", "true").toBoolean()
        val dotenvMergeEnvIntoProjectProperties = getProp("dotenv.mergeEnvIntoProjectProperties", "true").toBoolean()

        val extension = settings.gradle.extensions.create(DOTENV_EXTENSION_NAME, DotenvPluginExtension::class.java)

        val dotenvFile = settings.settingsDir.resolve(dotenvFilename)
        logger.trace("dotenv file path: ${dotenvFile.path}")

        val vars =
            if (dotenvFile.exists()) {
                parseDotenv(dotenvFile.readLines())
            } else {
                mapOf()
            }

        fun ExtraPropertiesExtension.setFrom(map: Map<String, String>) {
            map.forEach {
                set(it.key, it.value) // needed because properties Map is detached as per doc
            }
        }

        val env = System.getenv()

        if (dotenvMergeIntoSettingsProperties) {
            extraProperties.setFrom(vars)
        }

        if (dotenvMergeEnvIntoSettingsProperties) {
            extraProperties.setFrom(env)
        }

        settings.gradle.beforeProject {
            val projectExtraProperties = it.extra

            if (dotenvMergeIntoProjectProperties) {
                projectExtraProperties.setFrom(vars)
            }

            if (dotenvMergeEnvIntoProjectProperties) {
                projectExtraProperties.setFrom(env)
            }
        }

        extension.vars = vars

        logger.trace("dotenv: {}", vars)
    }
}
