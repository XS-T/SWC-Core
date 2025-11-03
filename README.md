# ⚙️ SWC-Core

**The foundation of the SWC plugin ecosystem.**  
SWC-Core handles addon management, loading, and communication between modular components — making your Minecraft server truly extensible.

---

## 🧩 Features

- 📦 Dynamic **addon loading system**
- 🔄 Support for **enable**, **disable**, **reload**, and **unload** operations
- 🧠 Safe dependency tracking and version info
- 🔍 Built-in **Addon Info** and **Addon List** commands
- 💡 Simplified permission-based command access
- 🧰 Developer-friendly API for creating custom addons

---

## 💬 Commands

```yaml
/addon                      # Displays the Addon Manager help menu
/addon list                 # Lists all addons with their status
/addon info <id>            # Shows detailed information about an addon
/addon load <filename>      # Loads an addon JAR from the /addons folder
/addon unload <id>          # Unloads a loaded addon
/addon enable <id>          # Enables a loaded addon
/addon disable <id>         # Disables a loaded addon
/addon reload [id]          # Reloads a specific addon or all addons
```

---

## 🔒 Permissions

```ini
swccore.addon               ; Access to the /addon command
swccore.addon.list          ; List all addons
swccore.addon.info          ; View addon information
swccore.addon.load          ; Load addons from the addons folder
swccore.addon.unload        ; Unload active addons
swccore.addon.enable        ; Enable loaded addons
swccore.addon.disable       ; Disable active addons
swccore.addon.reload        ; Reload one or all addons
```

---

## 🧱 Folder Structure

```
/plugins/
 ├─ SWCCore.jar
 ├─ addons/
 │   ├─ SWC-Bounties.jar
 │   ├─ SWC-TownyHook.jar
 │   └─ YourCustomAddon.jar
```

All addons must be placed inside the `/addons` directory.  
They will automatically be scanned and can be managed with `/addon load`, `/addon enable`, and other subcommands.

---

## 💡 Developer Guide

### 🔧 Creating an Addon

Each addon is a separate `.jar` file with its own entry class and metadata.  
Your addon must include an **`addon.yml`** file in the root of your jar, similar to how Bukkit uses `plugin.yml`.

#### Example `addon.yml`

```yaml
id: swc-bounties
name: SWC-Bounties
version: 1.0.0
main: net.crewco.bounties.BountiesAddon
authors: [X ST]
description: Adds a bounty system that integrates with SWC-Core
dependencies: []
```

---

### 🧠 Addon Lifecycle

| Phase | Description |
|-------|--------------|
| **LOAD** | Detected from `/addons` folder and prepared for activation |
| **ENABLE** | Plugin logic is initialized (similar to `onEnable()` in Bukkit) |
| **DISABLE** | Addon is gracefully stopped and cleaned up |
| **UNLOAD** | Addon is fully removed from memory |
| **RELOAD** | Addon is reinitialized without restarting the server |

---

### 🧰 Example Kotlin Addon

```kotlin
package net.crewco.exampleaddon

import net.crewco.swccore.api.Addon
import org.bukkit.Bukkit

class ExampleAddon : Addon() {
    override fun onEnable() {
        Bukkit.getLogger().info("ExampleAddon enabled!")
    }

    override fun onDisable() {
        Bukkit.getLogger().info("ExampleAddon disabled.")
    }
}
```

---

## 🚀 Example Admin Workflow

```bash
# Check available addons
/addon list

# Load an addon jar file
/addon load SWC-TownyHook.jar

# Enable it
/addon enable swc-townyhook

# Reload it after updates
/addon reload swc-townyhook

# Disable or unload when not needed
/addon disable swc-townyhook
/addon unload swc-townyhook
```

---

<p align="center">
🧩 <b>SWC-Core</b> — The backbone of your plugin ecosystem. Modular. Powerful. Flexible.
</p>
