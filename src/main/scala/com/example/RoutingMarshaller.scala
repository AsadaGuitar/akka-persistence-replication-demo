package com.example

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import com.example.CounterRouter.PostUser

import spray.json.DefaultJsonProtocol
import scala.util.Try

import java.text.SimpleDateFormat
import java.util.{Date => UtilDate}


trait RoutingMarshaller extends DefaultJsonProtocol with SprayJsonSupport {

  import spray.json._
  import com.example.Users.User

  implicit object DateFormat extends JsonFormat[UtilDate] {
    def write(date: UtilDate) = JsString(dateToIsoString(date))

    def read(json: JsValue) = json match {
      case JsString(rawDate) =>
        parseIsoDateString(rawDate)
          .fold(deserializationError(s"Expected ISO Date format, got $rawDate"))(identity)
      case error => deserializationError(s"Expected JsString, got $error")
    }
  }

  private val localIsoDateFormatter = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  }

  private def dateToIsoString(date: UtilDate) =
    localIsoDateFormatter.get().format(date)

  private def parseIsoDateString(date: String): Option[UtilDate] =
    Try {
      localIsoDateFormatter.get().parse(date)
    }.toOption

  implicit val userMarshaller: RootJsonFormat[User] = jsonFormat3(User)
  implicit val postUserMarshaller: RootJsonFormat[PostUser] = jsonFormat2(PostUser)
}