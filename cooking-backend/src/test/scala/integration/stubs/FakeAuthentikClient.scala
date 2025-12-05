package integration.stubs

import api.users.AuthentikClient
import play.api.Configuration
import zio.{Task, ZIO}
import javax.inject.Inject

class FakeAuthentikClient @Inject() (config: Configuration) extends AuthentikClient(config) {
  override def updateUser(currentUsername: String, email: Option[String], newUsername: Option[String]): Task[Unit] = {
    ZIO.unit
  }
}
