package api.tags

import api.Listing
import com.google.inject.ImplementedBy

@ImplementedBy(classOf[TagsFacade])
trait TagsApi extends Listing[String]
