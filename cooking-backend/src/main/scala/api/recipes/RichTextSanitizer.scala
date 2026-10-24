package api.recipes

import api.moderation.{ModerationViolation, OpenAIModerationClient}
import com.google.inject.Inject
import io.circe.*
import io.circe.parser.*
import zio.{Task, ZIO}
import domain.types.InputError
import java.net.URI
import scala.util.Try

class RichTextSanitizer @Inject() (moderationClient: OpenAIModerationClient) {

  private val MAX_INSTRUCTIONS_LENGTH = 50000
  private val ALLOWED_FORMATS = Set(
    "bold",
    "italic",
    "underline",
    "strike",
    "code",
    "blockquote",
    "code-block",
    "header",
    "list",
    "bullet",
    "ordered",
    "link",
    "image"
  )

  def validateAndSanitize(instructionsJson: String): Task[String] = for {
    sanitized <- ZIO.attempt {
      if (instructionsJson.length > MAX_INSTRUCTIONS_LENGTH) {
        throw InputError(
          s"Instructions exceed maximum length of $MAX_INSTRUCTIONS_LENGTH characters"
        )
      }

      parse(instructionsJson) match {
        case Left(error) =>
          throw InputError(
            s"Invalid JSON format: ${error.message}"
          )

        case Right(json) =>
          val cursor = json.hcursor

          cursor.downField("ops").as[List[Json]] match {
            case Left(_) =>
              throw InputError(
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
    plainText <- ZIO.succeed(toPlainText(sanitized))
    moderationResult <- moderationClient.moderateText(plainText)
    _ <- moderationResult match {
      case Some(violation) =>
        ZIO.fail(InputError(violation.message))
      case None =>
        ZIO.unit
    }

  } yield sanitized

  private def validateOperation(op: Json): Unit = {
    val cursor = op.hcursor

    cursor.get[Json]("insert").toOption match {
      case Some(insert) if insert.isString => ()
      case Some(insert) if insert.asObject.exists(_.keys.toSet == Set("image")) =>
        val imageUrl = insert.hcursor.get[String]("image").fold(
          _ => throw InputError("Image embed must contain a URL string"), identity
        )
        validateImageUrl(imageUrl)
      case _ => throw InputError("Operation must insert text or an image URL")
    }

    cursor.downField("attributes").as[JsonObject] match {
      case Right(attrs) =>
        attrs.keys.foreach { key =>
          if (
            !ALLOWED_FORMATS.contains(key) && !Set("align", "indent").contains(key)
          ) {
            throw InputError(
              s"Unsupported format attribute: $key"
            )
          }
        }

        attrs("link").flatMap(_.asString).foreach { url =>
          validateUrl(url)
        }

        attrs("image").flatMap(_.asString).foreach { url =>
          validateImageUrl(url)
        }

      case Left(_) if cursor.downField("attributes").succeeded =>
        throw InputError("Operation attributes must be an object")
      case Left(_) => ()
    }
  }

  private def validateUrl(url: String): Unit = {
    val valid = Try(new URI(url)).toOption.exists(uri =>
      Set("https", "http", "mailto").contains(Option(uri.getScheme).getOrElse("")) ||
        (uri.getScheme == null && !url.startsWith("//") && (url.startsWith("/") || url.startsWith("#")))
    )
    if (!valid) throw InputError("Invalid link URL")
  }

  private def validateImageUrl(url: String): Unit = {
    val valid = Try(new URI(url)).toOption.exists(uri =>
      (Set("https", "http").contains(Option(uri.getScheme).getOrElse("")) && uri.getHost != null && uri.getUserInfo == null) ||
        (uri.getScheme == null && url.startsWith("/") && !url.startsWith("//"))
    )
    if (!valid) throw InputError("Invalid image URL")
  }

  def extractImageUrls(instructionsJson: String): Task[Seq[String]] =
    ZIO.attempt {
      parse(instructionsJson) match {
        case Left(_) => Seq.empty
        case Right(json) =>
          val cursor = json.hcursor
          cursor.downField("ops").as[List[Json]] match {
            case Left(_) => Seq.empty
            case Right(ops) =>
              ops.flatMap { op =>
                val opCursor = op.hcursor
                opCursor
                  .downField("insert")
                  .downField("image")
                  .as[String]
                  .toOption
                  .orElse(
                    opCursor
                      .downField("attributes")
                      .downField("image")
                      .as[String]
                      .toOption
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
            ops
              .flatMap { op =>
                op.hcursor.get[String]("insert").toOption
              }
              .mkString("")
        }
    }
  }
}
