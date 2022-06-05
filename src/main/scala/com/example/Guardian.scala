package com.example

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object Guardian {

  trait EmptyType

  def apply(httpPort: Int): Behavior[EmptyType] = Behaviors.setup{ ctx =>

    // Usersのシャーディングを初期化
    Users.initSharding(ctx.system)

    val counterRouter = new CounterRouter(ctx.system)
    ForClusterServer.startServer(counterRouter.router, httpPort, ctx.system)
    Behaviors.empty
  }
}
