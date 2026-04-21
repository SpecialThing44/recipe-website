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
  private val tagLabel = "Tag"

  val Indices: Seq[Index] = Vector(
    Index(Seq("lowername"), className(User.getClass), IndexType.Text),
    Index(Seq("lowername"), className(Recipe.getClass), IndexType.Text),
    Index(Seq("lowername"), tagLabel, IndexType.Text),

    Index(Seq("prepTime"), className(Recipe.getClass), IndexType.Range),
    Index(Seq("cookTime"), className(Recipe.getClass), IndexType.Range),

    Index(Seq("wikiLink"), className(Ingredient.getClass), IndexType.Range),
  )

  val Constraints: Seq[Constraint] = Vector(
    Constraint("name", className(Ingredient.getClass), Unique),
    Constraint(s"${lowerPrefix}name", className(Ingredient.getClass), Unique),
    Constraint("id", className(Ingredient.getClass), Unique),
    Constraint("name", tagLabel, Unique),
    Constraint(s"${lowerPrefix}name", tagLabel, Unique),

    Constraint("identity", className(User.getClass), Unique),
    Constraint("email", className(User.getClass), Unique),
    Constraint(s"${lowerPrefix}email", className(User.getClass), Unique),
    Constraint("id", className(User.getClass), Unique),
    Constraint("id", className(Recipe.getClass), Unique),
  )
}
