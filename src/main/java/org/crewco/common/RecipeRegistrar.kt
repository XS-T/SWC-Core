// RecipeRegistrar
package org.crewco.common

import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.reflect.KClass

object RecipeRegistrar {

    private lateinit var plugin: JavaPlugin
    val recipeManager = RecipeManager()

    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
    }

    fun registerRecipes(vararg recipeClasses: KClass<out CustomRecipe>) {
        for (recipeClass in recipeClasses) {
            try {
                val instance = recipeClass.java.newInstance()
                if (instance is CustomRecipe) {
                    instance.register(recipeManager)
                } else {
                    plugin.logger.warning("Class $recipeClass does not extend CustomRecipe.")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error while registering recipe: ${e.message}")
            }
        }
    }

    fun loadRecipesFromConfig() {
        val config = getRecipesConfig()

        if (config.isConfigurationSection("recipes")) {
            val recipeSection = config.getConfigurationSection("recipes")

            for (recipeKey in recipeSection.getKeys(false)) {
                val path = "recipes.$recipeKey."

                val resultMaterialName = config.getString("$path.resultMaterial") ?: ""
                val resultMaterial = Material.matchMaterial(resultMaterialName)
                val displayName = config.getString("$path.displayName", "")
                val shape = config.getStringList("$path.shape").toTypedArray()
                val ingredients = config.getConfigurationSection("$path.ingredients")
                        ?.getValues(false)
                        ?.mapKeys { it.key[0] }
                        ?.mapValues { Material.matchMaterial(it.value.toString()) }

                val enchantments = config.getConfigurationSection("$path.enchantments")
                        ?.getValues(false)
                        ?.mapKeys { Enchantment.getByName(it.key) }
                        ?.mapValues { it.value as Int }

                val lore = config.getStringList("$path.lore")

                if (resultMaterial != null && !displayName.isEmpty() && ingredients != null) {
                    val customRecipe = CustomRecipe(resultMaterial, displayName, shape, ingredients, enchantments, lore)
                    customRecipe.register(recipeManager) // Assuming you have a RecipeManager instance
                } else {
                    plugin.logger.warning("Invalid configuration for recipe '$recipeKey'. Skipping.")
                }
            }
        }
    }

    fun getRecipesConfig(): FileConfiguration {
        val configFile = File(plugin.dataFolder, "recipes.yml")

        // Save the resource from the jar if it doesn't exist
        if (!configFile.exists()) {
            plugin.saveResource("recipes.yml", false)
        }

       val  config = YamlConfiguration.loadConfiguration(configFile)
        return config
    }
}

