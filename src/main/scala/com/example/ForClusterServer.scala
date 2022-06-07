package com.example

import akka.Done
import akka.actor.{CoordinatedShutdown, Props}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object ForClusterServer {

  def startServer(router: Route, port: Int, system: ActorSystem[_]): Unit ={
    import akka.actor.typed.scaladsl.adapter._

    implicit val classicalSystem = system.toClassic
    implicit val ec = classicalSystem.dispatcher

    val shutdown = CoordinatedShutdown(system)

    Http().newServerAt("localhost", port).bind(router).onComplete{
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(
          "[START SERVER] online at http://{}:{}/",
          address.getHostString,
          address.getPort
        )
        shutdown.addTask(
          CoordinatedShutdown.PhaseServiceRequestsDone,
          "http-graceful-shutdown"
        ) { () =>
          binding.terminate(10.seconds).map { _ =>
            system.log.info(
              "[STOP SERVER] http://{}:{}/ graceful shutdown completed",
              address.getHostString,
              address.getPort
            )
            Done
          }
        }
      case Failure(exception) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", exception)
        system.terminate()
    }
  }

}
