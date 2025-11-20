package api.users

import api.storage.SeaweedFSClient
import com.google.inject.Inject
import context.ApiContext
import domain.users.{User, UserUpdateInput}
import org.apache.pekko.util.ByteString
import persistence.users.Users
import zio.ZIO

import java.util.UUID

class UserAvatarInteractor @Inject() (
    persistence: Users,
    seaweedFSClient: SeaweedFSClient,
    updateInteractor: UserUpdateInteractor
) {

  def uploadAvatar(
      userId: UUID,
      fileBytes: ByteString,
      contentType: String
  ): ZIO[ApiContext, Throwable, User] = for {
    context <- ZIO.service[ApiContext]
    user <- persistence.getById(userId)
    _ <- ZIO.succeed(println(s"Uploading avatar for user $userId"))
    _ <- ZIO.succeed(
      println(s"current context user is ${context.applicationContext.user}")
    )

    _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
      context.applicationContext.user,
      userId
    )

    _ <- user.avatarUrl match {
      case Some(oldUrl) =>
        seaweedFSClient.deleteAvatar(oldUrl).catchAll(_ => ZIO.unit)
      case None => ZIO.unit
    }

    avatarUrl <- seaweedFSClient.uploadAvatar(fileBytes, contentType, userId)

    updateInput = UserUpdateInput(avatarUrl = Some(avatarUrl))
    updatedUser <- updateInteractor.update(updateInput, user)
  } yield updatedUser

  def deleteAvatar(userId: UUID): ZIO[ApiContext, Throwable, User] = for {
    context <- ZIO.service[ApiContext]
    user <- persistence.getById(userId)

    _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
      context.applicationContext.user,
      userId
    )

    _ <- user.avatarUrl match {
      case Some(url) => seaweedFSClient.deleteAvatar(url)
      case None      => ZIO.unit
    }

    updateInput = UserUpdateInput(avatarUrl = Some(null))
    updatedUser <- updateInteractor.update(updateInput, user)
  } yield updatedUser
}
