package domain.shared

import java.util.UUID

trait Identified {
  def id: Option[UUID]
}
