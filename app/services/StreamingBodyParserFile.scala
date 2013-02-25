package services

import play.api.mvc.{BodyParser, RequestHeader}
import play.api.mvc.BodyParsers.parse
import parse.Multipart.PartHandler
import play.api.mvc.MultipartFormData.FilePart
import java.io.OutputStream
import play.api.Logger
import play.api.libs.iteratee.{Cont, Done, Input, Iteratee}

object StreamingBodyParserFile {

  def streamingBodyParser(streamConstructor: String => Option[OutputStream]) = BodyParser { request =>
    // Use Play's existing multipart parser from play.api.mvc.BodyParsers.
    // The RequestHeader object is wrapped here so it can be accessed in streamingFilePartHandler
    parse.multipartFormData(new StreamingBodyParserFile(streamConstructor).streamingFilePartHandler(request)).apply(request)
  }
}

class StreamingBodyParserFile(streamConstructor: String => Option[OutputStream]) {

  /** Custom implementation of a PartHandler, inspired by these Play mailing list threads:
   * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/WY548Je8VB0/dJkj3arlBigJ
   * https://groups.google.com/forum/#!searchin/play-framework/PartHandler/play-framework/n7yF6wNBL_s/wBPFHBBiKUwJ */
  def streamingFilePartHandler(request: RequestHeader): PartHandler[FilePart[Either[StreamingError, StreamingSuccess]]] = {
    parse.Multipart.handleFilePart {
      case parse.Multipart.FileInfo(partName, filename, contentType) =>
        // Reference to hold the error message
        var errorMsg: Option[StreamingError] = None

          /* Create the output stream. If something goes wrong while trying to instantiate the output stream, assign the
             error message to the result reference, e.g. `result = Some(StreamingError("network error"))`
             and set the outputStream reference to `None`; the `Iteratee` will then do nothing and the error message will
             be passed to the `Action`. */
         val outputStream: Option[OutputStream] = try {
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
                case None => Cont[E, A](i => step(s1)(i))
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
              Left(result)

            case None =>
              Logger.info(s"$filename finished streaming.")
              Right(StreamingSuccess(filename))
          }
        }
    }
  }
}
