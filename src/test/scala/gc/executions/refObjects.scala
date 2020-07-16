package gc.executions

import gc.{ActorRef, AnyActorRef}

object DummyRef {
  def apply(owner: DummyName, target: DummyName): AnyActorRef = new ActorRef(Some(DummyToken()), Some(owner), target)
}

case class DummyToken(n: Int)

object DummyToken {
  private var count = 0
  def apply(): DummyToken = {
    val t = new DummyToken(count)
    count += 1
    t
  }
}