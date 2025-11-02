// RecipeManager
package org.crewco.swcTowny.sysCommon

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe

class RecipeManager {

    private val customRecipes = mutableListOf<ShapedRecipe>()

    fun createCustomRecipe(
            resultMaterial: Material,
            displayName: String,
            shape: Array<String>,
            ingredients: Map<Char, Material>,
            enchantments: Map<Enchantment, Int>?,
            lore: List<String>?,
            amount: Int?
    ) {
        val item = ItemStack(resultMaterial, amount ?: 1)
        val meta = item.itemMeta

        meta.displayName = displayName

        enchantments?.forEach { (enchantment, level) -> meta.addEnchant(enchantment, level, true) }
        lore?.let { meta.lore = it }

        item.itemMeta = meta

        val recipe = ShapedRecipe(item)
        recipe.shape(*shape)

        for ((key, value) in ingredients) {
            recipe.setIngredient(key, value)
        }

        customRecipes.add(recipe)
        Bukkit.addRecipe(recipe)
    }

    fun reloadRecipes() {
        // Clear existing recipes
        Bukkit.clearRecipes()
        customRecipes.clear()
        // Load recipes from config or other source
        // (Call the method you use to load recipes)

        // Register the reloaded recipes
        customRecipes.forEach { recipe ->
            Bukkit.addRecipe(recipe)
        }
    }
}
