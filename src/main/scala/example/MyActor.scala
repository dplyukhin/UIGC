package example

import gc.{AbstractBehavior, ActorContext, ActorFactory, Behavior, Behaviors}

object MyActor {
  def apply() : ActorFactory[String] =
    Behaviors.setup(context => new MyActor(context))
}

class MyActor(context: ActorContext[String]) extends AbstractBehavior[String](context) {

  override def onMessage(msg: String): Behavior[String] =
    msg match {
      case "printit" =>
        val secondRef = context.spawn(MyActor(), "child")
        println(s"Second: $secondRef")
        this
    }
}
