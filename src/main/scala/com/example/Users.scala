package com.example

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.example.Users.User
import spray.json.DefaultJsonProtocol
import java.util.{Date => UtilDate}

object Users {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Counter")

  case class UserRecord(user: User, created: UtilDate)
  var userRecord: Option[UserRecord] = None

  def initSharding(system: ActorSystem[_]): ActorRef[ShardingEnvelope[Command]] =
    ClusterSharding(system).init(Entity(TypeKey){ entityContext =>
      Users(entityContext.entityId)
    })

  def apply(entityId: String): Behavior[Command] = Behaviors.setup{ ctx =>
    ctx.log.info("Created actor `Users`.")
    Behaviors.receiveMessage{
      case Insert(user, created, replyTo) =>
        userRecord = Some(UserRecord(user, created))
        replyTo ! Inserted(entityId)
        Behaviors.same
      case Show(replyTo) =>
        replyTo ! userRecord.get.user
        Behaviors.same
    }
  }

  sealed trait Command extends CborSerializable
  final case class User(name: String, age: Int) extends CborSerializable
  final case class Insert(user: User, created: java.util.Date, replyTo: ActorRef[Inserted]) extends Command

  final case class Inserted(id: String) extends CborSerializable
  final case class Show(replyTo: ActorRef[User]) extends Command

}

trait CborSerializable

trait UsersMarshaller extends DefaultJsonProtocol with SprayJsonSupport{
  import spray.json._

  implicit val usersMarshaller: RootJsonFormat[User] = jsonFormat2(User)

}