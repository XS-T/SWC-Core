# 🏰 SWC-Core

A Towny integration plugin that enhances nation gameplay with claimable resources, admin tools, and performance stats.

---

## ⚙️ Commands & Permissions

```yaml
/claimResources                # 💎 Allows nation kings to claim configured world resources
Permission: swc.towny.claimResources

/claimResourcesAdmin           # 🧪 Allows admins to claim resources (mainly for testing)
Permission: swc.towny.claimResources.admin

/listData                      # 📋 Lists nation data (admin only)
Permission: swc.towny.admin.listData

/leadingNation                 # 🏆 Displays the current leading nation in each world
Permission: swc.towny.leadingNation

/reloadconfig                  # 🔁 Reloads the plugin configuration (admin only)
Permission: swc.towny.admin.reload
```

---

## 📜 Description

- **/claimResources** → Lets the King of a nation claim pre-configured world resources once every week *(or the configured interval)*.
- **/claimResourcesAdmin** → Used by Admins to test or bypass claim restrictions.
- **/listData** → Lists nation data for administrative monitoring.
- **/leadingNation** → Displays the top nations per world based on Town count and total block ownership.
- **/reloadconfig** → Reloads configuration files without restarting the server.

---

⚔️ **Empower your nations. Manage your worlds. Lead your empire.**
