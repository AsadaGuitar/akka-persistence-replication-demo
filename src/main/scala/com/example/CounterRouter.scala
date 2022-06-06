package com.example

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.event.Logging.ErrorLevel
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, NotFound}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object CounterRouter {
  case class PostUser(name: String, age: Int)
}


final class CounterRouter(system: ActorSystem[_]) extends RoutingMarshaller  {
  import CounterRouter._
  import com.example.Users._

  val sharding: ClusterSharding = ClusterSharding(system)
  implicit val timeout: Timeout = system.settings.config.getDuration("for-clustering.routes.ask-timeout").toMillis.millis

  def insert(id: Long, postUser: PostUser): Future[Inserting] ={
    val ref = sharding.entityRefFor(Users.TypeKey, id.toString)
    ref.ask(Insert(postUser.name, postUser.age, _))
  }

  def find(id: Long): Future[Finding] ={
    val ref = sharding.entityRefFor(Users.TypeKey, id.toString)
    ref.ask(Find)
  }

  val router: Route = pathPrefix("user" / LongNumber) { id =>
    pathEndOrSingleSlash {
      get {
        onComplete(find(id)) {
          case Success(Finding(result)) =>
            result match {
              case Some(user) => complete(user)
              case None => complete(NotFound)
            }
          case Failure(exception) =>
            logRequest(s"An error occurred while searching for a user: ${exception.getMessage}", ErrorLevel) {
              complete(HttpResponse(InternalServerError))
            }
        }
      } ~
        post {
          entity(as[PostUser]) { user =>
            onComplete(insert(id, user)) {
              case Success(inserting) =>
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, inserting.msg))
              case Failure(exception) =>
                logRequest(s"An error occurred while posting user: ${exception.getMessage}", ErrorLevel) {
                  complete(HttpResponse(InternalServerError, entity = "Failed to insert record."))
                }
            }
          }
        }
    }
  }
}
