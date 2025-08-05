package persistence.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.food.ingredients.InstructionIngredient
import domain.food.recipes.Recipe
import domain.people.users.User
import domain.types.NoSuchEntityError
import io.circe.jawn.decode
import io.circe.syntax.EncoderOps
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import play.api.Configuration
import play.api.libs.json.JsValue
import zio.ZIO

import java.time.Instant
import java.util.UUID
import scala.util.Try

class RecipePersistence @Inject() (config: Configuration) extends Recipes {

  private val uri = config.get[String]("neo4j.uri")
  private val username = config.get[String]("neo4j.username")
  private val password = config.get[String]("neo4j.password")
  private val driver: Driver =
    GraphDatabase.driver(uri, AuthTokens.basic(username, password))

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] =
    ???

  override def find(query: Filters): ZIO[ApiContext, Throwable, Recipe] = ???

  override def create(entity: Recipe): ZIO[ApiContext, Throwable, Recipe] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""
               |CREATE (r:Recipe {
               |  id: '${entity.id}',
               |  name: '${entity.name}',
               |  user_id: '${entity.user.id}',
               |  aliases: '${entity.aliases.asJson.noSpaces}',
               |  tags: '${entity.tags.asJson.noSpaces}',
               |  ingredients: '${entity.ingredients.asJson.noSpaces}',
               |  prep_time: ${entity.prepTime},
               |  cook_time: ${entity.cookTime},
               |  vegetarian: ${entity.vegetarian},
               |  vegan: ${entity.vegan},
               |  country_of_origin: '${entity.countryOfOrigin.getOrElse("")}',
               |  cuisine: '${entity.cuisine.getOrElse("")}',
               |  public: ${entity.public},
               |  wiki_link: '${entity.wikiLink}',
               |  video_link: '${entity.videoLink}',
               |  instructions: '${entity.instructions}',
               |  created_on: '${entity.createdOn}',
               |  updated_on: '${entity.updatedOn}'
               |})
               |RETURN r
               |""".stripMargin
          session.run(query)
          entity
        } finally {
          session.close()
        }
      }
    }

  override def update(
      entity: Recipe,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""
               |MATCH (r:Recipe {id: '${entity.id}'})
               |SET r.name = '${entity.name}',
               |    r.user_id = '${entity.user.id}',
               |    r.aliases = '${entity.aliases.asJson.noSpaces}',
               |    r.tags = '${entity.tags.asJson.noSpaces}',
               |    r.ingredients = '${entity.ingredients.asJson.noSpaces}',
               |    r.prep_time = ${entity.prepTime},
               |    r.cook_time = ${entity.cookTime},
               |    r.vegetarian = ${entity.vegetarian},
               |    r.vegan = ${entity.vegan},
               |    r.country_of_origin = '${entity.countryOfOrigin.getOrElse(
                ""
              )}',
               |    r.cuisine = '${entity.cuisine.getOrElse("")}',
               |    r.public = ${entity.public},
               |    r.wiki_link = '${entity.wikiLink}',
               |    r.video_link = '${entity.videoLink}',
               |    r.instructions = '${entity.instructions}',
               |    r.created_on = '${entity.createdOn}',
               |    r.updated_on = '${entity.updatedOn}'
               |RETURN r
               |""".stripMargin
          session.run(query)
          entity
        } finally {
          session.close()
        }
      }
    }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    for {
      recipe <- getById(id)
      _ <- ZIO.fromTry {
        Try {
          val session: Session = driver.session()
          try {
            val query =
              s"""
                 |MATCH (r:Recipe {id: '$id'})
                 |DETACH DELETE r
                 |""".stripMargin
            session.run(query)
          } finally {
            session.close()
          }
        }
      }
    } yield recipe

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""
               |MATCH (r:Recipe {id: '$id'})
               |RETURN r
               |""".stripMargin
          val result = session.run(query)
          if (result.hasNext) {
            val record = result.next().get("r").asMap()
            Recipe(
              id = UUID.fromString(record.get("id").toString),
              name = record.get("name").toString,
              user = User
                .empty()
                .copy(id =
                  UUID.fromString(record.get("user_id").toString)
                ), // Assuming you have a way to fetch the user by ID
              aliases = decode[Seq[String]](record.get("aliases").toString)
                .getOrElse(Seq.empty),
              tags = decode[Seq[String]](record.get("tags").toString)
                .getOrElse(Seq.empty),
              ingredients = decode[Seq[InstructionIngredient]](
                record.get("ingredients").toString
              ).getOrElse(Seq.empty),
              prepTime = record.get("prep_time").toString.toInt,
              cookTime = record.get("cook_time").toString.toInt,
              vegetarian = record.get("vegetarian").toString.toBoolean,
              vegan = record.get("vegan").toString.toBoolean,
              countryOfOrigin =
                Option(record.get("country_of_origin").toString),
              cuisine = Option(record.get("cuisine").toString),
              public = record.get("public").toString.toBoolean,
              wikiLink = record.get("wiki_link").toString,
              videoLink = record.get("video_link").toString,
              instructions = record.get("instructions").toString,
              createdOn = Instant.parse(record.get("created_on").toString),
              updatedOn = Instant.parse(record.get("updated_on").toString)
            )
          } else {
            throw NoSuchEntityError(s"Recipe with id $id not found")
          }
        } finally {
          session.close()
        }
      }
    }
}
