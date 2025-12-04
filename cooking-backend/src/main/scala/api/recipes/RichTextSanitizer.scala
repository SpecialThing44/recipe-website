package api.recipes

import api.moderation.{OpenAIModerationClient, ModerationViolation}
import com.google.inject.Inject
import io.circe.*
import io.circe.parser.*
import zio.{Task, ZIO}

class RichTextSanitizer @Inject() (moderationClient: OpenAIModerationClient) {
  
  private val MAX_INSTRUCTIONS_LENGTH = 50000
  private val ALLOWED_FORMATS = Set(
    "bold", "italic", "underline", "strike",
    "code", "blockquote", "code-block",
    "header", "list", "bullet", "ordered",
    "link", "image"
  )

  def validateAndSanitize(instructionsJson: String): Task[String] = for {
    // First, check content moderation
    plainText <- ZIO.succeed(toPlainText(instructionsJson))
    moderationResult <- moderationClient.moderateText(plainText)
    _ <- moderationResult match {
      case Some(violation) =>
        ZIO.fail(new IllegalArgumentException(violation.message))
      case None =>
        ZIO.unit
    }
    
    // Then validate JSON and sanitize
    sanitized <- ZIO.attempt {
      if (instructionsJson.length > MAX_INSTRUCTIONS_LENGTH) {
        throw new IllegalArgumentException(
          s"Instructions exceed maximum length of $MAX_INSTRUCTIONS_LENGTH characters"
        )
      }
      
      parse(instructionsJson) match {
        case Left(error) =>
          throw new IllegalArgumentException(s"Invalid JSON format: ${error.message}")
        
        case Right(json) =>
          val cursor = json.hcursor
          
          cursor.downField("ops").as[List[Json]] match {
            case Left(_) =>
              throw new IllegalArgumentException(
                "Invalid Quill Delta format: missing 'ops' array"
              )
            
            case Right(ops) =>
              ops.foreach { op =>
                validateOperation(op)
              }
              json.noSpaces
          }
      }
    }
  } yield sanitized
  
  private def validateOperation(op: Json): Unit = {
    val cursor = op.hcursor
    
    cursor.get[String]("insert").orElse(cursor.get[Json]("insert")) match {
      case Left(_) =>
        throw new IllegalArgumentException("Operation missing 'insert' field")
      case Right(_) =>
    }
    
    cursor.downField("attributes").as[JsonObject] match {
      case Right(attrs) =>
        attrs.keys.foreach { key =>
          if (!ALLOWED_FORMATS.contains(key) && !key.startsWith("align") && !key.startsWith("indent")) {
            throw new IllegalArgumentException(s"Unsupported format attribute: $key")
          }
        }
        
        attrs("link").flatMap(_.asString).foreach { url =>
          validateUrl(url)
        }
        
        attrs("image").flatMap(_.asString).foreach { url =>
          validateImageUrl(url)
        }
        
      case Left(_) =>
    }
  }
  
  private def validateUrl(url: String): Unit = {
    if (url.trim.isEmpty) {
      throw new IllegalArgumentException("Empty URL not allowed")
    }
    
    val lowercaseUrl = url.toLowerCase.trim
    if (lowercaseUrl.startsWith("javascript:") || 
        lowercaseUrl.startsWith("data:") ||
        lowercaseUrl.startsWith("vbscript:")) {
      throw new IllegalArgumentException(s"Unsafe URL scheme detected: $url")
    }
  }
  
  private def validateImageUrl(url: String): Unit = {
    if (url.trim.isEmpty) {
      throw new IllegalArgumentException("Empty image URL not allowed")
    }
    
    val lowercaseUrl = url.toLowerCase.trim
    if (!lowercaseUrl.startsWith("http://") && 
        !lowercaseUrl.startsWith("https://") &&
        !lowercaseUrl.startsWith("/")) {
      throw new IllegalArgumentException(s"Invalid image URL scheme: $url")
    }
  }
  

  def extractImageUrls(instructionsJson: String): Task[Seq[String]] = ZIO.attempt {
    parse(instructionsJson) match {
      case Left(_) => Seq.empty
      case Right(json) =>
        val cursor = json.hcursor
        cursor.downField("ops").as[List[Json]] match {
          case Left(_) => Seq.empty
          case Right(ops) =>
            ops.flatMap { op =>
              val opCursor = op.hcursor
              opCursor.downField("insert").downField("image").as[String].toOption
                .orElse(
                  opCursor.downField("attributes").downField("image").as[String].toOption
                )
            }
        }
    }
  }

  private def toPlainText(instructionsJson: String): String = {
    parse(instructionsJson) match {
      case Left(_) => ""
      case Right(json) =>
        val cursor = json.hcursor
        cursor.downField("ops").as[List[Json]] match {
          case Left(_) => ""
          case Right(ops) =>
            ops.flatMap { op =>
              op.hcursor.get[String]("insert").toOption
            }.mkString("")
        }
    }
  }
}
