package org.crewco.swccore.api.addon

import org.crewco.swccore.system.managers.CommandManager

/**
 * Interface that the main plugin must implement to provide CommandManager
 */
interface SWC_Core {
    val commandManager: CommandManager
}