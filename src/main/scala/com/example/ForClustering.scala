package com.example

import akka.actor.AddressFromURIString
import akka.actor.typed.ActorSystem
import com.example.Guardian.EmptyType
import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.ListHasAsScala

object ForClustering {

  def main(args: Array[String]): Unit = {

    val seedNodePorts = ConfigFactory.load().getStringList("akka.cluster.seed-nodes")
      .asScala
      .flatMap { case AddressFromURIString(s) => s.port }

    val ports = args.headOption match {
      case Some(port) => Seq(port.toInt)
      case None       => seedNodePorts ++ Seq(0)
    }

    ports.foreach { port =>
      val httpPort =
        if (port > 0) 10000 + port
        else 0

      val config = configWithPort(port)
      ActorSystem[EmptyType](Guardian(httpPort), "ForClustering", config)
    }
  }

  private def configWithPort(port: Int): Config =
    ConfigFactory.parseString(s"""
       akka.remote.artery.canonical.port = $port
        """).withFallback(ConfigFactory.load())
}
