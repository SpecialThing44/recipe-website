package api.users

import api.storage.{ImageProcessor, SeaweedFSClient}
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
    _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
      context.applicationContext.user,
      userId
    )

    processedImage <- ImageProcessor.processImage(fileBytes, contentType)

    seaweedUrls <- seaweedFSClient.uploadAvatar(processedImage, userId)

    avatarUrls = domain.users.AvatarUrls(
      thumbnail = seaweedUrls.thumbnailUrl,
      medium = seaweedUrls.mediumUrl,
      large = seaweedUrls.largeUrl
    )

    updateInput = UserUpdateInput(avatar = Some(avatarUrls))
    updatedUser <- updateInteractor
      .update(updateInput, user)
      .onError(_ =>
        seaweedFSClient.deleteImageUrls(avatarUrls, s"/avatars/$userId").ignore
      )
    _ <- ZIO.foreachDiscard(user.avatar)(urls =>
      seaweedFSClient.deleteImageUrls(urls, s"/avatars/$userId")
    )
  } yield updatedUser

  def deleteAvatar(userId: UUID): ZIO[ApiContext, Throwable, User] = for {
    context <- ZIO.service[ApiContext]
    user <- persistence.getById(userId)

    _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
      context.applicationContext.user,
      userId
    )

    updateInput = UserUpdateInput(removeAvatar = true)
    updatedUser <- updateInteractor.update(updateInput, user)
    _ <- ZIO.foreachDiscard(user.avatar)(urls =>
      seaweedFSClient.deleteImageUrls(urls, s"/avatars/$userId")
    )
  } yield updatedUser
}
