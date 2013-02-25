package services

import play.api.mvc.{BodyParser, RequestHeader}
import play.api.mvc.BodyParsers.parse
import parse.Multipart.PartHandler
import play.api.mvc.MultipartFormData.FilePart
import play.api.Logger
import play.api.libs.iteratee.{Cont, Done, Input, Iteratee}
import com.amazonaws.services.s3.model.{InitiateMultipartUploadRequest, UploadPartRequest}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.util.Md5Utils
import java.io.ByteArrayInputStream

/** The last chunk is a regular chunk, with the exception that its length is zero.
  * @see http://en.wikipedia.org/wiki/Chunked_transfer_encoding
  * @see http://stackoverflow.com/questions/8653146/can-i-stream-a-file-upload-to-s3-without-a-content-length-header
  *
  * JQuery (XHR) - not tested, not sure how much work this would be.
  * Chunked file uploads are only supported by browsers with support for XHR file uploads and the Blob API, which
  * includes Google Chrome and Mozilla Firefox 4+.
  * To upload large files in smaller chunks, set the maxChunkSize option (see Options) to a preferred maximum
  * chunk size in bytes:
  * $('#fileupload').fileupload({
  *     maxChunkSize: 10000000 // 10 MB
  * );
  * For chunked uploads to work in Mozilla Firefox 4-6 (XHR upload capable Firefox versions prior to Firefox 7), the
  * multipart option also has to be set to false - see the Options documentation on maxChunkSize for an explanation.
  * @see https://github.com/blueimp/jQuery-File-Upload/wiki/Chunked-file-uploads
  * @see https://github.com/blueimp/jQuery-File-Upload/wiki/Options
  * @param defaultMaxUploadFileSize is enforced if browser does not send Content-Length header */
class Uploader(filename: String, defaultMaxUploadFileSize: Long = 5000000)(implicit request: RequestHeader) {
  val AwsMaxFileUploadSize: Long = Long.MaxValue // this is as close to 5TB as we can specify with a Long (AWS limitation)
  val AwsMinChunkSize: Long = 5000000 // 5MB (AWS limitation)

  val s3 = new AmazonS3Client(new AWSCredentials {
    val getAWSAccessKeyId = sys.env("awsAccessKey")

    val getAWSSecretKey = sys.env("awsSecretKey")
  })

  val awsBucketName  = sys.env("awsBucketName").toLowerCase // bucket names must be lower case
  if (!s3.doesBucketExist(awsBucketName)) // upload will throw exception if bucket belongs to another account
    s3.createBucket(awsBucketName)

  val uploadRequest = new InitiateMultipartUploadRequest(awsBucketName, filename)
  private val initiateMultipartUploadResult = s3.initiateMultipartUpload(uploadRequest)
  val uploadId = initiateMultipartUploadResult.getUploadId

  // some clients do not provide Content-Length
  val contentLength = request.headers.get("Content-Length").
    getOrElse(math.min(AwsMaxFileUploadSize, defaultMaxUploadFileSize).toString).toInt
  val chunkSize = math.ceil(math.max(AwsMinChunkSize, contentLength / 10000)).toInt // last chunk can be smaller
  // apparently some/all clients provide a zero-sized final chunk. We'll see if this is true.

  private val buffer = Array.ofDim[Byte](chunkSize)

  private var partNumber = 0

  private var bufPos = 0

  private var totalBytes = 0

  /** AWS S3 parts can be 5 MB to 5 GB in size; last part can be <5 MB.
    * Max of 10,000 parts, for a total of 5TB max upload.
    * Look at the Content-Length header for an approximation of the uploaded file size */
  def write(byteArray: Array[Byte], length: Int): Unit = {
    Array.copy(byteArray, 0, buffer, bufPos, length)
    bufPos = bufPos + length
    totalBytes = totalBytes + length
    if (bufPos >= chunkSize)
      flush(totalBytes>=contentLength)
  }

  def flush(isLastPart: Boolean = false): Unit = {
    partNumber = partNumber + 1
    val inputStream = new ByteArrayInputStream(buffer)
    val uploadPartRequest = new UploadPartRequest().withBucketName(awsBucketName).withKey(filename).
      withUploadId(uploadId).withPartNumber(partNumber). // computes incorrectly: withMD5Digest(Md5Utils.computeMD5Hash(buffer).toString).
      withLastPart(isLastPart).withInputStream(inputStream)
    inputStream.close()
    val uploadPartResult = s3.uploadPart(uploadPartRequest)
    // if callbacks were used, this could be used to verify that all the parts were acknowledged:
    //val partETag = uploadPartResult.getPartETag.getPartNumber
  }
}

object StreamingBodyParserAWS {

  /** Higher-order function that accepts the unqualified name of the AWS S3 file to stream to and returns the output
     * stream for the new file. */
  def streamConstructor(filename: String, request: RequestHeader): Uploader = new Uploader(filename)(request)

  def streamingBodyParser(streamConstructor: (String, RequestHeader) => Uploader) = BodyParser { implicit request =>
    // Use Play's existing multipart parser from play.api.mvc.BodyParsers.
    // The RequestHeader object is wrapped here so it can be accessed in streamingFilePartHandler
    parse.multipartFormData(new StreamingBodyParserAWS(streamConstructor).streamingFilePartHandler(request)).apply(request)
  }
}

/** @see http://awsdocs.s3.amazonaws.com/S3/latest/s3-dg.pdf page 148 (numbered as page 140) */
class StreamingBodyParserAWS(streamConstructor: (String, RequestHeader) => Uploader, s3: Option[AmazonS3Client]=None) {

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
         val maybeUploader: Option[Uploader] = try {
            // AWS stream constructor needs access to the input stream but I don't know how to get it
            Some(streamConstructor(filename, request))
          } catch {
            case e: Exception => {
              Logger.error(e.getMessage)
              errorMsg = Some(StreamingError(e.getMessage))
              None
            }
          }

        // The fold method that actually does the parsing of the multipart file part.
        // Type A is expected to be Option[Uploader]
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

        fold[Array[Byte], Option[Uploader]](maybeUploader) { (uploader, byteArray) =>
          uploader foreach { _.write(byteArray, byteArray.length) }
          uploader
        }.mapDone { os =>
          os foreach { _.flush(true) }
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
