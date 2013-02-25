package services

import play.api.mvc.{BodyParser, RequestHeader}
import play.api.mvc.BodyParsers.parse
import parse.Multipart.PartHandler
import play.api.mvc.MultipartFormData.FilePart
import java.io.{InputStream, OutputStream}
import play.api.Logger
import play.api.libs.iteratee.{Cont, Done, Input, Iteratee}
import com.amazonaws.services.s3.model.{InitiateMultipartUploadRequest, InitiateMultipartUploadResult, UploadPartRequest}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.auth.AWSCredentials
import java.util.UUID

object StreamingBodyParserAWS {

  lazy val awsBucketName  = sys.env("awsBucketName")

  /** Higher-order function that accepts the unqualified name of the AWS S3 file to stream to and returns the output
     * stream for the new file. New file name is a GUID; rename it to the desired name once the upload is finished like this:
     * s3.getObject("x", "y").setKey("z") // renames file y in bucket x to z
     */
   def streamConstructor: InitiateMultipartUploadResult = {
     val s3 = new AmazonS3Client(new AWSCredentials {
       def getAWSAccessKeyId = sys.env("awsAccessKey")

       def getAWSSecretKey = sys.env("awsSecretKey")
     })

     if (!s3.doesBucketExist(awsBucketName))
       s3.createBucket(awsBucketName)
     val filename = UUID.randomUUID.toString
     val uploadRequest = new InitiateMultipartUploadRequest(awsBucketName, filename)
     val initiateMultipartUploadResult = s3.initiateMultipartUpload(uploadRequest)
     val uploadId = initiateMultipartUploadResult.getUploadId
     initiateMultipartUploadResult
   }

  def streamingBodyParser(streamConstructor: (String) => Option[OutputStream]) = BodyParser { request =>
    // Use Play's existing multipart parser from play.api.mvc.BodyParsers.
    // The RequestHeader object is wrapped here so it can be accessed in streamingFilePartHandler
    parse.multipartFormData(new StreamingBodyParserAWS(streamConstructor).streamingFilePartHandler(request)).apply(request)
  }
}

class StreamingBodyParserAWS(streamConstructor: (String) => Option[OutputStream], s3: Option[AmazonS3Client]=None) {

  /** Custom implementation of a PartHandler, inspired by these Play mailing list threads:
   * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/WY548Je8VB0/dJkj3arlBigJ
   * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/n7yF6wNBL_s/wBPFHBBiKUwJ */
  def streamingFilePartHandler(request: RequestHeader): PartHandler[FilePart[Either[StreamingError, StreamingSuccess]]] = {
    parse.Multipart.handleFilePart {
      case parse.Multipart.FileInfo(partName, filename, contentType) =>
        // Holds any error message
        var errorMsg: Option[StreamingError] = None

          /* Create the output stream. If something goes wrong while trying to instantiate the output stream, assign the
             error message to the result reference, e.g. `result = Some(StreamingError("network error"))`
             and set the outputStream reference to `None`; the `Iteratee` will then do nothing and the error message will
             be passed to the `Action`. */
         val outputStream: Option[OutputStream] = try {
            // AWS stream constructor needs access to the input stream but I don't know how to get it
            streamConstructor(filename)
          } catch {
            case e: Exception => {
              Logger.error(e.getMessage)
              errorMsg = Some(StreamingError(e.getMessage))
              None
            }
          }

        // The fold method that actually does the parsing of the multipart file part.
        // Type A is expected to be Option[OutputStream]
        def fold[E, A](state: A)(f: (A, E) => A): Iteratee[E, A] = {
          def step(s: A)(i: Input[E]): Iteratee[E, A] = i match {
            case Input.EOF => Done(s, Input.EOF)
            case Input.Empty => Cont[E, A](i => step(s)(i))
            case Input.El(e) => {
              val s1 = f(s, e)
              errorMsg match { // if an error occurred during output stream initialisation, set Iteratee to Done
                case Some(result) => Done(s, Input.EOF)
                case None =>
                  //val uploadPartResult = s3.get.uploadPart(new UploadPartRequest().withUploadId(uploadId).withPartNumber())
                  Cont[E, A](i => step(s1)(i))
              }
            }
          }
          (Cont[E, A](i => step(state)(i)))
        }

        fold[Array[Byte], Option[OutputStream]](outputStream) { (os, data) =>
          os foreach { _.write(data) }
          os
        }.mapDone { os =>
          os foreach { _.close }
          errorMsg match {
            case Some(result) =>
              Logger.error(s"Streaming the file $filename failed: ${result.errorMessage}")
//              s3.completeMultipartUpload(putObjectResult)
              Left(result)

            case None =>
              Logger.info(s"$filename finished streaming.")
//              s3.completeMultipartUpload(putObjectResult)
              Right(StreamingSuccess(filename))
          }
        }
    }
  }
}
