package org.crewco.swccore.api.addon

import java.io.File
import java.util.logging.Logger
abstract class AbstractBaseAddon: BaseAddon {

    /**
     * Logger instance for this addon
     */
    protected val logger: Logger = Logger.getLogger(name)

    /**
     * Data folder for this addon's files
     */
    val dataFolder: File by lazy {
        File(plugin.dataFolder, "addons/$id").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Whether this addon is currently enabled
     */
    var isEnabled: Boolean = false
        private set

    override fun onLoad() {
        logger.info("Loading addon: $name v$version")
    }

    override fun onEnable() {
        logger.info("Enabling addon: $name v$version")
        isEnabled = true
    }

    override fun onDisable() {
        logger.info("Disabling addon: $name v$version")
        isEnabled = false
    }

    override fun onReload() {
        logger.info("Reloading addon: $name")
    }

    /**
     * Helper method to log info messages
     */
    protected fun logInfo(message: String) {
        logger.info("[$name] $message")
    }

    /**
     * Helper method to log warning messages
     */
    protected fun logWarning(message: String) {
        logger.warning("[$name] $message")
    }

    /**
     * Helper method to log error messages
     */
    protected fun logError(message: String, throwable: Throwable? = null) {
        logger.severe("[$name] $message")
        throwable?.printStackTrace()
    }
}