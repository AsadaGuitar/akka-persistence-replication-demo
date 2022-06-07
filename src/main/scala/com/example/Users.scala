package com.example

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.text.SimpleDateFormat
import java.util.{Date => UtilDate}

object Users {

  case class User(name: String, age: Int, created: UtilDate)

  final case class State(user: Option[User] = None)

  sealed trait Command extends CborSerializable
  final case class Find(replyTo: ActorRef[Finding]) extends Command
  final case class Insert(name: String, age: Int, replyTo: ActorRef[Inserting]) extends Command

  sealed trait Event extends CborSerializable
  final case class Inserted(name: String, age: Int, created: UtilDate) extends Event
  final case object Found extends Event

  sealed trait Response extends CborSerializable
  final case class Inserting(msg: String)
  final case class Finding(result: Option[User])

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Counter")

  def initSharding(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] =
    ClusterSharding(system).init(Entity(TypeKey){ entityContext =>
      Users(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
    })

  private def dateFormat(date: UtilDate) ={
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    formatter.format(date)
  }

  private def commandHandler: (State, Command) => Effect[Event,State] = { (_, command) =>
    command match {
      case Insert(name, age, replyTo) =>
        val event = Inserted(name, age, new UtilDate())
        Effect.persist(event)
          .thenRun{ state =>
            replyTo ! Inserting(s"Created user at ${dateFormat(state.user.get.created)}.")
          }
      case Find(replyTo) =>
        Effect.persist(Found).thenRun(state => replyTo ! Finding(state.user))
    }
  }

  private def eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case Inserted(name, age, created) => State(Some(User(name, age, created)))
      case Found => state
    }
  }

  def apply(entityId: String, persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup{ ctx =>
    ctx.log.info("Starting `Users Actor`.")
    EventSourcedBehavior(
      persistenceId,
      emptyState = State(None),
      commandHandler,
      eventHandler
    )
  }
}