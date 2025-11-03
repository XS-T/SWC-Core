package org.crewco.swccore.api.addon

import org.bukkit.plugin.Plugin

/**
 * Main interface that all addons must implement.
 * This defines the lifecycle and basic properties of an addon.
 */
interface Addon {

    /**
     * Unique identifier for this addon (e.g., "my-cool-addon")
     */
    val id: String

    /**
     * Human-readable name of the addon
     */
    val name: String

    /**
     * Version of the addon
     */
    val version: String

    /**
     * Author(s) of the addon
     */
    val authors: List<String>

    /**
     * Description of what this addon does
     */
    val description: String
        get() = ""

    /**
     * List of addon IDs that this addon depends on
     */
    val dependencies: List<String>
        get() = emptyList()

    /**
     * Plugin Deps for Addons
     */
    val pluginDependencies: List<String>
        get() = emptyList()

    /**
     * Reference to the main plugin instance
     */
    val plugin: Plugin

    /**
     * Called when the addon is loaded and registered
     */
    fun onLoad()

    /**
     * Called when the addon should be enabled
     * This is where you register listeners, commands, etc.
     */
    fun onEnable()

    /**
     * Called when the addon should be disabled
     * Clean up resources here
     */
    fun onDisable()

    /**
     * Called when the addon should reload its configuration
     */
    fun onReload() {
        // Optional override
    }
}