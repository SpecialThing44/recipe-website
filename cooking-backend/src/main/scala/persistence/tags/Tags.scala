package persistence.tags

import api.Listing
import com.google.inject.ImplementedBy

@ImplementedBy(classOf[TagsPersistence])
trait Tags extends Listing[String]
