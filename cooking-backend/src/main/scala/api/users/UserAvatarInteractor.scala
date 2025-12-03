package api.users

import api.storage.{SeaweedFSClient, ImageProcessor, StorageAvatarUrls}
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

    // Delete old avatars if they exist
    _ <- user.avatar match {
      case Some(avatar) =>
        // Extract extension from old URL or default to jpg
        val extension = avatar.large.split("\\.").lastOption.getOrElse("jpg")
        seaweedFSClient.deleteAllAvatarSizes(userId, extension).catchAll(_ => ZIO.unit)
      case None => ZIO.unit
    }

    // Process the image into three sizes
    processedImage <- ImageProcessor.processImage(fileBytes, contentType)
    
    // Upload all three sizes
    seaweedUrls <- seaweedFSClient.uploadAvatar(processedImage, userId)

    // Create AvatarUrls object
    avatarUrls = domain.users.AvatarUrls(
      thumbnail = seaweedUrls.thumbnailUrl,
      medium = seaweedUrls.mediumUrl,
      large = seaweedUrls.largeUrl
    )

    // Update user with avatar URLs
    updateInput = UserUpdateInput(avatar = Some(avatarUrls))
    updatedUser <- updateInteractor.update(updateInput, user)
  } yield updatedUser

  def deleteAvatar(userId: UUID): ZIO[ApiContext, Throwable, User] = for {
    context <- ZIO.service[ApiContext]
    user <- persistence.getById(userId)

    _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
      context.applicationContext.user,
      userId
    )

    // Delete all avatar sizes if they exist
    _ <- user.avatar match {
      case Some(avatar) =>
        val extension = avatar.large.split("\\.").lastOption.getOrElse("jpg")
        seaweedFSClient.deleteAllAvatarSizes(userId, extension)
      case None => ZIO.unit
    }

    updateInput = UserUpdateInput(avatar = None)
    updatedUser <- updateInteractor.update(updateInput, user)
  } yield updatedUser
}
