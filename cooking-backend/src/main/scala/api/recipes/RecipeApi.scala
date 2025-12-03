package api.recipes
import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import context.ApiContext
import domain.recipes.{Recipe, RecipeInput, RecipeUpdateInput}
import org.apache.pekko.util.ByteString
import zio.ZIO
import java.util.UUID

@ImplementedBy(classOf[RecipeFacade])
trait RecipeApi
    extends Persisting[Recipe, RecipeInput, RecipeUpdateInput]
    with Querying[Recipe] {
  def save(recipeId: UUID): ZIO[ApiContext, Throwable, Recipe]
  def deleteAll(): ZIO[ApiContext, Throwable, Unit]
  def uploadImage(recipeId: UUID, fileBytes: ByteString, contentType: String): ZIO[ApiContext, Throwable, Recipe]
  def deleteImage(recipeId: UUID): ZIO[ApiContext, Throwable, Recipe]
  def uploadInstructionImage(recipeId: UUID, fileBytes: ByteString, contentType: String): ZIO[ApiContext, Throwable, String]
}
