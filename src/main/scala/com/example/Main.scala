package com.example

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ReplicatedShardingExtension
import akka.http.scaladsl.Http
import akka.persistence.typed.ReplicaId
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.{Failure, Success}


object Main {

  def main(args: Array[String]): Unit = {

    val extractedArgs =
      if (args.length == 2 && args.head.matches("""^\d+$""") && args(1).nonEmpty)
        Some((args.head.toInt, args(1)))
      else None

    extractedArgs match {
      case Some((port, replicaId)) =>
        val config = {
          ConfigFactory.parseString(
            s"""
              akka.remote.artery.canonical.port = $port
              akka.cluster.multi-data-center.self-data-center = $replicaId
            """
          ).withFallback(ConfigFactory.load("application.conf"))
        }
        implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "clustering", config)
        implicit val ec: ExecutionContextExecutor = system.executionContext

        val allReplicas: Set[ReplicaId] =
          config.getStringList("akka.cluster.replication-nodes")
            .asScala.toSet
            .map(ReplicaId)
        val replicatedSharding: ReplicatedShardingExtension = ReplicatedShardingExtension(system)

        val httpHost = config.getString("akka.remote.artery.canonical.hostname")
        val httpPort = port + 10000
        val shutdown = CoordinatedShutdown(system)

        val counterRouter = new CounterRouter(replicatedSharding, allReplicas, ReplicaId(replicaId))

        Http().newServerAt(httpHost, httpPort).bind(counterRouter.route)
          .onComplete{
            case Success(binding) =>
              val address = binding.localAddress
              system.log.info(
                "[START SERVER] online at http://{}:{}/",
                address.getHostString,
                address.getPort
              )
              shutdown.addTask(
                CoordinatedShutdown.PhaseClusterShardingShutdownRegion,
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

        case None => sys.error("Invalid arguments.")
      }
  }
}
