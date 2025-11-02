package org.crewco.swccore.api.addon.events.subclasses

import org.crewco.swccore.api.addon.events.AddonEvent

/**
 * Called when an addon is reloaded
 */
class AddonReloadEvent(val addonId: String, val addonName: String) : AddonEvent()