package domain.food.ingredients

import java.net.URL

case class Ingredient(
    name: String,
    aliases: Seq[String],
    wikiLink: URL, // Make want to strongly type this
    vegetarian: Boolean,
    vegan: Boolean,
    tags: Seq[String], // Strongly type this ,//
    category: String
)
