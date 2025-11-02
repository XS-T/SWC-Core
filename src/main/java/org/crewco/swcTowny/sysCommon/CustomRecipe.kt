// CustomRecipe
package org.crewco.swcTowny.sysCommon

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment

open class CustomRecipe(
        private val resultMaterial: Material,
        private val displayName: String,
        private val shape: Array<String>,
        private val ingredients: Map<Char, Material>,
        private val enchantments: Map<Enchantment, Int>? = null,
        private val lore: List<String>? = null,
        private val ammount: Int? = null
) {

    fun register(recipeManager: RecipeManager) {
        recipeManager.createCustomRecipe(resultMaterial, displayName, shape, ingredients, enchantments, lore,ammount)
    }
}