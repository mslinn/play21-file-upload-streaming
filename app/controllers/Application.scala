package controllers

import play.api._
import mvc._
import services.StreamingBodyParser.streamingBodyParser
import java.io.{FileOutputStream, File}
import models.MultipartUploadHandler

object Application extends Controller {
  val welcomeMsg = "Demonstration of Streaming File Upload for Play 2.1 with AWS S3"

  def index = Action {
    Ok(views.html.index(welcomeMsg))
  }

  /** Higher-order function that accepts the unqualified name of the file to stream to and returns the output stream
    * for the new file. This example streams to a file, but streaming to AWS S3 is also possible
    *
    * To store in more specific folder in S3, just add the path when passing to the function eg "test/path" + filename
    *
    * */
  def streamConstructor(filename: String) = {
    Option(new MultipartUploadHandler(filename))
  }

  def upload = Action(streamingBodyParser(streamConstructor)) { request =>
    val params = request.body.asFormUrlEncoded // you can extract request parameters for whatever your app needs
    val result = request.body.files(0).ref
    if (result.isRight) { // streaming succeeded
      val filename = result.right.get.filename
      Ok(s"File $filename successfully streamed.")
    } else { // file streaming failed
      Ok(s"Streaming error occurred: ${result.left.get.errorMessage}")
    }
  }
}
