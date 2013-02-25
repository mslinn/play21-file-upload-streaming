package controllers

import play.api._
import mvc._
import services._
import java.io.{InputStream, FileOutputStream, File}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._

object Application extends Controller {
  val welcomeMsg = "Demonstrations of Streaming File Uploads for Play 2.1"

  def index = Action {
    Ok(views.html.index(welcomeMsg))
  }

  /** Higher-order function that accepts the unqualified name of the file to stream to and returns the output stream
    * for the new file. This example streams to a file. */
  def streamConstructor(filename: String) = {
      val dir = new File(sys.env("HOME"), "uploadedFiles")
      dir.mkdirs()
      Option(new FileOutputStream(new File(dir, filename)))
    }

  def upload = Action(StreamingBodyParserFile.streamingBodyParser(streamConstructor)) { request =>
    val result = request.body.files(0).ref
    if (result.isRight) { // streaming succeeded
      val filename = result.right.get.filename
      Ok(s"File $filename successfully streamed.")
    } else { // file streaming failed
      Ok(s"Streaming error occurred: ${result.left.get.errorMessage}")
    }
  }


  def uploadAWS = Action(StreamingBodyParserAWS.streamingBodyParser(StreamingBodyParserAWS.streamConstructor)) { request =>
/*    val result = request.body.files(0).ref
    if (result.isRight) { // streaming succeeded
      val filename = result.right.get.filename
      Ok(s"File $filename successfully streamed.")
    } else { // file streaming failed
      Ok(s"Streaming error occurred: ${result.left.get.errorMessage}")
    }*/
    Ok
  }
}
