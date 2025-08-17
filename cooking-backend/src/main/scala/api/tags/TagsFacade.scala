package api.tags

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import persistence.tags.Tags
import zio.ZIO

class TagsFacade @Inject() (
    persistence: Tags,
) extends TagsApi {
  override def list(
      query: Filters
  ): ZIO[ApiContext, Throwable, Seq[String]] = {
    persistence.list(query)
  }
}
