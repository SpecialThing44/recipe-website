package domain.shared

import java.util.UUID

trait Identified {
  def id: Option[UUID]

  def idOrError: UUID = id.getOrElse(throw new RuntimeException("Id not found"))

  def idOrGenerate: UUID = id.getOrElse(UUID.randomUUID())
}
