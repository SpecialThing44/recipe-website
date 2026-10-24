package api.storage

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import org.apache.pekko.util.ByteString
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import scala.jdk.CollectionConverters.*
import zio.{Runtime, Unsafe}

class SeaweedFSClientSpec extends AnyFlatSpec with Matchers {
  it should "upload three versioned sizes directly to the filer without master assignments" in {
    val requests = new ConcurrentLinkedQueue[(String, String)]()
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", exchange => {
      requests.add(exchange.getRequestMethod -> exchange.getRequestURI.getPath)
      exchange.getRequestBody.readAllBytes()
      exchange.sendResponseHeaders(201, -1)
      exchange.close()
    })
    server.start()
    try {
      val base = s"http://127.0.0.1:${server.getAddress.getPort}"
      val client = new SeaweedFSClient(Configuration.from(Map(
        "seaweedfs.internalFilerUrl" -> base, "seaweedfs.publicFilerUrl" -> "https://images.example"
      )))
      val bytes = ByteString("image")
      val image = ProcessedImage(bytes, bytes, bytes, "webp")
      val urls = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(client.uploadRecipeImage(image, UUID.randomUUID())).getOrThrowFiberFailure()
      }
      val uploaded = requests.iterator().asScala.toSeq
      uploaded.map(_._1) shouldBe Seq("PUT", "PUT", "PUT")
      uploaded.map(_._2.split("/").dropRight(1).mkString("/")).distinct.size shouldBe 1
      urls.thumbnailUrl should endWith("recipe-thumbnail.webp")
      urls.mediumUrl should endWith("recipe-medium.webp")
      urls.largeUrl should endWith("recipe.webp")
    } finally server.stop(0)
  }
  it should "remove only image paths owned by the updated entity" in {
    val removed = new ConcurrentLinkedQueue[String]()
    val client = new SeaweedFSClient(Configuration.from(Map(
      "seaweedfs.internalFilerUrl" -> "http://internal.example", "seaweedfs.publicFilerUrl" -> "https://images.example"
    ))) {
      override def deleteFile(url: String): zio.Task[Unit] = zio.ZIO.succeed { removed.add(url); () }
    }
    val owned = "https://images.example/recipes/owner/version/small.webp"
    val urls = domain.users.AvatarUrls(owned,
      "https://images.example/recipes/victim/version/medium.webp",
      "https://images.example/recipes/owner/../victim/large.webp")
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(client.deleteImageUrls(urls, "/recipes/owner")).getOrThrowFiberFailure()
    }
    removed.iterator().asScala.toSeq shouldBe Seq(owned)
  }

}
