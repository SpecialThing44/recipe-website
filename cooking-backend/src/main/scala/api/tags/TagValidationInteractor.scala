package api.tags

import com.google.inject.Inject
import context.ApiContext
import domain.filters.{Filters, StringFilter}
import domain.types.InputError
import persistence.tags.Tags
import zio.ZIO

class TagValidationInteractor @Inject() (
    tagsPersistence: Tags,
) {
  def validateNoUnauthorizedNewTags(
      tags: Seq[String],
      isAdmin: Boolean
  ): ZIO[ApiContext, Throwable, Unit] = {
    if (tags.isEmpty) {
      ZIO.unit
    } else {
      for {
        existingTags <- tagsPersistence.list(
          Filters
            .empty()
            .copy(name = Some(StringFilter.empty().copy(anyOf = Some(tags))))
        )
        newTags = tags.filterNot(existingTags.contains)
        _ <-
          if (newTags.nonEmpty && !isAdmin) {
            ZIO.fail(
              InputError(
                s"Only admins can create new tags: ${newTags.mkString(", ")}"
              )
            )
          } else {
            ZIO.unit
          }
      } yield ()
    }
  }
}
