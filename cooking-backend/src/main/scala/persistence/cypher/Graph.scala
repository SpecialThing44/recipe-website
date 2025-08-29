package persistence.cypher

import domain.ingredients.Ingredient
import domain.recipes.Recipe
import domain.users.User
import persistence.schema.ConstraintType.Unique
import persistence.schema.{Constraint, Index, IndexType}
import persistence.users.UserConverter.lowerPrefix

import scala.reflect.ClassTag

trait Graph[Domain: ClassTag] extends TagPathing {
  lazy val nodeLabel: String = implicitly[ClassTag[Domain]].runtimeClass.getSimpleName
  lazy val nodeVar: String = nodeLabel.toLowerCase

}


object Graph {
  val Indices: Seq[Index] = Vector(Index(Seq("name"), User.getClass.getSimpleName.replaceAll("$", ""), IndexType.Text))

  val Constraints: Seq[Constraint] = Vector(
    Constraint("name", Ingredient.getClass.getSimpleName.replaceAll("$", ""), Unique),
    Constraint(s"${lowerPrefix}name", Ingredient.getClass.getSimpleName.replaceAll("$", ""), Unique),
    Constraint("wikiLink", Ingredient.getClass.getSimpleName.replaceAll("$", ""), Unique),
    Constraint("id", Ingredient.getClass.getSimpleName.replaceAll("$", ""), Unique),
    Constraint("email", User.getClass.getSimpleName.replaceAll("$", ""), Unique),
    Constraint(s"${lowerPrefix}email", User.getClass.getSimpleName.replaceAll("$", ""), Unique),
    Constraint("id", User.getClass.getSimpleName.replaceAll("$", ""), Unique),
    Constraint("id", Recipe.getClass.getSimpleName.replaceAll("$", ""), Unique),

  )

}
