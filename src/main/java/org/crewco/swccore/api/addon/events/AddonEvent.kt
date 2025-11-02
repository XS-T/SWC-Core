package org.crewco.swccore.api.addon.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Base class for addon-related events
 */
abstract class AddonEvent : Event() {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLERS
    }

    override fun getHandlers() = HANDLERS
}
