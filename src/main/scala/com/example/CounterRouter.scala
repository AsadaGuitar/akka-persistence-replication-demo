package com.example

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.{ReplicatedSharding, ReplicatedShardingExtension}
import akka.event.Logging
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, NotFound}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.persistence.typed.ReplicaId
import akka.util.Timeout
import com.example.Counter.{CountUp, Counting, Find, Finding}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}


class CounterRouter(replicatedSharding: ReplicatedShardingExtension, allReplicas: Set[ReplicaId], replicaId: ReplicaId)(implicit system: ActorSystem[_]) {

  implicit val timeout: Timeout = system.settings.config.getDuration("for-clustering.routes.ask-timeout").toMillis.millis
  implicit val ec: ExecutionContext = system.executionContext

  private val counterReplicatedSharding = replicatedSharding.init(Counter.getProvider(allReplicas))

  private def findCounter(resourceId: String): Future[Finding] =
    counterReplicatedSharding.entityRefsFor(resourceId)(replicaId).ask[Finding](replyTo => Find(resourceId, replyTo))

  private def countUp(resourceId: String, number: Int): Future[Counting] =
    counterReplicatedSharding.entityRefsFor(resourceId)(replicaId).ask[Counting](replyTo => CountUp(resourceId, number, replyTo))

  def route: Route = {
    pathPrefix(Segment) { resourceId =>
      pathEndOrSingleSlash {
        get {
          onComplete(findCounter(resourceId)) {
            case Success(finding) =>
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Current counter number = ${finding.number}."))
            case Failure(exception) =>
              logRequest("find-counter", Logging.ErrorLevel) {
                system.log.error("Failed to retrieve counter. {}", exception.getMessage)
                complete(InternalServerError)
              }
          }
        }
      } ~
        pathPrefix(IntNumber) { number =>
          pathEndOrSingleSlash {
            post {
              onComplete(countUp(resourceId, number)) {
                case Success(counting) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, counting.msg))
                case Failure(exception) =>
                  logRequest("countUp-counter", Logging.ErrorLevel) {
                    system.log.error("Failed to retrieve counter. {}", exception.getMessage)
                    complete(InternalServerError)
                  }
              }
            }
          }
        }
    }
  }
}

