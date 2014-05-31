package models

case class Quality(
  value: Int,
  // TODO : move to an enum or sealed object later
  valueType: String,
  channel: String,
  criteria: String,
  year: Int,
  domain: Option[String],
  place: Option[String])

object JsonFormats {
  import play.api.libs.json.Json
  import play.api.data._
  import play.api.data.Forms._

  // Generates Writes and Reads for Quality thanks to Json Macros
  implicit val qualityFormat = Json.format[Quality]
}
