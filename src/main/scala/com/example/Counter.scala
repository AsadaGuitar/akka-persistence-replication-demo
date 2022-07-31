package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.{ReplicatedEntityProvider, ReplicatedShardingExtension, ShardingEnvelope}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.typed.{PersistenceId, ReplicaId, ReplicationId}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplicatedEventSourcing}

import java.text.SimpleDateFormat
import java.util.{Date => UtilDate}


object Counter {

  final case class State(count: Int, created: UtilDate, modified: Option[UtilDate]) extends CborSerializable {
    def add(number: Int, modified: UtilDate): State =
      copy(count = count + number, created, modified = Some(modified))
  }

  sealed trait Command extends CborSerializable {
    def resourceId: String
  }
  final case class CountUp(resourceId: String, number: Int, replyTo: ActorRef[Counting]) extends Command
  final case class Find(resourceId: String, replyTo: ActorRef[Finding]) extends Command

  sealed trait Event extends CborSerializable
  final case class CountUpped(number: Int, modified: UtilDate) extends Event
  final case object Found extends Event

  sealed trait Response extends CborSerializable
  final case class Counting(msg: String)
  final case class Finding(number: Int, created: UtilDate, modified: Option[UtilDate])

  def getProvider(allReplicas: Set[ReplicaId]): ReplicatedEntityProvider[Command] =
    ReplicatedEntityProvider.perDataCenter("Users", allReplicas) { replicationId =>
      this.apply(replicationId, allReplicas)
    }

  private def dateFormat(date: UtilDate) ={
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    formatter.format(date)
  }

  private def commandHandler(ctx: ActorContext[Command]): (State, Command) => Effect[Event,State] = { (_, command) =>
    command match {
      case CountUp(_, number, replyTo) =>
        ctx.log.info("starting-command-phase[CountUp]: count up with number {}", number)
        val event = CountUpped(number, new UtilDate())
        Effect.persist(event)
          .thenRun{ state =>
            ctx.log.info(
              "does-event-phase[CountUpped]: count upped number = {}, created = {}, modified = {}",
              state.count, state.modified.fold("nothing")(dateFormat), dateFormat(state.created))
            val response = Counting(
              s"""counter is
                 |count = ${state.count},
                 |created = ${dateFormat(state.created)},
                 |modified = ${state.modified.fold("nothing")(dateFormat)}.""".stripMargin)
            replyTo ! response
          }
      case Find(_, replyTo) =>
        ctx.log.info("starting-command-phase[Find]")
        Effect.persist(Found)
          .thenRun{ state =>
            ctx.log.info(
              "does-event-phase[Found]: counter is count = {}, created = {}, modified = {}",
              state.count, dateFormat(state.created), state.modified.fold("nothing")(dateFormat))
            replyTo ! Finding(state.count, state.created, state.modified)
          }
    }
  }

  private def eventHandler(ctx: ActorContext[Command]): (State, Event) => State = { (state, event) =>
    event match {
      case CountUpped(number, modified) =>
        ctx.log.debug(
          "starting-event-phase[CountUpped]: number = {}, modified = {}",
          number, dateFormat(modified))
        state.add(number, modified)
      case Found =>
        ctx.log.debug("starting-event-phase[Found]")
        state
    }
  }

  def apply(replicationId: ReplicationId, allReplicaIds: Set[ReplicaId]): Behavior[Command] =
    Behaviors.setup{ ctx =>
      ReplicatedEventSourcing.commonJournalConfig(
        replicationId,
        allReplicaIds,
        CassandraReadJournal.Identifier
      ) { _ =>
        EventSourcedBehavior[Command,Event,State] (
          replicationId.persistenceId,
          emptyState = State(0, new UtilDate(), None),
          commandHandler(ctx),
          eventHandler(ctx)
        )
      }
    }

}