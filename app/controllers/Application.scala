package controllers

import play.api._
import mvc._
import services.StreamingBodyParser.streamingBodyParser
import java.io.{InputStream, FileOutputStream, File}
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{UploadPartRequest, InitiateMultipartUploadRequest, ObjectMetadata, PutObjectRequest}

object Application extends Controller {
  val welcomeMsg = "Demonstrations of Streaming File Uploads for Play 2.1"

  def index = Action {
    Ok(views.html.index(welcomeMsg))
  }

  /** Higher-order function that accepts the unqualified name of the file to stream to and returns the output stream
    * for the new file. This example streams to a file. */
  def streamConstructor(filename: String, ignored: InputStream) = {
    val dir = new File(sys.env("HOME"), "uploadedFiles")
    dir.mkdirs()
    Option(new FileOutputStream(new File(dir, filename)))
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


  lazy val awsBucketName  = sys.env("awsBucketName")

  /** Higher-order function that accepts the unqualified name of the AWS S3 file to stream to and returns the output
    * stream for the new file. */
  def streamConstructorAWS(filename: String, inputStream: InputStream) = {
    val s3 = new AmazonS3Client(new AWSCredentials {
      def getAWSAccessKeyId = sys.env("awsAccessKey")

      def getAWSSecretKey = sys.env("awsSecretKey")
    })

    if (!s3.doesBucketExist(awsBucketName))
      s3.createBucket(awsBucketName)
    val uploadRequest = new InitiateMultipartUploadRequest(awsBucketName, filename)
    val initiateMultipartUploadResult = s3.initiateMultipartUpload(uploadRequest)
    val uploadId = initiateMultipartUploadResult.getUploadId
    //Option(s3.completeMultipartUpload())
  }

  def uploadAWS = Action(streamingBodyParser(streamConstructor)) { request =>
    val result = request.body.files(0).ref
    if (result.isRight) { // streaming succeeded
      val filename = result.right.get.filename
      Ok(s"File $filename successfully streamed.")
    } else { // file streaming failed
      Ok(s"Streaming error occurred: ${result.left.get.errorMessage}")
    }
  }
}
