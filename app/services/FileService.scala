package services

import fly.play.s3.ACL
import fly.play.s3.AUTHENTICATED_READ
import fly.play.s3.BucketFile
import fly.play.s3.S3
import play.api.libs.concurrent.Execution.Implicits._
import scalaz.{Success => _, Failure => _, _}
import concurrent.Await
import scalaz.Scalaz._
import play.api.Play
import scala.concurrent.duration._
import scala.Some
import fly.play.s3.{Success => _, _}
import play.api.Play.current
import play.Logger

/**
 * This is a simple FileService wrapper to communicate with the S3 library
 * User: Mashhood Rastgar, Markus Jura
 * Date: 6/26/13
 *
 */

trait FileService {

  def add(fileName: String, mimeType: String, content: Array[Byte], fileType: FileType): Validation[String, String]
  def initiateMultipartUpload(fileName: String): Validation[String, String]
  def uploadPart(fileName: String, partNumber: Int, uploadId: String, content: Array[Byte]): Validation[String, MultipartItem]
  def completeMultipartUpload(fileName: String, uploadId: String, parts: List[MultipartItem]): Validation[String, String]
}

class FileServiceS3 extends FileService {
  //standard bucket
  val bucket = S3(Play.application.configuration.getString("aws.s3.bucket").get)

  /**
   * Add file to S3 without using multipart (just normal upload)
   */
  def add(fileName: String, mimeType: String, content: Array[Byte], fileType: FileType): Validation[String, String] = {
    //concatenate folder and filename and remove spaces
    val absoluteFileName = fileType.folder + fileName
    //add file to bucket
    val future = bucket + BucketFile(absoluteFileName, mimeType, content, fileType.acl, fileType.headers)
    val value = Await.result(future, 5 minutes)

    value.fold(
      failure => ("Error by adding a file occured: Filename: " + fileName + ", failure: " + failure).fail,
      s => absoluteFileName.success
    )
  }

  /* The following functions are used by the MultipartUploadHandler class as a demo */
  def initiateMultipartUpload(fileName: String): Validation[String, String] = {
    val future = bucket.initiateMultipartUpload(fileName)
    val value = Await.result(future, 1 minute)

    value.fold(
      failure => ("Unable to get upLoad ID : " + failure).fail,
      uploadId => {
        Logger.info("uploadId: " + uploadId)
        uploadId.success
      }
    )
  }

  def uploadPart(fileName: String, partNumber: Int, uploadId: String, content: Array[Byte]): Validation[String, MultipartItem] = {
    val future = bucket.uploadPart(fileName, partNumber, uploadId, content)
    val value = Await.result(future, 1 minute)
    value.fold(
      failure => ("Unable to upload part: " + failure).fail,
      multipartItem => multipartItem.success
    )
  }

  def completeMultipartUpload(fileName: String, uploadId: String, parts: List[MultipartItem]): Validation[String, String] = {
    val future = bucket.completeMultipartUpload(fileName, uploadId, parts)
    val value = Await.result(future, 1 minute)
    value.fold(
      failure => ("Unable to complete multipart upload: " + failure).fail,
      location => location.success
    )
  }
}

/**
 * FileType
 */
sealed trait FileType {

  def folder: String
  def acl: Option[ACL]
  def headers: Option[Map[String, String]] = None
}

case class ZIP(name: String) extends FileType {

  override val folder: String = {
    val buf = new StringBuilder ++= name ++= "/"
    buf.toString
  }
  override val acl: Option[ACL] = Some(AUTHENTICATED_READ)
}

case class File(name: String, contentType: String, content: Array[Byte])