package api.users

import com.google.inject.Inject
import play.api.Configuration
import sttp.client3._
import sttp.model.{MediaType, Uri}
import zio.{Task, ZIO}
import io.circe.syntax._
import io.circe.parser._

class AuthentikClient @Inject() (config: Configuration) {
  private lazy val apiUrl = config.get[String]("auth.apiUrl")
  private lazy val apiToken = config.get[String]("auth.apiToken").trim

  private val backend = HttpClientSyncBackend()

  def updateUser(currentUsername: String, email: Option[String], newUsername: Option[String]): Task[Unit] = {
    if (email.isEmpty && newUsername.isEmpty) {
      ZIO.unit
    } else {
      ZIO.attempt {
        if (apiToken.isEmpty) {
          throw new RuntimeException("Authentik API token is missing. Please set AUTH_API_TOKEN environment variable.")
        }
        
        // 1. Find user PK by username
        val searchUrl = s"$apiUrl/core/users/?username=$currentUsername"
        // println(s"AuthentikClient: Searching user at $searchUrl with token (length: ${apiToken.length})")
        
        val searchRequest = basicRequest
          .get(Uri.parse(searchUrl).right.get)
          .header("Authorization", s"Bearer $apiToken")
          .contentType(MediaType.ApplicationJson)

        val searchResponse = searchRequest.send(backend)

        if (!searchResponse.code.isSuccess) {
           throw new RuntimeException(s"Failed to search user in Authentik: ${searchResponse.code} ${searchResponse.body}")
        }

        val pk = parse(searchResponse.body.getOrElse("{}"))
          .flatMap(_.hcursor.downField("results").downArray.downField("pk").as[Int]) match {
            case Right(id) => id
            case Left(_) => 
               // Try parsing as String (UUID)
               parse(searchResponse.body.getOrElse("{}"))
                .flatMap(_.hcursor.downField("results").downArray.downField("pk").as[String]) match {
                  case Right(id) => id
                  case Left(e) => throw new RuntimeException(s"User not found or PK invalid for username: $currentUsername. Error: $e")
                }
          }

        // 2. Update user
        val bodyMap = scala.collection.mutable.Map[String, String]()
        email.foreach(e => bodyMap.put("email", e))
        newUsername.foreach(u => {
            bodyMap.put("username", u)
            bodyMap.put("name", u)
        })

        if (bodyMap.nonEmpty) {
             val request = basicRequest
              .patch(Uri.parse(s"$apiUrl/core/users/$pk/").right.get)
              .header("Authorization", s"Bearer $apiToken")
              .contentType(MediaType.ApplicationJson)
              .body(bodyMap.toMap.asJson.noSpaces)
              
             val response = request.send(backend)
             
             if (!response.code.isSuccess) {
               throw new RuntimeException(s"Failed to update user in Authentik: ${response.code} ${response.body}")
             }
        }
      }
    }
  }
}
