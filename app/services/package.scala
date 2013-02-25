package object services {
  case class StreamingSuccess(filename: String)
  case class StreamingError(errorMessage: String)
}
