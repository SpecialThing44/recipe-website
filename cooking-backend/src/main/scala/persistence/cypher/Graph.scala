package persistence.cypher

import domain.ingredients.Ingredient
import domain.recipes.Recipe
import domain.users.User
import persistence.schema.ConstraintType.Unique
import persistence.schema.{Constraint, Index, IndexType}
import persistence.users.UserConverter.lowerPrefix

import scala.reflect.ClassTag

def className[A](classType: Class[A]): String =
  classType.getSimpleName.replace("$", "")


trait Graph[Domain: ClassTag] extends TagPathing {
  lazy val nodeLabel: String = implicitly[ClassTag[Domain]].runtimeClass.getSimpleName
  lazy val nodeVar: String = nodeLabel.toLowerCase

}


object Graph {
  val Indices: Seq[Index] = Vector(Index(Seq("name"), User.getClass.getSimpleName.replaceAll("$", ""), IndexType.Text))

  val Constraints: Seq[Constraint] = Vector(
    Constraint("name", className(Ingredient.getClass), Unique),
    Constraint(s"${lowerPrefix}name", className(Ingredient.getClass), Unique),
    Constraint("wikiLink", className(Ingredient.getClass), Unique),
    Constraint("id", className(Ingredient.getClass), Unique),
    Constraint("email", className(User.getClass), Unique),
    Constraint(s"${lowerPrefix}email", className(User.getClass), Unique),
    Constraint("id", className(User.getClass), Unique),
    Constraint("id", className(Recipe.getClass), Unique),
  )
}
