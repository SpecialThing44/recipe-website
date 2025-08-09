package termination

import api.RecipeApp
import com.google.inject.{Inject, Singleton}
import org.apache.pekko.actor.CoordinatedShutdown

@Singleton
class Shutdown @Inject (
    coordinatedShutdown: CoordinatedShutdown,
    recipeApp: RecipeApp
) {
  recipeApp.shutdown()
}
