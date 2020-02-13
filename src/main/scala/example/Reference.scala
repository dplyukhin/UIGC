package example

import akka.actor.typed.ActorRef

case class Token(ref: ActorRef[Any], n: Int)
case class Reference(x: Token, from: ActorRef[Any], to: ActorRef[Any])
