package com.example

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LoggingMagnet}
import akka.util.Timeout
import com.example.Users._

import java.util.{Date => UtilDate}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object CounterRouter {
}

final class CounterRouter(system: ActorSystem[_]) extends UsersMarshaller  {
  import com.example.Users._

  val sharding: ClusterSharding = ClusterSharding(system)
  implicit val timeout: Timeout = system.settings.config.getDuration("for-clustering.routes.ask-timeout").toMillis.millis

  def insert(id: Long, user: User): Future[Inserted] ={
    val ref = sharding.entityRefFor(Users.TypeKey, id.toString)
    ref.ask(Insert(user, new UtilDate(), _))
  }

  def find(id: Long): Future[User] ={
    val ref = sharding.entityRefFor(Users.TypeKey, id.toString)
    ref.ask(Show)
  }

  val router: Route = pathPrefix("user" / LongNumber) { id =>
    pathEndOrSingleSlash {
      get {
        onComplete(find(id)) {
          case Success(user) =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"username is ${user.name}, age is ${user.age}"))
          case Failure(exception) =>
            extractLog { log =>
              log.error(s"Cannot find user by id $id : ${exception.getMessage}")
              complete(HttpResponse(InternalServerError, entity = s"Cannot find user by id $id"))
            }
        }
      } ~
        post {
          entity(as[User]) { user =>
            onComplete(insert(id, user)) {
              case Success(inserted) =>
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"success to insert record. id ${inserted.id}."))
              case Failure(exception) =>
                extractLog { log =>
                  log.error(s"Failed to insert record : ${exception.getMessage}")
                  complete(HttpResponse(InternalServerError, entity = "Failed to insert record."))
                }
            }
          }
        }
    }
  }
}
