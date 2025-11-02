package org.crewco.swccore.api.addon.events.subclasses

import org.crewco.swccore.api.addon.events.AddonEvent

/**
 * Called when an addon is enabled
 */
class AddonEnableEvent(val addonId: String, val addonName: String) : AddonEvent()