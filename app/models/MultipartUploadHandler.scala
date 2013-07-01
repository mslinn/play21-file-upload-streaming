package models

import services.FileServiceS3
import fly.play.s3.MultipartItem
import play.api.Logger
import java.io.OutputStream
/**
 * This file manages the fie object being streamed to AWS S3
 * @author Mashhood Rastgar
 * Date: 7/1/13
 */
class MultipartUploadHandler(fileName: String) extends OutputStream {

  def name: String = fileName;
  var fileService = new FileServiceS3();
  /* The number for pieces the file is broken into, needed while uploading to S3 */
  var part: Int = 1
  /* upLoad maintains which file is being uploaded, issued by S3 */
  val uploadId: String = fileService.initiateMultipartUpload(name).fold(
    fail => {
      Logger.info("Unable to get uploadID :" + fail)
      ""
    },
    id => id
  )
  /* Contains the ETag for each part along with its number for the CompleteMultipartUpload call */
  var parts: List[MultipartItem] = List()
  /* Each part must be atleast 5MB before it can be uploaded (restriction by S3), so cached before uploading */
  var cache: Array[Byte] = Array()

  private def performUpload =
    fileService.uploadPart(name,part,uploadId,cache).fold(
      fail => Logger.info(fail),
      succ => parts = parts :+ succ
    )

  override def write(data: Array[Byte]) = {
    if(cache.length >= 5242880) {
      performUpload
      part += 1
      cache = Array()
    }

    cache = cache ++ data
  }

  override def write(data: Int) = {
     cache :+ data
  }

  override def close = {
    if (cache.length > 0) performUpload
    fileService.completeMultipartUpload(name, uploadId, parts).fold(
      fail =>
        Logger.info(fail)
       ,
      location =>location
    )
  }

}
