package domain.shared

import java.util.UUID

trait Identified {
  def id: Option[UUID]

  def idOrGenerate: UUID = id.getOrElse(UUID.randomUUID())
}
